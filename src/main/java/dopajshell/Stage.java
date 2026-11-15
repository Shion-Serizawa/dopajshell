package dopajshell;

import java.util.ArrayList;
import java.util.List;

/**
 * A temporary block of terminal rows starting at the cursor. {@code top} rows already on
 * screen (the submitted input) can be taken over and are restored verbatim on close.
 */
public class Stage implements Term.Overlay {
    protected final int height;
    protected final List<String> top;
    private final Thread owner = Thread.currentThread();
    protected boolean open, closed;

    public Stage(int height, List<String> top) {
        this.height = height;
        this.top = top == null ? List.of() : top;
    }

    @Override
    public Thread owner() { return owner; }

    public int total() { return top.size() + height; }

    public boolean isOpen() {
        synchronized (Term.LOCK) { return open && !closed; }
    }

    public Stage open() {
        synchronized (Term.LOCK) {
            StringBuilder buf = new StringBuilder("\033[?25l\r");
            if (height > 1) buf.append("\n".repeat(height - 1));
            int up = height - 1 + top.size();
            if (up > 0) buf.append("\033[").append(up).append('A');
            buf.append('\r');
            Term.writeLocked(buf.toString());
            open = true;
            Term.overlay = this;
        }
        return this;
    }

    public void draw(List<String> lines) {
        synchronized (Term.LOCK) {
            if (!open || closed) return;
            Term.writeLocked(frame(lines));
        }
    }

    public void close() {
        synchronized (Term.LOCK) {
            if (!open || closed) return;
            restore();
            if (Term.overlay == this) Term.overlay = null;
        }
    }

    /** Another thread is about to print: get off the screen immediately. */
    @Override
    public void yieldToOutput() {
        if (!open || closed) return;
        restore();
        if (Term.overlay == this) Term.overlay = null;
    }

    protected void restore() {
        List<String> lines = new ArrayList<>(top);
        for (int i = 0; i < height; i++) lines.add("");
        StringBuilder buf = new StringBuilder(frame(lines));
        if (!top.isEmpty()) buf.append("\033[").append(top.size()).append('B');
        buf.append("\r\033[0m\033[?25h");
        Term.writeLocked(buf.toString());
        closed = true;
    }

    private String frame(List<String> lines) {
        StringBuilder buf = new StringBuilder();
        int n = total();
        for (int i = 0; i < n; i++) {
            buf.append("\r\033[2K").append(i < lines.size() && lines.get(i) != null ? lines.get(i) : "");
            if (i < n - 1) buf.append("\033[0m\n");
        }
        buf.append("\033[0m");
        if (n > 1) buf.append("\033[").append(n - 1).append('A');
        buf.append('\r');
        return buf.toString();
    }

    /** Full-screen stage on the alternate screen: scrollback is left untouched. */
    public static final class Alt extends Stage {
        public Alt(int height) {
            super(height, List.of());
        }

        @Override
        public Stage open() {
            synchronized (Term.LOCK) {
                Term.writeLocked("\033[?1049h\033[?25l\033[H\033[2J");
                open = true;
                Term.overlay = this;
            }
            return this;
        }

        @Override
        public void draw(List<String> lines) {
            synchronized (Term.LOCK) {
                if (!open || closed) return;
                StringBuilder buf = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) {
                    buf.append("\033[").append(i + 1).append(";1H\033[2K").append(lines.get(i)).append("\033[0m");
                }
                Term.writeLocked(buf.toString());
            }
        }

        @Override
        protected void restore() {
            if (!open || closed) return;
            Term.writeLocked("\033[0m\033[?1049l\033[?25h");
            closed = true;
        }
    }
}
