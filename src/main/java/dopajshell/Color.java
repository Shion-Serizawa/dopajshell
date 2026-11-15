package dopajshell;

/** RGB helpers (colors are int[3]) and SGR encoding for truecolor / 256 / 16 / no-color terminals. */
public final class Color {
    private Color() {}

    public enum Depth { NONE, C16, C256, TRUECOLOR }

    public static int[] rgb(int r, int g, int b) { return new int[] {r, g, b}; }

    public static final int[] WHITE = rgb(255, 255, 255);
    public static final int[] BLACK = rgb(0, 0, 0);

    public static final int[][] FIRE = {{255, 255, 230}, {255, 236, 110}, {255, 160, 20}, {235, 60, 10}, {120, 10, 30}, {40, 0, 20}};
    public static final int[][] ICE = {{240, 255, 255}, {130, 230, 255}, {40, 140, 255}, {30, 50, 190}, {20, 10, 80}};
    public static final int[][] GOLD = {{255, 255, 240}, {255, 230, 120}, {255, 190, 40}, {200, 120, 0}, {90, 40, 0}};
    public static final int[][] TOXIC = {{240, 255, 230}, {150, 255, 120}, {40, 220, 90}, {0, 130, 70}, {0, 50, 30}};
    public static final int[][] BLOOD = {{255, 230, 230}, {255, 90, 90}, {220, 20, 40}, {130, 0, 20}, {50, 0, 10}};
    public static final int[][] NEON = {{255, 240, 255}, {255, 110, 230}, {190, 60, 255}, {80, 40, 220}, {30, 10, 90}};
    public static final int[][] SMOKE = {{235, 235, 235}, {170, 170, 180}, {110, 110, 125}, {60, 60, 70}, {30, 30, 35}};
    public static final int[][] STEEL = {{230, 240, 255}, {150, 170, 200}, {90, 105, 140}, {50, 60, 85}, {25, 28, 40}};

    private static final int[][] BASIC16 = {
        {0, 0, 0}, {205, 0, 0}, {0, 205, 0}, {205, 205, 0}, {0, 0, 238}, {205, 0, 205}, {0, 205, 205}, {229, 229, 229},
        {127, 127, 127}, {255, 0, 0}, {0, 255, 0}, {255, 255, 0}, {92, 92, 255}, {255, 0, 255}, {0, 255, 255}, {255, 255, 255},
    };

    public static int clamp(double x) {
        int i = (int) x;
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    public static int[] mix(int[] a, int[] b, double t) {
        t = t < 0 ? 0.0 : (t > 1 ? 1.0 : t);
        return new int[] {clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t)};
    }

    public static int[] scale(int[] c, double k) {
        return new int[] {clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k)};
    }

    public static int[] hsv(double h, double s, double v) {
        h = (((h % 360) + 360) % 360) / 60.0;
        int i = (int) Math.floor(h);
        double f = h - i;
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return new int[] {clamp(r * 255), clamp(g * 255), clamp(b * 255)};
    }

    public static int[] rainbow(double t) { return hsv(t * 360, 0.85, 1.0); }
    public static int[] rainbow(double t, double s) { return hsv(t * 360, s, 1.0); }
    public static int[] rainbow(double t, double s, double v) { return hsv(t * 360, s, v); }

    /** t in 0..1 walks through the stops. */
    public static int[] ramp(int[][] stops, double t) {
        t = t < 0 ? 0.0 : (t > 1 ? 1.0 : t);
        double pos = t * (stops.length - 1);
        int i = (int) Math.floor(pos);
        if (i >= stops.length - 1) return stops[stops.length - 1];
        return mix(stops[i], stops[i + 1], pos - i);
    }

    public static String sgr(int[] fg, int[] bg, boolean bold, Depth depth) {
        StringBuilder sb = new StringBuilder("\033[0");
        if (bold) sb.append(";1");
        if (depth == Depth.NONE) {
            if (bg != null && luminance(bg) > 90) sb.append(";7");
        } else {
            if (fg != null) sb.append(';').append(fgCode(fg, depth));
            if (bg != null) sb.append(';').append(bgCode(bg, depth));
        }
        return sb.append('m').toString();
    }

    public static String fg(int[] c, Depth depth) { return sgr(c, null, false, depth); }

    public static final String RESET = "\033[0m";

    public static String fgCode(int[] c, Depth depth) {
        return switch (depth) {
            case TRUECOLOR -> "38;2;" + c[0] + ";" + c[1] + ";" + c[2];
            case C256 -> "38;5;" + to256(c);
            default -> {
                int i = to16(c);
                yield String.valueOf(i < 8 ? 30 + i : 82 + i);
            }
        };
    }

    public static String bgCode(int[] c, Depth depth) {
        return switch (depth) {
            case TRUECOLOR -> "48;2;" + c[0] + ";" + c[1] + ";" + c[2];
            case C256 -> "48;5;" + to256(c);
            default -> {
                int i = to16(c);
                yield String.valueOf(i < 8 ? 40 + i : 92 + i);
            }
        };
    }

    public static double luminance(int[] c) {
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    public static int to256(int[] c) {
        int r = c[0], g = c[1], b = c[2];
        if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
            if (r < 8) return 16;
            if (r > 246) return 231;
            return 232 + Math.max(0, Math.min(23, (int) Math.round((r - 8) / 10.0)));
        }
        return 16 + 36 * q256(r) + 6 * q256(g) + q256(b);
    }

    private static int q256(int v) {
        if (v < 48) return 0;
        if (v < 115) return 1;
        return Math.max(0, Math.min(5, (v - 35) / 40));
    }

    public static int to16(int[] c) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < BASIC16.length; i++) {
            int[] bc = BASIC16[i];
            long d = 2L * sq(bc[0] - c[0]) + 4L * sq(bc[1] - c[1]) + 3L * sq(bc[2] - c[2]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static long sq(int x) { return (long) x * x; }
}
