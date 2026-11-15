package dopajshell;

/**
 * Shown on the line below the input while an evaluation keeps running: an energetic
 * charge at first, then a calm elapsed-time readout.
 */
public final class ChargeLine implements Term.Overlay {
    static final double DELAY = 0.25;
    static final double CALM_AFTER = 3.0;

    private final Config config;
    private final Object signal = new Object();
    private volatile boolean stopped;
    private boolean drawn, dead;
    private Thread thread;
    private double t0;

    public ChargeLine(Config config) { this.config = config; }

    @Override
    public Thread owner() { return thread; }

    public ChargeLine start() {
        t0 = now();
        thread = new Thread(this::run, "dopa-charge");
        thread.setDaemon(true);
        thread.start();
        return this;
    }

    public void stop() {
        stopped = true;
        synchronized (signal) { signal.notifyAll(); }
        try {
            if (thread != null) thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (Term.LOCK) {
            clear();
            dead = true;
            if (Term.overlay == this) Term.overlay = null;
        }
    }

    /** User output is coming (called under Term.LOCK). */
    @Override
    public void yieldToOutput() {
        clear();
        dead = true;
        if (Term.overlay == this) Term.overlay = null;
    }

    private boolean await(double seconds) {
        synchronized (signal) {
            if (stopped) return true;
            try {
                signal.wait(Math.max(1, (long) (seconds * 1000)));
            } catch (InterruptedException e) {
                return true;
            }
            return stopped;
        }
    }

    private void run() {
        try {
            if (await(DELAY)) return;
            synchronized (Term.LOCK) {
                if (dead || OutputTap.output()) return;
                Term.overlay = this;
            }
            Color.Depth depth = Term.depth(config);
            String[] spin = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};
            for (int k = 0; k < spin.length; k++) spin[k] = Term.glyph(spin[k], "|");
            if ("|".equals(spin[0]) && "|".equals(spin[1])) spin = new String[] {"|", "/", "-", "\\"};
            int i = 0;
            while (true) {
                double el = now() - t0;
                synchronized (Term.LOCK) {
                    if (dead) return;
                    Term.writeLocked("\r\033[2K" + line(el, i, spin, depth) + "\033[0m\r");
                    drawn = true;
                }
                i++;
                if (await(el < CALM_AFTER ? 1 / 15.0 : 0.5)) break;
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
    }

    private String line(double el, int i, String[] spin, Color.Depth depth) {
        int cols = Term.cols() - 2;
        if (el < CALM_AFTER || !config.motion) {
            double k = (el - DELAY) / (CALM_AFTER - DELAY);
            int cells = Math.max(Math.min(cols - 30, 24), 4);
            int filled = (int) Math.round(cells * Fx.clamp01(k));
            StringBuilder bar = new StringBuilder();
            for (int x = 0; x < cells; x++) {
                boolean on = x < filled;
                int[] col = on ? Color.ramp(Color.FIRE, 0.5 - 0.4 * Math.floorMod(x - i, 6) / 6.0) : Color.rgb(60, 60, 70);
                bar.append(Color.sgr(col, null, on, depth)).append(Term.glyph(on ? "⣿" : "⣀", on ? "#" : "."));
            }
            String head = Color.sgr(Color.rainbow(el), null, true, depth);
            return head + spin[i % spin.length] + " CHARGING\033[0m " + bar + "\033[0m "
                    + Color.sgr(Color.rgb(255, 220, 150), null, true, depth) + String.format("%.1fs", el);
        }
        return Color.sgr(Color.rgb(110, 130, 170), null, false, depth) + spin[i % spin.length] + " running " + clock(el)
                + "  (Ctrl-C to interrupt)";
    }

    private void clear() {
        if (!drawn) return;
        Term.writeLocked("\r\033[2K");
        drawn = false;
    }

    private static String clock(double el) {
        return el < 60 ? String.format("%.1fs", el) : String.format("%dm%02ds", (int) (el / 60), (int) (el % 60));
    }

    private static double now() { return System.nanoTime() / 1e9; }
}
