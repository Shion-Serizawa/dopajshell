package dopajshell;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;

import dopajshell.Event.Flag;
import dopajshell.Event.Kind;

/** `/dopa demo`: every scene once, with made-up events. Does not touch the score. */
public final class Demo {
    private Demo() {}

    record Case(String code, Event event) {}

    private static Info integer(long v) {
        Info i = Info.of("integer");
        i.intValue = BigInteger.valueOf(v);
        return i;
    }

    private static Info failure(String type, String cls, String message) {
        Info i = Info.of(type);
        i.className = cls;
        i.message = message;
        return i;
    }

    static List<Case> cases() {
        Info str = Info.of("string");
        str.head = "dopamine ".repeat(12);
        str.length = str.head.length();
        Info arr = Info.of("array");
        arr.size = 24;
        Info def = Info.of("definition");
        def.what = "method";
        def.name = "boost";
        Info rain = Info.of("nil");
        rain.nullLabel = "void";
        rain.outLines = 120;
        Info nul = Info.of("nil");
        Info voidI = Info.of("nil");
        voidI.nullLabel = "void";
        Info big = Integer2(new BigInteger("18446744073709551616"));
        Info syntax = failure("syntax", "syntax error", "illegal start of expression");
        syntax.line = 1;
        syntax.column = 4;
        syntax.detail = "illegal start of expression";
        Info name = failure("name", "cannot find symbol", "cannot find symbol undefinedThing");
        name.name = "undefinedThing";
        Info nomethod = failure("nomethod", "cannot find symbol", "cannot find method shout in String");
        nomethod.name = "shout";
        nomethod.receiver = "String";
        Info npe = failure("nomethod", "NullPointerException", "Cannot invoke \"String.length()\" because \"s\" is null");
        npe.title = "NULL POINTER";
        npe.name = "length";
        npe.receiver = "null";
        Info type = failure("type", "incompatible types", "incompatible types: String cannot be converted to int");
        type.sides = new String[] {"String", "int"};
        Info arg = failure("argument", "cannot be applied", "method max in class Math cannot be applied to given types");
        arg.sides = new String[] {"given int", "expected int,int"};
        Info other = failure("other", "ArithmeticException", "/ by zero");
        Info fl = Info.of("float");
        fl.floatValue = 2.4;
        Info fl2 = Info.of("float");
        fl2.floatValue = 42.0;
        Info inter = Info.of("interrupt");
        inter.charge = 60;
        Info lvl = Info.of("string");
        lvl.head = "levellevel";
        lvl.length = 10;
        Info huge = Integer2(BigInteger.TWO.pow(128));
        return List.of(
                new Case("IntStream.rangeClosed(1, 100).sum()", ev(Kind.SUCCESS, integer(5050), 120, 3, 0, 1)),
                new Case("Map.of().get(\"x\")", ev(Kind.SUCCESS, nul, 120, 3, 0, 1)),
                new Case("System.out.println()", ev(Kind.SUCCESS, voidI, 120, 3, 0, 1)),
                new Case("1 < 2", ev(Kind.SUCCESS, Info.of("true"), 120, 3, 0, 1)),
                new Case("List.of().contains(1)", ev(Kind.SUCCESS, Info.of("false"), 120, 3, 0, 1)),
                new Case("BigInteger.TWO.pow(64)", ev(Kind.SUCCESS, big, 120, 3, 0, 1)),
                new Case("\"dopamine \".repeat(12)", ev(Kind.SUCCESS, str, 120, 3, 0, 1)),
                new Case("IntStream.range(0, 24).toArray()", ev(Kind.SUCCESS, arr, 120, 3, 0, 1)),
                new Case("String boost() { return \"on\"; }", ev(Kind.DEFINITION, def, 120, 3, 0, 1)),
                new Case("for (int i = 0; i < 120; i++) System.out.println(i)", ev(Kind.SUCCESS, rain, 120, 3, 0, 1, Flag.OUTPUT_RAIN)),
                new Case("var x = 1", ev(Kind.SUCCESS, integer(1), 120, 1, 0, 1, Flag.FIRST_HIT)),
                new Case("IntStream.range(0, 10).sum()", ev(Kind.SUCCESS, integer(45), 1350, 10, 0, 1, Flag.COMBO_MILESTONE, Flag.COMBO_MEGA)),
                new Case("1 + ", ev(Kind.FAILURE, syntax, 0, 0, 1, 1)),
                new Case("undefinedThing", ev(Kind.FAILURE, name, 0, 0, 1, 1)),
                new Case("\"hi\".shout()", ev(Kind.FAILURE, nomethod, 0, 0, 2, 1)),
                new Case("String s = null; s.length()", ev(Kind.FAILURE, npe, 0, 0, 3, 1)),
                new Case("int n = \"1\"", ev(Kind.FAILURE, type, 0, 0, 4, 1)),
                new Case("Math.max(1)", ev(Kind.FAILURE, arg, 0, 0, 5, 1)),
                new Case("1 / 0", ev(Kind.FAILURE, other, 0, 0, 6, 1)),
                new Case("1 + 1", ev(Kind.SUCCESS, integer(2), 600, 1, 6, 1, Flag.COMEBACK)),
                new Case("while (true) { }", ev(Kind.INTERRUPT, inter, 0, 0, 0, 1)),
                new Case("BigInteger.TWO.pow(128)", ev(Kind.SUCCESS, huge, 420, 3, 0, 1, Flag.NEW_RECORD, Flag.RECORD_JUMP)),
                new Case("Stream.of(3, 1, 2).sorted().toList()", ev(Kind.SUCCESS, sized(3), 960, 3, 0, 8, Flag.CRITICAL)),
                new Case("12 / 5.0", ev(Kind.SUCCESS, fl, 1100, 12, 0, 2, Flag.FEVER)),
                new Case("21.0 * 2", ev(Kind.SUCCESS, fl2, 1200, 13, 0, 2, Flag.FEVER, Flag.COMBO_BEST)),
                new Case("new Random().nextInt(777)", ev(Kind.SUCCESS, integer(777), 6_400, 3, 0, 16, Flag.JACKPOT)),
                new Case("\"level\".repeat(2)", ev(Kind.SUCCESS, lvl, 200, 3, 0, 1, Flag.LEVEL_UP)));
    }

    private static Info Integer2(BigInteger v) {
        Info i = Info.of("integer");
        i.intValue = v;
        return i;
    }

    private static Info sized(int n) {
        Info i = Info.of("array");
        i.size = n;
        return i;
    }

    private static Event ev(Kind kind, Info info, long gain, int combo, int streak, int mult, Flag... flags) {
        Event e = Event.of(kind, info, gain, combo, streak, mult, 5, flags);
        if (e.flag(Flag.LEVEL_UP)) e.level = 6;
        return e;
    }

    public static void run(Session session, Consumer<String> say) {
        if (!session.active()) {
            say.accept("dopajshell: not active (no terminal or intensity=off)");
            return;
        }
        Config config = session.config;
        String prompt = Term.depth(config) == Color.Depth.NONE ? "demo> " : "\033[1;38;2;255;110;230mdemo\033[0m> ";
        DopaHighlighter hl = new DopaHighlighter(null, () -> false, () -> Term.depth(config) != Color.Depth.NONE);
        for (Case c : cases()) {
            if (Term.inputPending()) break;
            String colored = hl.syntax(c.code).toAnsi(Term.terminal());
            Term.write(prompt + colored + "\r\n");
            InputShot shot = InputShot.of(List.of(prompt), List.of(colored), List.of(c.code), c.code, Term.cols());
            if (c.event.kind == Kind.FAILURE && "syntax".equals(c.event.info.type)) {
                c.event.info.column = Math.min(c.code.length(), c.event.info.column);
            }
            double t0 = System.nanoTime() / 1e9;
            Scene scene = session.director.playEvent(c.event, shot, c.event.kind == Kind.SUCCESS ? config.charge : 0);
            Term.write("\033[2m|  (demo)\033[0m\r\n");
            waitForSound(config, scene, t0);
            sleep(0.4);
        }
        Term.write("\033[0m");
    }

    /** Let the sound ring out so the next one does not step on it. */
    private static void waitForSound(Config config, Scene scene, double t0) {
        if (scene == null || scene.sfx() == null || !"sfx".equals(config.sound) || !Sound.available()) return;
        double ends = (scene.pre() + scene.impactAt()) * config.duration + Sound.tail(scene.sfx(), config.duration);
        double deadline = t0 + ends;
        while (System.nanoTime() / 1e9 < deadline && !Term.inputPending()) sleep(0.02);
    }

    private static void sleep(double s) {
        try {
            Thread.sleep((long) (s * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
