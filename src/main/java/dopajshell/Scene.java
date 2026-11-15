package dopajshell;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dopajshell.Event.Flag;

/**
 * One effect. Scenes are pure functions of time drawn on a Canvas; the Director opens a
 * Stage and samples them frame by frame.
 *
 * <p>Subclasses set their fields in the constructor and do the rest in {@link #setup()},
 * which the Director calls through {@link #init()} before anything else.
 */
public abstract class Scene {
    /** Everything a scene needs to know about where it is drawing. */
    public static final class Ctx {
        public int w;
        public int rows;
        public Color.Depth depth;
        public Config config;
        public Random rng;
        public InputShot input;
        public double pre;
        public boolean compact;

        public Ctx copy() {
            Ctx c = new Ctx();
            c.w = w;
            c.rows = rows;
            c.depth = depth;
            c.config = config;
            c.rng = rng;
            c.input = input;
            c.pre = pre;
            c.compact = compact;
            return c;
        }
    }

    protected final Ctx ctx;
    protected final Event event;
    protected final Random rng;
    protected final int w;
    private boolean ready;

    protected Scene(Ctx ctx, Event event) {
        this.ctx = ctx;
        this.event = event;
        this.rng = ctx.rng;
        this.w = ctx.w;
    }

    public final Scene init() {
        if (!ready) {
            ready = true;
            setup();
        }
        return this;
    }

    public Ctx ctx() { return ctx; }
    public Event event() { return event; }

    protected void setup() {}

    public int height() { return 1; }
    public double length() { return 0.4; }
    public boolean top() { return false; }
    public boolean big() { return false; }
    /** Played on the alternate screen (full screen, scrollback untouched). */
    public boolean alt() { return false; }
    public boolean mega() { return false; }
    public boolean bell() { return mega(); }
    public String trail() { return null; }
    public int shake(double t) { return 0; }
    /** Scene time of the flash, or NaN for none. */
    public double flashAt() { return Double.NaN; }
    /** Sound patch name, or null. */
    public String sfx() { return null; }
    /** When (scene time) the sound's impact should be heard. */
    public double impactAt() { return Double.isNaN(flashAt()) ? 0.0 : flashAt(); }

    /** Wind-up before the main animation (input row glow); 0 when not wanted. */
    public double pre() { return top() ? ctx.pre : 0.0; }
    public double totalLength() { return pre() + length(); }
    /** Scenes that wait on something can end early. */
    public boolean done(double t) { return false; }

    /** Draw the top rows (the input); false leaves them untouched. */
    public boolean drawTop(Canvas c, double t) {
        if (t >= pre()) return false;
        glowInput(c, t / pre());
        return true;
    }

    public void draw(Canvas c, double t) {}

    public int w() { return w; }
    public Color.Depth depth() { return ctx.depth; }

    protected String paint(String text, int[] col) { return paint(text, col, false); }

    protected String paint(String text, int[] col, boolean bold) {
        if (depth() == Color.Depth.NONE && !bold) return text;
        return Color.sgr(col, null, bold, depth()) + text + "\033[0m";
    }

    /** Rainbow letters (for trails). */
    protected String rainbowText(String s, double step) {
        if (depth() == Color.Depth.NONE) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            sb.append(Color.sgr(Color.rainbow(i / step), null, true, depth())).appendCodePoint(cp);
            k += Character.charCount(cp);
            i++;
        }
        return sb.append("\033[0m").toString();
    }

    protected List<String> subBits(String... extra) {
        List<String> bits = new ArrayList<>(List.of(extra));
        if (event == null) return bits;
        if (event.gain > 0) bits.add("+" + Fx.commas(event.gain));
        if (event.flag(Flag.JACKPOT)) bits.add("JACKPOT x" + Game.JACKPOT_MULT);
        if (event.flag(Flag.CRITICAL)) bits.add("CRITICAL x" + event.mult / (event.flag(Flag.FEVER) ? 2 : 1));
        if (event.flag(Flag.FEVER) && !event.flag(Flag.FEVER_START)) bits.add("FEVER x2");
        if (event.flag(Flag.LEVEL_UP)) bits.add("LEVEL UP LV " + event.level);
        if (event.combo > 1) bits.add("COMBO " + event.combo);
        if (event.flag(Flag.NEW_RECORD) && !bits.contains("NEW RECORD")) bits.add("NEW RECORD");
        if (event.flag(Flag.FIRST_HIT)) bits.add("FIRST HIT");
        if (event.flag(Flag.COMEBACK)) bits.add("RECOVERED after " + event.streak + " error" + (event.streak > 1 ? "s" : ""));
        return bits;
    }

    protected String subLine(String... extra) { return String.join("   ", subBits(extra)); }

    protected void plainInput(Canvas c, int[] color) {
        List<String> plain = ctx.input.plain;
        for (int y = 0; y < plain.size(); y++) c.put(0, y, plain.get(y), color);
    }

    /** A hot band sweeping across the submitted code: "EXECUTE". */
    protected void glowInput(Canvas c, double p) {
        double band = p * (w + 16) - 8;
        List<String> plain = ctx.input.plain;
        for (int y = 0; y < plain.size(); y++) {
            String r = plain.get(y);
            int x = 0;
            for (int k = 0; k < r.length(); ) {
                int cp = r.codePointAt(k);
                String ch = new String(Character.toChars(cp));
                double d = Math.abs(x - band - y * 2);
                int[] col = d < 6 ? Color.mix(Color.WHITE, Color.rgb(255, 170, 40), d / 6.0) : Color.rgb(200, 200, 215);
                int[] bg = d < 3 ? Color.mix(Color.rgb(255, 120, 0), Color.rgb(60, 20, 0), d / 3.0) : null;
                c.put(x, y, ch, col, bg, d < 3);
                x += cp < 0x80 ? 1 : Term.charWidth(cp);
                k += Character.charCount(cp);
            }
        }
    }

    protected int[][] comboPalette() { return comboPalette(event == null ? 0 : event.combo); }

    protected static int[][] comboPalette(int combo) {
        if (combo <= 2) return Color.ICE;
        if (combo <= 5) return Color.TOXIC;
        if (combo <= 9) return Color.GOLD;
        if (combo <= 19) return Color.FIRE;
        return Color.NEON;
    }

    protected int[] comboColor(double t) {
        int combo = event == null ? 0 : event.combo;
        if (combo >= 20) return Color.rainbow(t * 1.5);
        return Color.ramp(comboPalette(combo), 0.25);
    }

    protected double ease(double t) { return Fx.easeOut(t); }

    /** 0 when compact or the big font does not fit. */
    protected int bestScale(String text) { return ctx.compact ? 0 : Fx.bestScale(text, w); }

    protected boolean flashOn() { return !"off".equals(ctx.config.flash); }
}
