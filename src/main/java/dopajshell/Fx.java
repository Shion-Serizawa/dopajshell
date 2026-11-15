package dopajshell;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drawing primitives shared by scenes and the input lane. All motion is a closed-form
 * function of time so frames can be sampled at any rate.
 */
public final class Fx {
    private Fx() {}

    /** color_fn.(x, y, f) -> rgb where f is the pixel column in 0..1. */
    @FunctionalInterface
    public interface ColorFn {
        int[] at(int rx, int ry, double f);
    }

    public static final class Particle {
        double x, y, vx, vy, born, life, gravity, drag;
        int[][] palette;
        String glyph;
    }

    public static final class Particles {
        private final Random rng;
        public final List<Particle> list = new ArrayList<>();

        public Particles(Random rng) { this.rng = rng; }

        public void clear() { list.clear(); }
        public boolean isEmpty() { return list.isEmpty(); }

        /** Burst options; speed in cells/sec, y velocity squashed for the cell aspect. */
        public static final class Opt {
            double speed = 20.0, spread = 1.0, life = 0.6, gravity = 18.0, arc = Math.PI * 2, drag = 1.5;
            Double angle = null;
            int[][] palette = Color.FIRE;
            String[] glyphs = null;

            public Opt speed(double v) { speed = v; return this; }
            public Opt spread(double v) { spread = v; return this; }
            public Opt life(double v) { life = v; return this; }
            public Opt gravity(double v) { gravity = v; return this; }
            public Opt angle(double v) { angle = v; return this; }
            public Opt arc(double v) { arc = v; return this; }
            public Opt drag(double v) { drag = v; return this; }
            public Opt palette(int[][] v) { palette = v; return this; }
            public Opt glyphs(String... v) { glyphs = v; return this; }
        }

        public static Opt opt() { return new Opt(); }

        public Particles burst(double x, double y, int n, double at, Opt o) {
            for (int i = 0; i < n; i++) {
                double a = o.angle != null ? o.angle + (rng.nextDouble() - 0.5) * o.arc : rng.nextDouble() * Math.PI * 2;
                double s = o.speed * (0.35 + rng.nextDouble() * 0.65 * o.spread);
                Particle p = new Particle();
                p.x = x;
                p.y = y;
                p.vx = Math.cos(a) * s;
                p.vy = Math.sin(a) * s * 0.5;
                p.born = at;
                p.life = o.life * (0.6 + rng.nextDouble() * 0.4);
                p.palette = o.palette;
                p.glyph = o.glyphs == null ? null : o.glyphs[rng.nextInt(o.glyphs.length)];
                p.gravity = o.gravity;
                p.drag = o.drag;
                list.add(p);
            }
            return this;
        }

        public void add(double x, double y, double vx, double vy, double at, double life, int[][] palette,
                        String glyph, double gravity, double drag) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            p.vx = vx;
            p.vy = vy;
            p.born = at;
            p.life = life;
            p.palette = palette;
            p.glyph = glyph;
            p.gravity = gravity;
            p.drag = drag;
            list.add(p);
        }

        public void add(double x, double y, double vx, double vy, double at, double life, int[][] palette) {
            add(x, y, vx, vy, at, life, palette, null, 0.0, 0.0);
        }

        public void prune(double t) { list.removeIf(p -> t - p.born > p.life); }

        public boolean alive(double t) {
            for (Particle p : list) if (t - p.born <= p.life) return true;
            return false;
        }

        public void draw(Canvas canvas, double t) {
            for (Particle p : list) {
                double age = t - p.born;
                if (age < 0 || age > p.life) continue;
                double x, y;
                if (p.drag > 0) {
                    double k = (1 - Math.exp(-p.drag * age)) / p.drag;
                    x = p.x + p.vx * k;
                    y = p.y + p.vy * k + 0.5 * p.gravity * age * age * 0.25;
                } else {
                    x = p.x + p.vx * age;
                    y = p.y + p.vy * age + 0.5 * p.gravity * age * age * 0.25;
                }
                int[] col = Color.ramp(p.palette, age / p.life);
                if (p.glyph != null) {
                    canvas.put(x, y, p.glyph, col, age < p.life * 0.3);
                } else {
                    canvas.dot(x * 2, y * 4, col);
                }
            }
        }
    }

    public static double clamp01(double t) { return t < 0 ? 0.0 : (t > 1 ? 1.0 : t); }
    public static double easeOut(double t) { double c = 1 - clamp01(t); return 1 - c * c * c; }
    public static double easeIn(double t) { double c = clamp01(t); return c * c * c; }

    public static double easeOutBack(double t) {
        t = clamp01(t);
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    public static double phase(double t, double from, double to) { return clamp01((t - from) / (to - from)); }

    public static boolean between(double t, double a, double b) { return t >= a && t <= b; }

    public static String pixelChar() { return Term.glyph("█", "⣿", "#"); }

    public static int bigText(Canvas canvas, String text, double x, double y, ColorFn fn, int scale) {
        return bigText(canvas, text, x, y, fn, scale, 0.0, null, 1.0, null);
    }

    /** Big bitmap text; scale doubles pixels horizontally for chunkier letters. */
    public static int bigText(Canvas canvas, String text, double x, double y, ColorFn fn, int scale, double glitch,
                              Random rng, double reveal, ColorFn bgFn) {
        boolean[][] bm = Font.bitmap(text);
        String pc = pixelChar();
        int width = bm[0].length;
        int shown = (int) Math.ceil(width * reveal);
        int xi = (int) Math.round(x), yi = (int) Math.round(y);
        for (int ry = 0; ry < bm.length; ry++) {
            for (int rx = 0; rx < width; rx++) {
                if (!bm[ry][rx] || rx >= shown) continue;
                int ox = 0;
                if (glitch > 0 && rng != null && rng.nextDouble() < glitch) ox = rng.nextInt(5) - 2;
                int[] col = fn.at(rx, ry, (double) rx / Math.max(width - 1, 1));
                int[] bgc = bgFn == null ? null : bgFn.at(rx, ry, 0);
                for (int s = 0; s < scale; s++) canvas.put(xi + rx * scale + s + ox, yi + ry, pc, col, bgc, false);
            }
        }
        return width * scale;
    }

    /** The big font squeezed into three rows with half blocks. -1 when the terminal lacks the glyphs. */
    public static int halfText(Canvas canvas, String text, double x, double y, ColorFn fn) {
        String up = Term.glyph("▀", null), down = Term.glyph("▄", null), full = Term.glyph("█", null);
        if (up == null || down == null || full == null) return -1;
        boolean[][] bm = Font.bitmap(text);
        int width = bm[0].length;
        for (int r = 0; r < 3; r++) {
            boolean[] top = bm[r * 2];
            boolean[] bot = r * 2 + 1 < bm.length ? bm[r * 2 + 1] : new boolean[width];
            for (int cx = 0; cx < width; cx++) {
                boolean a = top[cx], b = bot[cx];
                if (!a && !b) continue;
                String c = a && b ? full : (a ? up : down);
                canvas.put(x + cx, y + r, c, fn.at(cx, r, (double) cx / Math.max(width - 1, 1)));
            }
        }
        return width;
    }

    public static boolean textFits(String text, int w, int scale) { return Font.width(text, scale) <= w; }

    /** Largest scale (2, 1) that fits; 0 if the big font does not fit at all. */
    public static int bestScale(String text, int w) {
        if (textFits(text, w, 2) && Font.width(text, 2) <= 110) return 2;
        if (textFits(text, w, 1)) return 1;
        return 0;
    }

    public static void shockwave(Canvas canvas, double cx, double cy, double age, double speed, int[][] palette, double life, int rings) {
        if (age < 0 || age > life) return;
        for (int i = 0; i < rings; i++) {
            double a = age - i * 0.06;
            if (a < 0) continue;
            canvas.ring(cx, cy, a * speed, Color.ramp(palette, a / life), 1.3);
        }
    }

    public static void sparkles(Canvas canvas, double t, long seed, int count, int[][] palette) {
        sparkles(canvas, t, seed, count, palette, 0, 0, canvas.w, canvas.h);
    }

    public static void sparkles(Canvas canvas, double t, long seed, int count, int[][] palette, int x0, int y0, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Random r = new Random(seed);
        String[] stars = {Term.glyph("✦", "*"), Term.glyph("✧", "+"), "*", "+", "."};
        for (int i = 0; i < count; i++) {
            int px = x0 + r.nextInt(w);
            int py = y0 + r.nextInt(h);
            double period = 0.25 + r.nextDouble() * 0.5;
            double ph = (((t + r.nextDouble()) / period) % 1.0 + 1.0) % 1.0;
            if (ph > 0.6) continue;
            canvas.put(px, py, stars[i % stars.length], Color.ramp(palette, ph / 0.6), ph < 0.2);
        }
    }

    public static String commas(long n) { return commas(BigInteger.valueOf(n)); }

    public static String commas(BigInteger n) {
        String s = n.abs().toString();
        StringBuilder sb = new StringBuilder();
        int first = s.length() % 3;
        if (first > 0) sb.append(s, 0, first);
        for (int i = first; i < s.length(); i += 3) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s, i, i + 3);
        }
        return n.signum() < 0 ? "-" + sb : sb.toString();
    }

    /** "+" glyph-friendly: spaced-out text "A B C". */
    public static String spaced(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    public static String take(String s, int n) {
        if (n <= 0) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
