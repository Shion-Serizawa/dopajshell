package dopajshell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What survives between sessions: lifetime XP (= total score), best score, best combo and
 * the day streak. One small JSON file, or $DOPAJSHELL_PROFILE. DOPAJSHELL_PROFILE=off keeps nothing.
 */
public final class Profile {
    public long xp;
    public long bestScore;
    public int bestCombo;
    public int sessions;
    public int streak;
    public String lastDay;
    private final Path path;

    public Profile(Path path) { this.path = path; }

    public Path path() { return path; }
    public boolean persistent() { return path != null; }
    public int level() { return Game.levelFor(xp); }

    public static Path defaultPath() {
        String env = System.getenv("DOPAJSHELL_PROFILE");
        if (env != null && (env.equalsIgnoreCase("off") || env.equals("0") || env.equalsIgnoreCase("none"))) return null;
        if (env != null && !env.isEmpty()) return Path.of(env);
        try {
            String local = System.getenv("LOCALAPPDATA");
            if (Term.isWindows() && local != null && !local.isEmpty()) return Path.of(local, "dopajshell", "profile.json");
            String xdg = System.getenv("XDG_STATE_HOME");
            Path base = xdg == null || xdg.isEmpty() ? Path.of(System.getProperty("user.home"), ".local", "state") : Path.of(xdg);
            return base.resolve("dopajshell").resolve("profile.json");
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Profile load() { return load(defaultPath()); }

    public static Profile load(Path path) {
        Profile p = new Profile(path);
        if (path == null || !Files.exists(path)) return p;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            p.xp = num(json, "xp");
            p.bestScore = num(json, "best_score");
            p.bestCombo = (int) num(json, "best_combo");
            p.sessions = (int) num(json, "sessions");
            p.streak = (int) num(json, "streak");
            Matcher m = Pattern.compile("\"last_day\"\\s*:\\s*\"([0-9-]+)\"").matcher(json);
            p.lastDay = m.find() ? m.group(1) : null;
        } catch (IOException | RuntimeException e) {
            Debug.log(e);
            return new Profile(path);
        }
        return p;
    }

    private static long num(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    /** Count today toward the day streak. Returns true when the streak grew today. */
    public boolean visit() { return visit(LocalDate.now()); }

    public boolean visit(LocalDate today) {
        String day = today.toString();
        if (day.equals(lastDay)) return false;
        streak = today.minusDays(1).toString().equals(lastDay) ? streak + 1 : 1;
        lastDay = day;
        return true;
    }

    /**
     * Fold a finished session in. Another dopajshell may have saved meanwhile, so the file
     * is re-read first and only this session's numbers are added.
     */
    public Profile finish(Game.Stats stats) {
        Profile disk = persistent() ? load(path) : this;
        disk.xp += stats.score;
        disk.bestScore = Math.max(disk.bestScore, stats.score);
        disk.bestCombo = Math.max(disk.bestCombo, stats.maxCombo);
        disk.sessions += 1;
        if (lastDay != null && (disk.lastDay == null || lastDay.compareTo(disk.lastDay) > 0)) {
            disk.lastDay = lastDay;
            disk.streak = streak;
        }
        disk.save();
        return disk;
    }

    public boolean save() {
        if (path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            String json = "{\n"
                    + "  \"xp\": " + xp + ",\n"
                    + "  \"best_score\": " + bestScore + ",\n"
                    + "  \"best_combo\": " + bestCombo + ",\n"
                    + "  \"sessions\": " + sessions + ",\n"
                    + "  \"streak\": " + streak + ",\n"
                    + "  \"last_day\": " + (lastDay == null ? "null" : "\"" + lastDay + "\"") + "\n"
                    + "}\n";
            Path tmp = path.resolveSibling(path.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException e) {
            Debug.log(e);
            return false;
        }
    }
}
