package dopajshell;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dopajshell.Event.Flag;
import dopajshell.Event.Kind;
import dopajshell.scenes.BonusScenes;
import dopajshell.scenes.FailureScenes;
import dopajshell.scenes.SessionScenes;
import dopajshell.scenes.SuccessScenes;

/** Every scene x width x height x color depth renders without errors and stays inside the width. */
class RenderTest {
    static final int[] WIDTHS = {30, 60, 80, 120};
    static final int[] ROWS = {24, 40};

    @AfterEach
    void reset() {
        Term.fakeRows = 0;
        Term.fakeCols = 0;
    }

    record Case(String name, String code, Function<Scene.Ctx, Scene> make) {}

    static Info info(String type) { return Info.of(type); }

    static Info integer(String v) {
        Info i = info("integer");
        i.intValue = new BigInteger(v);
        return i;
    }

    static Event ev(Kind kind, Info info, long gain, int combo, int streak, int mult, Flag... flags) {
        return Event.of(kind, info, gain, combo, streak, mult, 5, flags);
    }

    static Event ok(Info info, Flag... flags) { return ev(Kind.SUCCESS, info, 120, 3, 0, 1, flags); }

    static Info failure(String type, String cls, String msg) {
        Info i = info(type);
        i.className = cls;
        i.message = msg;
        return i;
    }

    static Game.Stats stats(boolean career) {
        Game.Stats s = new Game.Stats();
        s.evals = 42;
        s.successes = 37;
        s.failures = 5;
        s.interrupts = 1;
        s.maxCombo = 14;
        s.comebacks = 3;
        s.keystrokes = 1234;
        s.bestTyping = 40;
        s.score = 23_456;
        s.time = 754.2;
        if (career) {
            s.level = 7;
            s.levelBefore = 6;
            s.xp = 9000;
            s.xpBefore = 4000;
            s.bestBefore = 20_000;
            s.firstSession = false;
        }
        return s;
    }

    static List<Case> cases() {
        List<Case> cs = new ArrayList<>();
        cs.add(new Case("hit", "IntStream.range(1, 101).sum()", c -> new SuccessScenes.Hit(c, ok(integer("5050")))));
        cs.add(new Case("hit-fever", "12 / 5.0", c -> new SuccessScenes.Hit(c, ev(Kind.SUCCESS, info("float"), 1100, 12, 0, 2, Flag.FEVER))));
        Info nul = info("nil");
        nul.nullLabel = "void";
        cs.add(new Case("puff", "System.out.println()", c -> new SuccessScenes.Puff(c, ok(nul))));
        cs.add(new Case("stamp-true", "1 < 2", c -> new SuccessScenes.Stamp(c, ok(info("true")))));
        cs.add(new Case("stamp-false", "List.of().isEmpty() == false", c -> new SuccessScenes.Stamp(c, ok(info("false")))));
        cs.add(new Case("counter", "BigInteger.TWO.pow(64)", c -> new SuccessScenes.Counter(c, ok(integer("18446744073709551616")))));
        cs.add(new Case("counter-neg", "-123456789L", c -> new SuccessScenes.Counter(c, ok(integer("-123456789")))));
        Info fl = info("float");
        fl.floatValue = 123456.789;
        cs.add(new Case("counter-float", "123456.789", c -> new SuccessScenes.Counter(c, ok(fl))));
        Info big = info("float");
        big.floatValue = 1.5e300;
        cs.add(new Case("counter-bigfloat", "1.5e300", c -> new SuccessScenes.Counter(c,
                ev(Kind.SUCCESS, big, 420, 3, 0, 1, Flag.NEW_RECORD))));
        Info str = info("string");
        str.length = 108;
        str.head = "dopamine ".repeat(12);
        cs.add(new Case("stream", "\"dopamine \".repeat(12)", c -> new SuccessScenes.Stream(c, ok(str))));
        Info arr = info("array");
        arr.size = 240;
        cs.add(new Case("crate", "new int[240]", c -> new SuccessScenes.Crate(c, ok(arr))));
        Info map = info("hash");
        map.size = 5;
        cs.add(new Case("crate-map", "Map.of(1,2,3,4,5,6,7,8,9,10)", c -> new SuccessScenes.Crate(c, ok(map))));
        Info def = info("definition");
        def.what = "method";
        def.name = "boost";
        cs.add(new Case("unlock", "int boost() { return 1; }", c -> new SuccessScenes.Unlock(c, new Event(Kind.DEFINITION, def))));
        Info rec = info("definition");
        rec.what = "record";
        rec.name = "Point";
        cs.add(new Case("unlock-record", "record Point(int x, int y) {}", c -> new SuccessScenes.Unlock(c, ok(rec))));
        Info rain = info("nil");
        rain.outLines = 120;
        cs.add(new Case("rain", "for (int i = 0; i < 120; i++) System.out.println(i)",
                c -> new SuccessScenes.Rain(c, ok(rain, Flag.OUTPUT_RAIN))));
        cs.add(new Case("banner", "int x = 1", c -> new SuccessScenes.Banner(c, ev(Kind.SUCCESS, integer("1"), 200, 1, 0, 1, Flag.FIRST_HIT),
                "FIRST HIT!", null, Color.ICE, false, false, false)));
        cs.add(new Case("banner-mega", "1 + 1", c -> new SuccessScenes.Banner(c, ev(Kind.SUCCESS, integer("2"), 1350, 10, 0, 2,
                Flag.COMBO_MEGA, Flag.FEVER, Flag.FEVER_START), "COMBO 10!", "FEVER TIME! ALL POINTS x2", Color.FIRE, true, false, false)));
        cs.add(new Case("banner-alt", "1 + 1", c -> new SuccessScenes.Banner(c, ev(Kind.SUCCESS, integer("2"), 600, 1, 5, 1, Flag.COMEBACK),
                "COMEBACK!", null, Color.GOLD, true, true, true)));
        cs.add(new Case("critical", "Arrays.sort(a)", c -> new BonusScenes.Critical(c, ev(Kind.SUCCESS, info("object"), 960, 4, 0, 8, Flag.CRITICAL))));
        cs.add(new Case("jackpot", "new Random().nextInt(777)", c -> new BonusScenes.Jackpot(c,
                ev(Kind.SUCCESS, integer("777"), 6400, 6, 0, 16, Flag.JACKPOT))));

        Info syn = failure("syntax", "CompileError", "illegal start of expression");
        syn.line = 1;
        syn.column = 4;
        syn.detail = "illegal start of expression";
        cs.add(new Case("crack", "1 + ", c -> new FailureScenes.Crack(c, ev(Kind.FAILURE, syn, 0, 0, 1, 1))));
        Info syn2 = failure("syntax", "CompileError", "';' expected");
        cs.add(new Case("crack-noloc", "int x = ", c -> new FailureScenes.Crack(c, ev(Kind.FAILURE, syn2, 0, 0, 2, 1))));
        Info name = failure("name", "CompileError", "cannot find symbol");
        name.name = "undefinedThing";
        name.brokenCombo = 12;
        cs.add(new Case("spotlight", "undefinedThing + undefinedThing", c -> new FailureScenes.Spotlight(c,
                ev(Kind.FAILURE, name, 0, 0, 1, 1, Flag.FEVER_END, Flag.COMBO_BREAK))));
        Info nm = failure("nomethod", "CompileError", "cannot find symbol: method shout()");
        nm.name = "shout";
        nm.receiver = "Integer";
        cs.add(new Case("snap", "Integer.valueOf(1).shout()", c -> new FailureScenes.Snap(c, ev(Kind.FAILURE, nm, 0, 0, 2, 1))));
        Info npe = failure("nomethod", "java.lang.NullPointerException", "Cannot invoke \"String.length()\" because \"s\" is null");
        npe.name = "length";
        npe.receiver = "null";
        npe.title = "NULL POINTER";
        cs.add(new Case("snap-npe", "s.length()", c -> new FailureScenes.Snap(c, ev(Kind.FAILURE, npe, 0, 0, 1, 1))));
        Info ty = failure("type", "CompileError", "incompatible types: String cannot be converted to int");
        ty.sides = new String[] {"String", "int"};
        cs.add(new Case("clash", "int x = \"1\"", c -> new FailureScenes.Clash(c, ev(Kind.FAILURE, ty, 0, 0, 3, 1))));
        Info arg = failure("argument", "CompileError", "method abs cannot be applied to given types");
        arg.sides = new String[] {"given 2", "expected 1"};
        cs.add(new Case("clash-arg", "Math.abs(1, 2)", c -> new FailureScenes.Clash(c, ev(Kind.FAILURE, arg, 0, 0, 4, 1))));
        Info other = failure("other", "java.lang.ArithmeticException", "/ by zero");
        cs.add(new Case("failed", "1 / 0", c -> new FailureScenes.FailureScene(c, ev(Kind.FAILURE, other, 0, 0, 5, 1))));
        Info comp = failure("other", "CompileError", "missing return statement");
        comp.title = "COMPILE ERROR";
        cs.add(new Case("failed-title", "int f() {}", c -> new FailureScenes.FailureScene(c, ev(Kind.FAILURE, comp, 0, 0, 1, 1))));
        Info intr = info("interrupt");
        intr.charge = 60;
        cs.add(new Case("scatter", "while (true) {}", c -> new FailureScenes.Scatter(c, ev(Kind.INTERRUPT, intr, 0, 3, 0, 1))));

        cs.add(new Case("intro", null, c -> new SessionScenes.Intro(c, new SessionScenes.Career(5, 3, true))));
        cs.add(new Case("intro-fresh", null, c -> new SessionScenes.Intro(c, null)));
        cs.add(new Case("outro", null, c -> new SessionScenes.Outro(c, stats(false))));
        cs.add(new Case("finale", null, c -> new SessionScenes.Finale(c, stats(true))));
        cs.add(new Case("finale-plain", null, c -> new SessionScenes.Finale(c, stats(false))));
        return cs;
    }

    @Test
    void everySceneRendersInsideTheWidth() {
        Config config = new Config();
        for (Case k : cases()) {
            for (int cols : WIDTHS) {
                for (int rows : ROWS) {
                    for (Color.Depth depth : Color.Depth.values()) {
                        Term.fakeRows = rows;
                        Term.fakeCols = cols;
                        String where = k.name() + " " + cols + "x" + rows + " " + depth;
                        try {
                            render(k, config, cols, rows, depth, where);
                        } catch (RuntimeException e) {
                            throw new AssertionError(where + ": " + e, e);
                        }
                    }
                }
            }
        }
    }

    private void render(Case k, Config config, int cols, int rows, Color.Depth depth, String where) {
        Scene.Ctx ctx = new Scene.Ctx();
        ctx.w = cols - 1;
        ctx.rows = rows;
        ctx.depth = depth;
        ctx.config = config;
        ctx.rng = new Random(1);
        ctx.pre = 0.12;
        ctx.compact = cols < 40;
        if (k.code() != null) {
            ctx.input = InputShot.of(List.of("dopa> "), List.of(k.code()), List.of(k.code()), k.code(), cols);
        }
        Scene scene = k.make().apply(ctx).init();
        int height = scene.height();
        assertTrue(height >= 1, where + ": height " + height);
        if (scene.alt()) assertTrue(height <= rows, where + ": alt scene taller than the screen");
        double total = scene.totalLength();
        assertTrue(total > 0, where + ": length");
        boolean drew = false;
        for (int i = 0; i <= 12; i++) {
            double t = total * i / 12.0;
            if (scene.top() && ctx.input != null) {
                Canvas tc = new Canvas(ctx.w, ctx.input.size());
                if (scene.drawTop(tc, t)) check(tc.render(depth), ctx.w, where + " top t=" + t);
            }
            double ts = t - scene.pre();
            Canvas c = new Canvas(ctx.w, height);
            if (ts >= 0) scene.draw(c, ts);
            int shake = ts >= 0 ? scene.shake(ts) : 0;
            String[] out = c.render(depth, shake);
            check(out, ctx.w, where + " t=" + t);
            for (String l : out) if (!Term.stripAnsi(l).isBlank()) drew = true;
        }
        assertTrue(drew, where + ": nothing drawn");
        String trail = scene.trail();
        if (trail != null && depth == Color.Depth.NONE && trail.contains("\033[3")) {
            fail(where + ": colors in a no-color trail: " + trail);
        }
    }

    private static void check(String[] lines, int w, String where) {
        for (String l : lines) {
            int vw = Term.strWidth(l);
            if (vw > w) fail(where + ": row " + vw + " columns wide > " + w + ": " + Term.stripAnsi(l));
        }
    }
}
