package dopajshell;

import java.util.HashMap;
import java.util.Map;

/**
 * A w x h grid of cells with an optional braille sub-pixel layer (2x4 dots per cell).
 * Rendered to SGR-colored strings, one per row. Colors are packed 0xRRGGBB, -1 = unset.
 */
public final class Canvas {
    private static final int[][] BRAILLE = {{0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}};
    private static final String CONT = new String("\u0000cont");
    private static final String[] BRAILLE_CHARS = new String[256];
    static {
        for (int i = 0; i < 256; i++) BRAILLE_CHARS[i] = new String(Character.toChars(0x2800 + i));
    }

    public final int w, h;
    private final String[][] ch;
    private final int[][] fg, bg, dotc;
    private final boolean[][] bold;
    private final int[][] dots;

    public Canvas(int w, int h) {
        this.w = Math.max(w, 0);
        this.h = Math.max(h, 0);
        ch = new String[this.h][this.w];
        fg = new int[this.h][this.w];
        bg = new int[this.h][this.w];
        dotc = new int[this.h][this.w];
        bold = new boolean[this.h][this.w];
        dots = new int[this.h][this.w];
        for (int y = 0; y < this.h; y++) {
            java.util.Arrays.fill(fg[y], -1);
            java.util.Arrays.fill(bg[y], -1);
            java.util.Arrays.fill(dotc[y], -1);
        }
    }

    static int pack(int[] c) { return c == null ? -1 : (c[0] << 16) | (c[1] << 8) | c[2]; }
    static int[] unpack(int p) { return p < 0 ? null : new int[] {(p >> 16) & 255, (p >> 8) & 255, p & 255}; }

    public int put(double x, double y, String str, int[] fgc) { return put(x, y, str, fgc, null, false); }
    public int put(double x, double y, String str, int[] fgc, boolean b) { return put(x, y, str, fgc, null, b); }

    /** Draw str at (x, y); returns the column after it. */
    public int put(double xd, double yd, String str, int[] fgc, int[] bgc, boolean b) {
        int x = (int) Math.round(xd);
        int y = (int) Math.round(yd);
        if (str == null) return x;
        if (y < 0 || y >= h) return x;
        int pf = pack(fgc), pb = pack(bgc);
        int i = 0;
        while (i < str.length()) {
            int cp = str.codePointAt(i);
            int n = Character.charCount(cp);
            int cw = cp < 0x80 ? 1 : Term.charWidth(cp);
            if (cw <= 0) {
                i += n;
                continue;
            }
            String c = str.substring(i, i + n);
            if (cw == 2) {
                if (x >= 0 && x + 1 < w) {
                    set(x, y, c, pf, pb, b);
                    set(x + 1, y, CONT, pf, pb, b);
                }
            } else if (x >= 0 && x < w) {
                set(x, y, c, pf, pb, b);
            }
            x += cw;
            i += n;
        }
        return x;
    }

    public String charAt(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return null;
        String c = ch[y][x];
        return c == CONT ? null : c;
    }

    public int putCenter(double y, String str, int[] fgc) { return putCenter(y, str, fgc, null, false); }
    public int putCenter(double y, String str, int[] fgc, boolean b) { return putCenter(y, str, fgc, null, b); }

    public int putCenter(double y, String str, int[] fgc, int[] bgc, boolean b) {
        return put((w - Term.strWidth(str)) / 2, y, str, fgc, bgc, b);
    }

    public void bg(double xd, double yd, int[] color) {
        int x = (int) Math.round(xd), y = (int) Math.round(yd);
        if (x >= 0 && y >= 0 && x < w && y < h) bg[y][x] = pack(color);
    }

    public void fillBg(int[] color) { fillBg(color, 0, 0, w, h); }

    public void fillBg(int[] color, double x0d, double y0d, double wd, double hd) {
        int x0 = (int) Math.round(x0d), y0 = (int) Math.round(y0d), ww = (int) Math.round(wd), hh = (int) Math.round(hd);
        int p = pack(color);
        for (int y = Math.max(y0, 0); y < Math.min(y0 + hh, h); y++) {
            for (int x = Math.max(x0, 0); x < Math.min(x0 + ww, w); x++) bg[y][x] = p;
        }
    }

    public void tintBg(int[] color, double k) { tintBg(color, k, 0, 0, w, h); }

    /** Blend the existing background towards color by k (0..1). Unset bg is black. */
    public void tintBg(int[] color, double k, double x0d, double y0d, double wd, double hd) {
        int x0 = (int) Math.round(x0d), y0 = (int) Math.round(y0d), ww = (int) Math.round(wd), hh = (int) Math.round(hd);
        for (int y = Math.max(y0, 0); y < Math.min(y0 + hh, h); y++) {
            for (int x = Math.max(x0, 0); x < Math.min(x0 + ww, w); x++) {
                int[] cur = bg[y][x] < 0 ? Color.BLACK : unpack(bg[y][x]);
                bg[y][x] = pack(Color.mix(cur, color, k));
            }
        }
    }

    /** Sub-pixel dot; px in 0..w*2, py in 0..h*4. */
    public void dot(double pxd, double pyd, int[] color) {
        if (Double.isNaN(pxd) || Double.isNaN(pyd)) return;
        int px = (int) Math.floor(pxd), py = (int) Math.floor(pyd);
        if (px < 0 || py < 0) return;
        int x = px >> 1, y = py >> 2;
        if (x >= w || y >= h) return;
        dots[y][x] |= BRAILLE[py & 3][px & 1];
        dotc[y][x] = pack(color);
    }

    public void line(double x0, double y0, double x1, double y1, int[] color) {
        int steps = (int) Math.ceil(Math.max(Math.max(Math.abs((x1 - x0) * 2), Math.abs((y1 - y0) * 4)), 1));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            dot((x0 + (x1 - x0) * t) * 2, (y0 + (y1 - y0) * t) * 4, color);
        }
    }

    /** Ellipse in cell units; ry is visually halved by the cell aspect. */
    public void ring(double cx, double cy, double r, int[] color, double density) {
        if (r <= 0) return;
        int n = Math.max((int) Math.ceil(r * 12 * density), 8);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            dot((cx + Math.cos(a) * r) * 2, (cy + Math.sin(a) * r * 0.5) * 4, color);
        }
    }

    public String[] render(Color.Depth depth) { return render(depth, 0); }

    public String[] render(Color.Depth depth, int shake) {
        String[] out = new String[h];
        for (int y = 0; y < h; y++) out[y] = renderRow(y, depth, shake);
        return out;
    }

    private void set(int x, int y, String c, int f, int b, boolean bo) {
        ch[y][x] = c;
        fg[y][x] = f;
        if (b >= 0) bg[y][x] = b;
        bold[y][x] = bo;
    }

    private String renderRow(int y, Color.Depth depth, int shake) {
        String[] chs = ch[y];
        int[] fgs = fg[y], bgs = bg[y], dts = dots[y], dc = dotc[y];
        boolean[] bolds = bold[y];
        boolean utf8 = Term.utf8();
        int last = w - 1;
        while (last >= 0 && chs[last] == null && dts[last] == 0 && bgs[last] < 0) last--;
        StringBuilder out = new StringBuilder(Math.max(16, (last + 1) * 4));
        if (shake > 0) out.append(" ".repeat(shake));
        int x = shake < 0 ? -shake : 0;
        int limit = shake > 0 ? Math.min(last, w - 1 - shake) : last;
        long cur = Long.MIN_VALUE;
        boolean any = false;
        while (x <= limit) {
            String c = chs[x];
            if (c == CONT) {
                x++;
                continue;
            }
            int f = fgs[x], b = bgs[x];
            boolean bo = bolds[x];
            if (c == null) {
                int d = dts[x];
                if (d != 0) {
                    c = utf8 ? BRAILLE_CHARS[d] : ".";
                    f = dc[x];
                } else {
                    c = " ";
                    f = -1;
                }
                bo = false;
            }
            long key = styleKey(f, b, bo);
            if (key != cur) {
                out.append(sgrFor(key, f, b, bo, depth));
                cur = key;
                any = true;
            }
            out.append(c);
            x++;
        }
        if (any) out.append("\033[0m");
        return out.toString();
    }

    private static long styleKey(int f, int b, boolean bo) {
        long fk = f < 0 ? 0x1000000L : f;
        long bk = b < 0 ? 0x1000000L : b;
        return (fk << 26) | (bk << 1) | (bo ? 1 : 0);
    }

    private static final Map<Color.Depth, Map<Long, String>> SGR_CACHE = new java.util.EnumMap<>(Color.Depth.class);

    private static String sgrFor(long key, int f, int b, boolean bo, Color.Depth depth) {
        synchronized (SGR_CACHE) {
            Map<Long, String> cache = SGR_CACHE.computeIfAbsent(depth, d -> new HashMap<>());
            if (cache.size() > 50_000) cache.clear();
            String s = cache.get(key);
            if (s == null) {
                s = depth == Color.Depth.NONE && !bo && b < 0 ? "\033[0m" : Color.sgr(unpack(f), unpack(b), bo, depth);
                cache.put(key, s);
            }
            return s;
        }
    }
}
