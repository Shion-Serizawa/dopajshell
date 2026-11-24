package dopajshell;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import dopajshell.scenes.FinaleTimeline;

/**
 * Sound effects synthesized at runtime (no bundled audio) and played in-process through
 * javax.sound: one daemon thread mixes the active voices into a single low-latency line.
 */
public final class Sound {
    private Sound() {}

    public static final int RATE = 22_050;

    /** Every patch, the startup sound first (warm-up order). */
    public static final List<String> NAMES = List.of(
            "intro", "key", "key_hot", "delete", "nice", "sealed", "lock", "rewind", "charge", "hit", "puff", "stamp",
            "counter", "banner", "mega", "rain", "error", "crack", "snap", "interrupt", "result", "bits", "crit",
            "jackpot", "levelup", "finale");

    enum Wave { SQUARE, SAW, TRIANGLE, SINE }

    /** Waveform building blocks. Each returns samples in -1..1 (before mixing). */
    static final class Synth {
        private Synth() {}

        static int frames(double dur) { return (int) Math.max(0, Math.round(dur * RATE)); }

        static double[] silence(double dur) { return new double[frames(dur)]; }

        /** slideTo / decay: NaN for none. */
        static double[] tone(double freq, double dur, Wave wave, double vol, double slideTo, double decay, double vibrato) {
            int n = frames(dur);
            double[] out = new double[n];
            double phase = 0.0;
            double attack = 0.004;
            for (int i = 0; i < n; i++) {
                double t = (double) i / RATE;
                double f = Double.isNaN(slideTo) ? freq : freq * Math.pow(slideTo / freq, (double) i / n);
                if (vibrato > 0) f *= 1 + vibrato * Math.sin(t * 2 * Math.PI * 6);
                phase += f / RATE;
                double p = phase % 1.0;
                double s = switch (wave) {
                    case SQUARE -> p < 0.5 ? 1.0 : -1.0;
                    case SAW -> 2 * p - 1;
                    case TRIANGLE -> 1 - 4 * Math.abs(p - 0.5);
                    case SINE -> Math.sin(phase * 2 * Math.PI);
                };
                double env = t < attack ? t / attack : 1.0;
                env *= Double.isNaN(decay) ? (1 - (double) i / n) : Math.exp(-t / decay);
                out[i] = s * env * vol;
            }
            return out;
        }

        static double[] tone(double freq, double dur, Wave wave, double vol, double slideTo, double decay) {
            return tone(freq, dur, wave, vol, slideTo, decay, 0.0);
        }

        /** Low-passed white noise; smooth 0..1 (higher = darker). */
        static double[] noise(double dur, double vol, double decay, double smooth, Random rng) {
            int n = frames(dur);
            double[] out = new double[n];
            double prev = 0.0;
            for (int i = 0; i < n; i++) {
                prev = prev * smooth + (rng.nextDouble() * 2 - 1) * (1 - smooth);
                out[i] = prev * vol * Math.exp(-((double) i / RATE) / decay);
            }
            return out;
        }

        static double[] noise(double dur, double vol, double decay, double smooth) {
            return noise(dur, vol, decay, smooth, new Random(7));
        }

        static double[] noise(double dur, double vol, double decay) { return noise(dur, vol, decay, 0.0); }

        static double[] seq(double[]... parts) {
            int len = 0;
            for (double[] p : parts) len += p.length;
            double[] out = new double[len];
            int o = 0;
            for (double[] p : parts) {
                System.arraycopy(p, 0, out, o, p.length);
                o += p.length;
            }
            return out;
        }

        static double[] mix(double[]... tracks) {
            int len = 0;
            for (double[] t : tracks) len = Math.max(len, t.length);
            double[] out = new double[len];
            for (double[] t : tracks) for (int i = 0; i < t.length; i++) out[i] += t[i];
            return out;
        }

        static double[] at(double offset, double[] track) { return seq(silence(offset), track); }

        static double[] arp(double[] freqs, double step, Wave wave, double vol, double decay) {
            double[][] parts = new double[freqs.length][];
            for (int i = 0; i < freqs.length; i++) parts[i] = tone(freqs[i], step, wave, vol, Double.NaN, decay);
            return seq(parts);
        }

        static double[] riser(double dur, double vol, double smooth, Random rng) {
            int n = frames(dur);
            double[] out = new double[n];
            double prev = 0.0;
            for (int i = 0; i < n; i++) {
                prev = prev * smooth + (rng.nextDouble() * 2 - 1) * (1 - smooth);
                double k = (double) i / n;
                out[i] = prev * vol * k * k;
            }
            return out;
        }

        static double[] boom(double dur, double vol, double low) {
            return mix(noise(dur, vol * 0.8, dur / 3.5, 0.6),
                    tone(low, dur, Wave.SINE, vol, low / 2.0, dur / 3));
        }

        static double len(double[] s) { return (double) s.length / RATE; }
    }

    /** A growable buffer that tracks are mixed into at a time offset. */
    static final class Buf {
        double[] data = new double[0];
        int size;

        Buf add(double t, double[] track) { return add(t, track, 1.0); }

        Buf add(double t, double[] track, double gain) {
            int o = (int) Math.round(t * RATE);
            if (o < 0) o = 0;
            int need = o + track.length;
            if (need > data.length) data = Arrays.copyOf(data, Math.max(need, data.length * 3 / 2));
            if (need > size) size = need;
            for (int i = 0; i < track.length; i++) data[o + i] += track[i] * gain;
            return this;
        }

        double[] samples() { return Arrays.copyOf(data, size); }
    }

    record Raw(double[] samples, double impact) {}

    record Rendered(short[] pcm, double impact, double length) {}

    private static final Wave SQ = Wave.SQUARE, SAW = Wave.SAW, TRI = Wave.TRIANGLE, SIN = Wave.SINE;
    private static final double N = Double.NaN;

    private static double[] tones(double[] freqs, double dur, Wave wave, double vol, double decay, double vibrato) {
        double[][] ts = new double[freqs.length][];
        for (int i = 0; i < freqs.length; i++) ts[i] = Synth.tone(freqs[i], dur, wave, vol, N, decay, vibrato);
        return Synth.mix(ts);
    }

    private static double[] d(double... v) { return v; }

    static Raw patch(String name, double k) {
        switch (name) {
            case "key": return new Raw(Synth.tone(1400, 0.022, SQ, 0.24, N, 0.008), 0);
            case "key_hot": return new Raw(Synth.tone(1900, 0.024, SQ, 0.24, N, 0.008), 0);
            case "delete": return new Raw(Synth.mix(Synth.noise(0.04, 0.12, 0.012), Synth.tone(500, 0.05, TRI, 0.08, 180, N)), 0);
            case "nice": return new Raw(Synth.seq(Synth.tone(988, 0.05, TRI, 0.2, N, 0.03), Synth.tone(1480, 0.09, TRI, 0.2, N, 0.05)), 0);
            case "sealed": return new Raw(Synth.arp(d(659, 988, 1319), 0.05, TRI, 0.18, 0.04), 0);
            case "lock": return new Raw(Synth.tone(1200, 0.09, SQ, 0.12, 2600, 0.05), 0);
            case "rewind": return new Raw(Synth.tone(1400, 0.16, TRI, 0.12, 350, N), 0);
            case "charge": return new Raw(Synth.tone(300, 0.22, SAW, 0.16, 1200, N), 0);
            case "hit": return new Raw(Synth.seq(Synth.tone(440, 0.05, SQ, 0.16, 900, N), Synth.tone(1760, 0.12, TRI, 0.2, N, 0.05)), 0.05);
            case "puff": return new Raw(Synth.seq(Synth.tone(900, 0.07, TRI, 0.08, 300, N), Synth.noise(0.3, 0.45, 0.12, 0.85)), 0.07);
            case "stamp": return new Raw(Synth.mix(Synth.noise(0.05, 0.5, 0.015), Synth.tone(110, 0.2, SIN, 0.6, 60, 0.07)), 0);
            case "counter": {
                double[] fs = new double[14];
                for (int i = 0; i < 14; i++) fs[i] = 400 * Math.pow(2, i / 7.0);
                double[] ticks = Synth.arp(fs, 0.038, SQ, 0.12, 0.012);
                double[] boom = Synth.mix(Synth.noise(0.45, 0.45, 0.14, 0.6), Synth.tone(90, 0.4, SIN, 0.6, 45, 0.15));
                return new Raw(Synth.seq(ticks, boom), Synth.len(ticks));
            }
            case "banner": {
                double[] fan = Synth.arp(d(523, 659, 784), 0.07, SQ, 0.16, 0.05);
                double[] hold = tones(d(1047, 1319, 1568), 0.35, SQ, 0.09, N, 0.01);
                double[] boom = Synth.noise(0.25, 0.3, 0.08, 0.5);
                return new Raw(Synth.mix(Synth.seq(fan, hold), Synth.at(Synth.len(fan), boom)), Synth.len(fan));
            }
            case "mega": {
                double[] fan = Synth.arp(d(523, 659, 784, 1047), 0.07, SQ, 0.16, 0.05);
                double[] chord = tones(d(1047, 1319, 1568, 2093), 0.7, SQ, 0.07, N, 0.012);
                double[] blast = Synth.mix(Synth.noise(0.8, 0.5, 0.25, 0.55), Synth.tone(70, 0.6, SIN, 0.6, 35, 0.2));
                Random r = new Random(3);
                double head = Synth.len(fan);
                Buf b = new Buf().add(0, Synth.seq(fan, chord)).add(head, blast);
                for (int i = 0; i < 6; i++) b.add(head + 0.25 + i * 0.12, Synth.noise(0.12, 0.25, 0.04, 0.0, r));
                return new Raw(b.samples(), head);
            }
            case "rain": {
                Buf b = new Buf();
                for (int i = 0; i < 16; i++) b.add(i * 0.045, Synth.tone(600 + (i * 373) % 900, 0.03, SQ, 0.22, N, 0.012));
                return new Raw(b.samples(), 0);
            }
            case "error": return new Raw(Synth.mix(Synth.tone(220, 0.28, SAW, 0.18, 110, N), Synth.tone(233, 0.28, SAW, 0.14, 116, N)), 0);
            case "crack": return new Raw(Synth.mix(Synth.noise(0.12, 0.55, 0.03), Synth.tone(160, 0.25, SAW, 0.2, 70, N)), 0);
            case "snap": return new Raw(Synth.seq(Synth.tone(1800, 0.03, SQ, 0.14, N, N),
                    Synth.mix(Synth.noise(0.08, 0.35, 0.02), Synth.tone(300, 0.2, TRI, 0.14, 90, N))), 0.03);
            case "interrupt": return new Raw(Synth.tone(900, 0.32, SIN, 0.25, 90, N), 0);
            case "intro": {
                double[] sweep = Synth.tone(200, 0.3, SAW, 0.12, 1600, N);
                double[] chord = tones(d(523, 659, 784, 1047), 0.6, SQ, 0.07, N, 0.01);
                return new Raw(Synth.seq(sweep, chord), 0.3);
            }
            case "result": return new Raw(Synth.seq(Synth.arp(d(784, 988, 1175, 1568), 0.08, TRI, 0.2, 0.06),
                    Synth.tone(2093, 0.4, TRI, 0.18, N, 0.2)), 0);
            case "bits": {
                double[] up = Synth.arp(d(523, 659, 784, 1047, 1319, 1568, 2093), 0.035, SQ, 0.13, 0.03);
                return new Raw(Synth.seq(up, Synth.tone(2093, 0.18, SQ, 0.1, N, 0.08, 0.02)), 0);
            }
            case "crit": {
                double[] shing = Synth.tone(2400, 0.1, SAW, 0.12, 5200, 0.05);
                double[] thud = Synth.mix(Synth.noise(0.12, 0.55, 0.03), Synth.tone(140, 0.25, SIN, 0.7, 60, 0.08));
                double[] sting = tones(d(880, 1109, 1319), 0.3, SQ, 0.06, 0.12, 0.0);
                return new Raw(Synth.mix(shing, Synth.at(0.1, thud), Synth.at(0.12, sting)), 0.1);
            }
            case "jackpot": {
                Random r = new Random(9);
                Buf b = new Buf();
                for (double t = 0.0; t < 0.95; t += 0.045) b.add(t, Synth.tone(700 + r.nextInt(500), 0.02, SQ, 0.1, N, 0.008));
                double[] stops = {0.45, 0.7, 0.95};
                double[] notes = {784, 988, 1175};
                for (int i = 0; i < 3; i++) {
                    b.add(stops[i], Synth.mix(Synth.noise(0.05, 0.4, 0.015, 0.0, r), Synth.tone(160 + i * 40, 0.15, SIN, 0.5, 80, 0.05)));
                    b.add(stops[i], Synth.tone(notes[i], 0.12, TRI, 0.18, N, 0.06));
                }
                b.add(0.95, Synth.boom(0.8, 0.6, 75));
                b.add(1.0, Synth.arp(d(1047, 1319, 1568, 2093, 1568, 2093, 2637), 0.07, SQ, 0.12, 0.05));
                for (int i = 0; i < 40; i++) b.add(1.0 + r.nextDouble() * 1.2, Synth.tone(2600 + r.nextInt(1800), 0.06, SIN, 0.08, N, 0.02));
                return new Raw(b.samples(), 0.95);
            }
            case "levelup": {
                Buf b = new Buf();
                b.add(0, Synth.tone(150, 0.22, SAW, 0.12, 900, N));
                b.add(0.22, Synth.boom(0.7, 0.6, 80));
                b.add(0.24, Synth.arp(d(523, 659, 784, 1047, 784, 1047, 1319, 1568), 0.06, SQ, 0.13, 0.05));
                for (double f : d(1047, 1319, 1568, 2093)) b.add(0.72, Synth.tone(f, 0.5, SQ, 0.06, N, 0.3, 0.015));
                b.add(1.0, Synth.tone(98, 1.6, SIN, 0.45, N, 0.7, 0.004));
                b.add(1.0, Synth.tone(196, 1.2, TRI, 0.12, N, 0.5));
                b.add(1.45, Synth.noise(0.5, 0.25, 0.15, 0.5));
                double[] fs = d(523, 659, 784, 1047, 1319);
                for (int i = 0; i < fs.length; i++) b.add(1.45 + i * 0.05, Synth.tone(fs[i], 1.4, SQ, 0.05, N, 0.6, 0.01));
                Random r = new Random(4);
                for (int i = 0; i < 16; i++) b.add(1.6 + r.nextDouble() * 1.4, Synth.tone(3000 + r.nextInt(2000), 0.05, SIN, 0.06, N, 0.015));
                return new Raw(b.samples(), 0.22);
            }
            case "finale": return finale(k);
            default: throw new IllegalArgumentException("no such sound: " + name);
        }
    }

    /** Session finale, laid out on FinaleTimeline; k stretches time. */
    static Raw finale(double k) {
        Buf b = new Buf();
        Random rng = new Random(5);
        double drop = FinaleTimeline.DROP;
        b.add(0, Synth.tone(120, drop * k, SAW, 0.14, 700, N));
        b.add(0, Synth.riser(drop * k, 0.35, 0.3, new Random(11)));
        b.add(drop * k, Synth.boom(0.6, 0.7, 80));
        b.add(drop * k, tones(d(523, 659, 784), 0.35, SQ, 0.06, 0.15, 0.0));
        for (int i = 0; i < FinaleTimeline.ROWS; i++) {
            double st = (FinaleTimeline.rowStart(i) + FinaleTimeline.SLIDE) * k;
            int n = (int) Math.floor(FinaleTimeline.COUNT * k / 0.03);
            for (int j = 0; j < n; j++) b.add(st + j * 0.03, Synth.tone(900 + i * 70 + j * 15, 0.012, SQ, 0.08, N, 0.005));
            double f = FinaleTimeline.NOTES[i];
            double land = FinaleTimeline.rowLand(i) * k;
            b.add(land, Synth.tone(f, 0.28, TRI, 0.22, N, 0.12));
            b.add(land, Synth.tone(f * 2, 0.2, SIN, 0.06, N, 0.08));
            b.add(land, Synth.noise(0.03, 0.12, 0.01, 0.0, rng));
        }
        double a = FinaleTimeline.scoreStart() * k;
        double e = FinaleTimeline.scoreLand() * k;
        double t = a;
        while (t < e - 0.01) {
            double p = (t - a) / (e - a);
            b.add(t, Synth.noise(0.03, 0.12 + 0.4 * p, 0.012, 0.3, rng));
            t += 0.045 - 0.02 * p;
        }
        b.add(a, Synth.tone(180, e - a, SAW, 0.08, 900, N));
        b.add(e, Synth.boom(0.9, 0.8, 70));
        b.add(e, Synth.noise(1.1, 0.22, 0.45, 0.0, rng));
        b.add(e, tones(d(523, 659, 784, 1047), 0.6, SQ, 0.05, 0.25, 0.0));
        double r = FinaleTimeline.rank() * k;
        b.add(r, Synth.mix(Synth.noise(0.05, 0.5, 0.015, 0.0, rng), Synth.tone(110, 0.25, SIN, 0.7, 55, 0.08)));
        double[] fan = Synth.arp(d(392, 523, 659, 784), 0.09, SQ, 0.13, 0.07);
        b.add(r + 0.06, fan);
        double hold = r + 0.06 + Synth.len(fan);
        for (double f : d(523, 659, 784, 1047)) {
            b.add(hold, Synth.tone(f, 1.3, SQ, 0.05, N, 0.7, 0.012));
            b.add(hold, Synth.tone(f / 2.0, 1.3, TRI, 0.08, N, 0.7));
        }
        b.add(hold, Synth.tone(131, 1.2, TRI, 0.18, N, 0.6));
        for (int j = 0; j < 10; j++) {
            double at = r + 0.2 + j * 0.13;
            b.add(at - 0.15, Synth.tone(500 + rng.nextInt(400), 0.15, SIN, 0.05, 1600, N));
            b.add(at, Synth.noise(0.14, 0.4, 0.05, 0.4, rng));
            for (int q = 0; q < 14; q++) b.add(at + 0.05 + rng.nextDouble() * 0.35, Synth.noise(0.006, 0.18, 0.003, 0.0, rng));
        }
        return new Raw(b.samples(), 0);
    }

    // ---- rendering & cache ----------------------------------------------

    private static final Map<String, Rendered> CACHE = new ConcurrentHashMap<>();

    static String key(String name, double stretch) {
        return "finale".equals(name) ? "finale-" + String.format(Locale.ROOT, "%.2f", stretch) : name;
    }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }

    /** Render (normalized 16-bit PCM). Pure; throws on unknown names. */
    static Rendered render(String name, double stretch) {
        Raw raw = patch(name, round2(stretch));
        double[] s = raw.samples();
        double peak = 0;
        for (double v : s) peak = Math.max(peak, Math.abs(v));
        double k = peak > 0.9 ? 0.9 / peak : 1.0; // mixed patches would clip otherwise
        short[] pcm = new short[s.length];
        for (int i = 0; i < s.length; i++) {
            long v = Math.round(s[i] * k * 32_000);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return new Rendered(pcm, raw.impact(), (double) s.length / RATE);
    }

    private static Rendered file(String name, double stretch) {
        return CACHE.computeIfAbsent(key(name, stretch), x -> render(name, stretch));
    }

    public static boolean ready(String name, double stretch) { return CACHE.containsKey(key(name, stretch)); }

    /** Seconds from the impact until the sound has finished. */
    public static double tail(String name, double stretch) {
        try {
            Rendered r = file(name, stretch);
            return r.length() - r.impact();
        } catch (RuntimeException e) {
            debug(e);
            return 0;
        }
    }

    private static volatile Thread warming;

    /** Render every missing sound in the background, the startup sound first. */
    public static synchronized void warmUp(double stretch) {
        try {
            if (warming != null && warming.isAlive()) return;
            List<String> missing = new ArrayList<>();
            for (String n : NAMES) if (!ready(n, stretch)) missing.add(n);
            if (missing.isEmpty()) return;
            Thread t = new Thread(() -> {
                for (String n : missing) {
                    try {
                        file(n, stretch);
                    } catch (RuntimeException e) {
                        debug(e);
                    }
                }
            }, "dopajshell-sound-warmup");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            warming = t;
            t.start();
        } catch (RuntimeException e) {
            debug(e);
        }
    }

    // ---- device ---------------------------------------------------------

    static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
    private static volatile Boolean available;

    public static boolean available() {
        Boolean a = available;
        if (a != null) return a;
        synchronized (Sound.class) {
            if (available != null) return available;
            boolean ok;
            try {
                String env = System.getenv("DOPAJSHELL_SOUND");
                if (env != null && env.equalsIgnoreCase("off")) {
                    ok = false;
                } else if (logging()) {
                    ok = true; // recording a timeline instead of playing: no device needed
                } else {
                    ok = AudioSystem.getMixerInfo().length > 0
                            && AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, FORMAT));
                }
            } catch (Throwable e) {
                debug(e);
                ok = false;
            }
            available = ok;
            return ok;
        }
    }

    public static boolean fast() { return available(); }

    // ---- timeline log (DOPAJSHELL_SOUND_LOG) ------------------------------
    //
    // Instead of playing, append "requestEpoch startEpoch name stretch" per sound, so that a
    // recording made without an audio device (VHS in Docker) can get the exact same audio
    // afterwards: see SoundLog.

    private static final String LOG = System.getenv("DOPAJSHELL_SOUND_LOG");
    private static final long ANCHOR_MILLIS = System.currentTimeMillis();
    private static final long ANCHOR_NANOS = System.nanoTime();

    /** When the last logged sound would have finished (System.nanoTime). */
    private static volatile long logEnd;

    static boolean logging() { return LOG != null && !LOG.isEmpty(); }

    private static double epoch(long nanos) { return ANCHOR_MILLIS / 1e3 + (nanos - ANCHOR_NANOS) / 1e9; }

    private static synchronized void log(String name, double stretch, long requestedAt, long start) {
        try (PrintWriter w = new PrintWriter(new FileWriter(LOG, true))) {
            w.printf(Locale.ROOT, "%.4f %.4f %s %.4f%n", epoch(requestedAt), epoch(start), name, stretch);
        } catch (Throwable e) {
            debug(e);
        }
    }

    // ---- mixer ----------------------------------------------------------

    private static final class Voice {
        final short[] pcm;
        final long startNanos;
        int pos;

        Voice(short[] pcm, long startNanos) {
            this.pcm = pcm;
            this.startNanos = startNanos;
        }
    }

    private static final int MAX_VOICES = 8;
    private static final int BLOCK = 384; // frames per mix block (~17ms)
    private static final long FRAME_NANOS = 1_000_000_000L / RATE;
    private static final Object MIX = new Object();
    private static final List<Voice> voices = new ArrayList<>(); // guarded by MIX
    private static final AtomicInteger pending = new AtomicInteger();
    private static final Map<String, Long> last = new ConcurrentHashMap<>();
    private static Thread mixer; // guarded by MIX
    private static SourceDataLine line; // owned by the mixer thread
    private static volatile boolean running;
    private static ExecutorService renderer;

    public static void play(String name) { play(name, 0.0, 1.0); }

    /** Start playing so that the sound's impact lands `delay` seconds from now. */
    public static boolean play(String name, double delay, double stretch) {
        try {
            if (name == null || !available()) return false;
            long now = System.nanoTime();
            Long prev = last.get(name);
            if (prev != null && now - prev < 40_000_000L) return false;
            last.put(name, now);
            Rendered r = CACHE.get(key(name, stretch));
            if (r != null) {
                enqueue(name, stretch, r, now, delay);
            } else {
                pending.incrementAndGet();
                renderer().execute(() -> {
                    try {
                        enqueue(name, stretch, file(name, stretch), now, delay);
                    } catch (Throwable e) {
                        debug(e);
                    } finally {
                        pending.decrementAndGet();
                    }
                });
            }
            return true;
        } catch (Throwable e) {
            debug(e);
            return false;
        }
    }

    private static synchronized ExecutorService renderer() {
        if (renderer == null) {
            renderer = Executors.newSingleThreadExecutor(rn -> {
                Thread t = new Thread(rn, "dopajshell-sound-render");
                t.setDaemon(true);
                return t;
            });
        }
        return renderer;
    }

    private static void enqueue(String name, double stretch, Rendered r, long requestedAt, double delay) {
        double offset = delay - r.impact();
        long start = requestedAt + (long) (Math.max(offset, 0) * 1e9);
        if (logging()) {
            log(name, stretch, requestedAt, start);
            logEnd = Math.max(logEnd, start + (long) (r.length() * 1e9));
            return;
        }
        synchronized (MIX) {
            if (!ensureMixer()) return;
            while (voices.size() >= MAX_VOICES) voices.remove(0);
            voices.add(new Voice(r.pcm(), start));
            MIX.notifyAll();
        }
    }

    /** Caller holds MIX. */
    private static boolean ensureMixer() {
        if (mixer != null && mixer.isAlive()) return true;
        running = true;
        mixer = new Thread(Sound::mixLoop, "dopajshell-sound-mixer");
        mixer.setDaemon(true);
        mixer.setPriority(Thread.MAX_PRIORITY);
        mixer.start();
        return true;
    }

    private static void mixLoop() {
        SourceDataLine l;
        try {
            l = AudioSystem.getSourceDataLine(FORMAT);
            l.open(FORMAT, 2048);
            l.start();
            line = l;
        } catch (Throwable e) {
            debug(e);
            available = false;
            synchronized (MIX) {
                voices.clear();
            }
            return;
        }
        int[] acc = new int[BLOCK];
        byte[] out = new byte[BLOCK * 2];
        long idleSince = -1;
        try {
            while (running) {
                long now = System.nanoTime();
                boolean any;
                synchronized (MIX) {
                    if (voices.isEmpty()) {
                        if (idleSince < 0) idleSince = now;
                        if (now - idleSince > 300_000_000L) {
                            // nothing to play: sleep until the next play() instead of streaming silence
                            try {
                                MIX.wait(1000);
                            } catch (InterruptedException e) {
                                return;
                            }
                            continue;
                        }
                    } else {
                        idleSince = -1;
                    }
                    Arrays.fill(acc, 0);
                    long blockEnd = now + BLOCK * FRAME_NANOS;
                    any = false;
                    Iterator<Voice> it = voices.iterator();
                    while (it.hasNext()) {
                        Voice v = it.next();
                        if (v.startNanos >= blockEnd) {
                            any = true;
                            continue;
                        }
                        int from = v.pos == 0 && v.startNanos > now ? (int) ((v.startNanos - now) / FRAME_NANOS) : 0;
                        for (int i = from; i < BLOCK && v.pos < v.pcm.length; i++) acc[i] += v.pcm[v.pos++];
                        if (v.pos >= v.pcm.length) it.remove();
                        else any = true;
                    }
                }
                for (int i = 0; i < BLOCK; i++) {
                    int s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, acc[i]));
                    out[i * 2] = (byte) s;
                    out[i * 2 + 1] = (byte) (s >> 8);
                }
                l.write(out, 0, out.length);
            }
        } catch (Throwable e) {
            debug(e);
        } finally {
            try {
                l.stop();
                l.close();
            } catch (Throwable e) {
                debug(e);
            }
            line = null;
        }
    }

    /** Block until nothing is playing or pending, or the timeout passes. */
    public static void waitIdle(double maxSeconds) {
        long deadline = System.nanoTime() + (long) (maxSeconds * 1e9);
        try {
            if (logging()) {
                // nothing is playing, but keep the same pacing as if it were
                while (pending.get() > 0 && System.nanoTime() < deadline) Thread.sleep(20);
                long wait = (Math.min(deadline, logEnd) - System.nanoTime()) / 1_000_000;
                if (wait > 0) Thread.sleep(wait);
                return;
            }
            while (System.nanoTime() < deadline) {
                boolean busy;
                synchronized (MIX) {
                    busy = !voices.isEmpty() || pending.get() > 0;
                }
                if (!busy) {
                    // let the line's buffer (~45ms) drain
                    Thread.sleep(60);
                    return;
                }
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void shutdown() {
        Thread m;
        synchronized (MIX) {
            running = false;
            voices.clear();
            MIX.notifyAll();
            m = mixer;
            mixer = null;
        }
        try {
            if (m != null) m.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void debug(Throwable e) {
        String path = System.getenv("DOPAJSHELL_DEBUG");
        if (path == null || path.isEmpty()) return;
        try (PrintWriter w = new PrintWriter(new FileWriter(path, true))) {
            e.printStackTrace(w);
        } catch (Throwable ignored) {
            // nowhere to report
        }
    }
}
