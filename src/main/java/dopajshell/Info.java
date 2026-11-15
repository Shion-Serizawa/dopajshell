package dopajshell;

import java.math.BigInteger;

/**
 * Cheap, side-effect free facts about a result or a failure (filled in by Probe).
 *
 * <p>type is one of: nil (void / null), true, false, integer, float, huge, string, char,
 * array, hash, object, definition, import for successes; syntax, name, nomethod, type,
 * argument, other, interrupt for failures.
 */
public final class Info {
    public String type = "object";

    // values
    public BigInteger intValue;     // integer
    public double floatValue;       // float
    public int digits;              // huge
    public int length;              // string
    public String head = "";        // string (sanitized prefix)
    public int size;                // array / hash
    public String className = "";   // object; exception class for failures
    public String typeName = "";    // the Java type of the value, e.g. "int"
    public String nullLabel = "null"; // nil: "null" or "void"

    // definitions
    public String what;             // method / class / interface / enum / record / annotation / variable
    public String name;             // name of the definition, or the unknown symbol

    // failures
    public String message = "";
    public int line = -1;           // 1-based line of a syntax error
    public int column = -1;         // 0-based column
    public String detail;
    public String receiver;
    public String[] sides;
    public String title;            // optional title override (e.g. "NULL POINTER")
    public int brokenCombo;
    public double charge;

    // output
    public int outLines;

    public static Info of(String type) {
        Info i = new Info();
        i.type = type;
        return i;
    }

    public boolean isNumeric() { return "integer".equals(type) || "float".equals(type); }

    /** |value| as a double (for thresholds). */
    public double magnitude() {
        if ("integer".equals(type) && intValue != null) return intValue.abs().doubleValue();
        if ("float".equals(type)) return Math.abs(floatValue);
        return 0;
    }
}
