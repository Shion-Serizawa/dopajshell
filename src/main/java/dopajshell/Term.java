package dopajshell;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.jline.utils.WCWidth;

/**
 * Terminal capabilities and the single writer lock shared by everything that draws
 * transient decorations.
 */
public final class Term {
    private Term() {}

    /** Something transient on screen that must get out of the way when the user's program writes. */
    public interface Overlay {
        Thread owner();
        /** Called under LOCK right before program output reaches the terminal. */
        void yieldToOutput();
    }

    public static final Object LOCK = new Object();

    private static volatile Terminal terminal;
    private static volatile boolean interactive;
    /** Guarded by LOCK. */
    static Overlay overlay;
    /** Test hook: rendering without a terminal. */
    static volatile int fakeRows = 0, fakeCols = 0;

    public static void init(Terminal t, boolean isInteractive) {
        terminal = t;
        interactive = isInteractive;
        glyphs.clear();
    }

    public static Terminal terminal() { return terminal; }
    public static boolean interactive() { return interactive && terminal != null; }

    public static void overlay(Overlay o) {
        synchronized (LOCK) { overlay = o; }
    }

    public static Overlay overlay() {
        synchronized (LOCK) { return overlay; }
    }

    public static void clearOverlay(Overlay o) {
        synchronized (LOCK) {
            if (overlay == o) overlay = null;
        }
    }

    public static int[] size() {
        if (fakeRows > 0) return new int[] {fakeRows, fakeCols};
        int rows = 0, cols = 0;
        Terminal t = terminal;
        if (t != null) {
            try {
                Size s = t.getSize();
                rows = s.getRows();
                cols = s.getColumns();
            } catch (RuntimeException e) {
                // fall through
            }
        }
        if (rows <= 0) rows = envInt("LINES", 24);
        if (cols <= 0) cols = envInt("COLUMNS", 80);
        return new int[] {rows, cols};
    }

    public static int rows() { return size()[0]; }
    public static int cols() { return size()[1]; }

    private static int envInt(String name, int dflt) {
        try {
            String v = System.getenv(name);
            return v == null ? dflt : Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public static Color.Depth depth(Config config) {
        if (noColor() || "none".equals(config.color)) return Color.Depth.NONE;
        switch (config.color) {
            case "truecolor": return Color.Depth.TRUECOLOR;
            case "256": return Color.Depth.C256;
            case "16": return Color.Depth.C16;
            default: break;
        }
        String ct = String.valueOf(System.getenv("COLORTERM"));
        if (ct.contains("truecolor") || ct.contains("24bit")) return Color.Depth.TRUECOLOR;
        // Windows Terminal, VS Code and ConEmu all do 24-bit color.
        if (System.getenv("WT_SESSION") != null || "vscode".equals(System.getenv("TERM_PROGRAM"))
                || System.getenv("ConEmuANSI") != null) return Color.Depth.TRUECOLOR;
        if (isWindows() && System.getenv("TERM") == null) return Color.Depth.TRUECOLOR;
        if (String.valueOf(System.getenv("TERM")).contains("256")) return Color.Depth.C256;
        return Color.Depth.C16;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static boolean noColor() {
        String nc = System.getenv("NO_COLOR");
        return nc != null && !nc.isEmpty();
    }

    public static void write(String s) {
        Terminal t = terminal;
        if (t == null) return;
        synchronized (LOCK) {
            try {
                PrintWriter w = t.writer();
                w.write(s);
                w.flush();
            } catch (RuntimeException e) {
                // the terminal went away; nothing to decorate
            }
        }
    }

    /** Write without taking LOCK (caller holds it). */
    static void writeLocked(String s) {
        Terminal t = terminal;
        if (t == null) return;
        try {
            PrintWriter w = t.writer();
            w.write(s);
            w.flush();
        } catch (RuntimeException e) {
            // ignore
        }
    }

    public static int charWidth(int cp) {
        if (cp < 0x80) return cp < 0x20 ? 0 : 1;
        int w = WCWidth.wcwidth(cp);
        return w < 0 ? 1 : w;
    }

    public static int strWidth(String s) {
        int w = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\033') {
                int j = i + 1;
                if (j < n && s.charAt(j) == '[') {
                    j++;
                    while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == ';' || s.charAt(j) == '?')) j++;
                    i = j + 1;
                    continue;
                }
                i = j;
                continue;
            }
            int cp = s.codePointAt(i);
            w += charWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    private static final Map<String, String> glyphs = new HashMap<>();

    /**
     * First candidate that renders one column wide on this terminal. A null candidate
     * means "none of the above": glyph("▀", null) may be null.
     */
    public static String glyph(String... candidates) {
        String key = String.join("\u0000", java.util.Arrays.stream(candidates).map(String::valueOf).toList());
        synchronized (glyphs) {
            if (glyphs.containsKey(key)) return glyphs.get(key);
            String pick = candidates[candidates.length - 1];
            for (String c : candidates) {
                if (c == null || ascii(c) || (utf8() && c.codePoints().allMatch(cp -> charWidth(cp) == 1))) {
                    pick = c;
                    break;
                }
            }
            glyphs.put(key, pick);
            return pick;
        }
    }

    private static boolean ascii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 0x80) return false;
        return true;
    }

    public static boolean utf8() {
        if (isWindows()) return true; // JLine writes UTF-16 to the console
        Terminal t = terminal;
        if (t != null && t.encoding() != null) return t.encoding().name().toUpperCase().contains("UTF");
        String lang = System.getenv("LC_ALL") + System.getenv("LC_CTYPE") + System.getenv("LANG");
        return lang.toUpperCase().contains("UTF");
    }

    /** Keys typed during an effect cut it short; they stay queued for the line editor. */
    public static boolean inputPending() {
        Terminal t = terminal;
        if (t == null) return false;
        try {
            // A POSIX tty in canonical (cooked) mode ignores the read timeout, so peek would block
            // until Enter. Peek in raw mode instead; the key stays queued either way.
            if (t.getAttributes().getLocalFlag(Attributes.LocalFlag.ICANON)) {
                boolean[] pending = {false};
                raw(() -> pending[0] = peek(t));
                return pending[0];
            }
            return peek(t);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean peek(Terminal t) {
        try {
            NonBlockingReader r = t.reader();
            return r.peek(1) >= 0;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Run with the terminal in raw mode (no echo; keys stay buffered). */
    public static void raw(Runnable body) {
        Terminal t = terminal;
        if (t == null) {
            body.run();
            return;
        }
        Attributes saved;
        try {
            saved = t.enterRawMode();
        } catch (RuntimeException e) {
            body.run();
            return;
        }
        try {
            body.run();
        } finally {
            try {
                t.setAttributes(saved);
            } catch (RuntimeException e) {
                // ignore
            }
        }
    }

    public static void bell() { write("\007"); }

    /** Called (under LOCK) by the output tap before user output hits the terminal. */
    static void yieldToOutputLocked() {
        Overlay ov = overlay;
        if (ov == null || ov.owner() == Thread.currentThread()) return;
        ov.yieldToOutput();
    }

    public static String stripAnsi(String s) {
        return s.replaceAll("\033\\[[\\d;?]*[A-Za-z]", "");
    }
}
