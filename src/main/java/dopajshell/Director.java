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

    private Scene mega(Scene.Ctx ctx, Event event, String text, int[][] palette, boolean rainbow, String sub) {
        boolean alt = config.max() && config.motion;
        if (alt) {
            Scene.Ctx full = ctx.copy();
            full.input = null;
            return new SuccessScenes.Banner(full, event, text, sub, palette, true, rainbow, true);
        }
        return new SuccessScenes.Banner(ctx, event, text, sub, palette, true, rainbow, false);
    }

    private Scene successScene(Event event, Scene.Ctx ctx) {
        Info info = event.info;
        if (event.flag(Flag.JACKPOT)) return new BonusScenes.Jackpot(ctx, event);
        if (event.flag(Flag.LEVEL_UP)) return mega(ctx, event, "LEVEL UP!", Color.NEON, true, "LV " + event.level);
        if (event.flag(Flag.COMEBACK)) {
            return mega(ctx, event, event.streak >= 2 ? "COMEBACK!" : "FIXED!", Color.GOLD, event.streak >= 2, null);
        }
        if (event.flag(Flag.EVAL_MILESTONE)) return mega(ctx, event, Fx.commas(event.evals) + " EVALS", Color.NEON, true, null);
        if (event.flag(Flag.RECORD_JUMP)) return mega(ctx, event, "NEW RECORD", Color.GOLD, true, numberText(info));
        if (event.flag(Flag.COMBO_MEGA)) {
            return mega(ctx, event, "COMBO " + event.combo + "!", Color.FIRE, event.combo >= 50,
                    event.flag(Flag.FEVER_START) ? "FEVER TIME! ALL POINTS x2" : null);
        }
        if (event.flag(Flag.COMBO_BEST)) {
            return new SuccessScenes.Banner(ctx, event, "BEST COMBO!", "ALL-TIME BEST " + event.combo, Color.FIRE, false, false, false);
        }
        if ("definition".equals(info.type)) return new SuccessScenes.Unlock(ctx, event);
        if (event.flag(Flag.COMBO_MILESTONE) && event.combo >= 5) {
            return new SuccessScenes.Banner(ctx, event, "COMBO " + event.combo, null, Color.FIRE, false, false, false);
        }
        if (event.flag(Flag.FIRST_HIT)) return new SuccessScenes.Banner(ctx, event, "FIRST HIT!", null, Color.ICE, false, false, false);
        if (event.flag(Flag.CRITICAL) && !counter(event)) return new BonusScenes.Critical(ctx, event);
        if (event.flag(Flag.OUTPUT_RAIN)) return new SuccessScenes.Rain(ctx, event);
        return valueScene(event, ctx);
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

    public Scene playEvent(Event event, InputShot input, double pre) {
        if (config.off()) return null;
        Scene.Ctx c = ctx(input, pre);
        if (input != null && input.size() + 12 > c.rows) c.input = null;
        Scene scene = sceneFor(event, c).init();
        play(scene, false);
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
        try {
            boolean animated = config.motion && scene.ctx().w >= 20 && fits(scene);
            if (animated && Term.inputPending()) animated = false;
            if (animated || !config.motion) sound(scene, animated);
            if (animated) run(scene);
            emitTrail(scene, forceTrail);
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
