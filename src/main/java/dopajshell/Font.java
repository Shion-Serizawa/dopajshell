package dopajshell;

import java.util.HashMap;
import java.util.Map;

/** 5-row bitmap font for the big banners. */
public final class Font {
    private Font() {}

    public static final int HEIGHT = 5;

    private static final Map<Character, boolean[][]> GLYPHS = new HashMap<>();

    private static void g(char c, String... rows) {
        boolean[][] bm = new boolean[HEIGHT][];
        for (int y = 0; y < HEIGHT; y++) {
            String r = rows[y];
            bm[y] = new boolean[r.length()];
            for (int x = 0; x < r.length(); x++) bm[y][x] = r.charAt(x) == '#';
        }
        GLYPHS.put(c, bm);
    }

    static {
        g('A', " ### ", "#   #", "#####", "#   #", "#   #");
        g('B', "#### ", "#   #", "#### ", "#   #", "#### ");
        g('C', " ####", "#    ", "#    ", "#    ", " ####");
        g('D', "#### ", "#   #", "#   #", "#   #", "#### ");
        g('E', "#####", "#    ", "#### ", "#    ", "#####");
        g('F', "#####", "#    ", "#### ", "#    ", "#    ");
        g('G', " ####", "#    ", "#  ##", "#   #", " ####");
        g('H', "#   #", "#   #", "#####", "#   #", "#   #");
        g('I', "###", " # ", " # ", " # ", "###");
        g('J', "  ###", "   # ", "   # ", "#  # ", " ##  ");
        g('K', "#   #", "#  # ", "###  ", "#  # ", "#   #");
        g('L', "#    ", "#    ", "#    ", "#    ", "#####");
        g('M', "#   #", "## ##", "# # #", "#   #", "#   #");
        g('N', "#   #", "##  #", "# # #", "#  ##", "#   #");
        g('O', " ### ", "#   #", "#   #", "#   #", " ### ");
        g('P', "#### ", "#   #", "#### ", "#    ", "#    ");
        g('Q', " ### ", "#   #", "# # #", "#  # ", " ## #");
        g('R', "#### ", "#   #", "#### ", "#  # ", "#   #");
        g('S', " ####", "#    ", " ### ", "    #", "#### ");
        g('T', "#####", "  #  ", "  #  ", "  #  ", "  #  ");
        g('U', "#   #", "#   #", "#   #", "#   #", " ### ");
        g('V', "#   #", "#   #", "#   #", " # # ", "  #  ");
        g('W', "#   #", "#   #", "# # #", "## ##", "#   #");
        g('X', "#   #", " # # ", "  #  ", " # # ", "#   #");
        g('Y', "#   #", " # # ", "  #  ", "  #  ", "  #  ");
        g('Z', "#####", "   # ", "  #  ", " #   ", "#####");
        g('0', " ### ", "#  ##", "# # #", "##  #", " ### ");
        g('1', " # ", "## ", " # ", " # ", "###");
        g('2', "#### ", "    #", " ### ", "#    ", "#####");
        g('3', "#### ", "    #", " ### ", "    #", "#### ");
        g('4', "#   #", "#   #", "#####", "    #", "    #");
        g('5', "#####", "#    ", "#### ", "    #", "#### ");
        g('6', " ### ", "#    ", "#### ", "#   #", " ### ");
        g('7', "#####", "    #", "   # ", "  #  ", "  #  ");
        g('8', " ### ", "#   #", " ### ", "#   #", " ### ");
        g('9', " ### ", "#   #", " ####", "    #", " ### ");
        g('!', "#", "#", "#", " ", "#");
        g('?', "### ", "   #", " ## ", "    ", " #  ");
        g('.', " ", " ", " ", " ", "#");
        g(',', "  ", "  ", "  ", " #", "# ");
        g(':', " ", "#", " ", "#", " ");
        g('\'', "#", "#", " ", " ", " ");
        g('+', "     ", "  #  ", "#####", "  #  ", "     ");
        g('-', "    ", "    ", "####", "    ", "    ");
        g('=', "    ", "####", "    ", "####", "    ");
        g('x', "   ", "# #", " # ", "# #", "   ");
        g('/', "    #", "   # ", "  #  ", " #   ", "#    ");
        g('>', "#   ", " #  ", "  # ", " #  ", "#   ");
        g('<', "   #", "  # ", " #  ", "  # ", "   #");
        g('^', " # ", "# #", "   ", "   ", "   ");
        g('#', " # # ", "#####", " # # ", "#####", " # # ");
        g('%', "#   #", "   # ", "  #  ", " #   ", "#   #");
        g('*', "     ", "# # #", " ### ", "# # #", "     ");
        g('$', " ####", "# #  ", " ### ", "  # #", "#### ");
        g(' ', " ", " ", " ", " ", " ");
    }

    public static boolean[][] glyph(char c) {
        boolean[][] g = GLYPHS.get(c);
        if (g == null) g = GLYPHS.get(Character.toUpperCase(c));
        return g == null ? GLYPHS.get('?') : g;
    }

    /** HEIGHT rows of booleans. */
    public static boolean[][] bitmap(String text) {
        int width = width(text, 1);
        boolean[][] rows = new boolean[HEIGHT][Math.max(width, 0)];
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) x += 1;
            boolean[][] g = glyph(text.charAt(i));
            for (int y = 0; y < HEIGHT; y++) {
                System.arraycopy(g[y], 0, rows[y], x, g[y].length);
            }
            x += g[0].length;
        }
        return rows;
    }

    public static int width(String text) { return width(text, 1); }

    public static int width(String text, int scale) {
        if (text.isEmpty()) return 0;
        int w = 0;
        for (int i = 0; i < text.length(); i++) w += glyph(text.charAt(i))[0].length;
        return (w + text.length() - 1) * scale;
    }
}
