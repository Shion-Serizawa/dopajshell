package dopajshell;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** User-tunable knobs. See `/dopa help` or README for the meaning of each. */
public final class Config {
    public static final List<String> INTENSITIES = List.of("off", "low", "normal", "max");
    public static final List<String> FLASHES = List.of("off", "soft", "full");
    public static final List<String> COLORS = List.of("auto", "truecolor", "256", "16", "none");
    public static final List<String> TRAILS = List.of("none", "big", "all");
    public static final List<String> SOUNDS = List.of("off", "bell", "sfx");

    public static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();
    static {
        DESCRIPTIONS.put("intensity", "off / low / normal / max  -- overall amount of effects");
        DESCRIPTIONS.put("motion", "on / off  -- animations (off = static badges only)");
        DESCRIPTIONS.put("flash", "off / soft / full  -- soft = region only, full = whole screen");
        DESCRIPTIONS.put("sound", "off / bell / sfx  -- sfx = synthesized effects (javax.sound), bell = terminal bell");
        DESCRIPTIONS.put("hud", "on / off  -- COMBO / SCORE / CHARGE next to the prompt");
        DESCRIPTIONS.put("duration", "float multiplier for every effect length (e.g. 0.5, 2)");
        DESCRIPTIONS.put("color", "auto / truecolor / 256 / 16 / none");
        DESCRIPTIONS.put("trail", "none / big / all  -- which effects leave a one-line badge");
        DESCRIPTIONS.put("charge", "seconds (max 0.3) of wind-up before a fast result lands; 0 disables");
        DESCRIPTIONS.put("intro", "on / off  -- title animation at startup");
        DESCRIPTIONS.put("keys", "on / off  -- per-keystroke effects while typing");
        DESCRIPTIONS.put("disguise", "on / off  -- start looking like plain jshell; the show begins with the first snippet");
    }

    public volatile String intensity = "max";
    public volatile boolean motion = true;
    public volatile String flash = "soft";
    public volatile String sound = "sfx";
    public volatile boolean hud = true;
    public volatile double duration = 1.0;
    public volatile String color = "auto";
    public volatile String trail = "big";
    public volatile double charge = 0.12;
    public volatile boolean intro = true;
    public volatile boolean keys = true;
    public volatile boolean disguise = false;

    public int level() { return INTENSITIES.indexOf(intensity); }
    public boolean off() { return "off".equals(intensity); }
    public boolean max() { return "max".equals(intensity); }

    public Config set(String name, Object value) {
        String key = name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        String v = String.valueOf(value);
        switch (key) {
            case "intensity" -> intensity = oneOf(v, INTENSITIES, key);
            case "flash" -> {
                Boolean b = boolOrNull(v);
                flash = b == null ? oneOf(v, FLASHES, key) : (b ? "soft" : "off");
            }
            case "color" -> {
                Boolean b = boolOrNull(v);
                color = b == null ? oneOf(v, COLORS, key) : (b ? "auto" : "none");
            }
            case "trail" -> trail = oneOf(v, TRAILS, key);
            case "sound" -> {
                Boolean b = boolOrNull(v);
                sound = b == null ? oneOf(v, SOUNDS, key) : (b ? "sfx" : "off");
            }
            case "motion", "hud", "intro", "keys", "disguise" -> {
                Boolean b = boolOrNull(v);
                if (b == null) throw new IllegalArgumentException(key + " expects on/off, got \"" + v + "\"");
                switch (key) {
                    case "motion" -> motion = b;
                    case "hud" -> hud = b;
                    case "intro" -> intro = b;
                    case "disguise" -> disguise = b;
                    default -> keys = b;
                }
            }
            case "duration" -> {
                double f = parse(v, key);
                if (f < 0.1 || f > 5) throw new IllegalArgumentException("duration must be in 0.1..5");
                duration = f;
            }
            case "charge" -> {
                double f = parse(v, key);
                if (f < 0 || f > 0.3) throw new IllegalArgumentException("charge must be in 0..0.3 (seconds)");
                charge = f;
            }
            default -> throw new IllegalArgumentException("unknown setting \"" + key + "\" (known: "
                    + String.join(", ", DESCRIPTIONS.keySet()) + ")");
        }
        return this;
    }

    /** "intensity=max,flash=off" (DOPAJSHELL env var / --set option). */
    public Config applyString(String str) {
        if (str == null) return this;
        for (String pair : str.split("[,\\s]+")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("expected key=value, got \"" + pair + "\"");
            set(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return this;
    }

    public Object get(String key) {
        return switch (key) {
            case "intensity" -> intensity;
            case "motion" -> motion;
            case "flash" -> flash;
            case "sound" -> sound;
            case "hud" -> hud;
            case "duration" -> duration;
            case "color" -> color;
            case "trail" -> trail;
            case "charge" -> charge;
            case "intro" -> intro;
            case "keys" -> keys;
            case "disguise" -> disguise;
            default -> null;
        };
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (var e : DESCRIPTIONS.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(String.format("  %-10s %-8s %s", e.getKey(), show(get(e.getKey())), e.getValue()));
        }
        return sb.toString();
    }

    private static String show(Object v) {
        if (v instanceof Boolean b) return b ? "on" : "off";
        return String.valueOf(v);
    }

    private static double parse(String v, String key) {
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " expects a number, got \"" + v + "\"");
        }
    }

    private static String oneOf(String value, List<String> list, String name) {
        String s = value.strip().toLowerCase(Locale.ROOT);
        if (list.contains(s)) return s;
        throw new IllegalArgumentException(name + " must be one of " + String.join(" / ", list) + ", got \"" + value + "\"");
    }

    private static Boolean boolOrNull(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
