package dopajshell;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * The streams JShell writes the user's program output to. Before any program output
 * reaches the terminal, transient decorations get out of the way; during an eval it also
 * counts output lines for OUTPUT RAIN.
 */
public final class OutputTap {
    private static volatile boolean counting;
    private static volatile int lines;
    private static volatile long bytes;

    private OutputTap() {}

    public static void start() {
        lines = 0;
        bytes = 0;
        counting = true;
    }

    /** Stop counting; returns the number of lines printed. */
    public static int stop() {
        counting = false;
        return lines;
    }

    public static boolean output() { return bytes > 0; }

    /** A PrintStream for JShell.builder().out()/.err(), writing to the real terminal. */
    public static PrintStream stream(boolean err) {
        return new PrintStream(new Tap(err), true, StandardCharsets.UTF_8);
    }

    private static final class Tap extends OutputStream {
        private final boolean err;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        Tap(boolean err) { this.err = err; }

        @Override
        public void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (len <= 0) return;
            synchronized (Term.LOCK) {
                Term.yieldToOutputLocked();
                if (counting) {
                    bytes += len;
                    for (int i = off; i < off + len; i++) if (b[i] == '\n') lines++;
                }
                pending.write(b, off, len);
                emit(false);
            }
        }

        @Override
        public void flush() {
            synchronized (Term.LOCK) {
                emit(true);
            }
        }

        /** Decode complete UTF-8 sequences and send them on. */
        private void emit(boolean all) {
            byte[] data = pending.toByteArray();
            int cut = data.length;
            if (!all) {
                // keep an incomplete trailing UTF-8 sequence for the next write
                int i = data.length - 1;
                int back = 0;
                while (i >= 0 && back < 4 && (data[i] & 0xC0) == 0x80) {
                    i--;
                    back++;
                }
                if (i >= 0 && (data[i] & 0x80) != 0) {
                    int need = (data[i] & 0xE0) == 0xC0 ? 2 : (data[i] & 0xF0) == 0xE0 ? 3 : (data[i] & 0xF8) == 0xF0 ? 4 : 1;
                    if (data.length - i < need) cut = i;
                }
            }
            if (cut == 0) return;
            String s = new String(data, 0, cut, StandardCharsets.UTF_8);
            pending.reset();
            pending.write(data, cut, data.length - cut);
            if (Term.terminal() != null) {
                Term.writeLocked(s);
            } else {
                (err ? System.err : System.out).print(s);
                (err ? System.err : System.out).flush();
            }
        }
    }
}
