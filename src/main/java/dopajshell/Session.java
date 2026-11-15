package dopajshell;

import java.util.Random;

import dopajshell.scenes.SessionScenes;

/** One interactive session: owns the game state and ties the evaluation cycle to the director. */
public final class Session {
    public final Config config;
    public final Game game;
    public final Director director;
    public final InputFx fx;
    private Profile profile;
    private long startXp, startBest;
    private int startLevel, startSessions;
    private boolean introduced, finished;

    public Session(Config config) {
        this.config = config;
        // DOPAJSHELL_SEED makes the luck (crits, jackpots, sparks) repeatable, e.g. for recordings.
        String seed = System.getenv("DOPAJSHELL_SEED");
        Random gameRng = new Random(), dirRng = new Random(), fxRng = new Random();
        if (seed != null) {
            try {
                long s = Long.parseLong(seed.strip());
                gameRng = new Random(s);
                dirRng = new Random(s + 1);
                fxRng = new Random(s + 2);
            } catch (NumberFormatException ignored) {
                // keep it random
            }
        }
        game = new Game(() -> System.nanoTime() / 1e9, gameRng);
        director = new Director(config, dirRng);
        fx = new InputFx(config, game, () -> System.nanoTime() / 1e9, fxRng, null);
    }

    public boolean active() { return !config.off() && Term.interactive(); }

    /** Loaded on first use, and only for interactive sessions. */
    public synchronized Profile profile() {
        if (profile == null) {
            profile = active() ? Profile.load() : new Profile(null);
            startXp = profile.xp;
            startLevel = profile.level();
            startBest = profile.bestScore;
            startSessions = profile.sessions;
            if (profile.persistent()) game.career(profile.xp, profile.bestCombo > 0 ? profile.bestCombo : null);
        }
        return profile;
    }

    /** Once per process, right before the first prompt. */
    public void intro() {
        if (introduced || !active()) return;
        introduced = true;
        Profile prof = profile();
        boolean grew = prof.visit();
        if (grew) prof.save();
        if ("sfx".equals(config.sound)) Sound.warmUp(config.duration);
        director.intro(prof.persistent() ? new SessionScenes.Career(prof.level(), prof.streak, grew && prof.streak >= 2) : null);
    }

    public void finish() {
        if (finished) return;
        finished = true;
        if (!active() || game.evals() == 0) return;
        Profile prof = profile();
        Game.Stats stats = game.stats();
        if (prof.persistent()) {
            stats.xpBefore = startXp;
            stats.levelBefore = startLevel;
            stats.level = game.level();
            stats.xp = game.xp();
            stats.bestBefore = startBest;
            stats.firstSession = startSessions == 0;
        }
        prof.visit();
        profile = prof.finish(stats);
        director.outro(stats);
        if ("sfx".equals(config.sound) && Sound.available()) Sound.waitIdle(2.5);
    }

    /** React to one evaluated snippet. elapsed is how long the evaluation took. */
    public void react(Info info, boolean success, boolean interrupted, InputShot shot, double elapsed) {
        try {
            if (!active()) return;
            if (interrupted) {
                director.playEvent(game.interrupted(), shot, 0.0);
            } else if (success) {
                profile();
                Event event = game.success(info);
                double pre = elapsed > 0.2 || shot == null ? 0.0 : Math.min(config.charge, 0.3);
                director.playEvent(event, shot, pre);
            } else {
                director.playEvent(game.failure(info), shot, 0.0);
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
    }
}
