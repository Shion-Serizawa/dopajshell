package dopajshell.scenes;

import java.util.ArrayList;
import java.util.List;

import dopajshell.Canvas;
import dopajshell.Color;
import dopajshell.Event;
import dopajshell.Font;
import dopajshell.Fx;
import dopajshell.Scene;
import dopajshell.Term;

/** Lucky moments: critical hits and the jackpot. */
public final class BonusScenes {
    private BonusScenes() {}

    /** A random critical hit: a slash, a red flash and the multiplier punching in. */
    public static class Critical extends Scene {
        private final int x = 4;
        private Fx.Particles parts;

        public Critical(Ctx ctx, Event event) { super(ctx, event); }

        @Override public boolean top() { return ctx.input != null; }
        @Override public boolean big() { return true; }
        @Override public int height() { return 3; }
        @Override public double length() { return 0.7; }
        @Override public String sfx() { return "crit"; }
        @Override public double impactAt() { return 0.1; }
        @Override public double flashAt() { return 0.1; }

        @Override
        protected void setup() {
            parts = new Fx.Particles(rng);
            parts.burst(x + 6, 1.5, 34, 0.1, Fx.Particles.opt().speed(42).life(0.4).palette(Color.BLOOD).gravity(8));
            parts.burst(x + 6, 1.5, 16, 0.12, Fx.Particles.opt().speed(26).life(0.35).palette(Color.GOLD).gravity(6)
                    .glyphs("*", "+", Term.glyph("✦", "*")));
        }

        @Override public int shake(double t) { return Fx.between(t, 0.1, 0.22) ? (rng.nextInt(3) - 1) * 2 : 0; }

        @Override
        public void draw(Canvas c, double t) {
            if (t < 0.1) {
                double p = t / 0.1;
                for (int y = 0; y <= 2; y++) {
                    double xx = Math.round(x - 2 + p * 16 + y * 3);
                    c.put(xx, y, "\\", Color.WHITE, true);
                    c.put(xx - 1, y, "\\", Color.rgb(255, 120, 120));
                }
                return;
            }
            parts.draw(c, t);
            double p = Fx.phase(t, 0.1, 0.7);
            if (flashOn()) c.tintBg(Color.rgb(255, 40, 40), 0.5 * (1 - Fx.phase(t, 0.1, 0.22)), 0, 0, w, 3);
            String label = "CRITICAL!!";
            for (int i = 0; i < label.length(); i++) {
                boolean flick = (((int) (t * 20)) + i) % 2 == 0;
                int[] col = flick ? Color.rgb(255, 70, 60) : Color.rgb(255, 220, 90);
                c.put(x + i, 1, String.valueOf(label.charAt(i)), t < 0.16 ? Color.WHITE : col, true);
            }
            int mx = x + label.length() + 2;
            String m = " x" + event.mult + " ";
            double pop = Fx.easeOutBack(Fx.phase(t, 0.12, 0.3));
            int[] bg = Color.mix(Color.WHITE, Color.rgb(200, 30, 30), pop);
            if (pop > 0.05) c.put(mx, 1, m, Color.WHITE, bg, true);
            c.put(mx + m.length() + 2, 1, "+" + Fx.commas(event.gain), Color.mix(Color.WHITE, Color.rgb(255, 210, 90), p), true);
            List<String> bits = new ArrayList<>();
            for (String b : subBits()) if (!b.startsWith("+") && !b.startsWith("CRITICAL")) bits.add(b);
            String sub = String.join("   ", bits);
            if (p > 0.2 && !sub.isEmpty()) c.put(x, 2, sub, Color.rgb(170, 150, 150));
            c.put(x, 0, "=>", Color.rgb(255, 160, 160), true);
        }

        @Override
        public String trail() {
            return paint("=> " + Term.glyph("✦", "*") + " CRITICAL!! x" + event.mult + "  +" + Fx.commas(event.gain)
                    + (event.combo > 1 ? "  COMBO " + event.combo : ""), Color.rgb(255, 110, 90), true);
        }
    }

    /** The rare jackpot: three slot reels stop on 7-7-7, then coins rain. */
    public static class Jackpot extends Scene {
        static final String[] SYMBOLS = {"7", "$", "?", "!", "*", "#"};
        static final double[] STOPS = {0.45, 0.7, 0.95};
        static final int REEL_W = 9;
        private static final int[] REEL_BG = Color.rgb(250, 245, 235);

        private boolean full;
        private int x0;
        private Fx.Particles parts;
        private Fx.Particles coins;

        public Jackpot(Ctx ctx, Event event) { super(ctx, event); }

        @Override public boolean top() { return ctx.input != null; }
        @Override public boolean big() { return true; }
        @Override public boolean mega() { return true; }
        @Override public double length() { return 2.3; }
        @Override public String sfx() { return "jackpot"; }
        @Override public double impactAt() { return STOPS[2]; }
        @Override public double flashAt() { return STOPS[2]; }
        @Override public int height() { return full ? 10 : 2; }

        @Override
        protected void setup() {
            full = !ctx.compact && w >= REEL_W * 3 + 8;
            x0 = (w - (REEL_W * 3 + 4)) / 2;
            parts = new Fx.Particles(rng);
            coins = new Fx.Particles(rng);
            if (!full) return;
            for (int i = 0; i < STOPS.length; i++) {
                parts.burst(x0 + i * (REEL_W + 2) + REEL_W / 2.0, 3.5, 14, STOPS[i],
                        Fx.Particles.opt().speed(22).life(0.3).palette(Color.GOLD).gravity(6));
            }
            parts.burst(w / 2.0, 3.5, 90, STOPS[2], Fx.Particles.opt().speed(70).life(0.7).palette(Color.GOLD).gravity(14));
            String[] glyphs = {"$", "o", "O", "$", Term.glyph("✦", "*")};
            int[][] coinPal = {Color.rgb(255, 250, 200), Color.rgb(255, 210, 60), Color.rgb(200, 140, 0)};
            for (int i = 0; i < 70; i++) {
                coins.add(rng.nextDouble() * w, -1, (rng.nextDouble() - 0.5) * 6, 6 + rng.nextDouble() * 8,
                        STOPS[2] + rng.nextDouble() * 1.1, 1.1, coinPal, glyphs[rng.nextInt(5)], 6, 0);
            }
        }

        @Override
        public int shake(double t) {
            double a = t - STOPS[2];
            return Fx.between(a, 0, 0.35) ? (int) Math.round((rng.nextDouble() * 2 - 1) * 3 * (1 - a / 0.35)) : 0;
        }

        @Override
        public void draw(Canvas c, double t) {
            if (!full) {
                drawCompact(c, t);
                return;
            }
            int[] frame = Color.rgb(60, 20, 70);
            c.fillBg(frame, x0 - 2, 0, REEL_W * 3 + 8, 7);
            int lights = (int) (t * 12);
            for (int i = 0; i < REEL_W * 3 + 8; i++) {
                boolean on = (i + lights) % 2 == 0;
                c.put(x0 - 2 + i, 0, "o", on ? Color.rgb(255, 230, 120) : Color.rgb(120, 80, 40), frame, on);
                c.put(x0 - 2 + i, 6, "o", on ? Color.rgb(120, 80, 40) : Color.rgb(255, 230, 120), frame, !on);
            }
            for (int i = 0; i < 3; i++) {
                int x = x0 + i * (REEL_W + 2);
                c.fillBg(REEL_BG, x, 1, REEL_W, 5);
                boolean stopped = t >= STOPS[i];
                String sym = stopped ? "7" : SYMBOLS[((int) (t * 26) + i * 2) % SYMBOLS.length];
                int bounce = stopped && t - STOPS[i] < 0.06 ? 1 : 0;
                int[] col;
                if (stopped) {
                    col = t >= STOPS[2] ? Color.rainbow(t * 1.5 + i * 0.2) : Color.rgb(220, 30, 40);
                } else {
                    col = Color.rgb(140, 140, 160);
                }
                int gw = Font.width(sym);
                final int[] fc = col;
                Fx.bigText(c, sym, x + (REEL_W - gw) / 2, 1 + bounce, (rx, ry, f) -> fc, 1, 0.0, null, 1.0,
                        (rx, ry, f) -> REEL_BG);
                if (!stopped) c.tintBg(Color.BLACK, 0.25, x, 1, REEL_W, 5);
            }
            parts.draw(c, t);
            coins.draw(c, t);
            if (t < STOPS[2]) return;
            if (flashOn()) {
                c.tintBg(Color.rgb(255, 230, 120), 0.6 * (1 - Fx.phase(t, STOPS[2], STOPS[2] + 0.15)), 0, 0, w, height());
            }
            String label = "J A C K P O T !!   x" + event.mult;
            int n = (int) Math.round(label.length() * Fx.phase(t, STOPS[2], STOPS[2] + 0.3));
            String shown = Fx.take(label, n);
            for (int i = 0; i < shown.length(); i++) {
                c.put((w - label.length()) / 2 + i, 8, String.valueOf(shown.charAt(i)), Color.rainbow(i / 14.0 - t), true);
            }
            long gain = Math.round(event.gain * Fx.easeOut(Fx.phase(t, STOPS[2] + 0.1, STOPS[2] + 0.7)));
            List<String> bits = new ArrayList<>();
            for (String b : subBits()) if (!b.startsWith("+") && !b.startsWith("JACKPOT")) bits.add(b);
            c.putCenter(9, "+" + Fx.commas(gain) + "  " + String.join("   ", bits), Color.rgb(255, 220, 120), true);
        }

        private void drawCompact(Canvas c, double t) {
            for (int i = 0; i < 3; i++) {
                String sym = t >= STOPS[i] ? "7" : SYMBOLS[((int) (t * 26) + i) % SYMBOLS.length];
                c.put(1 + i * 4, 0, "[" + sym + "]", t >= STOPS[i] ? Color.rgb(255, 80, 80) : Color.rgb(180, 180, 190), true);
            }
            if (t >= STOPS[2]) c.put(1, 1, "JACKPOT!! x" + event.mult + "  +" + Fx.commas(event.gain), Color.rainbow(t), true);
        }

        @Override
        public String trail() {
            String s = Term.glyph("✦", "*") + " [7][7][7] JACKPOT!! x" + event.mult + "  +" + Fx.commas(event.gain);
            if (depth() == Color.Depth.NONE) return s;
            return rainbowText(s, 20.0);
        }
    }
}
