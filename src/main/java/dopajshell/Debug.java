package dopajshell;

import java.io.FileWriter;
import java.io.PrintWriter;

/** Effects never break the REPL; set DOPAJSHELL_DEBUG=path to see what they swallowed. */
public final class Debug {
    private Debug() {}

    public static void log(Throwable e) {
        String path = System.getenv("DOPAJSHELL_DEBUG");
        if (path == null || path.isEmpty()) return;
        synchronized (Debug.class) {
            try (PrintWriter w = new PrintWriter(new FileWriter(path, true))) {
                e.printStackTrace(w);
            } catch (Exception ignored) {
                // nowhere to report
            }
        }
    }

    public static void log(String msg) {
        String path = System.getenv("DOPAJSHELL_DEBUG");
        if (path == null || path.isEmpty()) return;
        synchronized (Debug.class) {
            try (PrintWriter w = new PrintWriter(new FileWriter(path, true))) {
                w.println(msg);
            } catch (Exception ignored) {
                // nowhere to report
            }
        }
    }
}
