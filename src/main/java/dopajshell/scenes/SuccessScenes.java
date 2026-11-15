package dopajshell.scenes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dopajshell.Canvas;
import dopajshell.Color;
import dopajshell.Event;
import dopajshell.Event.Flag;
import dopajshell.Font;
import dopajshell.Fx;
import dopajshell.Scene;
import dopajshell.Term;

/** Effects for successful evaluations. */
public final class SuccessScenes {
    private SuccessScenes() {}

    /** Ordinary value: a comet leaves "=>" and lands with a small burst. */
    public static class Hit extends Scene {
        private int target;
        private Fx.Particles parts;

        public Hit(Ctx ctx, Event event) { super(ctx, event); }

        @Override public boolean top() { return ctx.input != null; }
        @Override public double length() { return 0.42; }
        @Override public String sfx() { return "hit"; }
        @Override public double impactAt() { return 0.16; }

        @Override
        protected void setup() {
            target = Math.max(Math.min(w - 30, 34), 8);
            parts = new Fx.Particles(rng);
            int[][] pal = comboPalette();
            parts.burst(target, 0.5, 14 + Math.min(event.combo, 20), 0.16,
                    Fx.Particles.opt().speed(26).life(0.28).palette(pal).gravity(3).angle(0).arc(Math.PI * 2));
        }

        @Override
        public void draw(Canvas c, double t) {
            int[][] pal = comboPalette();
            int[] hot = t < 0.3 ? Color.mix(Color.WHITE, Color.ramp(pal, 0.2), t / 0.3) : Color.ramp(pal, 0.4);
            c.put(0, 0, "=>", hot, true);
            if (t < 0.16) {
                double head = 3 + (target - 3) * Fx.easeIn(t / 0.16);
                boolean fever = event.flag(Flag.FEVER);
                for (int i = 0; i < 9; i++) {
                    double x = head - i;
                    if (x < 3) break;
                    int[] col = fever ? Color.rainbow(i / 9.0 - t * 4) : Color.ramp(pal, i / 9.0);
                    c.put(x, 0, i == 0 ? Term.glyph("✦", "*") : (i < 3 ? "=" : "-"), col, i < 2);
                }
            } else {
                double p = Fx.phase(t, 0.16, 0.42);
                parts.draw(c, t);
                String txt = "+" + Fx.commas(event.gain);
                int[] col = Color.mix(Color.WHITE, Color.ramp(pal, 0.3), p);
                int x = c.put(target + 2, 0, txt, col, p < 0.25 ? Color.ramp(pal, 0.6) : null, true);
                if (event.combo > 1) x = c.put(x + 2, 0, "COMBO " + event.combo, comboColor(t), event.combo >= 5);
                if (event.flag(Flag.FEVER)) {
                    String f = " FEVER x2 ";
                    for (int i = 0; i < f.length(); i++) {
                        x = c.put(x + (i == 0 ? 2 : 0), 0, String.valueOf(f.charAt(i)), Color.rgb(20, 10, 30),
                                Color.rainbow(i / 10.0 - t * 2), true);
                    }
                }
            }
        }

        @Override
        public String trail() {
            return paint("=> " + Term.glyph("✦", "*") + " +" + Fx.commas(event.gain)
                    + (event.combo > 1 ? "  COMBO " + event.combo : "")
                    + (event.flag(Flag.FEVER) ? "  FEVER x2" : ""), comboColor(0));
        }
    }

    /** null / void: a swing and a miss, then smoke. */
    public static class Puff extends Scene {
        private Fx.Particles parts;

        public Puff(Ctx ctx, Event event) { super(ctx, event); }

        @Override public boolean top() { return ctx.input != null; }
        @Override public double length() { return 0.5; }
        @Override public String sfx() { return "puff"; }
        @Override public double impactAt() { return 0.12; }

        @Override
        protected void setup() {
            parts = new Fx.Particles(rng);
            for (int i = 0; i < 18; i++) {
                parts.add(12 + rng.nextDouble() * 6, 0.9, (rng.nextDouble() - 0.3) * 10, -1.5 - rng.nextDouble() * 2.5,
                        0.12 + rng.nextDouble() * 0.08, 0.35, Color.SMOKE);
            }
        }

        @Override
        public void draw(Canvas c, double t) {
            c.put(0, 0, "=>", Color.rgb(160, 160, 170), true);
            if (t < 0.12) {
                double x = 3 + 14 * Fx.easeOut(t / 0.12);
                c.put(x - 3, 0, "~~~", Color.rgb(200, 200, 210));
            } else {
                parts.draw(c, t);
                double p = Fx.phase(t, 0.12, 0.5);
                String label = event.info.nullLabel == null ? "null" : event.info.nullLabel;
                c.put(22, 0, label, Color.mix(Color.rgb(230, 230, 230), Color.rgb(90, 90, 100), p));
                if (p > 0.2) c.put(23 + label.length() + 1, 0, "whiff...", Color.mix(Color.rgb(150, 150, 160), Color.rgb(60, 60, 70), p));
            }
        }
    }

    /** true / false: an ink stamp slams down. */
    public static class Stamp extends Scene {
        private boolean yes;
        private String label;
        private int[] ink;
        private final int x = 4;
        private final int bw = 12;
        private Fx.Particles parts;

        public Stamp(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "stamp"; }
        @Override public int height() { return 3; }
        @Override public double length() { return 0.6; }
        @Override public boolean top() { return ctx.input != null; }

        @Override
        protected void setup() {
            yes = "true".equals(event.info.type);
            label = yes ? "YES!" : "NO!";
            ink = yes ? Color.rgb(30, 190, 90) : Color.rgb(220, 40, 60);
            parts = new Fx.Particles(rng);
            parts.burst(x + bw / 2.0, 1.5, 26, 0.14, Fx.Particles.opt().speed(30).life(0.35)
                    .palette(new int[][] {Color.mix(ink, Color.WHITE, 0.6), ink, Color.scale(ink, 0.3)}).gravity(10));
        }

        @Override public int shake(double t) { return Fx.between(t, 0.14, 0.24) ? rng.nextInt(3) - 1 : 0; }
        @Override public double flashAt() { return 0.14; }

        @Override
        public void draw(Canvas c, double t) {
            if (t < 0.14) {
                double p = Fx.easeIn(t / 0.14);
                int grow = (int) Math.round((1 - p) * 10);
                c.fillBg(Color.scale(ink, 0.25 + 0.5 * p), x - grow, 0, bw + grow * 2, 3);
                if (p > 0.6) c.put(x + (bw - label.length()) / 2, 1, label, Color.WHITE, true);
            } else {
                double p = Fx.phase(t, 0.14, 0.6);
                parts.draw(c, t);
                int[] edge = Color.scale(ink, 0.55);
                c.fillBg(edge, x, 0, bw, 3);
                c.fillBg(t < 0.2 ? Color.mix(ink, Color.WHITE, 0.6) : ink, x + 1, 1, bw - 2, 1);
                c.put(x + (bw - label.length()) / 2, 1, label, Color.WHITE, true);
                c.put(x + bw + 3, 1, yes ? "true" : "false", Color.mix(Color.WHITE, ink, p), true);
                if (p > 0.3 && event.combo > 1) c.put(x + bw + 10, 1, subLine(), Color.rgb(150, 150, 160));
            }
        }

        @Override public String trail() { return paint("[ " + label + " ]", ink, true); }
    }

    /** Numbers that deserve it: count up in giant digits, then a shockwave. */
    public static class Counter extends Scene {
        private boolean isInt;
        private BigInteger intValue;
        private double floatValue;
        private String fin;
        private int scale;
        private Fx.Particles parts;

        public Counter(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "counter"; }
        @Override public boolean big() { return true; }
        @Override public boolean top() { return ctx.input != null; }
        @Override public double length() { return 1.0; }

        @Override
        protected void setup() {
            isInt = "integer".equals(event.info.type) && event.info.intValue != null;
            intValue = isInt ? event.info.intValue : BigInteger.ZERO;
            floatValue = event.info.floatValue;
            fin = isInt ? Fx.commas(intValue) : formatFloat(floatValue);
            scale = bestScale(fin);
            parts = new Fx.Particles(rng);
            double cx = w / 2.0;
            parts.burst(cx, 3, 60, 0.55, Fx.Particles.opt().speed(55).life(0.5).palette(Color.GOLD).gravity(14));
            parts.burst(cx, 3, 20, 0.58, Fx.Particles.opt().speed(30).life(0.45).palette(Color.FIRE).gravity(8)
                    .glyphs("*", "+", "'", "."));
        }

        @Override public int height() { return scale > 0 ? 7 : 3; }
        @Override public int shake(double t) { return Fx.between(t, 0.55, 0.7) ? rng.nextInt(5) - 2 : 0; }
        @Override public double flashAt() { return 0.55; }

        @Override
        public void draw(Canvas c, double t) {
            double p = Fx.phase(t, 0, 0.55);
            String shown;
            if (p >= 1) {
                shown = fin;
            } else if (isInt) {
                BigInteger v = new BigDecimal(intValue).multiply(BigDecimal.valueOf(Fx.easeOut(p))).toBigInteger();
                shown = Fx.commas(v);
            } else {
                shown = formatFloat(floatValue * Fx.easeOut(p));
            }
            boolean landed = t >= 0.55;
            double shimmer = (t * 1.6) % 1.0;
            if (scale > 0) {
                int x = (w - Font.width(shown, scale)) / 2;
                Fx.ColorFn fn = (rx, ry, f) -> {
                    int[] base = landed ? Color.ramp(Color.GOLD, 0.15 + ry * 0.12) : Color.ramp(Color.ICE, 0.2 + ry * 0.1);
                    double d = Math.abs(f - shimmer);
                    return d < 0.08 ? Color.mix(Color.WHITE, base, d / 0.08) : base;
                };
                Fx.bigText(c, shown, x, 1, fn, scale);
                Fx.shockwave(c, w / 2.0, 3, t - 0.55, 70, Color.GOLD, 0.45, 2);
                parts.draw(c, t);
                if (landed && t < 0.7 && flashOn()) {
                    c.tintBg(Color.rgb(255, 220, 120), 0.35 * (1 - Fx.phase(t, 0.55, 0.7)), 0, 0, w, 7);
                }
                if (landed) c.putCenter(6, subLine(digitsLabel()), Color.ramp(Color.GOLD, 0.2), true);
            } else {
                c.put(2, 1, "=> ", Color.rgb(255, 220, 120), true);
                c.put(5, 1, shown, landed ? Color.rgb(255, 230, 120) : Color.rgb(140, 220, 255), true);
                parts.draw(c, t);
                if (landed) c.put(8 + shown.length(), 1, subLine(digitsLabel()), Color.rgb(220, 200, 150));
            }
        }

        private String digitsLabel() {
            return isInt ? intValue.abs().toString().length() + " DIGITS" : "FLOAT";
        }

        @Override
        public String trail() {
            return paint(Term.glyph("✦", "*") + " " + fin + "  " + subLine(digitsLabel()), Color.rgb(255, 210, 90), true);
        }

        static String formatFloat(double f) {
            if (!Double.isFinite(f)) return Double.toString(f);
            if (Math.abs(f) >= 1e15) return String.format(Locale.ROOT, "%.3e", f);
            String s = String.format(Locale.ROOT, "%.3f", f);
            s = s.replaceAll("\\.?0+$", "");
            return s.isEmpty() || s.equals("-") ? "0" : s;
        }
    }

    /** Long strings: characters pour in while the length counts up. */
    public static class Stream extends Scene {
        public Stream(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "hit"; }
        @Override public int height() { return 2; }
        @Override public double length() { return 0.75; }
        @Override public boolean top() { return ctx.input != null; }

        @Override
        public void draw(Canvas c, double t) {
            int len = event.info.length;
            double p = Fx.phase(t, 0, 0.5);
            c.put(0, 0, "STRING", Color.rgb(255, 140, 220), true);
            c.put(8, 0, Fx.commas(Math.round(len * Fx.easeOut(p))) + " chars", Color.WHITE, p >= 1);
            if (p >= 1) c.put(26, 0, subLine(), Color.rgb(150, 150, 160));
            String head = event.info.head == null ? "" : event.info.head;
            int[] cps = head.codePoints().toArray();
            int span = Math.min(w - 4, cps.length);
            for (int i = 0; i < span; i++) {
                double arrive = (double) i / Math.max(span, 1) * 0.45;
                double lt = t - arrive;
                if (lt < 0) continue;
                double x = 2 + i + Math.round((w - i) * (1 - Fx.easeOut(lt / 0.18)));
                int[] col = lt < 0.12 ? Color.WHITE : Color.rainbow(i / 40.0 + t, 0.6);
                c.put(x, 1, new String(Character.toChars(cps[i])), col);
            }
            if (len > span && t > 0.5) c.put(2 + span, 1, "...", Color.rgb(120, 120, 130));
        }
    }

    /** Arrays and maps: one block per element pops into place. */
    public static class Crate extends Scene {
        public Crate(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "hit"; }
        @Override public int height() { return 2; }
        @Override public double length() { return 0.7; }
        @Override public boolean top() { return ctx.input != null; }

        @Override
        public void draw(Canvas c, double t) {
            boolean hash = "hash".equals(event.info.type);
            int size = event.info.size;
            String label = hash ? "MAP" : "ARRAY";
            int cell = hash ? 3 : 2;
            int fit = Math.max((w - 14) / cell, 1);
            int n = Math.min(size, fit);
            c.put(0, 0, label, hash ? Color.rgb(255, 200, 90) : Color.rgb(110, 220, 255), true);
            c.put(label.length() + 1, 0, "x " + Fx.commas(Math.round(size * Fx.easeOut(Fx.phase(t, 0, 0.45)))), Color.WHITE, true);
            if (t > 0.45) c.put(label.length() + 12, 0, subLine(), Color.rgb(150, 150, 160));
            for (int i = 0; i < n; i++) {
                double born = (double) i / Math.max(n, 1) * 0.4;
                double age = t - born;
                if (age < 0) continue;
                int x = 2 + i * cell;
                boolean pop = age < 0.06;
                if (hash) {
                    c.bg(x, 1, pop ? Color.WHITE : Color.hsv(30 + i * 7, 0.7, 0.95));
                    c.bg(x + 1, 1, pop ? Color.WHITE : Color.hsv(200 + i * 7, 0.6, 0.8));
                } else {
                    c.bg(x, 1, pop ? Color.WHITE : Color.hsv(180 + i * 9, 0.65, 0.95));
                }
            }
            if (size > n && t > 0.4) c.put(2 + n * cell + 1, 1, "+" + (size - n), Color.rgb(200, 200, 210));
        }
    }

    /** Big word banners: FIRST HIT, COMBO 10, NEW RECORD, COMEBACK, milestones. */
    public static class Banner extends Scene {
        protected final String text;
        protected final String sub;
        protected final int[][] pal;
        protected final boolean isMega;
        protected final boolean rainbow;
        protected final boolean isAlt;
        protected int scale;
        protected double land;
        protected int tx;
        protected Fx.Particles parts;
        protected Fx.Particles fire;

        public Banner(Ctx ctx, Event event, String text, String sub, int[][] palette, boolean mega, boolean rainbow, boolean alt) {
            super(ctx, event);
            this.text = text;
            this.sub = sub;
            this.pal = palette == null ? Color.FIRE : palette;
            this.isMega = mega;
            this.rainbow = rainbow;
            this.isAlt = alt;
        }

        @Override public boolean big() { return true; }
        @Override public boolean mega() { return isMega; }
        @Override public boolean alt() { return isAlt && isMega; }
        @Override public String sfx() { return isMega ? "mega" : "banner"; }
        @Override public boolean top() { return ctx.input != null && !isAlt; }
        @Override public double length() { return isMega ? 1.9 : 1.05; }

        @Override
        protected void setup() {
            scale = bestScale(text);
            if (scale == 2 && !isMega && Font.width(text, 2) > w * 0.8) scale = 1;
            land = 0.2;
            parts = new Fx.Particles(rng);
            fire = new Fx.Particles(rng);
            if (scale == 0) return;
            int tw = Font.width(text, scale);
            tx = (w - tw) / 2;
            int by = textY() + Font.HEIGHT;
            for (int i = 0; i < 10; i++) {
                parts.burst(tx + tw * i / 9.0, by, isMega ? 10 : 6, land, Fx.Particles.opt().speed(40).life(0.55)
                        .palette(pal).gravity(20).angle(-Math.PI / 2).arc(Math.PI * 1.6));
            }
            if (!isMega) return;
            int height = height();
            int[][][] pals = {Color.FIRE, Color.ICE, Color.NEON, Color.TOXIC, Color.GOLD};
            for (int i = 0; i < 8; i++) {
                double at = 0.35 + i * 0.16;
                double x = 4 + rng.nextDouble() * (w - 8);
                double y = 1 + rng.nextDouble() * Math.max(height - 6, 2);
                fire.add(x, height, 0, -(height - y) / 0.15 * 2, at - 0.15, 0.15,
                        new int[][] {Color.WHITE, Color.rgb(255, 200, 120)});
                fire.burst(x, y, 45, at, Fx.Particles.opt().speed(45).life(0.7).palette(pals[i % 5]).gravity(10).drag(2.5));
            }
            String[] glyphs = {"*", "+", "'", ",", Term.glyph("✦", "*")};
            for (int i = 0; i < 40; i++) {
                fire.add(rng.nextDouble() * w, -1, (rng.nextDouble() - 0.5) * 4, 2 + rng.nextDouble() * 4,
                        0.3 + rng.nextDouble() * 1.2, 1.2,
                        new int[][] {Color.rainbow(rng.nextDouble()), Color.rainbow(rng.nextDouble())},
                        glyphs[rng.nextInt(5)], 1.5, 0);
            }
        }

        @Override
        public int height() {
            if (isAlt) return ctx.rows;
            if (scale == 0) return 2;
            return isMega ? Math.max(Math.min(ctx.rows - 4, 13), 8) : 7;
        }

        protected int textY() { return isMega ? (height() - Font.HEIGHT) / 2 - 1 : 1; }

        @Override
        public int shake(double t) {
            if (t < land) return 0;
            double k = 1 - Fx.phase(t, land, land + (isMega ? 0.45 : 0.25));
            return k > 0 ? (int) Math.round((rng.nextDouble() * 2 - 1) * (isMega ? 4 : 2) * k) : 0;
        }

        @Override public double flashAt() { return land; }

        @Override
        public void draw(Canvas c, double t) {
            if (scale == 0) {
                drawCompact(c, t);
                return;
            }
            int ty = textY();
            double drop = t < land ? (1 - Fx.easeIn(t / land)) * (ty + Font.HEIGHT + 1) : 0;
            double y = ty - Math.round(drop);
            double sweep = (t * 1.3) % 1.4 - 0.2;
            Fx.ColorFn fn = (rx, ry, f) -> {
                int[] base = rainbow ? Color.rainbow(f * 0.8 - t * 0.9) : Color.ramp(pal, 0.1 + ry * 0.14);
                double d = Math.abs(f - sweep);
                return t >= land && d < 0.07 ? Color.mix(Color.WHITE, base, d / 0.07) : base;
            };
            if (isMega) fire.draw(c, t);
            Fx.bigText(c, text, tx, y, fn, scale);
            if (t >= land) {
                Fx.shockwave(c, w / 2.0, ty + 2.5, t - land, isMega ? 90 : 60, pal, 0.5, isMega ? 3 : 2);
                parts.draw(c, t);
                if (flashOn() && t < land + 0.12) {
                    c.tintBg(Color.ramp(pal, 0.1), 0.5 * (1 - Fx.phase(t, land, land + 0.12)), 0, 0, w, height());
                }
                if (t > land + 0.2) Fx.sparkles(c, t, 7, isMega ? 30 : 10, pal);
                String s = bannerSub();
                int n = (int) Math.round(s.length() * Fx.phase(t, land + 0.1, land + 0.45));
                if (n > 0 && ty + Font.HEIGHT < height()) {
                    c.putCenter(ty + Font.HEIGHT + (isMega ? 2 : 0), Fx.take(s, n), Color.ramp(pal, 0.15), true);
                }
            }
        }

        protected String bannerSub() {
            List<String> bits = new ArrayList<>();
            if (sub != null) bits.add(sub);
            for (String b : subBits()) if (!text.contains(b)) bits.add(b);
            return String.join("   ", bits);
        }

        protected void drawCompact(Canvas c, double t) {
            String txt = Fx.spaced(text);
            double p = Fx.phase(t, 0, 0.3);
            for (int i = 0; i < txt.length(); i++) {
                int[] col = rainbow ? Color.rainbow(i / 12.0 - t) : Color.ramp(pal, (i / (double) Math.max(txt.length(), 1)) * 0.5);
                if (i <= txt.length() * p) c.put(1 + i, 0, String.valueOf(txt.charAt(i)), col, true);
            }
            if (t > 0.3) c.put(1, 1, bannerSub(), Color.ramp(pal, 0.2));
            Fx.sparkles(c, t, 3, 6, pal);
        }

        @Override
        public String trail() {
            String star = Term.glyph("✦", "*");
            StringBuilder s = new StringBuilder(star + " " + text + " " + star);
            String bs = bannerSub();
            if (!bs.isEmpty()) s.append("  ").append(bs);
            if (rainbow && depth() != Color.Depth.NONE) return rainbowText(s.toString(), 20.0);
            return paint(s.toString(), Color.ramp(pal, 0.15), true);
        }
    }

    /** Definitions: a new ability unlocks. */
    public static class Unlock extends Banner {
        public Unlock(Ctx ctx, Event event) {
            super(ctx, event, titleFor(ctx, event), null, Color.NEON, false, false, false);
        }

        private static String titleFor(Ctx ctx, Event event) {
            String what = event.info.what == null ? "" : event.info.what;
            String t = switch (what) {
                case "method" -> "NEW ABILITY";
                case "class" -> "NEW CLASS";
                case "interface" -> "NEW INTERFACE";
                case "enum" -> "NEW ENUM";
                case "record" -> "NEW RECORD TYPE";
                case "annotation" -> "NEW ANNOTATION";
                case "variable" -> "NEW VARIABLE";
                default -> "UNLOCKED";
            };
            if (Font.width(t) > ctx.w - 2) t = "UNLOCKED";
            return t;
        }

        private String label() {
            String what = event.info.what == null ? "def" : event.info.what;
            return what + " " + (event.info.name == null ? "" : event.info.name);
        }

        @Override
        public void draw(Canvas c, double t) {
            super.draw(c, t);
            if (scale == 0 || t < land + 0.15) return;
            String label = label();
            int n = (int) Math.round(label.length() * Fx.phase(t, land + 0.15, land + 0.5));
            int x = (w - label.length() - 4) / 2;
            c.fillBg(Color.rgb(90, 30, 160), x, 6, label.length() + 4, 1);
            c.put(x + 2, 6, Fx.take(label, n), Color.WHITE, true);
        }

        @Override
        public String trail() {
            return paint(Term.glyph("✦", "*") + " " + text + ": " + (event.info.name == null ? "" : event.info.name) + "  " + subLine(),
                    Color.ramp(Color.NEON, 0.2), true);
        }
    }

    /** Floods of stdout: digital rain behind a counter. */
    public static class Rain extends Scene {
        private double[][] cols;
        private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyz{}()<>=;:";

        public Rain(Ctx ctx, Event event) { super(ctx, event); }

        @Override public String sfx() { return "rain"; }
        @Override public int height() { return 5; }
        @Override public double length() { return 0.9; }
        @Override public boolean big() { return true; }

        @Override
        protected void setup() {
            cols = new double[Math.max(w, 0)][];
            for (int x = 0; x < cols.length; x++) {
                cols[x] = new double[] {rng.nextDouble() * 5, 6 + rng.nextDouble() * 14, rng.nextInt(3)};
            }
        }

        @Override
        public void draw(Canvas c, double t) {
            int height = height();
            for (int x = 0; x < cols.length; x++) {
                double off = cols[x][0], speed = cols[x][1];
                if ((int) cols[x][2] == 2) continue;
                double head = (((t * speed - off) % (height + 6)) + (height + 6)) % (height + 6);
                for (int k = 0; k < 6; k++) {
                    int y = (int) Math.floor(head - k);
                    if (y < 0 || y >= height) continue;
                    char ch = CHARS.charAt((x * 7 + y * 13 + (int) (t * 20)) % CHARS.length());
                    int[] col = k == 0 ? Color.rgb(230, 255, 230) : Color.ramp(Color.TOXIC, k / 6.0 + 0.1);
                    c.put(x, y, String.valueOf(ch), col, k == 0);
                }
            }
            String label = "  OUTPUT RAIN  x" + Fx.commas(event.info.outLines) + " LINES  ";
            int x = (w - label.length()) / 2;
            c.fillBg(Color.rgb(0, 40, 10), x, 2, label.length(), 1);
            c.put(x, 2, label, Color.rgb(180, 255, 180), true);
        }

        @Override
        public String trail() {
            return paint(Term.glyph("✦", "*") + " OUTPUT RAIN x" + event.info.outLines + " lines", Color.rgb(120, 230, 140), true);
        }
    }
}
