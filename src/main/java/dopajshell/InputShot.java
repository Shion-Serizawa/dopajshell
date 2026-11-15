package dopajshell;

import java.util.ArrayList;
import java.util.List;

/** Snapshot of the submitted input as it sits on screen right above the cursor. */
public final class InputShot {
    /** Physical rows (with SGR) exactly as they were printed. */
    public final List<String> rows;
    public final List<String> plain;
    /** Per logical line: prompt width and the code on that line. */
    public final List<Integer> promptWidths;
    public final List<String> lines;
    public final String code;
    public final int screenWidth;

    public InputShot(List<String> rows, List<Integer> promptWidths, List<String> lines, String code, int screenWidth) {
        this.rows = List.copyOf(rows);
        List<String> p = new ArrayList<>();
        for (String r : rows) p.add(Term.stripAnsi(r));
        this.plain = List.copyOf(p);
        this.promptWidths = List.copyOf(promptWidths);
        this.lines = List.copyOf(lines);
        this.code = code;
        this.screenWidth = Math.max(screenWidth, 1);
    }

    /**
     * Lay out prompt + colored code lines the way the line editor printed them
     * (wrapping at the screen width).
     */
    public static InputShot of(List<String> prompts, List<String> coloredLines, List<String> lines, String code, int screenWidth) {
        List<String> rows = new ArrayList<>();
        List<Integer> pws = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String prompt = i < prompts.size() ? prompts.get(i) : "";
            pws.add(Term.strWidth(prompt));
            rows.addAll(wrap(prompt + coloredLines.get(i), screenWidth));
        }
        return new InputShot(rows, pws, lines, code, screenWidth);
    }

    /** Split an SGR-colored string into rows of at most width columns. */
    static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String style = "";
        int col = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\033' && i + 1 < s.length() && s.charAt(i + 1) == '[') {
                int j = i + 2;
                while (j < s.length() && !Character.isLetter(s.charAt(j))) j++;
                String seq = s.substring(i, Math.min(j + 1, s.length()));
                cur.append(seq);
                if (seq.endsWith("m")) style = seq.equals("\033[0m") || seq.equals("\033[m") ? "" : style + seq;
                i = j + 1;
                continue;
            }
            int cp = s.codePointAt(i);
            int cw = Term.charWidth(cp);
            if (col + cw > width) {
                out.add(cur.append("\033[0m").toString());
                cur = new StringBuilder(style);
                col = 0;
            }
            cur.appendCodePoint(cp);
            col += cw;
            i += Character.charCount(cp);
        }
        out.add(cur.toString());
        return out;
    }

    public int size() { return rows.size(); }

    /** [row, x] on screen of (logical line index, char column), or null. */
    public int[] locate(int lineIdx, int charCol) {
        int row = 0;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int pw = promptWidths.get(i);
            if (i == lineIdx) {
                String prefix = text.substring(0, Math.max(0, Math.min(charCol, text.length())));
                int x = pw + Term.strWidth(prefix);
                return new int[] {row + x / screenWidth, x % screenWidth};
            }
            row += (pw + Term.strWidth(text)) / screenWidth + 1;
        }
        return null;
    }

    /** [[row, x, width], ...] for each word-bounded occurrence of name. */
    public List<int[]> find(String name) {
        List<int[]> hits = new ArrayList<>();
        if (name == null || name.isEmpty()) return hits;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int from = 0;
            while (true) {
                int k = text.indexOf(name, from);
                if (k < 0) break;
                boolean before = k > 0 && isIdent(text.charAt(k - 1));
                int end = k + name.length();
                boolean after = end < text.length() && isIdent(text.charAt(end));
                if (!before && !after) {
                    int[] pos = locate(i, k);
                    if (pos != null) hits.add(new int[] {pos[0], pos[1], Term.strWidth(name)});
                }
                from = k + 1;
            }
        }
        return hits;
    }

    private static boolean isIdent(char c) { return Character.isJavaIdentifierPart(c); }
}
