package dopajshell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import dopajshell.Event.Flag;
import dopajshell.scenes.BonusScenes;
import dopajshell.scenes.FailureScenes;
import dopajshell.scenes.SessionScenes;
import dopajshell.scenes.SuccessScenes;

/**
 * Picks a scene for each event and plays it. Bigger moments win; smaller ones are folded
 * into the subtitle instead of being queued.
 */
public final class Director {
    static final double FPS = 40.0;

    private final Config config;
    private final Random rng;
    private double lastFlash = -10.0;

    public Director(Config config, Random rng) {
        this.config = config;
        this.rng = rng;
    }

    public Random rng() { return rng; }

    public Scene.Ctx ctx(InputShot input, double pre) {
        int[] rc = Term.size();
        Scene.Ctx c = new Scene.Ctx();
        c.w = rc[1] - 1;
        c.rows = rc[0];
        c.depth = Term.depth(config);
        c.config = config;
        c.rng = rng;
        c.input = input;
        c.pre = pre;
        c.compact = config.level() <= 1 || rc[1] < 40;
        return c;
    }

    public Scene sceneFor(Event event, Scene.Ctx ctx) {
        return switch (event.kind) {
            case INTERRUPT -> new FailureScenes.Scatter(ctx, event);
            case FAILURE -> failureScene(event, ctx);
            default -> successScene(event, ctx);
        };
    }

    private Scene failureScene(Event event, Scene.Ctx ctx) {
        return switch (event.info.type) {
            case "syntax" -> new FailureScenes.Crack(ctx, event);
            case "name" -> new FailureScenes.Spotlight(ctx, event);
            case "nomethod" -> new FailureScenes.Snap(ctx, event);
            case "type", "argument" -> new FailureScenes.Clash(ctx, event);
            default -> new FailureScenes.FailureScene(ctx, event);
        };
    }

    /** The scene for the result itself. Banners for what it earned come after it (see bannerFor). */
    private Scene successScene(Event event, Scene.Ctx ctx) {
        if (event.flag(Flag.JACKPOT)) return new BonusScenes.Jackpot(ctx, event);
        if ("definition".equals(event.info.type)) return new SuccessScenes.Unlock(ctx, event);
        if (event.flag(Flag.CRITICAL) && !counter(event)) return new BonusScenes.Critical(ctx, event);
        if (event.flag(Flag.OUTPUT_RAIN)) return new SuccessScenes.Rain(ctx, event);
        return valueScene(event, ctx);
    }

    /**
     * The one banner that follows a success, or null. Bigger moments win; smaller ones are
     * folded into its subtitle. It plays below the result (never full screen), and LEVEL UP!
     * stays short because a new profile levels up on almost every early evaluation.
     */
    Scene bannerFor(Event event, Scene.Ctx resultCtx) {
        if (event.kind != Event.Kind.SUCCESS && event.kind != Event.Kind.DEFINITION) return null;
        Scene.Ctx ctx = resultCtx.copy();
        ctx.input = null; // the result scene already showed the input
        ctx.pre = 0;
        Info info = event.info;
        if (event.flag(Flag.LEVEL_UP)) return banner(ctx, event, "LEVEL UP!", "LV " + event.level, Color.NEON, false, true);
        if (event.flag(Flag.COMEBACK)) {
            boolean big = event.streak >= 2;
            return banner(ctx, event, big ? "COMEBACK!" : "FIXED!", null, Color.GOLD, big, big);
        }
        if (event.flag(Flag.EVAL_MILESTONE)) return banner(ctx, event, Fx.commas(event.evals) + " EVALS", null, Color.NEON, true, true);
        if (event.flag(Flag.RECORD_JUMP)) return banner(ctx, event, "NEW RECORD", numberText(info), Color.GOLD, true, true);
        if (event.flag(Flag.COMBO_MEGA)) {
            return banner(ctx, event, "COMBO " + event.combo + "!", event.flag(Flag.FEVER_START) ? "FEVER TIME! ALL POINTS x2" : null,
                    Color.FIRE, true, event.combo >= 50);
        }
        if (event.flag(Flag.COMBO_BEST)) return banner(ctx, event, "BEST COMBO!", "ALL-TIME BEST " + event.combo, Color.FIRE, false, false);
        if (event.flag(Flag.COMBO_MILESTONE) && event.combo >= 5) return banner(ctx, event, "COMBO " + event.combo, null, Color.FIRE, false, false);
        if (event.flag(Flag.FIRST_HIT)) return banner(ctx, event, "FIRST HIT!", null, Color.ICE, false, false);
        return null;
    }

    private static Scene banner(Scene.Ctx ctx, Event event, String text, String sub, int[][] palette, boolean mega, boolean rainbow) {
        return new SuccessScenes.Banner(ctx, event, text, sub, palette, mega, rainbow, false);
    }

    private static boolean counter(Event event) {
        return event.info.isNumeric() && (event.info.magnitude() >= 10_000 || event.flag(Flag.NEW_RECORD));
    }

    private Scene valueScene(Event event, Scene.Ctx ctx) {
        Info info = event.info;
        return switch (info.type) {
            case "nil" -> new SuccessScenes.Puff(ctx, event);
            case "true", "false" -> new SuccessScenes.Stamp(ctx, event);
            case "integer", "float" -> counter(event) ? new SuccessScenes.Counter(ctx, event) : new SuccessScenes.Hit(ctx, event);
            case "huge" -> new SuccessScenes.Banner(ctx, event, "HUGE", Fx.commas(info.digits) + " DIGITS", Color.GOLD, false, false, false);
            case "string" -> info.length >= 40 ? new SuccessScenes.Stream(ctx, event) : new SuccessScenes.Hit(ctx, event);
            case "array", "hash" -> info.size > 0 ? new SuccessScenes.Crate(ctx, event) : new SuccessScenes.Hit(ctx, event);
            default -> new SuccessScenes.Hit(ctx, event);
        };
    }

    private static String numberText(Info info) {
        if ("integer".equals(info.type) && info.intValue != null) return Fx.commas(info.intValue);
        return String.valueOf(info.floatValue);
    }

    /**
     * Plays the scene for the result first, then (for a success or a definition) at most one banner for what
     * the result earned: LEVEL UP!, FIXED!, COMBO 10! and so on. A key press cuts the banner too.
     */
    public Scene playEvent(Event event, InputShot input, double pre) {
        if (config.off()) return null;
        Scene.Ctx c = ctx(input, pre);
        if (input != null && input.size() + 12 > c.rows) c.input = null;
        Scene scene = sceneFor(event, c).init();
        Scene banner = bannerFor(event, c);
        // One badge line in the scrollback: the banner's, which already sums up the gain.
        play(scene, false, banner == null);
        if (banner != null) play(banner.init(), false, true);
        return scene;
    }

    public void intro(SessionScenes.Career career) {
        if (config.off() || !config.intro) return;
        play(new SessionScenes.Intro(ctx(null, 0), career).init(), true);
    }

    public void outro(Game.Stats stats) {
        if (config.off()) return;
        Scene.Ctx c = ctx(null, 0);
        Scene scene = config.motion && SessionScenes.Finale.fits(c.rows, c.w + 1) && config.level() >= 2
                ? new SessionScenes.Finale(c, stats).init()
                : new SessionScenes.Outro(c, stats).init();
        play(scene, true);
    }

    public void play(Scene scene, boolean forceTrail) {
        play(scene, forceTrail, true);
    }

    private void play(Scene scene, boolean forceTrail, boolean trail) {
        try {
            boolean animated = config.motion && scene.ctx().w >= 20 && fits(scene);
            if (animated && Term.inputPending()) animated = false;
            if (animated || !config.motion) sound(scene, animated);
            if (animated) run(scene);
            if (trail) emitTrail(scene, forceTrail);
        } catch (RuntimeException e) {
            // Never let an effect break the REPL.
            Debug.log(e);
            Term.write("\033[0m\033[?25h");
        }
    }

    private void sound(Scene scene, boolean animated) {
        switch (config.sound) {
            case "bell" -> {
                if (scene.bell()) Term.bell();
            }
            case "sfx" -> {
                if (Sound.available()) {
                    double delay = animated ? (scene.pre() + scene.impactAt()) * config.duration : 0.0;
                    if (scene.sfx() != null) Sound.play(scene.sfx(), delay, config.duration);
                } else if (scene.bell()) {
                    Term.bell();
                }
            }
            default -> { }
        }
    }

    private static boolean fits(Scene scene) {
        int top = scene.top() && scene.ctx().input != null ? scene.ctx().input.size() : 0;
        return scene.alt() || scene.height() + top <= scene.ctx().rows - 1;
    }

    private void emitTrail(Scene scene, boolean force) {
        String tr = scene.trail();
        if (tr == null) return;
        boolean ok = force || "all".equals(config.trail) || ("big".equals(config.trail) && scene.big());
        if (!ok) return;
        if (scene.depth() == Color.Depth.NONE) tr = tr.replaceAll("\033\\[[\\d;]*m", "");
        Term.write(tr.replace("\n", "\r\n") + "\033[0m\r\n");
    }

    private static double now() { return System.nanoTime() / 1e9; }

    private void run(Scene scene) {
        boolean alt = scene.alt();
        List<String> top = !alt && scene.top() && scene.ctx().input != null ? scene.ctx().input.rows : List.of();
        Stage stage = alt ? new Stage.Alt(scene.height()) : new Stage(scene.height(), top);
        Color.Depth depth = scene.depth();
        Term.raw(() -> {
            stage.open();
            double t0 = now();
            boolean flashed = false;
            double flashOff = -1;
            try {
                while (true) {
                    if (Term.inputPending() || !stage.isOpen()) break;
                    double t = (now() - t0) / config.duration;
                    if (t >= scene.totalLength() || scene.done(t)) break;
                    List<String> lines = new ArrayList<>(scene.height() + top.size());
                    if (!top.isEmpty()) {
                        Canvas tc = new Canvas(scene.ctx().w, top.size());
                        if (scene.drawTop(tc, t)) lines.addAll(Arrays.asList(tc.render(depth)));
                        else lines.addAll(top);
                    }
                    double ts = t - scene.pre();
                    Canvas c = new Canvas(scene.ctx().w, scene.height());
                    if (ts >= 0) scene.draw(c, ts);
                    lines.addAll(Arrays.asList(c.render(depth, ts >= 0 ? scene.shake(ts) : 0)));
                    double fa = scene.flashAt();
                    if (!flashed && !Double.isNaN(fa) && ts >= fa) {
                        flashed = true;
                        if ("full".equals(config.flash) && now() - lastFlash > 0.4) {
                            lastFlash = now();
                            Term.write("\033[?5h");
                            flashOff = now() + 0.05;
                        }
                    }
                    stage.draw(lines);
                    if (flashOff > 0 && now() >= flashOff) {
                        Term.write("\033[?5l");
                        flashOff = -1;
                    }
                    try {
                        Thread.sleep((long) (1000 / FPS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (flashOff > 0) Term.write("\033[?5l");
                stage.close();
            }
        });
    }
}
