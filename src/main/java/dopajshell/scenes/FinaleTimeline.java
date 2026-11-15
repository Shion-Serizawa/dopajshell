package dopajshell.scenes;

/** The session finale's timeline, shared by the Finale scene and its synced sound track. */
public final class FinaleTimeline {
    private FinaleTimeline() {}

    public static final double DROP = 0.35;
    public static final double ROW0 = 0.55;
    public static final double ROW_GAP = 0.17;
    public static final double SLIDE = 0.08;
    public static final double COUNT = 0.24;
    public static final int ROWS = 8;
    /** One rising note per stat row. */
    public static final int[] NOTES = {1047, 1175, 1319, 1397, 1568, 1760, 1976, 2093};

    public static double rowStart(int i) { return ROW0 + i * ROW_GAP; }
    public static double rowLand(int i) { return rowStart(i) + SLIDE + COUNT; }
    public static double scoreStart() { return rowLand(ROWS - 1) + 0.15; }
    public static double scoreLand() { return scoreStart() + 0.85; }
    public static double rank() { return scoreLand() + 0.3; }
    public static double length() { return rank() + 1.9; }
}
