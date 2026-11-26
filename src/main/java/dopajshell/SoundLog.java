package dopajshell;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Turns a sound timeline written with DOPAJSHELL_SOUND_LOG into a WAV file, using the same
 * synthesizer the live player uses. For recording demo videos with sound.
 *
 * <pre>java -cp dopajshell-all.jar dopajshell.SoundLog LOG OUT.wav ORIGIN_EPOCH_SECONDS</pre>
 *
 * Time 0 of the WAV is ORIGIN_EPOCH_SECONDS; sounds that would start before it are cut.
 */
public final class SoundLog {
    private SoundLog() {}

    record Entry(double start, String name, double stretch) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: SoundLog LOG OUT.wav ORIGIN_EPOCH_SECONDS");
            System.exit(2);
        }
        double origin = Double.parseDouble(args[2]);
        List<Entry> entries = Files.readAllLines(Path.of(args[0])).stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .map(l -> l.split("\\s+"))
                .map(f -> new Entry(Double.parseDouble(f[1]) - origin, f[2], Double.parseDouble(f[3])))
                .toList();

        int total = 0;
        short[][] pcm = new short[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            pcm[i] = Sound.render(e.name(), e.stretch()).pcm();
            total = Math.max(total, frame(e.start()) + pcm[i].length);
        }
        int[] acc = new int[Math.max(total, 0)];
        for (int i = 0; i < entries.size(); i++) {
            int at = frame(entries.get(i).start());
            for (int j = Math.max(0, -at); j < pcm[i].length; j++) acc[at + j] += pcm[i][j];
        }

        byte[] out = new byte[acc.length * 2];
        for (int i = 0; i < acc.length; i++) {
            int s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, acc[i]));
            out[i * 2] = (byte) s;
            out[i * 2 + 1] = (byte) (s >> 8);
        }
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(out), Sound.FORMAT, acc.length)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, new File(args[1]));
        }
        System.out.printf("%d sounds, %.2fs -> %s%n", entries.size(), (double) acc.length / Sound.RATE, args[1]);
    }

    private static int frame(double seconds) { return (int) Math.round(seconds * Sound.RATE); }
}
