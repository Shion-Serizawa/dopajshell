package dopajshell;

import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/** Java syntax colors for the input line, plus the live keystroke highlights from InputFx. */
public final class DopaHighlighter implements Highlighter {
    private final InputFx fx;
    private final BooleanSupplier effects;
    private final BooleanSupplier color;

    public DopaHighlighter(InputFx fx, BooleanSupplier effects, BooleanSupplier color) {
        this.fx = fx;
        this.effects = effects;
        this.color = color;
    }

    private static AttributedStyle rgb(int r, int g, int b) { return AttributedStyle.DEFAULT.foreground(r, g, b); }

    static final AttributedStyle KEYWORD = rgb(255, 121, 198).bold();
    static final AttributedStyle LITERAL = rgb(255, 160, 90).bold();
    static final AttributedStyle TYPE = rgb(110, 220, 230);
    static final AttributedStyle STRING = rgb(160, 230, 130);
    static final AttributedStyle NUMBER = rgb(190, 150, 255);
    static final AttributedStyle COMMENT = rgb(110, 120, 135).italic();
    static final AttributedStyle ANNOTATION = rgb(240, 200, 100);
    static final AttributedStyle COMMAND = rgb(120, 200, 255).bold();

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        AttributedString base = syntax(buffer);
        if (fx != null && effects.getAsBoolean()) {
            try {
                return fx.apply(base, buffer, fx.now());
            } catch (RuntimeException e) {
                Debug.log(e);
            }
        }
        return base;
    }

    /** Just the syntax colors. */
    public AttributedString syntax(String buffer) {
        AttributedStringBuilder sb = new AttributedStringBuilder(buffer.length());
        if (!color.getAsBoolean()) return sb.append(buffer).toAttributedString();
        if (buffer.stripLeading().startsWith("/")) {
            int sp = buffer.indexOf(' ');
            if (sp < 0) sp = buffer.length();
            sb.styled(COMMAND, buffer.substring(0, sp)).append(buffer.substring(sp));
            return sb.toAttributedString();
        }
        try {
            for (JavaLexer.Token tk : JavaLexer.lex(buffer)) {
                AttributedStyle st = switch (tk.kind()) {
                    case KEYWORD -> KEYWORD;
                    case LITERAL -> LITERAL;
                    case TYPE -> TYPE;
                    case STRING, CHAR -> STRING;
                    case NUMBER -> NUMBER;
                    case COMMENT -> COMMENT;
                    case ANNOTATION -> ANNOTATION;
                    default -> AttributedStyle.DEFAULT;
                };
                sb.styled(st, tk.text());
            }
        } catch (RuntimeException e) {
            Debug.log(e);
            return new AttributedStringBuilder().append(buffer).toAttributedString();
        }
        return sb.toAttributedString();
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {}

    @Override
    public void setErrorIndex(int errorIndex) {}
}
