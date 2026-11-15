package dopajshell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Per-keystroke effects. Pure logic: it is told what changed in the line editor and
 * answers what to draw; DopaLineReader does the plumbing.
 */
public final class InputFx {
    /** Where things are on screen right now. */
    public record Layout(int cursorX, int rowEndX, int screenWidth) {}

    /** The editor's buffer and cursor (char index). */
    public record Snap(String buffer, int cursor) {}

    public static final int STRIP_ROWS = 3;

    static final Map<Integer, String[]> STREAK_NAMES = Map.of(
            8, new String[] {"8-BIT", "RUSH!"}, 16, new String[] {"16-BIT", "COMBO!"}, 32, new String[] {"32-BIT", "BLAZE!!"},
            64, new String[] {"64-BIT", "OVERDRIVE!!"}, 128, new String[] {"128-BIT", "HYPER MODE!!"},
            256, new String[] {"0x100", "OVERFLOW!!!"}, 512, new String[] {"2^9", "GODSPEED!!!"},
            1024, new String[] {"1KiB", "TYPING LEGEND!!!"});

    static final Set<String> HISTORY_KEYS = Set.of("up-line-or-history", "down-line-or-history", "up-history", "down-history",
            "history-search-backward", "history-search-forward", "history-incremental-search-backward",
            "history-incremental-search-forward", "beginning-of-history", "end-of-history", "up-line-or-search",
            "down-line-or-search", "history-beginning-search-backward", "history-beginning-search-forward",
            "vi-up-line-or-history", "vi-down-line-or-history");
    static final Set<String> COMPLETE_KEYS = Set.of("complete-word", "expand-or-complete", "menu-complete",
            "reverse-menu-complete", "complete-prefix", "menu-expand-or-complete", "expand-or-complete-prefix",
            "menu-select");

    enum HlKind { FLASH, PAIR, RANGE, LOCK, REVEAL }

    record Highlight(int from, int to, double born, double life, HlKind kind) {}

    record Popup(String text, double x, double born, double life, int[][] palette, boolean rise, int prio) {}

    record Streak(int n, double x, double born, double life) {}

    private final Config config;
    private final Game game;
    private final DoubleSupplier clock;
    private final Random rng;
    private final Consumer<String> sound;
    private final Fx.Particles parts;
    private final List<Popup> popups = new ArrayList<>();
    private final List<Highlight> highlights = new ArrayList<>();
    private final List<Double> inserts = new ArrayList<>();
    private String lastKind;
    private int pasteChars;
    private boolean hlWasActive;
    private Object lastHud;
    private Popup inline;
    private Streak streak;

    public InputFx(Config config, Game game) {
        this(config, game, () -> System.nanoTime() / 1e9, new Random(), null);
    }

    public InputFx(Config config, Game game, DoubleSupplier clock, Random rng, Consumer<String> sound) {
        this.config = config;
        this.game = game;
        this.clock = clock;
        this.rng = rng;
        this.sound = sound != null ? sound : name -> {
            if ("sfx".equals(config.sound) && Sound.fast()) Sound.play(name);
        };
        this.parts = new Fx.Particles(rng);
    }

    public double now() { return clock.getAsDouble(); }
    Color.Depth depth() { return Term.depth(config); }
    public boolean lively() { return config.keys && config.level() >= 1; }
    boolean full() { return config.level() >= 2; }

    public Game game() { return game; }

    /** Count a pasted character without doing any layout work. */
    public synchronized void pasting() { pasteChars++; }

    public synchronized void onKey(String widget, Snap before, Snap after, int prevX, int curX) {
        try {
            double t = now();
            if (pasteChars > 0) flushPaste(curX, t);
            if ("begin-paste".equals(widget)) {
                pasteChars = Math.abs(after.buffer.length() - before.buffer.length());
                flushPaste(curX, t);
                return;
            }
            String old = before.buffer, neu = after.buffer;
            if (old.equals(neu)) {
                if (before.cursor == after.cursor) return;
                moved(prevX, curX, t);
                return;
            }
            if (widget != null && HISTORY_KEYS.contains(widget)) {
                history(neu, curX, t);
                return;
            }
            int pre = commonPrefix(old, neu);
            int suf = commonSuffix(old, neu, pre);
            String removed = old.substring(pre, old.length() - suf);
            String added = neu.substring(pre, neu.length() - suf);
            if (widget != null && COMPLETE_KEYS.contains(widget) && !added.isEmpty()) {
                completed(pre, added, curX, t);
                return;
            }
            if (added.startsWith("\n") && removed.isEmpty()) {
                game.key("newline");
                long n = neu.chars().filter(ch -> ch == '\n').count() + 1;
                pop("CHARGE LV" + n, curX, t, Color.FIRE, 0.6, false, 1);
                if (full()) parts.burst(curX + 2, 0.2, 14, t, Fx.Particles.opt().speed(24).life(0.4).palette(Color.FIRE).gravity(6).angle(0).arc(Math.PI));
                lastKind = "newline";
                return;
            }
            if (!removed.isEmpty() && added.isEmpty()) {
                deleted(removed, curX, t);
                return;
            }
            if (added.codePointCount(0, added.length()) == 1 && removed.isEmpty()) {
                inserted(added, pre, neu, curX, t);
                return;
            }
            if (added.length() > 8) {
                pasteChars = added.length();
                flushPaste(curX, t);
                return;
            }
            if (!added.isEmpty()) {
                game.key("insert", added.substring(0, 1));
                spark(curX, t, 2);
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
    }

    public synchronized boolean animating(double t) {
        if (parts.alive(t)) return true;
        for (Popup p : popups) if (t - p.born < p.life) return true;
        if (inline != null && t - inline.born < inline.life + 0.1) return true;
        for (double it : inserts) if (t - it < 0.4) return true;
        if (streak != null && t - streak.born < streak.life + 0.1) return true;
        if (hlDirty(t)) return true;
        Object hud = config.hud ? hudKey(t) : null;
        if (!java.util.Objects.equals(hud, lastHud)) {
            lastHud = hud;
            return true;
        }
        return false;
    }

    /** True while highlights are live and for the one render after they end. */
    synchronized boolean hlDirty(double t) {
        boolean active = false;
        for (Highlight h : highlights) if (t - h.born < h.life) active = true;
        boolean dirty = active || hlWasActive;
        hlWasActive = active;
        return dirty;
    }

    public synchronized void resetLine() {
        inline = null;
        streak = null;
        highlights.clear();
        inserts.clear();
        popups.clear();
        parts.clear();
        pasteChars = 0;
    }

    // ---- rendering -------------------------------------------------------

    /** The HUD as an SGR string, or null. */
    public synchronized String hudRender(Layout lay, double t) {
        if (!config.hud) return null;
        String text = hudText(lay.screenWidth - lay.rowEndX - 4, t);
        if (text == null) return null;
        double heat = game.heat(t);
        int[] bg = Color.mix(Color.rgb(22, 26, 44), Color.rgb(90, 26, 10), heat);
        int[] fg = Color.mix(Color.rgb(120, 135, 170), Color.rgb(255, 215, 120), heat);
        int[] gaugeOn = Color.mix(Color.rgb(80, 110, 170), Color.ramp(Color.FIRE, 0.35), heat);
        Canvas c = new Canvas(Term.strWidth(text), 1);
        c.put(0, 0, text, fg, bg, heat > 0.5);
        int f = text.indexOf("FEVER!");
        if (f >= 0) {
            for (int i = 0; i < 6; i++) c.put(f + i, 0, String.valueOf("FEVER!".charAt(i)), Color.rgb(20, 10, 30), Color.rainbow(i / 6.0 - t * 2), true);
        }
        int g = text.indexOf('[');
        if (g >= 0) {
            int cells = 8;
            int filled = (int) Math.round(game.charge() / Game.CHARGE_MAX * cells);
            for (int i = 0; i < cells; i++) {
                boolean on = i < filled;
                c.put(g + 1 + i, 0, Term.glyph(on ? "⣿" : "⣀", on ? "#" : "."), on ? gaugeOn : Color.rgb(70, 70, 90), bg, false);
            }
        }
        return c.render(depth())[0];
    }

    private Object hudKey(double t) {
        return List.of(String.valueOf(hudText(200, t)), Math.round(game.heat(t) * 12), Math.round(game.charge() / Game.CHARGE_MAX * 8),
                game.fever() ? (long) (t * 15) : 0L);
    }

    /** Sparks right of the cursor (only when the cursor is at the end of its row). */
    public synchronized String trailRender(Layout lay, boolean atEnd, double t) {
        if (!lively() || !atEnd) return null;
        if (inline != null && t - inline.born < inline.life) return inlineRender(lay, t);
        List<Double> recent = new ArrayList<>();
        for (double it : inserts) if (t - it < 0.35) recent.add(it);
        if (recent.isEmpty()) return null;
        int len = Math.min(recent.size() + 1, 6);
        int room = lay.screenWidth - lay.cursorX - 2;
        len = Math.min(len, room);
        if (len <= 0) return null;
        Canvas c = new Canvas(len, 1);
        String[] glyphs = {Term.glyph("✦", "*"), "*", "+", "'", "."};
        double age = t - recent.stream().mapToDouble(Double::doubleValue).max().orElse(t);
        for (int i = 0; i < len; i++) {
            double f = (double) i / len + age * 2;
            if (f > 1) continue;
            c.put(i, 0, glyphs[(int) ((i + (long) (t * 30)) % glyphs.length)], Color.ramp(Color.FIRE, f), i == 0);
        }
        return c.render(depth())[0];
    }

    /** Popup on the cursor row, for moments when the strip below is covered. */
    private String inlineRender(Layout lay, double t) {
        Popup p = inline;
        int room = lay.screenWidth - lay.cursorX - 2;
        String text = Fx.take(p.text, room);
        if (text.isEmpty()) return null;
        double age = (t - p.born) / p.life;
        Canvas c = new Canvas(text.length(), 1);
        for (int i = 0; i < text.length(); i++) {
            boolean sweep = Math.abs(i - age * text.length() * 2.5) < 1.5;
            int[] col = sweep ? Color.WHITE : Color.ramp(p.palette, 0.1 + age * 0.7);
            c.put(i, 0, String.valueOf(text.charAt(i)), col, age < 0.15 ? Color.ramp(p.palette, 0.5) : null, age < 0.6);
        }
        return c.render(depth())[0];
    }

    /** The effect strip below the input (STRIP_ROWS rows), or null when idle. */
    public synchronized String[] stripRender(Layout lay, double t) {
        if (!lively()) return null;
        popups.removeIf(p -> t - p.born > p.life);
        parts.prune(t);
        if (streak != null && t - streak.born > streak.life) streak = null;
        if (popups.isEmpty() && parts.isEmpty() && streak == null) return null;
        int w = lay.screenWidth - 1;
        Canvas c = new Canvas(w, STRIP_ROWS);
        if (streak != null && drawStreak(c, w, t)) {
            parts.draw(c, t);
            return c.render(depth());
        }
        parts.draw(c, t);
        for (Popup p : popups) {
            double age = (t - p.born) / p.life;
            int y = p.rise && age > 0.5 ? 0 : 1;
            int[] col = age < 0.15 ? Color.WHITE : Color.ramp(p.palette, 0.1 + age * 0.7);
            int[] bg = age < 0.12 ? Color.ramp(p.palette, 0.4) : null;
            double x = Math.min(Math.max(p.x, 0), w - p.text.length());
            c.put(x, y, p.text, col, bg, age < 0.6);
        }
        return c.render(depth());
    }

    /** Decorate the colored input with the live highlights (char offsets into buffer). */
    public synchronized AttributedString apply(AttributedString colored, String buffer, double t) {
        List<Highlight> live = new ArrayList<>();
        for (Highlight h : highlights) if (t - h.born < h.life) live.add(h);
        if (live.isEmpty() || colored == null || colored.length() != buffer.length()) return colored;
        AttributedStringBuilder sb = new AttributedStringBuilder(colored.length());
        boolean none = depth() == Color.Depth.NONE;
        for (int ci = 0; ci < colored.length(); ci++) {
            char ch = colored.charAt(ci);
            AttributedStyle st = colored.styleAt(ci);
            if (ch != '\n') {
                for (int k = live.size() - 1; k >= 0; k--) {
                    Highlight h = live.get(k);
                    if (ci >= h.from && ci < h.to) {
                        st = style(h, ci, (t - h.born) / h.life, st, none);
                        break;
                    }
                }
            }
            sb.style(st).append(ch);
        }
        return sb.toAttributedString();
    }

    private static AttributedStyle fgbg(int[] fg, int[] bg, boolean bold) {
        AttributedStyle s = AttributedStyle.DEFAULT;
        if (fg != null) s = s.foreground(fg[0], fg[1], fg[2]);
        if (bg != null) s = s.background(bg[0], bg[1], bg[2]);
        return bold ? s.bold() : s;
    }

    private AttributedStyle style(Highlight h, int ci, double f, AttributedStyle cur, boolean none) {
        switch (h.kind) {
            case FLASH -> {
                if (none) return AttributedStyle.DEFAULT.inverse();
                return fgbg(Color.rgb(20, 10, 0), Color.ramp(Color.FIRE, 0.05 + f * 0.8), true);
            }
            case PAIR -> {
                if (none) return AttributedStyle.DEFAULT.inverse().bold();
                double pulse = 0.5 + 0.5 * Math.cos(f * Math.PI * 5);
                int[] bg = Color.mix(Color.mix(Color.rgb(255, 190, 40), Color.rgb(255, 255, 200), pulse), Color.rgb(40, 30, 10), f);
                return fgbg(Color.rgb(30, 20, 0), bg, true);
            }
            case RANGE, LOCK -> {
                int[] base = h.kind == HlKind.LOCK ? Color.rgb(0, 170, 200) : Color.rgb(70, 60, 150);
                double sweep = h.from + (h.to - h.from) * Math.min(f * 2.5, 1);
                int[] bg = Math.abs(ci - sweep) < 1.5 ? Color.rgb(230, 250, 255) : Color.mix(base, Color.rgb(20, 20, 30), f);
                if (none) return cur.inverse();
                return cur.background(bg[0], bg[1], bg[2]);
            }
            case REVEAL -> {
                double head = h.from + (h.to - h.from) * Math.min(f * 1.6, 1);
                if (ci > head) return none ? AttributedStyle.DEFAULT.faint() : fgbg(Color.rgb(70, 70, 85), null, false);
                if (head - ci < 1.5) return none ? AttributedStyle.DEFAULT.inverse() : fgbg(Color.rgb(20, 20, 30), Color.rgb(220, 240, 255), true);
                return cur;
            }
            default -> {
                return cur;
            }
        }
    }

    // ---- reacting ----------------------------------------------------------

    private String hudText(int space, double t) {
        String score = Fx.commas(game.score());
        double mult = game.multiplier(t);
        String m = mult > 1.04 ? String.format(" x%.1f", mult) : "";
        String fever = game.fever() ? " FEVER!" : "";
        String lv = game.leveling() ? " LV" + game.level() + " " : "";
        String full = fever + lv + " COMBO " + String.format("%02d", game.combo()) + "  SCORE " + score + "  [" + " ".repeat(8) + "]" + m + " ";
        if (Term.strWidth(full) <= space) return full;
        String shrt = fever + " C" + String.format("%02d", game.combo()) + " " + score + " ";
        return Term.strWidth(shrt) <= space ? shrt : null;
    }

    private void inserted(String ch, int off, String neu, int curX, double t) {
        Game.KeyResult res = game.key("insert", ch);
        boolean recovering = "delete".equals(lastKind);
        lastKind = "insert";
        inserts.add(t);
        while (inserts.size() > 8) inserts.remove(0);
        if (!lively()) return;
        if (neu.length() < 4000) {
            highlights.removeIf(h -> h.kind == HlKind.FLASH);
            highlights.add(new Highlight(off, off + ch.length(), t, 0.18, HlKind.FLASH));
        }
        spark(curX, t, full() ? 4 : 1);
        sfx(game.typingCombo() >= 20 ? "key_hot" : "key");
        if (res.resume()) {
            sfx("charge");
            pop("CHARGE!", curX - 3, t, Color.FIRE, 0.7, true, 3);
            if (full()) parts.burst(curX, 0.0, 26, t, Fx.Particles.opt().speed(34).life(0.55).palette(Color.FIRE).gravity(10));
        } else if (recovering) {
            pop("RECOVERY", curX - 4, t, Color.TOXIC, 0.55, false, 1);
        }
        if (res.streak() > 0) {
            int n = res.streak();
            sfx("bits");
            if (full()) {
                streak = new Streak(n, curX, t, 1.3 * config.duration);
                parts.burst(curX, 1.0, 40, t, Fx.Particles.opt().speed(46).life(0.6).palette(n >= 64 ? Color.NEON : Color.GOLD).gravity(12));
            } else {
                String[] nm = STREAK_NAMES.get(n);
                pop(nm != null ? nm[0] + " " + nm[1] : "STREAK " + n, curX - 5, t, Color.GOLD, 0.9, true, 3);
            }
        }
        syntaxMoment(ch, off, neu, curX, t);
    }

    private void syntaxMoment(String ch, int off, String neu, int curX, double t) {
        if (neu.length() >= 20_000) return;
        try {
            if (")]}".contains(ch)) {
                List<JavaLexer.Token> tokens = JavaLexer.lex(neu);
                int open = JavaLexer.matchBracket(tokens, off);
                if (open < 0) return;
                if (ch.equals("}") && JavaLexer.isBlock(tokens, open)) {
                    game.key("block");
                    highlights.add(new Highlight(open, off + 1, t, 0.55, HlKind.RANGE));
                    highlights.removeIf(h -> h.kind == HlKind.FLASH);
                    pop("SEALED!", curX - 4, t, Color.NEON, 0.7, true, 2);
                    sfx("sealed");
                    if (full()) parts.burst(curX - 2, 0.0, 16, t, Fx.Particles.opt().speed(30).life(0.5).palette(Color.NEON).gravity(8));
                } else {
                    game.key("bracket");
                    highlights.add(new Highlight(open, open + 1, t, 0.6, HlKind.PAIR));
                    highlights.add(new Highlight(off, off + 1, t, 0.6, HlKind.PAIR));
                    highlights.removeIf(h -> h.kind == HlKind.FLASH);
                    pop("NICE!", curX - 3, t, Color.GOLD, 0.6, true, 2);
                    sfx("nice");
                    if (full()) parts.burst(curX - 1, 0.0, 12, t, Fx.Particles.opt().speed(26).life(0.4).palette(Color.GOLD).gravity(8));
                }
            } else if (ch.equals("\"") || ch.equals("'")) {
                int[] range = JavaLexer.matchString(JavaLexer.lex(neu), off);
                if (range != null) {
                    game.key("string");
                    highlights.add(new Highlight(range[0], range[1], t, 0.45, HlKind.RANGE));
                    if (full()) parts.burst(curX - 1, 0.0, 6, t, Fx.Particles.opt().speed(16).life(0.35).palette(Color.ICE).gravity(6));
                }
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
    }

    private void deleted(String removed, int curX, double t) {
        game.key("delete");
        lastKind = "delete";
        highlights.clear();
        if (!lively()) return;
        sfx("delete");
        String stripped = removed.replaceAll("\\s", "");
        String glyph = stripped.isEmpty() ? "." : stripped.substring(stripped.offsetByCodePoints(stripped.length(), -1));
        if (Term.strWidth(glyph) != 1) glyph = ".";
        parts.add(curX, 0.0, (rng.nextDouble() - 0.5) * 6, 3, t, 0.45,
                new int[][] {{255, 255, 255}, {255, 120, 120}, {120, 40, 40}}, glyph, 16, 0);
        int n = full() ? 7 : 3;
        String[] bits = {"'", ",", ".", "`"};
        for (int i = 0; i < n; i++) {
            parts.add(curX + 0.5, 0.1, (rng.nextDouble() - 0.5) * 22, -2 + rng.nextDouble() * 6, t, 0.4 + rng.nextDouble() * 0.2,
                    new int[][] {{255, 200, 200}, {230, 60, 60}, {80, 20, 20}}, bits[rng.nextInt(4)], 26, 1.0);
        }
    }

    private void moved(int prevX, int curX, double t) {
        game.key("move");
        lastKind = "move";
        if (!lively() || prevX < 0 || curX < 0 || prevX == curX) return;
        int dir = curX > prevX ? 1 : -1;
        int span = Math.abs(curX - prevX);
        int steps = Math.min(span, 30);
        for (int i = 0; i < steps; i++) {
            double x = curX - dir * (i + 1) * ((double) span / steps);
            parts.add(x, 0.15, dir * 4.0, 0, t + i * 0.004, 0.12 + 0.2 * (1 - (double) i / steps), Color.ICE);
        }
    }

    private void history(String neu, int curX, double t) {
        game.key("history");
        lastKind = "history";
        if (!lively()) return;
        highlights.clear();
        if (neu.length() < 4000) highlights.add(new Highlight(0, neu.length(), t, 0.35, HlKind.REVEAL));
        pop("<< REWIND", curX - 10, t, Color.ICE, 0.5, false, 1);
        sfx("rewind");
    }

    private void completed(int off, String added, int curX, double t) {
        game.key("complete");
        lastKind = "complete";
        if (!lively()) return;
        highlights.removeIf(h -> h.kind == HlKind.FLASH);
        highlights.add(new Highlight(off, off + added.length(), t, 0.5, HlKind.LOCK));
        inline = new Popup("<< LOCK ON", curX + 1, t, 0.6 * config.duration, Color.ICE, false, 2);
        sfx("lock");
        if (full()) parts.burst(curX, 0.2, 10, t, Fx.Particles.opt().speed(28).life(0.3).palette(Color.ICE).gravity(0));
    }

    private void flushPaste(int curX, double t) {
        int n = pasteChars;
        pasteChars = 0;
        game.key("paste");
        lastKind = "paste";
        if (lively()) pop("PASTE x" + n, Math.max(curX, 0) - 6, t, Color.NEON, 0.8, true, 1);
    }

    /** Big "64-BIT OVERDRIVE!!" in the strip. False when it cannot be drawn big. */
    private boolean drawStreak(Canvas c, int w, double t) {
        Streak s = streak;
        String[] names = STREAK_NAMES.getOrDefault(s.n, new String[] {String.valueOf(s.n), "STREAK!"});
        String big = names[0], tag = names[1];
        int bw = Font.width(big);
        String sub = "TYPING x" + s.n + "  +" + s.n * 4;
        int total = bw + 2 + Math.max(tag.length(), sub.length());
        if (total > w) return false;
        double age = (t - s.born) / s.life;
        int x = (int) Math.max(0, Math.min(s.x - total / 2.0, w - total));
        int slide = (int) Math.round((1 - Fx.easeOut(age / 0.12)) * 12);
        if (age < 0.1 && !"off".equals(config.flash)) c.tintBg(Color.WHITE, 0.6 * (1 - age / 0.1), Math.max(x - 1, 0), 0, total + 2, STRIP_ROWS);
        int[][] pal = s.n >= 64 ? Color.NEON : Color.GOLD;
        double sweep = (age * 3) % 1.4 - 0.2;
        int drawn = Fx.halfText(c, big, x + slide, 0, (cx, r, f) -> {
            int[] base = s.n >= 128 ? Color.rainbow(f - t * 2) : Color.ramp(pal, 0.1 + f * 0.3);
            return Math.abs(f - sweep) < 0.08 ? Color.WHITE : base;
        });
        if (drawn < 0) return false;
        int tx = x + bw + 2 + slide;
        for (int i = 0; i < tag.length(); i++) {
            int[] col = ((long) (t * 16) + i) % 2 == 0 ? Color.WHITE : Color.rainbow(i / 10.0 - t);
            c.put(tx + i, 0, String.valueOf(tag.charAt(i)), age > 0.7 ? Color.ramp(pal, age) : col, true);
        }
        c.put(tx, 2, sub, Color.ramp(pal, 0.2 + age * 0.5), age < 0.6);
        return true;
    }

    private void sfx(String name) {
        try {
            sound.accept(name);
        } catch (RuntimeException e) {
            Debug.log(e);
        }
    }

    private void spark(int curX, double t, int n) {
        if (n <= 0) return;
        parts.burst(curX - 0.5, -0.3, n, t, Fx.Particles.opt().speed(14).life(0.35).palette(Color.FIRE).gravity(20).angle(Math.PI / 2).arc(Math.PI * 1.2));
    }

    /** One popup at a time: a newer one wins unless the current one is more important and still fresh. */
    private void pop(String text, double x, double t, int[][] palette, double life, boolean rise, int prio) {
        if (!popups.isEmpty()) {
            Popup cur = popups.get(popups.size() - 1);
            if (t - cur.born < cur.life * 0.5 && cur.prio > prio) return;
        }
        popups.clear();
        popups.add(new Popup(text, x, t, life * config.duration, palette, rise, prio));
    }

    static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        if (i > 0 && i < a.length() && Character.isLowSurrogate(a.charAt(i))) i--;
        return i;
    }

    static int commonSuffix(String a, String b, int pre) {
        int n = Math.min(a.length(), b.length()) - pre;
        int i = 0;
        while (i < n && a.charAt(a.length() - 1 - i) == b.charAt(b.length() - 1 - i)) i++;
        if (i > 0 && a.length() - i < a.length() && Character.isLowSurrogate(a.charAt(a.length() - i))) i--;
        return i;
    }
}
