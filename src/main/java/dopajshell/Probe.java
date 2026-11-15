package dopajshell;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.ExpressionSnippet;
import jdk.jshell.JShell;
import jdk.jshell.MethodSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.UnresolvedReferenceException;
import jdk.jshell.VarSnippet;

/**
 * Cheap facts about results and failures, read off JShell's snippet events. Values are
 * classified from their static type and JShell's own display string; nothing extra is
 * evaluated in the user's VM.
 */
public final class Probe {
    private Probe() {}

    static final int HUGE_BITS = 4000;
    static final Set<String> INTS = Set.of("int", "long", "short", "byte", "Integer", "Long", "Short", "Byte",
            "BigInteger", "java.math.BigInteger", "AtomicInteger", "AtomicLong");
    static final Set<String> FLOATS = Set.of("double", "float", "Double", "Float", "BigDecimal", "java.math.BigDecimal");

    /** A successful snippet (status valid or recoverable, no exception). */
    public static Info value(SnippetEvent ev) {
        try {
            Snippet sn = ev.snippet();
            if (sn instanceof MethodSnippet ms) {
                Info i = Info.of("definition");
                i.what = "method";
                i.name = ms.name();
                return i;
            }
            if (sn instanceof TypeDeclSnippet td) {
                Info i = Info.of("definition");
                i.what = switch (td.subKind()) {
                    case INTERFACE_SUBKIND -> "interface";
                    case ENUM_SUBKIND -> "enum";
                    case ANNOTATION_TYPE_SUBKIND -> "annotation";
                    case RECORD_SUBKIND -> "record";
                    default -> "class";
                };
                i.name = td.name();
                return i;
            }
            if (sn.kind() == Snippet.Kind.IMPORT) return Info.of("import");
            String type = null;
            if (sn instanceof VarSnippet vs) type = vs.typeName();
            else if (sn instanceof ExpressionSnippet es) type = es.typeName();
            return classify(type, ev.value());
        } catch (RuntimeException e) {
            Debug.log(e);
            return Info.of("object");
        }
    }

    /** A value from its static type name and JShell's display string. */
    public static Info classify(String type, String value) {
        if (type == null || "void".equals(type) || value == null) {
            Info i = Info.of("nil");
            i.nullLabel = "void";
            return i;
        }
        String t = type.replaceAll("<.*", "").strip();
        Info i;
        if ("null".equals(value)) {
            i = Info.of("nil");
            i.nullLabel = "null";
        } else if (t.equals("boolean") || t.equals("Boolean")) {
            i = Info.of("true".equals(value) ? "true" : "false");
        } else if (INTS.contains(t)) {
            i = integer(value);
        } else if (FLOATS.contains(t)) {
            i = Info.of("float");
            try {
                i.floatValue = t.endsWith("BigDecimal") ? new BigDecimal(value).doubleValue() : Double.parseDouble(value);
            } catch (NumberFormatException e) {
                i = object(t);
            }
        } else if (t.equals("String") || t.equals("java.lang.String")) {
            i = Info.of("string");
            String s = unquote(value);
            i.length = s.codePointCount(0, s.length());
            i.head = sanitize(s.length() > 160 ? s.substring(0, 160) : s);
        } else if (t.endsWith("[]")) {
            Matcher m = Pattern.compile("^[^\\[]*\\[(\\d+)\\]").matcher(value);
            if (m.find()) {
                i = Info.of("array");
                i.size = Integer.parseInt(m.group(1));
            } else {
                i = object(t);
            }
        } else if (t.matches(".*(Map|Properties|Hashtable)$") && value.startsWith("{") && value.endsWith("}")) {
            i = Info.of("hash");
            i.size = topLevelCount(value);
        } else if (t.matches(".*(List|Set|Collection|Queue|Deque|Vector|Stack|Iterable)$") && value.startsWith("[") && value.endsWith("]")) {
            i = Info.of("array");
            i.size = topLevelCount(value);
        } else if (t.equals("char") || t.equals("Character")) {
            i = Info.of("char");
        } else {
            i = object(t);
        }
        i.typeName = type;
        return i;
    }

    private static Info object(String t) {
        Info i = Info.of("object");
        i.className = t;
        return i;
    }

    private static Info integer(String value) {
        try {
            BigInteger v = new BigInteger(value.strip());
            if (v.bitLength() > HUGE_BITS) {
                Info i = Info.of("huge");
                i.digits = (int) Math.floor(v.bitLength() * 0.30103) + 1;
                return i;
            }
            Info i = Info.of("integer");
            i.intValue = v;
            return i;
        } catch (NumberFormatException e) {
            return object("Number");
        }
    }

    /** Elements of "[a, b, c]" or entries of "{a=1, b=2}" at the top level. */
    static int topLevelCount(String v) {
        String inner = v.substring(1, v.length() - 1).strip();
        if (inner.isEmpty()) return 0;
        int depth = 0, n = 1;
        char quote = 0;
        for (int k = 0; k < inner.length(); k++) {
            char c = inner.charAt(k);
            if (quote != 0) {
                if (c == '\\') k++;
                else if (c == quote) quote = 0;
                continue;
            }
            switch (c) {
                case '"', '\'' -> quote = c;
                case '[', '{', '(' -> depth++;
                case ']', '}', ')' -> depth--;
                case ',' -> {
                    if (depth == 0) n++;
                }
                default -> { }
            }
        }
        return n;
    }

    /** Undo the Java string literal JShell displays ("a\nb" -> a, newline, b). */
    static String unquote(String lit) {
        String s = lit;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        StringBuilder sb = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c != '\\' || k + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char d = s.charAt(++k);
            switch (d) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 's' -> sb.append(' ');
                case 'u' -> {
                    int e = k + 1;
                    while (e < s.length() && s.charAt(e) == 'u') e++;
                    if (e + 4 <= s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(e, e + 4), 16));
                            k = e + 3;
                        } catch (NumberFormatException ex) {
                            sb.append('u');
                        }
                    } else {
                        sb.append('u');
                    }
                }
                default -> {
                    if (d >= '0' && d <= '7') {
                        int e = k;
                        while (e < s.length() && e < k + 3 && s.charAt(e) >= '0' && s.charAt(e) <= '7') e++;
                        sb.append((char) Integer.parseInt(s.substring(k, e), 8));
                        k = e - 1;
                    } else {
                        sb.append(d);
                    }
                }
            }
        }
        return sb.toString();
    }

    static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (cp == '\n') sb.append(' ');
            else if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) sb.append('.');
            else sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    // ---- failures ------------------------------------------------------------------

    /**
     * A rejected snippet. src is the snippet source JShell compiled; srcOffset is where it
     * starts in the whole input (for locating syntax errors on screen).
     */
    public static Info compileError(JShell js, SnippetEvent ev, String input, int srcOffset) {
        Info info = Info.of("other");
        try {
            Diag d = js.diagnostics(ev.snippet()).filter(Diag::isError).findFirst().orElse(null);
            if (d == null) {
                info.className = "CompileError";
                info.title = "COMPILE ERROR";
                return info;
            }
            String code = String.valueOf(d.getCode());
            String msg = d.getMessage(Locale.ENGLISH);
            String first = msg.lines().findFirst().orElse("");
            info.message = sanitize(first);
            info.className = "error";
            if (code.contains("cant.resolve")) {
                Matcher m = Pattern.compile("symbol:\\s+(\\w+(?: \\w+)?)\\s+([\\w$]+)").matcher(msg);
                String kind = m.find() ? m.group(1) : "";
                String name = m.find(0) ? m.group(2) : null;
                info.name = name;
                if (kind.equals("method") || code.endsWith(".args")) {
                    info.type = "nomethod";
                    Matcher loc = Pattern.compile("location:\\s+(?:variable \\w+ of type |class |interface |enum |record )?([\\w.$<>]+)").matcher(msg);
                    info.receiver = loc.find() ? simple(loc.group(1)) : "?";
                    info.message = "cannot find method " + name + (info.receiver.isEmpty() ? "" : " in " + info.receiver);
                } else {
                    info.type = "name";
                    info.message = "cannot find symbol " + name;
                }
                info.className = "cannot find symbol";
            } else if (code.contains("prob.found.req")) {
                info.type = "type";
                info.title = null;
                Matcher m = Pattern.compile("(\\S+) cannot be converted to (\\S+)").matcher(msg);
                Matcher lossy = Pattern.compile("lossy conversion from (\\S+) to (\\S+)").matcher(msg);
                if (m.find()) info.sides = new String[] {simple(m.group(1)), simple(m.group(2))};
                else if (lossy.find()) info.sides = new String[] {simple(lossy.group(1)), simple(lossy.group(2))};
                else info.sides = new String[] {"TYPE", "TYPE"};
                info.className = "incompatible types";
            } else if (code.contains("operator.cant.be.applied")) {
                info.type = "type";
                Matcher a = Pattern.compile("first type:\\s+(\\S+)").matcher(msg);
                Matcher b = Pattern.compile("second type:\\s+(\\S+)").matcher(msg);
                Matcher u = Pattern.compile("bad operand type (\\S+)").matcher(msg);
                if (a.find() && b.find()) info.sides = new String[] {simple(a.group(1)), simple(b.group(1))};
                else if (u.find()) info.sides = new String[] {simple(u.group(1)), "operator"};
                else info.sides = new String[] {"TYPE", "TYPE"};
                info.className = "bad operand types";
            } else if (code.contains("cant.apply.symbol")) {
                info.type = "argument";
                Matcher req = Pattern.compile("required:\\s+(.+)").matcher(msg);
                Matcher fnd = Pattern.compile("found:\\s+(.+)").matcher(msg);
                // "no suitable method found for max(int)\n    method java.lang.Math.max(int,int) is not applicable"
                Matcher given = Pattern.compile("found for [\\w$]+\\(([^)]*)\\)").matcher(msg);
                Matcher cand = Pattern.compile("method [\\w.$]+\\(([^)]*)\\) is not applicable").matcher(msg);
                String r = req.find() ? req.group(1).strip() : cand.find() ? cand.group(1) : "ARG";
                String f = fnd.find() ? fnd.group(1).strip() : given.find() ? given.group(1) : "ARG";
                if (f.isEmpty()) f = "no args";
                info.sides = new String[] {Fx.take("given " + f, 22), Fx.take("expected " + r, 22)};
                info.className = "cannot be applied";
            } else if (syntaxCode(code)) {
                info.type = "syntax";
                info.className = "syntax error";
                info.detail = sanitize(first);
                long pos = d.getStartPosition();
                if (pos < 0) pos = d.getPosition();
                if (pos >= 0) locate(info, input, srcOffset + (int) pos);
            } else {
                info.title = "COMPILE ERROR";
                info.className = sanitize(first);
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
        return info;
    }

    private static boolean syntaxCode(String code) {
        return code.contains(".expected") || code.contains("illegal.start") || code.contains("premature.eof")
                || code.contains("not.stmt") || code.contains("unclosed") || code.contains("else.without.if")
                || code.contains("orphaned") || code.contains("malformed") || code.contains("illegal.char")
                || code.contains("int.number.too.large") || code.contains("illegal.") || code.contains("invalid.");
    }

    private static void locate(Info info, String input, int pos) {
        pos = Math.max(0, Math.min(pos, input.length()));
        String before = input.substring(0, pos);
        info.line = (int) before.chars().filter(c -> c == '\n').count() + 1;
        info.column = pos - (before.lastIndexOf('\n') + 1);
    }

    /** A snippet that threw. */
    public static Info exception(Exception ex) {
        Info info = Info.of("other");
        try {
            if (ex instanceof UnresolvedReferenceException ure) {
                info.type = "name";
                info.name = ure.getSnippet().name();
                info.className = "unresolved reference";
                info.message = "attempted to use " + info.name + " before its dependencies are declared";
                return info;
            }
            String cls = ex instanceof EvalException ee ? ee.getExceptionClassName() : ex.getClass().getName();
            String simple = simple(cls);
            String msg = ex.getMessage() == null ? "" : sanitize(ex.getMessage().lines().findFirst().orElse(""));
            info.className = simple;
            info.message = msg;
            switch (simple) {
                case "NullPointerException" -> {
                    info.type = "nomethod";
                    info.title = "NULL POINTER";
                    info.receiver = "null";
                    Matcher m = Pattern.compile("Cannot (?:invoke|read field|load from|store to|read the array length|assign field|throw exception)\\s*\"?([^\"]*)\"?").matcher(msg);
                    String what = m.find() ? m.group(1) : "";
                    Matcher meth = Pattern.compile("\\.?([\\w$]+)\\(").matcher(what);
                    info.name = meth.find() ? meth.group(1) : (what.isEmpty() ? "?" : simple(what));
                    if (msg.isEmpty()) info.message = "NullPointerException";
                }
                case "ClassCastException" -> {
                    info.type = "type";
                    Matcher m = Pattern.compile("class (\\S+) cannot be cast to class (\\S+)").matcher(msg);
                    info.sides = m.find() ? new String[] {simple(m.group(1)), simple(m.group(2))} : new String[] {"TYPE", "TYPE"};
                }
                case "NumberFormatException" -> {
                    info.type = "argument";
                    Matcher m = Pattern.compile("For input string: (\"[^\"]*\")").matcher(msg);
                    info.sides = new String[] {m.find() ? Fx.take(m.group(1), 20) : "input", "number"};
                }
                case "IllegalArgumentException" -> {
                    info.type = "argument";
                    info.sides = new String[] {"ARG", "ARG"};
                }
                default -> info.type = "other";
            }
        } catch (RuntimeException e) {
            Debug.log(e);
        }
        return info;
    }

    static String simple(String name) {
        if (name == null) return "?";
        String n = name.replaceAll("<.*", "");
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }
}
