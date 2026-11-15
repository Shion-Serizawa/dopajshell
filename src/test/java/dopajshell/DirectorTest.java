package dopajshell;

import static dopajshell.RenderTest.ev;
import static dopajshell.RenderTest.info;
import static dopajshell.RenderTest.integer;
import static dopajshell.RenderTest.ok;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dopajshell.Event.Flag;
import dopajshell.scenes.BonusScenes;
import dopajshell.scenes.SuccessScenes;

/** The result's own scene always plays; what it earned (LEVEL UP! and friends) follows as a banner. */
class DirectorTest {
    private final Director director = new Director(new Config(), new Random(1));
    private Scene.Ctx ctx;

    @BeforeEach
    void setUp() {
        Term.fakeRows = 40;
        Term.fakeCols = 120;
        ctx = director.ctx(null, 0);
    }

    @AfterEach
    void reset() {
        Term.fakeRows = 0;
        Term.fakeCols = 0;
    }

    private static String banner(Scene s) {
        // Rainbow trails color every letter, so compare without the SGR codes.
        return s.init().trail().replaceAll("\033\\[[\\d;]*m", "");
    }

    @Test
    void levelUpNoLongerHidesTheResult() {
        Event e = ok(integer("832040"), Flag.LEVEL_UP);
        assertInstanceOf(SuccessScenes.Counter.class, director.sceneFor(e, ctx));
        Scene b = director.bannerFor(e, ctx);
        assertTrue(banner(b).contains("LEVEL UP!"));
        assertFalse(b.mega(), "LEVEL UP! follows every early result, so it stays short");
        assertFalse(b.alt());
    }

    @Test
    void definitionsUnlockThenBanner() {
        Event e = ev(Event.Kind.DEFINITION, info("definition"), 120, 3, 0, 1, Flag.LEVEL_UP, Flag.COMBO_MILESTONE);
        assertInstanceOf(SuccessScenes.Unlock.class, director.sceneFor(e, ctx));
        assertTrue(banner(director.bannerFor(e, ctx)).contains("LEVEL UP!"));
    }

    @Test
    void comebackAndFirstHitFollowTheResult() {
        Event fixed = ok(info("true"), Flag.COMEBACK);
        assertInstanceOf(SuccessScenes.Stamp.class, director.sceneFor(fixed, ctx));
        assertTrue(banner(director.bannerFor(fixed, ctx)).contains("FIXED!"));

        Event first = ok(integer("2"), Flag.FIRST_HIT);
        assertInstanceOf(SuccessScenes.Hit.class, director.sceneFor(first, ctx));
        assertTrue(banner(director.bannerFor(first, ctx)).contains("FIRST HIT!"));
    }

    @Test
    void jackpotStaysTheResultScene() {
        Event e = ok(integer("2"), Flag.JACKPOT, Flag.LEVEL_UP);
        assertInstanceOf(BonusScenes.Jackpot.class, director.sceneFor(e, ctx));
        assertTrue(banner(director.bannerFor(e, ctx)).contains("LEVEL UP!"));
    }

    @Test
    void plainResultsAndFailuresHaveNoBanner() {
        assertNull(director.bannerFor(ok(integer("2")), ctx));
        assertNull(director.bannerFor(ev(Event.Kind.FAILURE, info("name"), 0, 0, 1, 1, Flag.EVAL_MILESTONE), ctx));
    }
}
