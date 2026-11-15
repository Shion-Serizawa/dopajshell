package dopajshell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jdk.jshell.JShell;
import jdk.jshell.MethodSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.Completeness;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EOFError;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

/** The read-eval-print loop: JShell evaluates, JLine reads, Session puts on the show. */
public final class Repl {
    static final String PROMPT_TEXT = "dopajshell> ";
    static final String SECONDARY = "       ...> ";
    static final List<String> DEFAULT_IMPORTS = List.of(
            "import java.io.*;", "import java.math.*;", "import java.net.*;", "import java.nio.file.*;",
            "import java.util.*;", "import java.util.concurrent.*;", "import java.util.function.*;",
            "import java.util.prefs.*;", "import java.util.regex.*;", "import java.util.stream.*;");
    /** Keep Ctrl-C from killing the user's VM: we stop the snippet through JShell instead. */
    static final String IGNORE_INT = "try { sun.misc.Signal.handle(new sun.misc.Signal(\"INT\"), sig -> { }); } catch (Throwable t) { }";

    static final Map<String, String> COMMANDS = new java.util.LinkedHashMap<>();
    static {
        COMMANDS.put("/list", "list the source you have typed (/list -all for startup snippets too)");
        COMMANDS.put("/vars", "list declared variables and their values");
        COMMANDS.put("/methods", "list declared methods");
        COMMANDS.put("/types", "list declared classes, interfaces, enums and records");
        COMMANDS.put("/imports", "list the imports");
        COMMANDS.put("/drop", "delete a snippet by name or id");
        COMMANDS.put("/open", "evaluate a file as input");
        COMMANDS.put("/save", "save what you typed to a file");
        COMMANDS.put("/history", "show the input history");
        COMMANDS.put("/reset", "reset the session state (keeps your score)");
        COMMANDS.put("/env", "/env -class-path <path>: add to the class path");
        COMMANDS.put("/dopa", "effect settings and session stats (/dopa help)");
        COMMANDS.put("/help", "this list");
        COMMANDS.put("/exit", "leave (with a result screen)");
        COMMANDS.put("/!", "re-run the last snippet");
    }

    private final Config config;
    private final Session session;
    private final Terminal terminal;
    private final boolean interactive;
    private final List<String> classPath;
    private DopaLineReader reader;
    private DopaHighlighter highlighter;
    private volatile CompletableFuture<JShell> booting;
    private volatile JShell js;
    private volatile boolean evaluating, interrupted;
    private boolean startup;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicInteger nextStartupId = new AtomicInteger(1);
    private String lastInput;
    private String resumeIntensity;
    private int exitCode = 0;

    public Repl(Config config, Terminal terminal, boolean interactive, List<String> classPath) {
        this.config = config;
        this.terminal = terminal;
        this.interactive = interactive;
        this.classPath = classPath;
        this.session = new Session(config);
    }

    // ---- JShell ----------------------------------------------------------------

    private void boot() {
        booting = CompletableFuture.supplyAsync(() -> {
            PrintStream out = OutputTap.stream(false);
            PrintStream err = OutputTap.stream(true);
            JShell shell = JShell.builder()
                    .out(out).err(err).in(System.in)
                    .idGenerator((sn, i) -> startup ? "s" + nextStartupId.getAndIncrement() : String.valueOf(nextId.getAndIncrement()))
                    .build();
            startup = true;
            try {
                for (String cp : classPath) shell.addToClasspath(cp);
                for (String imp : DEFAULT_IMPORTS) shell.eval(imp);
                shell.eval(IGNORE_INT);
            } finally {
                startup = false;
            }
            shell.onShutdown(dead -> {
                if (dead == js) js = null;
            });
            return shell;
        });
    }

    private JShell js() {
        JShell s = js;
        if (s != null) return s;
        if (booting == null) boot();
        try {
            s = booting.join();
        } catch (RuntimeException e) {
            booting = null;
            throw new IllegalStateException("could not start JShell: " + e.getMessage(), e);
        }
        js = s;
        return s;
    }

    private void restart(String why) {
        say("|  " + why);
        JShell old = js;
        js = null;
        booting = null;
        nextStartupId.set(1);
        if (old != null) {
            try {
                old.close();
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        boot();
    }

    private SourceCodeAnalysis sca() { return js().sourceCodeAnalysis(); }

    // ---- output ----------------------------------------------------------------

    void say(String line) {
        if (interactive) Term.write(line.replace("\n", "\r\n") + "\r\n");
        else {
            System.out.println(line);
            System.out.flush();
        }
    }

    // ---- main loop -------------------------------------------------------------

    public int run() {
        boot();
        if (!interactive) return runPlain();
        InputFx fx = session.fx;
        highlighter = new DopaHighlighter(fx, session::active, () -> Term.depth(config) != Color.Depth.NONE);
        reader = new DopaLineReader(terminal, fx, session::active, highlighter);
        reader.setParser(new JParser());
        reader.setCompleter(new JCompleter());
        reader.setSecondaryPrompt(SECONDARY);
        reader.setOpt(LineReader.Option.DISABLE_EVENT_EXPANSION);
        reader.setVariable(LineReader.INDENTATION, 4);
        reader.setVariable(LineReader.HISTORY_FILE, Path.of(System.getProperty("user.home"), ".dopajshell_history"));
        reader.setVariable(LineReader.HISTORY_SIZE, 1000);
        // JLine blinks the cursor on the opener for 500ms after ')', ']', '}' and blocks the key
        // until then; InputFx already highlights the pair, so close brackets should be instant.
        reader.setVariable(LineReader.BLINK_MATCHING_PAREN, 0L);
        terminal.handle(Terminal.Signal.INT, sig -> {
            if (evaluating) {
                interrupted = true;
                JShell s = js;
                if (s != null) {
                    try {
                        s.stop();
                    } catch (RuntimeException ignored) {
                        // nothing running
                    }
                }
            }
        });

        session.intro();
        if (!booting.isDone()) say(Color.fg(Color.rgb(150, 150, 165), Term.depth(config)) + "|  starting JShell..." + Color.RESET);
        try {
            js();
        } catch (IllegalStateException e) {
            say("|  " + e.getMessage());
            return 1;
        }
        say(Color.fg(Color.rgb(150, 150, 165), Term.depth(config)) + "|  Welcome to dopajshell -- JShell with fireworks. /help for commands, /dopa for effects."
                + Color.RESET);

        while (true) {
            String line;
            try {
                reader.beforeRead();
                line = reader.readLine(prompt());
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                break;
            }
            if (line == null) break;
            if (line.isBlank()) continue;
            try {
                if (!handle(line)) break;
            } catch (RuntimeException e) {
                Debug.log(e);
                say("|  " + e);
            }
        }
        session.finish();
        shutdown();
        return exitCode;
    }

    private String prompt() {
        if (Term.depth(config) == Color.Depth.NONE) return PROMPT_TEXT;
        return "\033[1;38;2;255;110;230mdopa\033[0;1mjshell\033[0m> ";
    }

    private void shutdown() {
        JShell s = js;
        if (s != null) {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // closing anyway
            }
        }
        Sound.shutdown();
    }

    /** Handle one complete input. False to quit. */
    boolean handle(String input) {
        String t = input.strip();
        if (t.startsWith("/") && !t.startsWith("//") && !t.startsWith("/*")) return command(t);
        lastInput = input;
        evaluate(input);
        return true;
    }

    /** Non-interactive: read stdin, evaluate, print jshell's feedback. No control sequences. */
    private int runPlain() {
        try {
            js();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder pending = new StringBuilder();
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (pending.length() == 0 && line.strip().startsWith("/") && !line.strip().startsWith("//")) {
                    if (!command(line.strip())) break;
                    continue;
                }
                if (pending.length() > 0) pending.append('\n');
                pending.append(line);
                if (incomplete(pending.toString(), line.isEmpty())) continue;
                String code = pending.toString();
                pending.setLength(0);
                if (!code.isBlank()) evaluate(code);
            }
            if (pending.length() > 0 && !pending.toString().isBlank()) evaluate(pending.toString());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        shutdown();
        return exitCode;
    }

    // ---- evaluation --------------------------------------------------------------

    /** Is the input missing something (an open brace, a dangling operator...)? */
    boolean incomplete(String text, boolean blankLast) {
        String rest = text;
        while (true) {
            if (rest.isBlank()) return false;
            SourceCodeAnalysis.CompletionInfo ci = sca().analyzeCompletion(rest);
            Completeness c = ci.completeness();
            if (c == Completeness.EMPTY || c == Completeness.UNKNOWN) return false;
            if (c == Completeness.DEFINITELY_INCOMPLETE) return true;
            if (c == Completeness.CONSIDERED_INCOMPLETE) return !blankLast;
            rest = ci.remaining();
        }
    }

    void evaluate(String input) {
        InputShot shot = reader == null ? null : reader.takeInput(input);
        String remaining = input;
        int consumed = 0;
        boolean first = true;
        while (!remaining.isBlank()) {
            SourceCodeAnalysis.CompletionInfo ci;
            try {
                ci = sca().analyzeCompletion(remaining);
            } catch (IllegalStateException e) {
                restart("State engine terminated. Starting a fresh one.");
                return;
            }
            Completeness c = ci.completeness();
            if (c == Completeness.EMPTY) break;
            String src, rest;
            if (c.isComplete()) {
                src = ci.source();
                rest = ci.remaining();
            } else {
                src = remaining;
                rest = "";
            }
            int srcOffset = consumed + (remaining.length() - remaining.stripLeading().length());
            consumed += remaining.length() - rest.length();
            remaining = rest;
            if (!evalSnippet(src, input, srcOffset, first ? shot : null)) return;
            first = false;
        }
    }

    /** Evaluate one snippet with the show around it. False when the engine died. */
    private boolean evalSnippet(String src, String input, int srcOffset, InputShot shot) {
        boolean show = session.active();
        if (show) OutputTap.start();
        ChargeLine charge = show && config.motion ? new ChargeLine(config).start() : null;
        interrupted = false;
        evaluating = true;
        double t0 = System.nanoTime() / 1e9;
        List<SnippetEvent> events;
        JShell shell = js();
        try {
            events = shell.eval(src);
        } catch (IllegalStateException e) {
            evaluating = false;
            if (charge != null) charge.stop();
            OutputTap.stop();
            restart("State engine terminated. Starting a fresh one.");
            return false;
        } finally {
            evaluating = false;
            if (charge != null) charge.stop();
        }
        int lines = show ? OutputTap.stop() : 0;
        double elapsed = System.nanoTime() / 1e9 - t0;
        boolean wasInterrupted = interrupted;
        interrupted = false;

        SnippetEvent main = null;
        for (SnippetEvent ev : events) {
            if (ev.causeSnippet() == null) {
                main = ev;
                break;
            }
        }
        if (show && main != null) {
            InputShot s = OutputTap.output() ? null : shot;
            if (wasInterrupted) {
                session.react(null, false, true, s, elapsed);
            } else if (main.status() == Snippet.Status.REJECTED) {
                session.react(Probe.compileError(shell, main, input, srcOffset), false, false, s, elapsed);
            } else if (main.exception() != null) {
                session.react(Probe.exception(main.exception()), false, false, s, elapsed);
            } else {
                Info info = Probe.value(main);
                info.outLines = lines;
                session.react(info, true, false, s, elapsed);
            }
        }
        for (SnippetEvent ev : events) {
            if (wasInterrupted && ev == main && ev.exception() == null && ev.status() != Snippet.Status.REJECTED) continue;
            for (String l : Feedback.lines(shell, ev, ev.snippet().source())) say(l);
        }
        if (wasInterrupted) say("|  Interrupted.");
        if (shell != js && js == null) {
            restart("State engine terminated. Starting a fresh one.");
            return false;
        }
        return true;
    }

    // ---- commands ------------------------------------------------------------------

    private List<Snippet> userSnippets(boolean all) {
        return js().snippets().filter(s -> all || !s.id().startsWith("s"))
                .filter(s -> js().status(s).isActive() || all).collect(Collectors.toList());
    }

    boolean command(String line) {
        String[] parts = line.split("\\s+", 2);
        String typed = parts[0];
        String arg = parts.length > 1 ? parts[1].strip() : "";
        List<String> matches = COMMANDS.keySet().stream().filter(k -> k.startsWith(typed)).toList();
        String cmd = !COMMANDS.containsKey(typed) && matches.size() == 1 ? matches.get(0) : typed;
        switch (cmd) {
            case "/exit", "/quit" -> {
                if (!arg.isEmpty()) {
                    try {
                        exitCode = Integer.parseInt(arg);
                    } catch (NumberFormatException e) {
                        say("|  /exit expects an integer exit code");
                        return true;
                    }
                }
                return false;
            }
            case "/help", "/?" -> {
                say("|  Type a Java expression, statement, or declaration.");
                COMMANDS.forEach((k, v) -> say(String.format("|  %-10s %s", k, v)));
            }
            case "/list" -> {
                for (Snippet s : userSnippets(arg.equals("-all"))) {
                    String[] src = s.source().split("\n", -1);
                    for (int i = 0; i < src.length; i++) {
                        say(String.format("%5s : %s", i == 0 ? s.id() : "", src[i]));
                    }
                }
            }
            case "/vars" -> js().variables().filter(v -> js().status(v).isActive()).forEach(v -> say(
                    "|    " + v.typeName() + " " + v.name() + " = " + varValue(v)));
            case "/methods" -> js().methods().filter(m -> js().status(m).isActive()).forEach(m -> say(
                    "|    " + returnType(m) + " " + m.name() + "(" + m.parameterTypes() + ")"));
            case "/types" -> js().types().filter(t -> js().status(t).isActive()).forEach(t -> say(
                    "|    " + Feedback.describe(t)));
            case "/imports" -> js().imports().filter(i -> js().status(i).isActive()).forEach(i -> say(
                    "|    import " + (i.isStatic() ? "static " : "") + i.fullname()));
            case "/drop" -> drop(arg);
            case "/reset" -> {
                restart("Resetting state.");
                js();
            }
            case "/history" -> {
                if (reader != null) reader.getHistory().forEach(e -> say(e.line()));
            }
            case "/open" -> open(arg);
            case "/save" -> save(arg);
            case "/env" -> {
                String[] a = arg.split("\\s+", 2);
                if (a.length == 2 && (a[0].equals("-class-path") || a[0].equals("--class-path") || a[0].equals("-cp"))) {
                    for (String p : a[1].split(java.io.File.pathSeparator)) js().addToClasspath(p);
                    say("|  class path updated");
                } else {
                    say("|  usage: /env -class-path <path>");
                }
            }
            case "/!" -> {
                if (lastInput == null) say("|  nothing to re-run");
                else {
                    say(lastInput);
                    evaluate(lastInput);
                }
            }
            case "/dopa" -> dopa(arg);
            default -> say("|  Unknown command: " + cmd + " -- /help for the list");
        }
        return true;
    }

    private String varValue(VarSnippet v) {
        try {
            return js().varValue(v);
        } catch (RuntimeException e) {
            return "(not initialized)";
        }
    }

    private static String returnType(MethodSnippet m) {
        String sig = m.signature();
        int close = sig.lastIndexOf(')');
        return close >= 0 ? sig.substring(close + 1) : "";
    }

    private void drop(String arg) {
        if (arg.isEmpty()) {
            say("|  usage: /drop <name or id>");
            return;
        }
        List<Snippet> hits = js().snippets().filter(s -> s.id().equals(arg)
                || (s instanceof jdk.jshell.PersistentSnippet p && p.name().equals(arg))).toList();
        if (hits.isEmpty()) {
            say("|  No such snippet: " + arg);
            return;
        }
        for (Snippet s : hits) {
            for (SnippetEvent ev : js().drop(s)) {
                if (ev.snippet() == s) {
                    say("|  dropped " + (s instanceof TypeDeclSnippet || s instanceof MethodSnippet || s instanceof VarSnippet
                            ? Feedback.describe((jdk.jshell.DeclarationSnippet) s) : s.id()));
                }
            }
        }
    }

    private void open(String arg) {
        if (arg.isEmpty()) {
            say("|  usage: /open <file>");
            return;
        }
        try {
            String text = Files.readString(Path.of(arg), StandardCharsets.UTF_8);
            evaluate(text);
        } catch (IOException e) {
            say("|  File '" + arg + "' could not be read: " + e.getMessage());
        }
    }

    private void save(String arg) {
        if (arg.isEmpty()) {
            say("|  usage: /save <file>");
            return;
        }
        String text = userSnippets(false).stream().map(Snippet::source).collect(Collectors.joining("\n")) + "\n";
        try {
            Files.writeString(Path.of(arg), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            say("|  File '" + arg + "' could not be written: " + e.getMessage());
        }
    }

    // ---- /dopa ---------------------------------------------------------------------

    static final Map<String, Map<String, Object>> PRESETS = Map.of(
            "off", Map.of("intensity", "off"),
            "low", Map.of("intensity", "low"),
            "normal", Map.of("intensity", "normal"),
            "max", Map.of("intensity", "max"),
            "calm", Map.of("intensity", "low", "flash", "off", "duration", 0.7),
            "party", Map.of("intensity", "max", "flash", "full", "sound", "sfx", "duration", 1.0));

    static final String HELP = """
            /dopa                  show settings and session stats
            /dopa on / /dopa off   switch the show on (back to where it was) or off
            /dopa off|low|normal|max
            /dopa calm             low intensity, no flash, shorter
            /dopa party            max intensity, full-screen flash, sound effects
            /dopa demo             play every effect once
            /dopa KEY=VALUE ...    change one setting, e.g. `/dopa flash=off duration=0.5`

            settings:""";

    void dopa(String arg) {
        String[] words = arg.isBlank() ? new String[0] : arg.split("\\s+");
        try {
            if (words.length == 0 || (words.length == 1 && words[0].equals("status"))) {
                String state = session.active() ? "active" : "inactive: not a terminal or intensity=off";
                say("dopajshell " + Version.VERSION + "  (" + state + ")");
                say(config.describe());
                say("  sound: " + (Sound.available() ? "javax.sound" : "(no audio device; bell only)"));
                Game g = session.game;
                say(String.format("  session: %d evals, %d hits, %d errors, max combo %d, score %s",
                        g.evals(), g.successes(), g.failures(), g.maxCombo(), Fx.commas(g.score())));
                Profile prof = session.profile();
                if (prof.persistent()) {
                    int lv = g.leveling() ? g.level() : prof.level();
                    long xp = g.leveling() ? g.xp() : prof.xp;
                    say("  career: LV " + lv + " (" + Fx.commas(xp) + " / " + Fx.commas(Game.xpFor(lv + 1)) + " XP), best score "
                            + Fx.commas(prof.bestScore) + ", best combo " + prof.bestCombo + ", day streak " + prof.streak
                            + "   (" + prof.path() + ")");
                }
            } else if (words.length == 1 && words[0].equals("help")) {
                say(HELP + "\n" + config.describe());
            } else if (words.length == 1 && words[0].equals("demo")) {
                Demo.run(session, this::say);
            } else if (words.length == 1 && words[0].equals("on")) {
                if (config.off()) config.set("intensity", resumeIntensity != null ? resumeIntensity : new Config().intensity);
                say("dopajshell: on (" + config.intensity + ")");
                if ("sfx".equals(config.sound)) Sound.warmUp(config.duration);
            } else if (words.length == 1 && PRESETS.containsKey(words[0])) {
                if (words[0].equals("off") && !config.off()) resumeIntensity = config.intensity;
                PRESETS.get(words[0]).forEach(config::set);
                if ("sfx".equals(config.sound) && session.active()) Sound.warmUp(config.duration);
                say("dopajshell: " + words[0]);
            } else {
                for (String w : words) {
                    int eq = w.indexOf('=');
                    if (eq < 0) throw new IllegalArgumentException("expected KEY=VALUE, got \"" + w + "\"");
                    config.set(w.substring(0, eq), w.substring(eq + 1));
                }
                if ("sfx".equals(config.sound) && session.active()) Sound.warmUp(config.duration);
                say("dopajshell: " + String.join(" ", words));
            }
        } catch (IllegalArgumentException e) {
            say("dopajshell: " + e.getMessage());
        }
    }

    // ---- line editor glue -------------------------------------------------------------

    /** Where the snippet containing the cursor starts. */
    private int snippetStart(String text, int cursor) {
        int off = 0;
        while (off < cursor) {
            String rest = text.substring(off);
            if (rest.isBlank()) break;
            SourceCodeAnalysis.CompletionInfo ci = sca().analyzeCompletion(rest);
            if (!ci.completeness().isComplete()) break;
            int used = rest.length() - ci.remaining().length();
            if (used <= 0 || off + used >= cursor) break;
            off += used;
        }
        return off;
    }

    private record Completion(String line, int cursor, int anchor, List<SourceCodeAnalysis.Suggestion> suggestions) {}

    private volatile Completion lastCompletion;

    private Completion complete(String line, int cursor) {
        Completion c = lastCompletion;
        if (c != null && c.line.equals(line) && c.cursor == cursor) return c;
        int[] anchor = new int[1];
        List<SourceCodeAnalysis.Suggestion> sugg;
        if (line.stripLeading().startsWith("/")) {
            sugg = List.of();
            anchor[0] = 0;
        } else {
            int start = snippetStart(line, cursor);
            sugg = sca().completionSuggestions(line.substring(start), cursor - start, anchor);
            anchor[0] += start;
        }
        c = new Completion(line, cursor, anchor[0], sugg);
        lastCompletion = c;
        return c;
    }

    /** Multi-line input until the snippet is complete; words for completion come from JShell. */
    private final class JParser implements Parser {
        @Override
        public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
            if (context == ParseContext.ACCEPT_LINE) {
                String t = line.strip();
                boolean cmd = t.startsWith("/") && !t.startsWith("//") && !t.startsWith("/*");
                if (!cmd && !t.isEmpty() && incomplete(line, line.endsWith("\n") || line.endsWith("\n" + " ".repeat(4)))) {
                    int open = 0;
                    for (JavaLexer.Token tk : JavaLexer.lex(line)) {
                        if (tk.kind() == JavaLexer.Kind.OPEN && tk.text().equals("{")) open++;
                        if (tk.kind() == JavaLexer.Kind.CLOSE && tk.text().equals("}")) open = Math.max(0, open - 1);
                    }
                    throw new EOFError(-1, -1, "incomplete", null, open, null);
                }
                return new Line(line, cursor, 0);
            }
            if (context == ParseContext.COMPLETE) {
                int anchor = cursor;
                try {
                    String t = line.stripLeading();
                    if (t.startsWith("/")) {
                        int sp = line.lastIndexOf(' ', Math.max(cursor - 1, 0));
                        anchor = sp < 0 || sp >= cursor ? line.indexOf('/') : sp + 1;
                    } else {
                        anchor = complete(line, cursor).anchor;
                    }
                } catch (RuntimeException e) {
                    Debug.log(e);
                }
                return new Line(line, cursor, Math.max(0, Math.min(anchor, cursor)));
            }
            return new Line(line, cursor, cursor);
        }
    }

    private record Line(String line, int cursor, int anchor) implements ParsedLine {
        @Override public String word() { return line.substring(anchor, cursor); }
        @Override public int wordCursor() { return cursor - anchor; }
        @Override public int wordIndex() { return 0; }
        @Override public List<String> words() { return List.of(word()); }
    }

    private final class JCompleter implements Completer {
        @Override
        public void complete(LineReader r, ParsedLine pl, List<Candidate> out) {
            try {
                String line = pl.line();
                int cursor = pl.cursor();
                String t = line.stripLeading();
                if (t.startsWith("/")) {
                    String before = line.substring(0, cursor).stripLeading();
                    if (!before.contains(" ")) {
                        for (String k : COMMANDS.keySet()) out.add(new Candidate(k, k, null, COMMANDS.get(k), null, null, true));
                    } else if (before.startsWith("/dopa ")) {
                        for (String k : List.of("on", "off", "low", "normal", "max", "calm", "party", "demo", "help", "status")) {
                            out.add(new Candidate(k, k, null, null, null, null, true));
                        }
                        for (String k : Config.DESCRIPTIONS.keySet()) out.add(new Candidate(k + "=", k + "=", null, null, null, null, false));
                    }
                    return;
                }
                Completion c = Repl.this.complete(line, cursor);
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (SourceCodeAnalysis.Suggestion s : c.suggestions) {
                    String v = s.continuation();
                    if (!seen.add(v)) continue;
                    boolean done = !(v.endsWith("(") || v.endsWith(".") || v.endsWith("<"));
                    out.add(new Candidate(v, v, null, null, null, null, done));
                }
            } catch (RuntimeException e) {
                Debug.log(e);
            }
        }
    }

    public static void printPlainHelp(PrintStream out) {
        out.println("Usage: dopajshell [options]");
        out.println();
        out.println("JShell with fireworks: every keystroke, result and exception gets a show.");
        out.println();
        out.println("  --intensity=LEVEL      off / low / normal / max (default)");
        out.println("  --calm                 low intensity, no flash, shorter effects");
        out.println("  --max                  everything at maximum (the default)");
        out.println("  --party                max + full-screen flash + sound effects");
        out.println("  --[no-]flash[=MODE]    flash: off / soft (default) / full");
        out.println("  --[no-]motion          animations (default on)");
        out.println("  --[no-]sound[=MODE]    sound: off / bell / sfx (default)");
        out.println("  --[no-]hud             COMBO / SCORE next to the prompt (default on)");
        out.println("  --[no-]keys            per-keystroke effects (default on)");
        out.println("  --[no-]intro           title animation (default on)");
        out.println("  --duration=X           effect length multiplier");
        out.println("  --set=K=V,...          any setting, same format as $DOPAJSHELL");
        out.println("  --class-path PATH      add to the class path (also -cp)");
        out.println("  --version              print version");
        out.println("  -h, --help             this help");
        out.println();
        out.println("Settings (also via DOPAJSHELL=\"key=value,...\" or `/dopa key=value` inside the session):");
        out.println(new Config().describe());
    }

    List<String> classPathEntries() { return new ArrayList<>(classPath); }
}
