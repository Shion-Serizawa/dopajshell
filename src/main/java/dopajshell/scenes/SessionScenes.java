package dopajshell.scenes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dopajshell.Canvas;
import dopajshell.Color;
import dopajshell.Font;
import dopajshell.Fx;
import dopajshell.Game;
import dopajshell.Scene;
import dopajshell.Term;
import dopajshell.Version;

/** Startup title, the session result and the full-screen finale. */
public final class SessionScenes {
    private SessionScenes() {}

    /** What the profile says at startup. */
    public record Career(int level, int streak, boolean streakUp) {}

    static String clock(double secs) {
        long sec = (long) secs;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, s) : String.format(Locale.ROOT, "%dm%02ds", m, s);
    }

    static String ljust(String s, int n, char pad) {
        if (s.length() >= n) return s;
        return s + String.valueOf(pad).repeat(n - s.length());
    }

    static String rjust(String s, int n) {
        if (s.length() >= n) return s;
        return " ".repeat(n - s.length()) + s;
    }

    /** Startup title: letters drop in one by one, each with its own impact. */
    public static class Intro extends Scene {
        public static final String TITLE = "DOPA JSHELL";
        public static final String TAG = "every keystroke counts.";
        private static final int[][][] PALS = {Color.FIRE, Color.GOLD, Color.NEON, Color.ICE, Color.TOXIC};

        private final Career career;
        private int scale;
        private Fx.Particles parts;
        private final List<Object[]> letters = new ArrayList<>(); // {String ch, Integer x, Double at}

        public Intro(Ctx ctx, Career career) {
            super(ctx, null);
            this.career = career;
        }

        @Override public boolean big() { return true; }
        @Override public String sfx() { return "intro"; }
        @Override public double impactAt() { return 0.2; }

        /** "LV 5  *  DAY 3 STREAK!" or null on a fresh profile. */
        public String careerLine() {
            if (career == null) return null;
            List<String> bits = new ArrayList<>();
            if (career.level() > 1) bits.add("LV " + career.level());
            if (career.streak() >= 2) bits.add("DAY " + career.streak() + " STREAK" + (career.streakUp() ? "!" : ""));
            return bits.isEmpty() ? null : String.join("  " + Term.glyph("✦", "*") + "  ", bits);
        }

        @Override
        protected void setup() {
            scale = bestScale(TITLE);
            parts = new Fx.Particles(rng);
            if (scale == 0) return;
            int x = (w - Font.width(TITLE, scale)) / 2;
            for (int i = 0; i < TITLE.length(); i++) {
                String ch = String.valueOf(TITLE.charAt(i));
                int lw = Font.width(ch, scale);
                double at = 0.08 + i * 0.07;
                letters.add(new Object[] {ch, x, at});
                if (!" ".equals(ch)) {
                    parts.burst(x + lw / 2.0, 6, 12, at + 0.12, Fx.Particles.opt().speed(30).life(0.45).palette(PALS[i % 5])
                            .gravity(20).angle(-Math.PI / 2).arc(Math.PI * 1.4));
                }
                x += lw + scale;
            }
        }

        @Override public int height() { return scale > 0 ? 9 : 2; }
        @Override public double length() { return 1.35; }

        @Override
        public int shake(double t) {
            for (Object[] l : letters) {
                if (Fx.between(t - (double) l[2] - 0.12, 0, 0.05)) return rng.nextInt(3) - 1;
            }
            return 0;
        }

        @Override
        public void draw(Canvas c, double t) {
            if (scale == 0) {
                c.put(1, 0, Fx.spaced(TITLE), Color.rainbow(t), true);
                if (t > 0.4) c.put(1, 1, TAG, Color.rgb(200, 200, 210));
                return;
            }
            for (int i = 0; i < letters.size(); i++) {
                Object[] l = letters.get(i);
                String ch = (String) l[0];
                int x = (int) l[1];
                double lt = t - (double) l[2];
                if (lt < 0) continue;
                double y = lt < 0.12 ? 1 - Math.round((1 - Fx.easeIn(lt / 0.12)) * 7) : 1;
                final int idx = i;
                Fx.ColorFn fn = (rx, ry, f) -> {
                    int[] base = Color.rainbow(idx / 9.0 - t * 0.6 + ry * 0.02, 0.75);
                    return lt < 0.2 ? Color.mix(Color.WHITE, base, lt / 0.2) : base;
                };
                Fx.bigText(c, ch, x, y, fn, scale);
            }
            parts.draw(c, t);
            if (t > 0.7) Fx.sparkles(c, t, 11, 16, Color.GOLD);
            String cl = careerLine();
            String tag = cl == null ? TAG : cl + "   " + TAG;
            if (tag.length() > w) tag = TAG;
            int n = (int) Math.round(tag.length() * Fx.phase(t, 0.75, 1.1));
            if (n > 0) c.putCenter(7, Fx.take(tag, n), Color.rgb(230, 230, 240), true);
        }

        @Override
        public String trail() {
            String star = Term.glyph("✦", "*");
            String title = star + " DOPA JSHELL " + Version.VERSION + " " + star;
            String colored = depth() == Color.Depth.NONE ? title : rainbowText(title, 22.0);
            String cl = careerLine();
            String car = cl == null ? "" : paint(cl, Color.rgb(255, 215, 120), true) + "  ";
            return colored + "  " + car
                    + paint("Java " + Runtime.version().feature() + "  --  type /dopa for effect settings", Color.rgb(150, 150, 165));
        }
    }

    /** Session result screen (small terminals). Leaves a plain text table behind. */
    public static class Outro extends Scene {
        private final Game.Stats stats;
        private int scale;
        private Fx.Particles parts;

        public Outro(Ctx ctx, Game.Stats stats) {
            super(ctx, null);
            this.stats = stats;
        }

        @Override public boolean big() { return true; }
        @Override public String sfx() { return "result"; }

        private List<Object[]> rowsData() {
            Game.Stats s = stats;
            List<Object[]> r = new ArrayList<>();
            r.add(new Object[] {"EVALS", (long) s.evals});
            r.add(new Object[] {"HITS", (long) s.successes});
            r.add(new Object[] {"ERRORS", (long) s.failures});
            r.add(new Object[] {"INTERRUPTS", (long) s.interrupts});
            r.add(new Object[] {"MAX COMBO", (long) s.maxCombo});
            r.add(new Object[] {"KEYSTROKES", (long) s.keystrokes});
            r.add(new Object[] {"SCORE", s.score});
            return r;
        }

        @Override
        protected void setup() {
            scale = Fx.textFits("RESULT", w, 1) ? 1 : 0;
            parts = new Fx.Particles(rng);
            parts.burst(w / 2.0, 3, 60, 0.25, Fx.Particles.opt().speed(50).life(0.7).palette(Color.GOLD).gravity(12));
        }

        @Override public int height() { return (scale > 0 ? 6 : 1) + rowsData().size() + 2; }
        @Override public double length() { return 1.6; }
        @Override public double flashAt() { return 0.25; }

        @Override
        public void draw(Canvas c, double t) {
            int y;
            if (scale > 0) {
                Fx.ColorFn fn = (rx, ry, f) -> Color.rainbow(f * 0.6 - t * 0.5 + ry * 0.03, 0.7);
                long drop = t < 0.25 ? Math.round((1 - Fx.easeIn(t / 0.25)) * 6) : 0;
                Fx.bigText(c, "RESULT", (w - Font.width("RESULT")) / 2, 0 - drop, fn, 1);
                parts.draw(c, t);
                y = 6;
            } else {
                c.put(1, 0, "R E S U L T", Color.rainbow(t), true);
                y = 1;
            }
            int x = Math.max((w - 34) / 2, 1);
            List<Object[]> rows = rowsData();
            for (int i = 0; i < rows.size(); i++) {
                String k = (String) rows.get(i)[0];
                long v = (long) rows.get(i)[1];
                double at = 0.3 + i * 0.1;
                if (t < at) continue;
                double p = Fx.phase(t, at, at + 0.35);
                String shown = Fx.commas(Math.round(v * Fx.easeOut(p)));
                c.put(x, y + i, ljust(k, 14, '.'), Color.rgb(160, 160, 175));
                int[] col = "SCORE".equals(k) ? Color.rainbow(t) : (p < 1 ? Color.WHITE : Color.rgb(255, 220, 130));
                c.put(x + 14, y + i, rjust(shown, 16), col, true);
            }
            double at = 0.3 + rows.size() * 0.1;
            if (t > at) {
                c.put(x, y + rows.size(), ljust("TIME", 14, '.'), Color.rgb(160, 160, 175));
                c.put(x + 14, y + rows.size(), rjust(clock(stats.time), 16), Color.rgb(255, 220, 130), true);
            }
            if (t > 0.8) Fx.sparkles(c, t, 5, 14, Color.GOLD);
        }

        @Override
        public String trail() {
            String x = "  ";
            String star = Term.glyph("✦", "*");
            List<String> lines = new ArrayList<>();
            lines.add(paint(x + star + " DOPA JSHELL RESULT " + star, Color.rgb(255, 210, 110), true));
            for (Object[] r : rowsData()) lines.add(x + ljust((String) r[0], 12, ' ') + rjust(Fx.commas((long) r[1]), 14));
            lines.add(x + ljust("TIME", 12, ' ') + rjust(clock(stats.time), 14));
            return String.join("\n", lines);
        }
    }

    /**
     * Full-screen session result: title drop, stats slam in one by one, drum-rolled total
     * score, a rank stamp and fireworks. The "finale" sound is one track built on the same
     * timeline (FinaleTimeline).
     */
    public static class Finale extends Scene {
        static final long[] RANK_MIN = {15_000, 6_000, 2_000, 0};
        static final String[] RANK_NAMES = {"S", "A", "B", "C"};

        private final Game.Stats stats;
        private int h;
        private String rank, title;
        private int[][] rpal;
        private int tscale;
        private boolean twoCol, bigScore, newBest;
        private int tableX, tableY, scoreY, xpY;
        private int[] panel; // x, y, w, h or null
        private long score;
        private double[][] stars;
        private Fx.Particles parts, fire;

        public Finale(Ctx ctx, Game.Stats stats) {
            super(ctx, null);
            this.stats = stats;
        }

        public static String rankFor(Game.Stats stats) {
            for (int i = 0; i < RANK_MIN.length; i++) if (stats.score >= RANK_MIN[i]) return RANK_NAMES[i];
            return "C";
        }

        /** A cheerful title; never a verdict on the code. */
        public static String titleFor(Game.Stats s) {
            if (s.maxCombo >= 20) return "COMBO LEGEND";
            if (s.comebacks >= 3) return "COMEBACK KID";
            if (s.maxCombo >= 10) return "COMBO MASTER";
            if (s.failures >= 3 && s.successes > s.failures) return "NEVER GIVE UP";
            if (s.keystrokes >= 2000) return "IRON FINGERS";
            if (s.evals >= 100) return "MARATHONER";
            if (s.bestTyping >= 60) return "SPEED TYPER";
            return "WELL PLAYED";
        }

        public static boolean fits(int rows, int cols) { return rows >= 20 && cols >= 61; }

        static int[][] rankPalette(String rank) {
            return switch (rank) {
                case "S" -> Color.NEON;
                case "A" -> Color.GOLD;
                case "B" -> Color.ICE;
                default -> Color.TOXIC;
            };
        }

        @Override public boolean alt() { return true; }
        @Override public boolean big() { return true; }
        @Override public boolean mega() { return true; }
        @Override public int height() { return ctx.rows; }
        @Override public double length() { return FinaleTimeline.length(); }
        @Override public String sfx() { return "finale"; }
        @Override public double impactAt() { return 0.0; }
        @Override public double flashAt() { return FinaleTimeline.scoreLand(); }

        private List<Object[]> rowsData() {
            Game.Stats s = stats;
            List<Object[]> r = new ArrayList<>();
            r.add(new Object[] {"EVALS", (double) s.evals});
            r.add(new Object[] {"HITS", (double) s.successes});
            r.add(new Object[] {"ERRORS", (double) s.failures});
            r.add(new Object[] {"COMEBACKS", (double) s.comebacks});
            r.add(new Object[] {"MAX COMBO", (double) s.maxCombo});
            r.add(new Object[] {"KEYSTROKES", (double) s.keystrokes});
            r.add(new Object[] {"INTERRUPTS", (double) s.interrupts});
            r.add(new Object[] {"TIME", s.time});
            return r;
        }

        @Override
        protected void setup() {
            h = ctx.rows;
            rank = rankFor(stats);
            title = titleFor(stats);
            rpal = rankPalette(rank);
            tscale = Font.width("RESULT", 2) <= w - 4 ? 2 : 1;
            twoCol = w >= 80;
            bigScore = h >= 23;
            tableX = twoCol ? w / 2 - 38 : (w - 34) / 2;
            tableY = 8;
            panel = twoCol ? new int[] {w / 2 + 4, tableY, 30, 9} : null;
            scoreY = h - 7;
            score = stats.score;
            newBest = stats.firstSession != null && !stats.firstSession && score > stats.bestBefore;
            xpY = bigScore ? h - 2 : tableY + FinaleTimeline.ROWS + 2;
            stars = new double[80][];
            for (int i = 0; i < stars.length; i++) {
                stars[i] = new double[] {rng.nextInt(Math.max(w, 1)), rng.nextInt(Math.max(h, 1)), rng.nextDouble(), 0.4 + rng.nextDouble()};
            }
            buildParticles();
        }

        private void buildParticles() {
            parts = new Fx.Particles(rng);
            int tw = Font.width("RESULT", tscale);
            parts.burst(w / 2.0, 6, 70, FinaleTimeline.DROP, Fx.Particles.opt().speed(60).life(0.6).palette(Color.GOLD).gravity(18)
                    .angle(-Math.PI / 2).arc(Math.PI * 1.5));
            for (int i = 0; i < 8; i++) {
                parts.burst((w - tw) / 2.0 + tw * i / 7.0, 5.5, 5, FinaleTimeline.DROP,
                        Fx.Particles.opt().speed(30).life(0.45).palette(Color.FIRE).gravity(20));
            }
            for (int i = 0; i < FinaleTimeline.ROWS; i++) {
                parts.burst(tableX + 31, tableY + i + 0.5, 10, FinaleTimeline.rowLand(i),
                        Fx.Particles.opt().speed(22).life(0.35).palette(Color.GOLD).gravity(8));
            }
            double cy = bigScore ? scoreY + 2.5 : tableY + 4;
            parts.burst(w / 2.0, cy, 140, FinaleTimeline.scoreLand(), Fx.Particles.opt().speed(90).life(0.8).palette(Color.GOLD).gravity(16));
            parts.burst(w / 2.0, cy, 50, FinaleTimeline.scoreLand() + 0.03, Fx.Particles.opt().speed(55).life(0.7).palette(Color.FIRE)
                    .gravity(12).glyphs("*", "+", Term.glyph("✦", "*"), "'"));
            if (panel != null) {
                int px = panel[0], py = panel[1], pw = panel[2], ph = panel[3];
                parts.burst(px + pw / 2.0, py + ph / 2.0, 60, FinaleTimeline.rank(), Fx.Particles.opt().speed(45).life(0.55)
                        .palette(new int[][] {Color.WHITE, rpal[0], rpal[1], rpal[2]}).gravity(14));
            }
            fire = new Fx.Particles(rng);
            int[][][] pals = {Color.FIRE, Color.ICE, Color.NEON, Color.TOXIC, Color.GOLD};
            for (int i = 0; i < 10; i++) {
                double at = FinaleTimeline.rank() + 0.2 + i * 0.13;
                double x = 4 + rng.nextDouble() * (w - 8);
                double y = 2 + rng.nextDouble() * (h * 0.45);
                fire.add(x, h, 0, -(h - y) / 0.15 * 2, at - 0.15, 0.15, new int[][] {Color.WHITE, Color.rgb(255, 200, 120)});
                fire.burst(x, y, 55, at, Fx.Particles.opt().speed(50).life(0.9).palette(pals[i % pals.length]).gravity(9).drag(2.2));
            }
            String[] glyphs = {"*", "+", "'", ",", Term.glyph("✦", "*")};
            for (int i = 0; i < 90; i++) {
                fire.add(rng.nextDouble() * w, -1, (rng.nextDouble() - 0.5) * 5, 3 + rng.nextDouble() * 5,
                        FinaleTimeline.rank() + rng.nextDouble() * 1.4, 1.8,
                        new int[][] {Color.rainbow(rng.nextDouble()), Color.rainbow(rng.nextDouble())}, glyphs[rng.nextInt(5)], 1.2, 0);
            }
        }

        @Override
        public int shake(double t) {
            double[][] hits = {{FinaleTimeline.DROP, 4, 0.3}, {FinaleTimeline.scoreLand(), 6, 0.4}, {FinaleTimeline.rank(), 4, 0.3}};
            for (double[] hit : hits) {
                double age = t - hit[0];
                if (age >= 0 && age < hit[2]) return (int) Math.round((rng.nextDouble() * 2 - 1) * hit[1] * (1 - age / hit[2]));
            }
            return 0;
        }

        @Override
        public void draw(Canvas c, double t) {
            drawStars(c, t);
            flash(c, t);
            drawTitle(c, t);
            drawTable(c, t);
            drawScore(c, t);
            drawRank(c, t);
            drawXp(c, t);
            parts.draw(c, t);
            fire.draw(c, t);
            drawFooter(c, t);
        }

        @Override
        public String trail() {
            String x = "  ";
            String star = Term.glyph("✦", "*");
            List<String> lines = new ArrayList<>();
            lines.add(paint(x + star + " DOPA JSHELL RESULT " + star, Color.rgb(255, 210, 110), true));
            for (Object[] r : rowsData()) {
                lines.add(x + ljust((String) r[0], 12, ' ') + rjust(valueText((String) r[0], (double) r[1]), 14));
            }
            lines.add(paint(x + ljust("SCORE", 12, ' ') + rjust(Fx.commas(score), 14), Color.rgb(255, 220, 130), true));
            lines.add(paint(x + ljust("RANK", 12, ' ') + rjust(rank, 14) + "  " + title, Color.ramp(rpal, 0.2), true));
            if (stats.level != null) {
                int up = stats.level - (stats.levelBefore == null ? stats.level : stats.levelBefore);
                String lv = "LV " + stats.level + (up > 0 ? "  (+" + up + ")" : "");
                lines.add(paint(x + ljust("LEVEL", 12, ' ') + rjust(lv, 14) + "  " + Fx.commas(stats.xp) + " XP",
                        Color.rgb(200, 160, 255), true));
            }
            if (newBest) {
                lines.add(paint(x + star + " NEW BEST SCORE! (was " + Fx.commas(stats.bestBefore) + ") " + star,
                        Color.rgb(255, 120, 160), true));
            }
            return String.join("\n", lines);
        }

        private String valueText(String k, double v) {
            return "TIME".equals(k) ? clock(v) : Fx.commas((long) v);
        }

        private void drawStars(Canvas c, double t) {
            for (double[] s : stars) {
                double f = (t * s[3] + s[2]) % 1.0;
                if (f > 0.5) continue;
                double k = 1 - Math.abs(f - 0.25) * 4;
                c.put(s[0], s[1], k > 0.7 ? "+" : ".", Color.mix(Color.rgb(40, 40, 70), Color.rgb(200, 200, 255), k));
            }
        }

        private void flash(Canvas c, double t) {
            if (!flashOn()) return;
            Object[][] fl = {{FinaleTimeline.DROP, 0.12, Color.WHITE}, {FinaleTimeline.scoreLand(), 0.18, Color.rgb(255, 230, 150)},
                {FinaleTimeline.rank(), 0.12, Color.ramp(rpal, 0.2)}};
            for (Object[] f : fl) {
                double age = t - (double) f[0];
                double len = (double) f[1];
                if (age >= 0 && age < len) c.tintBg((int[]) f[2], 0.55 * (1 - age / len));
            }
        }

        private void drawTitle(Canvas c, double t) {
            long drop = t < FinaleTimeline.DROP ? Math.round((1 - Fx.easeIn(t / FinaleTimeline.DROP)) * 8) : 0;
            double sweep = (t * 0.9) % 1.4 - 0.2;
            Fx.ColorFn fn = (rx, ry, f) -> {
                int[] base = Color.rainbow(f * 0.7 - t * 0.5 + ry * 0.03, 0.75);
                double d = Math.abs(f - sweep);
                return d < 0.06 ? Color.mix(Color.WHITE, base, d / 0.06) : base;
            };
            Fx.bigText(c, "RESULT", (w - Font.width("RESULT", tscale)) / 2, 1 - drop, fn, tscale);
            Fx.shockwave(c, w / 2.0, 3.5, t - FinaleTimeline.DROP, 110, Color.GOLD, 0.6, 3);
        }

        private void drawTable(Canvas c, double t) {
            List<Object[]> rows = rowsData();
            for (int i = 0; i < rows.size(); i++) {
                String k = (String) rows.get(i)[0];
                double v = (double) rows.get(i)[1];
                double st = FinaleTimeline.rowStart(i);
                if (t < st) continue;
                int y = tableY + i;
                double slide = Fx.easeOut((t - st) / FinaleTimeline.SLIDE);
                double p = Fx.phase(t, st + FinaleTimeline.SLIDE, FinaleTimeline.rowLand(i));
                boolean landed = t >= FinaleTimeline.rowLand(i);
                long lx = tableX - Math.round((1 - slide) * 20);
                long vx = tableX + 14 + Math.round((1 - slide) * 30);
                double age = t - FinaleTimeline.rowLand(i);
                if (age >= 0 && age < 0.25) c.tintBg(Color.rgb(255, 200, 90), 0.45 * (1 - age / 0.25), tableX - 1, y, 34, 1);
                c.put(lx, y, ljust(k, 14, '.'), Color.rgb(150, 150, 175));
                String shown = "TIME".equals(k) ? clock(v * Fx.easeOut(p)) : Fx.commas(Math.round(v * Fx.easeOut(p)));
                int[] col;
                if (!landed) col = Color.WHITE;
                else if ("ERRORS".equals(k) || "INTERRUPTS".equals(k)) col = Color.rgb(180, 190, 220);
                else col = Color.ramp(Color.GOLD, 0.2);
                c.put(vx, y, rjust(shown, 16), col, true);
            }
        }

        private void drawScore(Canvas c, double t) {
            if (t < FinaleTimeline.scoreStart()) return;
            double p = Fx.phase(t, FinaleTimeline.scoreStart(), FinaleTimeline.scoreLand());
            boolean landed = t >= FinaleTimeline.scoreLand();
            long val = landed ? score : Math.round(score * Math.pow(Fx.easeIn(p), 0.7));
            String txt = Fx.commas(val);
            boolean best = landed && newBest;
            if (!bigScore) {
                c.putCenter(tableY + FinaleTimeline.ROWS + 1, "TOTAL SCORE  " + txt + (best ? "  NEW BEST!" : ""),
                        landed ? Color.rainbow(t) : Color.rgb(150, 220, 255), true);
                return;
            }
            if (best) {
                String star = Term.glyph("✦", "*");
                c.putCenter(scoreY - 1, star + "  N E W   B E S T !  " + star, Color.rainbow(t * 1.5), true);
            } else {
                c.putCenter(scoreY - 1, "T O T A L   S C O R E", Color.rgb(170, 170, 200), true);
            }
            int scale = Font.width(Fx.commas(score), 2) <= w - 4 ? 2 : 1;
            if (Font.width(txt, scale) > w - 2) scale = 1;
            Fx.ColorFn fn = (rx, ry, f) -> {
                if (landed) {
                    int[] base = Color.ramp(Color.GOLD, 0.1 + ry * 0.12);
                    double sweep = ((t - FinaleTimeline.scoreLand()) * 1.5) % 1.4 - 0.2;
                    double d = Math.abs(f - sweep);
                    return d < 0.08 ? Color.mix(Color.WHITE, base, d / 0.08) : base;
                }
                return Color.ramp(Color.ICE, 0.15 + ry * 0.1);
            };
            int jitter = landed ? 0 : rng.nextInt(2);
            Fx.bigText(c, txt, (w - Font.width(txt, scale)) / 2 + jitter, scoreY, fn, scale);
            Fx.shockwave(c, w / 2.0, scoreY + 2.5, t - FinaleTimeline.scoreLand(), 140, Color.GOLD, 0.7, 4);
        }

        private void drawRank(Canvas c, double t) {
            double rk = FinaleTimeline.rank();
            if (panel == null) {
                if (t < rk) return;
                c.putCenter(bigScore ? scoreY - 3 : tableY + FinaleTimeline.ROWS + 3, "RANK " + rank + "   " + title,
                        Color.ramp(rpal, 0.15), true);
                return;
            }
            int px = panel[0], py = panel[1], pw = panel[2], ph = panel[3];
            if (t < rk - 0.14) return;
            if (t < rk) {
                long grow = Math.round((1 - Fx.easeIn((t - rk + 0.14) / 0.14)) * 8);
                c.fillBg(Color.scale(Color.ramp(rpal, 0.5), 0.35), px - grow, py - grow / 2, pw + grow * 2, ph + grow);
                return;
            }
            c.fillBg(Color.scale(Color.ramp(rpal, 0.6), 0.45), px, py, pw, ph);
            c.fillBg(Color.rgb(12, 12, 22), px + 1, py + 1, pw - 2, ph - 2);
            c.put(px + (pw - 4) / 2, py + 1, "RANK", Color.rgb(200, 200, 220), true);
            int lw = Font.width(rank, 2);
            Fx.ColorFn fn = (rx, ry, f) -> "S".equals(rank) ? Color.rainbow(f * 0.5 - t + ry * 0.05) : Color.ramp(rpal, 0.05 + ry * 0.1);
            Fx.bigText(c, rank, px + (pw - lw) / 2, py + 2, fn, 2);
            int n = (int) Math.round(title.length() * Fx.phase(t, rk + 0.15, rk + 0.5));
            c.put(px + (pw - title.length()) / 2, py + 7, Fx.take(title, n), Color.ramp(rpal, 0.1), true);
        }

        /** LV 7 [=========-----] 4,210 / 8,192 XP: fills from the start of the session. */
        private void drawXp(Canvas c, double t) {
            double rk = FinaleTimeline.rank();
            if (stats.level == null || t < rk + 0.3) return;
            double p = Fx.easeOut(Fx.phase(t, rk + 0.3, rk + 1.2));
            long xp = Math.round(stats.xpBefore + (stats.xp - stats.xpBefore) * p);
            int lv = Game.levelFor(xp);
            long lo = Game.xpFor(lv), hi = Game.xpFor(lv + 1);
            int barW = Math.max(Math.min(w - 50, 30), 10);
            long fill = Math.round((double) (xp - lo) / Math.max(hi - lo, 1) * barW);
            String label = "LV " + lv + " ";
            String tail = " " + Fx.commas(xp) + " / " + Fx.commas(hi) + " XP";
            boolean up = lv > (stats.levelBefore == null ? lv : stats.levelBefore);
            if (up) tail += "  LEVEL UP!";
            int total = label.length() + barW + tail.length();
            int x = (w - total) / 2;
            c.put(x, xpY, label, Color.rgb(210, 170, 255), true);
            for (int i = 0; i < barW; i++) {
                boolean on = i < fill;
                int[] col = on ? Color.rainbow((double) i / barW - t) : Color.rgb(60, 50, 80);
                c.put(x + label.length() + i, xpY, on ? Term.glyph("█", "#") : Term.glyph("░", "."), col);
            }
            c.put(x + label.length() + barW, xpY, tail,
                    up && ((int) (t * 8)) % 2 == 0 ? Color.WHITE : Color.rgb(210, 170, 255), up);
        }

        private void drawFooter(Canvas c, double t) {
            String msg = "thanks for playing  " + Term.glyph("✦", "*") + "  see you next session";
            double rk = FinaleTimeline.rank();
            int n = (int) Math.round(msg.length() * Fx.phase(t, rk + 0.9, rk + 1.5));
            if (n > 0) c.putCenter(h - 1, Fx.take(msg, n), Color.rgb(170, 170, 200));
        }
    }
}
