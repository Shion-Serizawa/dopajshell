package dopajshell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleSupplier;

import dopajshell.Event.Flag;
import dopajshell.Event.Kind;

/**
 * COMBO / CHARGE / SCORE bookkeeping. Points are toys: they reward rhythm and recovery,
 * never code length or correctness.
 */
public final class Game {
    public static final double CHARGE_MAX = 100.0;
    public static final double TYPING_WINDOW = 0.6;
    static final Set<Integer> COMBO_MILESTONES = Set.of(3, 5, 10, 15, 20, 30, 50, 75, 100);
    static final Set<Integer> COMBO_MEGA = Set.of(10, 25, 50, 100);
    static final Set<Integer> EVAL_MILESTONES = Set.of(10, 50, 100, 500, 1000, 5000, 10_000);
    /** Typing streaks pay out at powers of two: 8, 16, 32, ... 1024. */
    static final Set<Integer> TYPING_MILESTONES = Set.of(8, 16, 32, 64, 128, 256, 512, 1024);
    public static final int FEVER_COMBO = 10;
    static final double CRIT_CHANCE = 0.10;
    static final double CRIT_CHARGE_BONUS = 0.12;
    static final int[] CRIT_MULTS = {2, 2, 2, 4, 4, 8};
    static final double JACKPOT_CHANCE = 0.015;
    public static final int JACKPOT_MULT = 16;

    /** Result of a keystroke: RESUME when typing restarts after a pause, or a typing streak. */
    public record KeyResult(boolean resume, int streak) {
        static final KeyResult NONE = new KeyResult(false, 0);
    }

    private final DoubleSupplier clock;
    private final Random rng;
    private final double startedAt;

    long score;
    int combo, maxCombo, evals, successes, failures, interrupts;
    double charge;
    int errorStreak;
    Double record;
    int typingCombo, bestTyping;
    Double lastKeyAt;
    private String lastChar;
    private int sameChar;
    private long typingPoints;
    int keystrokes, comebacks, crits, jackpots;
    long xpBase;
    int level = 1;
    Integer bestComboEver;
    private boolean leveling;
    private final List<Integer> streaksPaid = new ArrayList<>();

    public Game() { this(() -> System.nanoTime() / 1e9, new Random()); }

    public Game(DoubleSupplier clock, Random rng) {
        this.clock = clock;
        this.rng = rng;
        this.startedAt = clock.getAsDouble();
    }

    /** XP needed for a level doubles each time: LV2 at 512, LV3 at 1024, ... */
    public static long xpFor(int level) { return level <= 1 ? 0 : 1L << Math.min(level + 7, 62); }

    public static int levelFor(long xp) {
        int lv = 1;
        while (lv < 55 && xp >= xpFor(lv + 1)) lv++;
        return lv;
    }

    /** Career so far (from Profile): lifetime XP before this session and the best combo ever. */
    public synchronized void career(long xp, Integer bestCombo) {
        leveling = true;
        xpBase = xp;
        bestComboEver = bestCombo;
        level = levelFor(xpBase + score);
    }

    public synchronized long score() { return score; }
    public synchronized int combo() { return combo; }
    public synchronized int maxCombo() { return maxCombo; }
    public synchronized int evals() { return evals; }
    public synchronized int successes() { return successes; }
    public synchronized int failures() { return failures; }
    public synchronized double charge() { return charge; }
    public synchronized int typingCombo() { return typingCombo; }
    public synchronized int level() { return level; }
    public synchronized long xp() { return xpBase + score; }
    public synchronized boolean fever() { return combo >= FEVER_COMBO; }
    public synchronized boolean leveling() { return leveling; }

    public double now() { return clock.getAsDouble(); }

    public synchronized double multiplier(double t) {
        if (lastKeyAt == null || t - lastKeyAt > TYPING_WINDOW * 2) return 1.0;
        return 1.0 + Math.min(typingCombo, 50) / 25.0;
    }

    private double multiplierNow() { return 1.0 + Math.min(typingCombo, 50) / 25.0; }

    public synchronized double heat(double t) {
        if (lastKeyAt == null) return 0.0;
        double idle = t - lastKeyAt;
        double base = Fx.clamp01(Math.max(charge / CHARGE_MAX, typingCombo / 30.0));
        return base * Math.exp(-Math.max(idle - 1.0, 0) / 4.0);
    }

    private static final Map<String, double[]> KEY_GAINS = Map.of(
            "insert", new double[] {2.2, 1}, "delete", new double[] {0.6, 0}, "move", new double[] {0.2, 0},
            "bracket", new double[] {6.0, 15}, "string", new double[] {4.0, 10}, "block", new double[] {9.0, 25},
            "complete", new double[] {5.0, 20}, "history", new double[] {3.0, 5}, "newline", new double[] {8.0, 10},
            "paste", new double[] {10.0, 10});

    /** kind: insert, delete, move, bracket, string, block, complete, history, newline, paste. */
    public synchronized KeyResult key(String kind, String ch) {
        double t = now();
        boolean resumed = lastKeyAt != null && t - lastKeyAt > 2.0 && "insert".equals(kind);
        boolean move = "move".equals(kind);
        if (lastKeyAt != null && t - lastKeyAt <= TYPING_WINDOW) {
            if (!move) typingCombo++;
        } else {
            typingCombo = move ? 0 : 1;
        }
        if (typingCombo > bestTyping) bestTyping = typingCombo;
        boolean milestone = "insert".equals(kind) && TYPING_MILESTONES.contains(typingCombo);
        lastKeyAt = t;
        if (!"paste".equals(kind)) keystrokes++;

        double[] gp = KEY_GAINS.getOrDefault(kind, new double[] {0, 0});
        double gain = gp[0];
        double pts = gp[1];
        if ("insert".equals(kind)) {
            if (ch != null && ch.equals(lastChar)) {
                sameChar++;
            } else {
                sameChar = 0;
                lastChar = ch;
            }
            gain *= Math.pow(0.35, Math.min(sameChar, 4));
            if (sameChar > 2) pts = 0;
        }
        charge += gain * (1 - charge / CHARGE_MAX);
        if (charge > CHARGE_MAX) charge = CHARGE_MAX;
        if (typingPoints < 200) {
            long p = Math.round(pts * multiplierNow());
            typingPoints += p;
            score += p;
        }
        if (resumed) return new KeyResult(true, 0);
        if (!milestone) return KeyResult.NONE;
        // outside the per-eval typing cap, but each streak pays once per eval
        if (!streaksPaid.contains(typingCombo)) {
            streaksPaid.add(typingCombo);
            score += typingCombo * 4L;
        }
        return new KeyResult(false, typingCombo);
    }

    public KeyResult key(String kind) { return key(kind, null); }

    public synchronized Event success(Info info) {
        evals++;
        successes++;
        Event e = new Event("definition".equals(info.type) ? Kind.DEFINITION : Kind.SUCCESS, info);
        var flags = e.flags;
        if (successes == 1) flags.add(Flag.FIRST_HIT);
        if (errorStreak > 0) {
            flags.add(Flag.COMEBACK);
            comebacks++;
        }
        int streak = errorStreak;
        errorStreak = 0;
        combo++;
        if (combo > maxCombo) maxCombo = combo;
        if (COMBO_MILESTONES.contains(combo)) flags.add(Flag.COMBO_MILESTONE);
        if (COMBO_MEGA.contains(combo)) flags.add(Flag.COMBO_MEGA);
        if (EVAL_MILESTONES.contains(evals)) flags.add(Flag.EVAL_MILESTONE);
        if (info.outLines >= 20) flags.add(Flag.OUTPUT_RAIN);
        if (combo >= FEVER_COMBO) flags.add(Flag.FEVER);
        if (combo == FEVER_COMBO) flags.add(Flag.FEVER_START);
        if (bestComboEver != null && bestComboEver >= 5 && combo == bestComboEver + 1) flags.add(Flag.COMBO_BEST);

        Double num = numeric(info);
        if (num != null) {
            if (record != null && num > record && num >= 100) {
                flags.add(Flag.NEW_RECORD);
                if (record <= 0 ? num >= 1000 : num >= record * 2) flags.add(Flag.RECORD_JUMP);
            }
            if (record == null || num > record) record = num;
        }

        long gain = 100 + Math.min(combo - 1, 40) * 25L + Math.round(charge * 2);
        if (flags.contains(Flag.COMEBACK)) gain += 500;
        if (flags.contains(Flag.FIRST_HIT)) gain += 100;
        if (flags.contains(Flag.NEW_RECORD)) gain += 300;
        if (flags.contains(Flag.EVAL_MILESTONE)) gain += 1000;
        if (flags.contains(Flag.COMBO_MILESTONE)) gain += combo * 20L;
        int mult = 1;
        double roll = rng.nextDouble();
        if (roll < JACKPOT_CHANCE) {
            flags.add(Flag.JACKPOT);
            mult = JACKPOT_MULT;
            jackpots++;
        } else if (roll < CRIT_CHANCE + CRIT_CHARGE_BONUS * charge / CHARGE_MAX) {
            flags.add(Flag.CRITICAL);
            mult = CRIT_MULTS[rng.nextInt(CRIT_MULTS.length)];
            crits++;
        }
        if (combo >= FEVER_COMBO) mult *= 2;
        gain *= mult;
        score += gain;
        charge = 0.0;
        typingPoints = 0;
        streaksPaid.clear();
        int lv = leveling ? levelFor(xpBase + score) : level;
        if (lv > level) {
            flags.add(Flag.LEVEL_UP);
            level = lv;
        }
        e.gain = gain;
        e.combo = combo;
        e.streak = streak;
        e.score = score;
        e.mult = mult;
        e.level = level;
        e.evals = evals;
        return e;
    }

    public synchronized Event failure(Info info) {
        evals++;
        failures++;
        errorStreak++;
        int broken = combo;
        combo = 0;
        charge = 0.0;
        typingPoints = 0;
        streaksPaid.clear();
        info.brokenCombo = broken;
        Event e = new Event(Kind.FAILURE, info);
        if (broken >= 3) e.flags.add(Flag.COMBO_BREAK);
        if (broken >= FEVER_COMBO) e.flags.add(Flag.FEVER_END);
        if (EVAL_MILESTONES.contains(evals)) e.flags.add(Flag.EVAL_MILESTONE);
        e.combo = 0;
        e.streak = errorStreak;
        e.score = score;
        e.level = level;
        e.evals = evals;
        return e;
    }

    public synchronized Event interrupted() {
        interrupts++;
        Info info = Info.of("interrupt");
        info.charge = charge;
        charge = 0.0;
        typingPoints = 0;
        streaksPaid.clear();
        Event e = new Event(Kind.INTERRUPT, info);
        e.combo = combo;
        e.streak = errorStreak;
        e.score = score;
        e.level = level;
        e.evals = evals;
        return e;
    }

    public double elapsed() { return now() - startedAt; }

    /** Session statistics for the result screen. */
    public static final class Stats {
        public int evals, successes, failures, interrupts, maxCombo, comebacks, keystrokes, bestTyping, crits, jackpots;
        public long score;
        public double time;
        // career (when a profile is kept)
        public Integer level, levelBefore;
        public long xp, xpBefore, bestBefore;
        public Boolean firstSession;
    }

    public synchronized Stats stats() {
        Stats s = new Stats();
        s.evals = evals;
        s.successes = successes;
        s.failures = failures;
        s.interrupts = interrupts;
        s.maxCombo = maxCombo;
        s.score = score;
        s.comebacks = comebacks;
        s.keystrokes = keystrokes;
        s.bestTyping = bestTyping;
        s.crits = crits;
        s.jackpots = jackpots;
        s.time = elapsed();
        return s;
    }

    private static Double numeric(Info info) {
        if ("integer".equals(info.type) && info.intValue != null) return info.intValue.doubleValue();
        if ("float".equals(info.type) && Double.isFinite(info.floatValue)) return info.floatValue;
        return null;
    }
}
