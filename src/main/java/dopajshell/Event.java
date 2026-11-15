package dopajshell;

import java.util.EnumSet;

/** What happened on one evaluation, as the Game scored it. */
public final class Event {
    public enum Kind { SUCCESS, DEFINITION, FAILURE, INTERRUPT }

    public enum Flag {
        FIRST_HIT, COMEBACK, COMBO_MILESTONE, COMBO_MEGA, EVAL_MILESTONE, OUTPUT_RAIN, FEVER, FEVER_START,
        COMBO_BEST, NEW_RECORD, RECORD_JUMP, JACKPOT, CRITICAL, LEVEL_UP, COMBO_BREAK, FEVER_END
    }

    public Kind kind;
    public Info info;
    public EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    public long gain;
    public int combo;
    public int streak;
    public long score;
    public int mult = 1;
    public int level = 1;
    public int evals;

    public boolean flag(Flag f) { return flags.contains(f); }

    public Event(Kind kind, Info info) {
        this.kind = kind;
        this.info = info;
    }

    /** For demos and tests. */
    public static Event of(Kind kind, Info info, long gain, int combo, int streak, int mult, int level, Flag... flags) {
        Event e = new Event(kind, info);
        e.gain = gain;
        e.combo = combo;
        e.streak = streak;
        e.mult = mult;
        e.level = level;
        e.score = 12_480;
        for (Flag f : flags) e.flags.add(f);
        return e;
    }
}
