package dopajshell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** `dopajshell [options]` */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Config config = new Config();
        List<String> classPath = new ArrayList<>();
        try {
            String env = System.getenv("DOPAJSHELL");
            if (env != null) {
                try {
                    config.applyString(env);
                } catch (IllegalArgumentException e) {
                    System.err.println("dopajshell: ignoring DOPAJSHELL=\"" + env + "\": " + e.getMessage());
                }
            }
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                String val = null;
                int eq = a.indexOf('=');
                String key = a;
                if (a.startsWith("--") && eq > 0) {
                    key = a.substring(0, eq);
                    val = a.substring(eq + 1);
                }
                switch (key) {
                    case "-h", "--help" -> {
                        Repl.printPlainHelp(System.out);
                        return;
                    }
                    case "--version" -> {
                        System.out.println("dopajshell " + Version.VERSION + " (Java " + Runtime.version() + ")");
                        return;
                    }
                    case "--intensity" -> config.set("intensity", need(val, args, ++i, key));
                    case "--calm" -> config.set("intensity", "low").set("flash", "off").set("duration", 0.7);
                    case "--max" -> config.set("intensity", "max");
                    case "--party" -> config.set("intensity", "max").set("flash", "full").set("sound", "sfx");
                    case "--flash" -> config.set("flash", val == null ? "soft" : val);
                    case "--no-flash" -> config.set("flash", "off");
                    case "--motion" -> config.set("motion", "on");
                    case "--no-motion" -> config.set("motion", "off");
                    case "--sound" -> config.set("sound", val == null ? "sfx" : val);
                    case "--no-sound" -> config.set("sound", "off");
                    case "--hud" -> config.set("hud", "on");
                    case "--no-hud" -> config.set("hud", "off");
                    case "--keys" -> config.set("keys", "on");
                    case "--no-keys" -> config.set("keys", "off");
                    case "--intro" -> config.set("intro", "on");
                    case "--no-intro" -> config.set("intro", "off");
                    case "--duration" -> config.set("duration", need(val, args, ++i, key));
                    case "--set" -> config.applyString(need(val, args, ++i, key));
                    case "--class-path", "-cp", "-classpath", "--classpath" -> {
                        for (String p : need(val, args, ++i, key).split(java.io.File.pathSeparator)) {
                            if (!p.isEmpty()) classPath.add(p);
                        }
                    }
                    default -> throw new IllegalArgumentException("unknown option " + a + " (try --help)");
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("dopajshell: " + e.getMessage());
            System.exit(2);
        }
        System.exit(run(config, classPath));
    }

    private static String need(String val, String[] args, int i, String key) {
        if (val != null) return val;
        if (i >= args.length) throw new IllegalArgumentException(key + " needs a value");
        return args[i];
    }

    static int run(Config config, List<String> classPath) {
        Terminal terminal = null;
        boolean interactive = false;
        if (!"dumb".equals(System.getenv("TERM"))) {
            try {
                terminal = TerminalBuilder.builder()
                        .system(true)
                        .dumb(true)
                        .nativeSignals(true)
                        .signalHandler(Terminal.SignalHandler.SIG_IGN)
                        .build();
                String type = terminal.getType();
                interactive = !(Terminal.TYPE_DUMB.equals(type) || Terminal.TYPE_DUMB_COLOR.equals(type));
            } catch (IOException | RuntimeException e) {
                Debug.log(e);
            }
        }
        if (!interactive && terminal != null) {
            try {
                terminal.close();
            } catch (IOException ignored) {
                // gone anyway
            }
            terminal = null;
        }
        Term.init(terminal, interactive);
        int code;
        try {
            code = new Repl(config, terminal, interactive, classPath).run();
        } finally {
            if (terminal != null) {
                try {
                    terminal.writer().flush();
                    terminal.close();
                } catch (IOException | RuntimeException ignored) {
                    // leaving
                }
            }
        }
        return code;
    }
}
