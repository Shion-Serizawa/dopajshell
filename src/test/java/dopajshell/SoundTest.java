package dopajshell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundTest {
    private static void check(String name, double stretch) {
        Sound.Rendered r = Sound.render(name, stretch);
        assertTrue(r.pcm().length > 0, name + " is empty");
        assertEquals((double) r.pcm().length / Sound.RATE, r.length(), 1e-9, name);
        assertTrue(Double.isFinite(r.impact()) && r.impact() >= 0 && r.impact() <= r.length(), name + " impact " + r.impact());
        int peak = 0;
        for (short s : r.pcm()) peak = Math.max(peak, Math.abs(s));
        assertTrue(peak > 0, name + " is silent");
        // normalized to 0.9 of full scale at most (|sample| <= 1)
        assertTrue(peak <= 0.9 * 32_000 + 1, name + " peak " + peak);
    }

    @Test
    void everyPatchRenders() {
        for (String name : Sound.NAMES) check(name, 1.0);
    }

    @Test
    void finaleStretches() {
        check("finale", 1.0);
        check("finale", 0.5);
        double full = Sound.render("finale", 1.0).length();
        double half = Sound.render("finale", 0.5).length();
        assertTrue(half < full, "stretch 0.5 should be shorter");
        assertEquals("finale-0.50", Sound.key("finale", 0.5));
        assertEquals("hit", Sound.key("hit", 0.5));
    }

    @Test
    void tailIsLengthAfterImpact() {
        Sound.Rendered r = Sound.render("counter", 1.0);
        assertEquals(r.length() - r.impact(), Sound.tail("counter", 1.0), 1e-9);
        assertTrue(Sound.ready("counter", 1.0));
    }
}
