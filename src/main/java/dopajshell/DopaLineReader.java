package dopajshell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Cursor;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Every dependency on JLine internals lives in this class (tested with JLine 3.30).
 *
 * <p>Protected hooks of LineReaderImpl: {@code getWidget} (before/after comparison of each
 * key), {@code getDisplayedBufferWithPrompts} (spark trail after the input and the effect
 * strip below it), {@code redisplay} (the HUD as the right prompt), {@code cleanup} (records
 * how the submitted input looks on screen). A daemon thread calls the public
 * {@code callWidget(REDISPLAY)} 30 times a second while something is animating.
 */
public final class DopaLineReader extends LineReaderImpl {
    static final double TICK = 1 / 30.0;
    static final long CURSOR_TIMEOUT_MS = 300;
    private static final Pattern CPR = Pattern.compile("\033\\[(\\d+);(\\d+)R");

    private final InputFx fx;
    private final BooleanSupplier active;
    private final DopaHighlighter highlighter;
    private volatile boolean finishing;
    private volatile boolean broken;
    private String secondaryPrompt = "";
    private int promptRow = -1;
    private InputShot lastShot;
    private Thread ticker;

    public DopaLineReader(Terminal terminal, InputFx fx, BooleanSupplier active, DopaHighlighter highlighter) {
        super(terminal, "dopajshell", null);
        this.fx = fx;
        this.active = active;
        this.highlighter = highlighter;
        setHighlighter(highlighter);
    }

    public void setSecondaryPrompt(String s) {
        secondaryPrompt = s;
        setVariable(SECONDARY_PROMPT_PATTERN, s);
    }

    boolean keys() {
        return !broken && fx != null && active.getAsBoolean() && fx.lively();
    }

    void broken(Throwable e) {
        Debug.log(e);
        broken = true;
    }

    /**
     * Before each read: keep a lane of blank rows under the prompt for the effect strip,
     * and remember the prompt's screen row so the strip never scrolls the screen.
     */
    public void beforeRead() {
        fx.resetLine();
        promptRow = -1;
        if (!keys()) return;
        int n = InputFx.STRIP_ROWS;
        Term.write("\r\n".repeat(n) + "\033[" + n + "A");
        // Raw mode so the terminal's reply is not echoed (POSIX terminals are still cooked here).
        // Skip the query when keys are already typed ahead: reading the reply would swallow them.
        Term.raw(() -> {
            if (Term.inputPending()) return;
            try {
                Cursor c = cursorPosition(getTerminal(), CURSOR_TIMEOUT_MS);
                if (c != null) promptRow = c.getY();
            } catch (RuntimeException e) {
                promptRow = -1;
            }
        });
        startTicker();
    }

    private synchronized void startTicker() {
        if (ticker != null) return;
        ticker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep((long) (TICK * 1000));
                    if (!keys() || !isReading()) continue;
                    if (fx.animating(fx.now())) callWidget(REDISPLAY);
                } catch (InterruptedException e) {
                    return;
                } catch (IllegalStateException e) {
                    // not reading any more
                } catch (RuntimeException e) {
                    broken(e);
                }
            }
        }, "dopa-tick");
        ticker.setDaemon(true);
        ticker.start();
    }

    /** The shot of the input that produced code, if it is still on screen. */
    public InputShot takeInput(String code) {
        InputShot shot = lastShot;
        lastShot = null;
        if (shot == null || !shot.code.strip().equals(code.strip())) return null;
        if (shot.size() > Term.rows() - 3) return null;
        return shot;
    }

    // ---- hooks ---------------------------------------------------------------

    @Override
    protected Widget getWidget(Object binding) {
        Widget w = super.getWidget(binding);
        if (!keys()) return w;
        String name = binding instanceof Reference r ? r.name() : null;
        return () -> {
            if ("self-insert".equals(name) && morePending()) {
                // keep pastes cheap: no layout work, just count
                boolean res = w.apply();
                fx.pasting();
                return res;
            }
            InputFx.Snap before;
            int prevX;
            try {
                before = new InputFx.Snap(buf.toString(), buf.cursor());
                prevX = layout().cursorX();
            } catch (RuntimeException e) {
                broken(e);
                return w.apply();
            }
            boolean res = w.apply();
            try {
                if (!finishing) fx.onKey(name, before, new InputFx.Snap(buf.toString(), buf.cursor()), prevX, layout().cursorX());
            } catch (RuntimeException e) {
                broken(e);
            }
            return res;
        };
    }

    private boolean morePending() {
        try {
            return getTerminal().reader().available() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public AttributedString getDisplayedBufferWithPrompts(List<AttributedString> secondaryPrompts) {
        if (!keys() || finishing || post != null) return super.getDisplayedBufferWithPrompts(secondaryPrompts);
        AttributedString full = super.getDisplayedBufferWithPrompts(secondaryPrompts);
        try {
            double t = fx.now();
            InputFx.Layout lay = layout();
            AttributedStringBuilder sb = new AttributedStringBuilder().append(full);
            boolean atEnd = buf.cursor() == buf.length();
            int hudRoom = rightPrompt.columnLength() > 0 && lay.cursorX() < size.getColumns() ? rightPrompt.columnLength() + 1 : 0;
            InputFx.Layout trailLay = new InputFx.Layout(lay.cursorX(), lay.rowEndX(), lay.screenWidth() - hudRoom);
            String trail = fx.trailRender(trailLay, atEnd && onFirstRow(), t);
            if (trail == null && atEnd && !onFirstRow()) trail = fx.trailRender(lay, true, t);
            if (trail != null) sb.append(AttributedString.fromAnsi(trail));
            if (roomForStrip()) {
                String[] strip = fx.stripRender(lay, t);
                if (strip != null) {
                    sb.append("\n");
                    sb.append(AttributedString.fromAnsi(String.join("\n", strip)));
                }
            }
            return sb.toAttributedString();
        } catch (RuntimeException e) {
            broken(e);
            return full;
        }
    }

    @Override
    protected void redisplay(boolean flush) {
        lock.lock();
        try {
            if (keys() && !finishing) {
                try {
                    String hud = fx.hudRender(layout(), fx.now());
                    rightPrompt = hud == null ? AttributedString.EMPTY : AttributedString.fromAnsi(hud);
                } catch (RuntimeException e) {
                    broken(e);
                    rightPrompt = AttributedString.EMPTY;
                }
            } else {
                rightPrompt = AttributedString.EMPTY;
            }
            super.redisplay(flush);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void cleanup() {
        finishing = true;
        try {
            capture();
            super.cleanup();
        } finally {
            finishing = false;
        }
    }

    private void capture() {
        try {
            String code = buf.toString();
            int sw = Math.max(size.getColumns(), 1);
            String[] lines = code.split("\n", -1);
            List<String> prompts = new ArrayList<>();
            List<String> colored = new ArrayList<>();
            String[] coloredLines = highlighter.syntax(code).toAnsi(getTerminal()).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                prompts.add(i == 0 ? prompt.toAnsi(getTerminal()) : secondaryPrompt);
                colored.add(i < coloredLines.length ? coloredLines[i] : lines[i]);
            }
            lastShot = InputShot.of(prompts, colored, List.of(lines), code, sw);
        } catch (RuntimeException e) {
            Debug.log(e);
            lastShot = null;
        }
    }

    // ---- layout ------------------------------------------------------------

    private int promptWidth(int lineIdx) {
        return lineIdx == 0 ? prompt.columnLength() : Term.strWidth(secondaryPrompt);
    }

    InputFx.Layout layout() {
        String b = buf.toString();
        int cur = Math.min(buf.cursor(), b.length());
        int sw = Math.max(size.getColumns(), 1);
        String upTo = b.substring(0, cur);
        int li = (int) upTo.chars().filter(c -> c == '\n').count();
        int ls = upTo.lastIndexOf('\n') + 1;
        int le = b.indexOf('\n', cur);
        if (le < 0) le = b.length();
        int pw = promptWidth(li);
        int x = pw + Term.strWidth(b.substring(ls, cur));
        int end = pw + Term.strWidth(b.substring(ls, le));
        int rowStart = (x / sw) * sw;
        return new InputFx.Layout(x % sw, Math.min(end - rowStart, sw), sw);
    }

    /** Is the cursor on the first physical row of the input (where the HUD sits)? */
    private boolean onFirstRow() {
        String b = buf.toString();
        int cur = Math.min(buf.cursor(), b.length());
        if (b.substring(0, cur).indexOf('\n') >= 0) return false;
        return promptWidth(0) + Term.strWidth(b.substring(0, cur)) < Math.max(size.getColumns(), 1);
    }

    /** Physical rows the input occupies. */
    private int inputRows() {
        int sw = Math.max(size.getColumns(), 1);
        String[] lines = buf.toString().split("\n", -1);
        int rows = 0;
        for (int i = 0; i < lines.length; i++) rows += (promptWidth(i) + Term.strWidth(lines[i])) / sw + 1;
        return rows;
    }

    private boolean roomForStrip() {
        if (promptRow < 0) return inputRows() == 1;
        int screenRows = size.getRows() > 0 ? size.getRows() : Term.rows();
        int lastRow = promptRow + inputRows() - 1;
        return screenRows - 1 - lastRow >= InputFx.STRIP_ROWS;
    }

    /**
     * The cursor position (0-based), or null when the terminal does not answer in time.
     *
     * <p>Not {@code Terminal.getCursorPosition}: that reads without a timeout, and when the
     * non-blocking reader's pump thread has just been woken by a peek (Term.inputPending) it
     * can take the reply while this thread blocks on the raw stream, holding the reader's
     * lock: the prompt never comes back. Timed reads always go through the pump thread.
     */
    static Cursor cursorPosition(Terminal t, long timeoutMs) {
        Term.write("\033[6n");
        NonBlockingReader r = t.reader();
        StringBuilder sb = new StringBuilder();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        try {
            while (sb.length() < 32) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) return null;
                int ch = r.read(left);
                if (ch < 0) return null; // timeout or EOF
                sb.append((char) ch);
                Matcher m = CPR.matcher(sb);
                if (m.find()) return new Cursor(Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)) - 1);
            }
        } catch (IOException | RuntimeException e) {
            Debug.log(e);
        }
        return null;
    }
}
