package dopajshell.scenes;

import java.util.ArrayList;
import java.util.List;

import dopajshell.Canvas;
import dopajshell.Color;
import dopajshell.Event;
import dopajshell.Event.Flag;
import dopajshell.Font;
import dopajshell.Fx;
import dopajshell.Scene;
import dopajshell.Term;

/** Effects for compile errors, exceptions and interrupts. They name the error; they never mock it. */
public final class FailureScenes {
    private FailureScenes() {}

    /** Shared bits for error scenes: red tint on the input, big glitching title. */
    public static class FailureScene extends Scene {
        protected int scale;

        public FailureScene(Ctx ctx, Event event) { super(ctx, event); }

        @Override public boolean big() { return true; }
        @Override public boolean top() { return ctx.input != null; }
        @Override public double pre() { return 0.0; }
        @Override public boolean bell() { return true; }

        protected String defaultTitle() { return "FAILED"; }

        public String title() {
            return event.info.title != null && !event.info.title.isEmpty() ? event.info.title : defaultTitle();
        }

        protected int[][] palette() { return Color.BLOOD; }
        @Override public String sfx() { return "error"; }

        @Override
        protected void setup() {
            scale = bestScale(title());
            if (scale == 2 && Font.width(title(), 2) > w * 0.7) scale = 1;
        }

        @Override public int height() { return scale > 0 ? 7 : 2; }
        @Override public double length() { return 0.85; }
        @Override public double flashAt() { return 0.0; }

        @Override
        public int shake(double t) {
            return t < 0.22 ? (int) Math.round((rng.nextDouble() * 2 - 1) * 3 * (1 - t / 0.22)) : 0;
        }

        @Override
        public boolean drawTop(Canvas c, double t) {
            if (t > 0.45) return false;
            double k = 1 - Fx.phase(t, 0.1, 0.45);
            int ox = t < 0.2 ? rng.nextInt(5) - 2 : 0;
            List<String> plain = ctx.input.plain;
            for (int y = 0; y < plain.size(); y++) {
                c.put(ox, y, plain.get(y), Color.mix(Color.rgb(220, 220, 225), Color.rgb(255, 70, 80), k), k > 0.5);
            }
            markTop(c, t);
            return true;
        }

        protected void markTop(Canvas c, double t) {}

        @Override
        public void draw(Canvas c, double t) {
            drawTitle(c, t);
            drawDetail(c, t);
        }

        protected void drawTitle(Canvas c, double t) {
            if (flashOn() && t < 0.1) c.tintBg(Color.rgb(160, 0, 20), 0.6 * (1 - t / 0.1), 0, 0, w, height());
            String title = title();
            int[][] pal = palette();
            if (scale > 0) {
                double glitch = t < 0.35 ? 0.35 * (1 - t / 0.35) : 0.0;
                int tear = (int) Math.floor(t * 17);
                Fx.ColorFn fn = (rx, ry, f) -> {
                    boolean flick = (rx * 3 + ry * 5 + tear) % 11 == 0 && t < 0.4;
                    return flick ? Color.WHITE : Color.ramp(pal, 0.15 + ry * 0.12);
                };
                int x = (w - Font.width(title, scale)) / 2;
                Fx.bigText(c, title, x, 0, fn, scale, glitch, rng, 1.0, null);
            } else {
                c.put(1, 0, Fx.spaced(title), Color.ramp(pal, 0.2), true);
            }
        }

        protected String detailText() {
            List<String> bits = new ArrayList<>();
            bits.add(event.info.className == null ? "" : event.info.className);
            if (event.streak > 1) bits.add("x" + event.streak + " in a row");
            if (event.info.brokenCombo >= 3) bits.add("combo reset (" + event.info.brokenCombo + ")");
            if (event.flag(Flag.FEVER_END)) bits.add("FEVER over");
            return String.join("   ", bits);
        }

        protected void drawDetail(Canvas c, double t) {
            if (t < 0.15) return;
            String txt = detailText();
            int n = (int) Math.round(txt.length() * Fx.phase(t, 0.15, 0.45));
            int y = scale > 0 ? 6 : 1;
            c.putCenter(y, Fx.take(txt, n), Color.rgb(255, 170, 170), true);
        }

        protected String streakBit() { return event.streak > 1 ? "x" + event.streak + " in a row" : null; }

        protected static String join(String... parts) {
            List<String> bits = new ArrayList<>();
            for (String p : parts) if (p != null) bits.add(p);
            return String.join("   ", bits);
        }

        @Override
        public String trail() {
            String s = Term.glyph("✖", "x") + " " + title();
            if (event.streak > 1) s += "  x" + event.streak;
            return paint(s, Color.ramp(palette(), 0.25), true);
        }
    }

    /** Syntax error: the input line cracks at the error position. */
    public static class Crack extends FailureScene {
        private Fx.Particles shards;
        private int[] pos;
        private final List<double[]> path = new ArrayList<>();

        public Crack(Ctx ctx, Event event) { super(ctx, event); }

        @Override protected String defaultTitle() { return "SYNTAX BREAK"; }
        @Override public String sfx() { return "crack"; }

        @Override
        protected void setup() {
            super.setup();
            shards = new Fx.Particles(rng);
            if (ctx.input != null && event.info.line >= 1) {
                pos = ctx.input.locate(event.info.line - 1, Math.max(event.info.column, 0));
            }
            if (pos == null && ctx.input != null) {
                String last = ctx.input.plain.get(ctx.input.size() - 1);
                pos = new int[] {ctx.input.size() - 1, Math.max(Term.strWidth(last), 1) - 1};
            }
            if (pos == null) return;
            double x = pos[1];
            int rows = (ctx.input.size() - pos[0]) + height();
            for (int i = 0; i < rows * 4; i++) {
                x += (rng.nextDouble() - 0.5) * 2.4;
                path.add(new double[] {x, pos[0] + i / 4.0});
            }
            shards.burst(pos[1], 0.3, 24, 0.05, Fx.Particles.opt().speed(25).life(0.6)
                    .palette(new int[][] {Color.WHITE, Color.rgb(255, 80, 80), Color.rgb(90, 20, 30)}).gravity(22)
                    .glyphs("/", "\\", "'", ",", "."));
        }

        @Override
        protected void markTop(Canvas c, double t) {
            if (pos == null) return;
            c.bg(pos[1], pos[0], t < 0.3 ? Color.rgb(255, 40, 60) : Color.rgb(120, 20, 30));
            drawCrack(c, t, 0);
        }

        @Override
        public void draw(Canvas c, double t) {
            super.draw(c, t);
            if (pos == null) return;
            drawCrack(c, t, -ctx.input.size());
            shards.draw(c, t);
        }

        @Override
        protected String detailText() {
            String d = event.info.detail;
            String loc = event.info.line >= 1 ? "line " + event.info.line + ":" + (Math.max(event.info.column, 0) + 1) : null;
            return join(loc, d == null ? null : Fx.take(d, Math.max(w - 20, 20)), streakBit());
        }

        private void drawCrack(Canvas c, double t, double yoff) {
            int n = (int) Math.round(path.size() * Fx.phase(t, 0, 0.18));
            for (int i = 0; i < n && i < path.size(); i++) {
                double[] pt = path.get(i);
                int[] col = Color.mix(Color.WHITE, Color.rgb(255, 60, 70), (double) i / path.size() + Fx.phase(t, 0.2, 0.8));
                c.dot(pt[0] * 2, (pt[1] + yoff) * 4, col);
            }
        }
    }

    /** Unknown symbol: the name is put under a spotlight. */
    public static class Spotlight extends FailureScene {
        private static final int[][] PAL = {{255, 250, 220}, {255, 210, 90}, {230, 140, 30}, {140, 60, 20}, {50, 20, 10}};
        private List<int[]> hits = List.of();

        public Spotlight(Ctx ctx, Event event) { super(ctx, event); }

        @Override protected String defaultTitle() { return "UNKNOWN SYMBOL"; }
        @Override protected int[][] palette() { return PAL; }

        @Override
        protected void setup() {
            super.setup();
            hits = ctx.input != null ? ctx.input.find(event.info.name) : List.of();
        }

        @Override
        public boolean drawTop(Canvas c, double t) {
            if (t > 0.75) return false;
            List<String> plain = ctx.input.plain;
            for (int y = 0; y < plain.size(); y++) c.put(0, y, plain.get(y), Color.rgb(90, 90, 100));
            double pulse = 0.5 + 0.5 * Math.sin(t * 30);
            String name = event.info.name == null ? "" : event.info.name;
            for (int[] h : hits) {
                c.put(h[1], h[0], name, Color.rgb(20, 10, 0), Color.mix(Color.rgb(255, 200, 60), Color.rgb(255, 255, 200), pulse), true);
            }
            return true;
        }

        @Override
        public void draw(Canvas c, double t) {
            super.draw(c, t);
            int height = height();
            for (int i = 0; i < Math.min(3, hits.size()); i++) {
                int[] h = hits.get(i);
                int x = h[1], wd = h[2];
                for (int y = 0; y < height; y++) {
                    double k = (1 - (double) y / height) * 0.45 * (1 - Fx.phase(t, 0.5, 0.85));
                    c.tintBg(Color.rgb(255, 220, 120), k, x - y / 2, y, wd + y, 1);
                }
            }
        }

        @Override
        protected String detailText() {
            String n = event.info.name;
            String base = n != null ? "`" + n + "` is not defined here" : Fx.take(event.info.message == null ? "" : event.info.message, 60);
            return join(base, streakBit());
        }
    }

    /** No such method (or a null receiver): the link between receiver and method snaps. */
    public static class Snap extends FailureScene {
        private String left, right;
        private Fx.Particles parts;
        private double mid;
        private List<int[]> hits = List.of();

        public Snap(Ctx ctx, Event event) { super(ctx, event); }

        @Override protected String defaultTitle() { return "NO METHOD"; }
        @Override public String sfx() { return "snap"; }
        @Override public double impactAt() { return 0.28; }
        @Override protected int[][] palette() { return Color.NEON; }
        @Override public int height() { return 4; }
        @Override public double length() { return 0.9; }

        @Override
        protected void setup() {
            super.setup();
            scale = 0;
            left = " " + (event.info.receiver == null ? "?" : event.info.receiver) + " ";
            right = " ." + (event.info.name == null ? "?" : event.info.name) + " ";
            parts = new Fx.Particles(rng);
            mid = w / 2.0;
            parts.burst(mid, 1.5, 40, 0.28, Fx.Particles.opt().speed(30).life(0.45)
                    .palette(new int[][] {Color.WHITE, Color.rgb(255, 230, 120), Color.rgb(255, 90, 200), Color.rgb(60, 20, 80)})
                    .gravity(12));
            hits = ctx.input != null ? ctx.input.find(event.info.name) : List.of();
        }

        @Override
        public boolean drawTop(Canvas c, double t) {
            if (t > 0.6) return false;
            List<String> plain = ctx.input.plain;
            for (int y = 0; y < plain.size(); y++) c.put(0, y, plain.get(y), Color.rgb(120, 120, 135));
            String name = event.info.name == null ? "" : event.info.name;
            for (int[] h : hits) c.put(h[1], h[0], name, Color.WHITE, Color.rgb(150, 40, 170), true);
            return true;
        }

        @Override
        public void draw(Canvas c, double t) {
            int gap = t < 0.28 ? 0 : (int) Math.round(Fx.easeOut(Fx.phase(t, 0.28, 0.6)) * 8);
            int lx = (int) Math.max(Math.round(mid - 14 - gap - left.length()), 0);
            int rx = (int) Math.min(Math.round(mid + 14 + gap), w - right.length() - 1);
            c.put(lx, 1, left, Color.WHITE, Color.rgb(40, 90, 200), true);
            c.put(rx, 1, right, Color.WHITE, Color.rgb(170, 40, 160), true);
            int a = lx + Term.strWidth(left);
            int b = rx - 1;
            if (t < 0.28) {
                double reach = a + (b - a) * Fx.easeOut(t / 0.2);
                for (int x = a; x <= Math.round(reach); x++) {
                    double wob = Math.sin(x * 1.3 + t * 60) * 0.35;
                    c.dot(x * 2, (1.5 + wob) * 4, Color.mix(Color.rgb(150, 220, 255), Color.WHITE, rng.nextDouble()));
                }
            } else {
                double fall = Fx.phase(t, 0.28, 0.9);
                for (int x = a; x <= Math.round(mid - gap / 2.0); x++) {
                    c.dot(x * 2, (1.5 + fall * fall * 2 * ((x - a) / 10.0)) * 4, Color.rgb(120, 170, 220));
                }
                for (long x = Math.round(mid + gap / 2.0); x <= b; x++) {
                    c.dot(x * 2, (1.5 + fall * fall * 2 * ((b - x) / 10.0)) * 4, Color.rgb(200, 120, 210));
                }
                parts.draw(c, t);
                c.putCenter(0, Fx.spaced(title()), Color.ramp(palette(), 0.15), true);
                String txt = detailText();
                c.putCenter(3, Fx.take(txt, (int) Math.round(txt.length() * Fx.phase(t, 0.35, 0.65))), Color.rgb(230, 190, 255));
            }
        }

        @Override
        protected String detailText() {
            String m = event.info.message == null ? "" : event.info.message;
            return join(Fx.take(m, Math.max(w - 4, 20)), streakBit());
        }
    }

    /** Type / argument mismatch: the two sides collide. */
    public static class Clash extends FailureScene {
        private String l, r;
        private Fx.Particles parts;
        private double cx;

        public Clash(Ctx ctx, Event event) { super(ctx, event); }

        @Override protected String defaultTitle() { return "argument".equals(event.info.type) ? "ARGUMENT CLASH" : "TYPE CLASH"; }
        @Override protected int[][] palette() { return Color.FIRE; }
        @Override public double length() { return 0.95; }
        @Override public String sfx() { return "crack"; }

        @Override
        protected void setup() {
            super.setup();
            scale = 0;
            String[] sides = event.info.sides;
            if (sides == null || sides.length < 2) {
                sides = "argument".equals(event.info.type) ? new String[] {"ARG", "ARG"} : new String[] {"TYPE", "TYPE"};
            }
            l = " " + sides[0] + " ";
            r = " " + sides[1] + " ";
            parts = new Fx.Particles(rng);
            cx = w / 2.0;
            parts.burst(cx, 2, 70, 0.3, Fx.Particles.opt().speed(60).life(0.55).palette(Color.FIRE).gravity(16));
            parts.burst(cx, 2, 16, 0.32, Fx.Particles.opt().speed(35).life(0.5).palette(Color.SMOKE).gravity(4)
                    .glyphs("*", "#", "%", "@"));
        }

        @Override public int height() { return 5; }

        @Override
        public int shake(double t) {
            return Fx.between(t, 0.3, 0.5) ? (int) Math.round((rng.nextDouble() * 2 - 1) * 4 * (1 - Fx.phase(t, 0.3, 0.5))) : 0;
        }

        @Override public double flashAt() { return 0.3; }

        @Override public boolean drawTop(Canvas c, double t) { return t >= 0.3 && super.drawTop(c, t); }

        @Override
        public void draw(Canvas c, double t) {
            int lw = Term.strWidth(l), rw = Term.strWidth(r);
            if (t < 0.3) {
                double p = Fx.easeIn(t / 0.3);
                double lx = (cx - lw) * p;
                double rx = w - rw - (w - rw - cx) * p;
                c.put(lx, 2, l, Color.WHITE, Color.rgb(30, 120, 220), true);
                c.put(rx, 2, r, Color.WHITE, Color.rgb(220, 90, 20), true);
                for (int k = 0; k <= 3; k++) c.put(lx - k - 1, 2, "-", Color.scale(Color.rgb(30, 120, 220), 1 - k / 4.0));
                for (int k = 0; k <= 3; k++) c.put(rx + rw + k, 2, "-", Color.scale(Color.rgb(220, 90, 20), 1 - k / 4.0));
            } else {
                double p = Fx.phase(t, 0.3, 0.95);
                double back = Fx.easeOut(p) * 10;
                c.put(cx - lw - back, 2, l, Color.mix(Color.WHITE, Color.rgb(90, 90, 100), p),
                        Color.mix(Color.rgb(30, 120, 220), Color.rgb(20, 30, 50), p), false);
                c.put(cx + back, 2, r, Color.mix(Color.WHITE, Color.rgb(90, 90, 100), p),
                        Color.mix(Color.rgb(220, 90, 20), Color.rgb(50, 25, 10), p), false);
                Fx.shockwave(c, cx, 2, t - 0.3, 80, Color.FIRE, 0.4, 3);
                parts.draw(c, t);
                if (flashOn()) c.tintBg(Color.rgb(255, 200, 120), 0.6 * (1 - Fx.phase(t, 0.3, 0.42)), 0, 0, w, height());
                if (t > 0.36) c.putCenter(0, Fx.spaced(title()), Color.ramp(palette(), 0.2), true);
                String txt = detailText();
                if (t > 0.4) c.putCenter(4, Fx.take(txt, (int) Math.round(txt.length() * Fx.phase(t, 0.4, 0.7))), Color.rgb(255, 200, 170));
            }
        }

        @Override
        protected String detailText() {
            String m = event.info.message == null ? "" : event.info.message;
            return join(Fx.take(m, Math.max(w - 4, 20)), streakBit());
        }
    }

    /** Ctrl-C: the collected charge scatters and fades. */
    public static class Scatter extends Scene {
        private Fx.Particles parts;

        public Scatter(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "interrupt"; }
        @Override public int height() { return 2; }
        @Override public double length() { return 0.65; }
        @Override public boolean big() { return true; }

        @Override
        protected void setup() {
            parts = new Fx.Particles(rng);
            int n = (int) Math.max(Math.round(event.info.charge / 4), 6);
            for (int i = 0; i < n; i++) {
                parts.burst(2 + i, 0.5, 4, 0.05, Fx.Particles.opt().speed(30).life(0.5).palette(Color.STEEL).gravity(6));
            }
        }

        @Override
        public void draw(Canvas c, double t) {
            parts.draw(c, t);
            String word = "INTERRUPTED";
            int sp = (int) Math.round((1 - Fx.easeOut(Fx.phase(t, 0, 0.35))) * 3) + 1;
            StringBuilder txt = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                if (i > 0) txt.append(" ".repeat(sp));
                txt.append(word.charAt(i));
            }
            c.put(2, 1, Fx.take(txt.toString(), w - 3), Color.mix(Color.WHITE, Color.rgb(120, 140, 180), Fx.phase(t, 0.1, 0.6)), true);
        }

        @Override
        public String trail() { return paint(Term.glyph("✖", "x") + " INTERRUPTED", Color.rgb(140, 160, 200), true); }
    }
}
