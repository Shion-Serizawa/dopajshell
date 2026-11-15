package dopajshell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * A forgiving tokenizer for (possibly incomplete) Java input: enough to color the line and
 * to find the bracket, string or block a keystroke just closed. Never throws.
 */
public final class JavaLexer {
    private JavaLexer() {}

    public enum Kind { KEYWORD, LITERAL, IDENT, TYPE, NUMBER, STRING, CHAR, COMMENT, OPEN, CLOSE, OP, SPACE, ANNOTATION }

    public record Token(Kind kind, int start, int end, String text, boolean closed) {}

    static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "yield", "sealed",
            "permits", "when");
    static final Set<String> LITERALS = Set.of("true", "false", "null");

    public static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            int st = i;
            if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                out.add(new Token(Kind.SPACE, st, i, s.substring(st, i), true));
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                out.add(new Token(Kind.COMMENT, st, i, s.substring(st, i), true));
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                boolean closed = e >= 0;
                i = closed ? e + 2 : n;
                out.add(new Token(Kind.COMMENT, st, i, s.substring(st, i), closed));
            } else if (c == '"' && s.startsWith("\"\"\"", i)) {
                int e = s.indexOf("\"\"\"", i + 3);
                while (e > 0 && s.charAt(e - 1) == '\\') e = s.indexOf("\"\"\"", e + 1);
                boolean closed = e >= 0;
                i = closed ? e + 3 : n;
                out.add(new Token(Kind.STRING, st, i, s.substring(st, i), closed));
            } else if (c == '"' || c == '\'') {
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == '\n') break;
                    i++;
                    if (d == c) {
                        closed = true;
                        break;
                    }
                }
                i = Math.min(i, n);
                out.add(new Token(c == '"' ? Kind.STRING : Kind.CHAR, st, i, s.substring(st, i), closed));
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                i++;
                while (i < n) {
                    char d = s.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '_' || d == '.') {
                        i++;
                    } else if ((d == '+' || d == '-') && (s.charAt(i - 1) == 'e' || s.charAt(i - 1) == 'E')
                            && !(s.startsWith("0x", st) || s.startsWith("0X", st))) {
                        i++;
                    } else {
                        break;
                    }
                }
                out.add(new Token(Kind.NUMBER, st, i, s.substring(st, i), true));
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                String w = s.substring(st, i);
                Kind k = KEYWORDS.contains(w) ? Kind.KEYWORD
                        : LITERALS.contains(w) ? Kind.LITERAL
                        : Character.isUpperCase(w.charAt(0)) ? Kind.TYPE : Kind.IDENT;
                out.add(new Token(k, st, i, w, true));
            } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(s.charAt(i + 1))) {
                i++;
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                out.add(new Token(Kind.ANNOTATION, st, i, s.substring(st, i), true));
            } else if (c == '(' || c == '[' || c == '{') {
                i++;
                out.add(new Token(Kind.OPEN, st, i, String.valueOf(c), true));
            } else if (c == ')' || c == ']' || c == '}') {
                i++;
                out.add(new Token(Kind.CLOSE, st, i, String.valueOf(c), true));
            } else {
                i++;
                if (c == '-' && i < n && s.charAt(i) == '>') i++;
                else if (c == ':' && i < n && s.charAt(i) == ':') i++;
                out.add(new Token(Kind.OP, st, i, s.substring(st, i), true));
            }
        }
        return out;
    }

    private static char pairOf(char open) {
        return switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            default -> '}';
        };
    }

    /** Offset of the opener matched by the close bracket at off, or -1. */
    public static int matchBracket(List<Token> tokens, int off) {
        Deque<Token> stack = new ArrayDeque<>();
        for (Token tk : tokens) {
            if (tk.start > off) break;
            if (tk.kind == Kind.OPEN) {
                stack.push(tk);
            } else if (tk.kind == Kind.CLOSE) {
                Token opener = stack.isEmpty() ? null : stack.pop();
                if (tk.start == off) {
                    if (opener == null || pairOf(opener.text.charAt(0)) != tk.text.charAt(0)) return -1;
                    return opener.start;
                }
            }
        }
        return -1;
    }

    /** [start, end) of the string or char literal that the quote at off just closed, or null. */
    public static int[] matchString(List<Token> tokens, int off) {
        for (Token tk : tokens) {
            if ((tk.kind == Kind.STRING || tk.kind == Kind.CHAR) && tk.closed && tk.end == off + 1 && tk.start < off) {
                return new int[] {tk.start, tk.end};
            }
            if (tk.start > off) break;
        }
        return null;
    }

    /**
     * True when the '{' at openOff starts a block (class / method / if / for / try ...) rather
     * than an initializer or a lambda body.
     */
    public static boolean isBlock(List<Token> tokens, int openOff) {
        Token prev = null;
        for (Token tk : tokens) {
            if (tk.start >= openOff) break;
            if (tk.kind == Kind.SPACE || tk.kind == Kind.COMMENT) continue;
            prev = tk;
        }
        if (prev == null) return true; // a bare block
        return switch (prev.kind) {
            case CLOSE -> prev.text.equals(")");
            case IDENT, TYPE, KEYWORD -> true;
            default -> false;
        };
    }
}
