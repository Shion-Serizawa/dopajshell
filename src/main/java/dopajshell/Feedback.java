package dopajshell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jdk.jshell.DeclarationSnippet;
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

/** The plain text jshell's "normal" feedback mode prints for each snippet event. */
public final class Feedback {
    private Feedback() {}

    static final String BAR = "|  ";

    public static List<String> lines(JShell js, SnippetEvent ev, String source) {
        List<String> out = new ArrayList<>();
        Snippet sn = ev.snippet();
        if (ev.causeSnippet() != null) {
            // a dependent snippet changed status: mention it like jshell does
            if (sn instanceof DeclarationSnippet ds && ev.status() != ev.previousStatus()) {
                String what = describe(ds);
                if (ev.status() == Snippet.Status.VALID) out.add(BAR + "  update modified " + what);
                else if (ev.status() == Snippet.Status.OVERWRITTEN) out.add(BAR + "  update overwritten " + what);
            }
            return out;
        }
        if (ev.status() == Snippet.Status.REJECTED) {
            out.add(BAR + "Error:");
            js.diagnostics(sn).filter(Diag::isError).forEach(d -> out.addAll(diag(d, source)));
            return out;
        }
        Exception ex = ev.exception();
        if (ex instanceof UnresolvedReferenceException ure) {
            DeclarationSnippet ds = ure.getSnippet();
            out.add(BAR + "attempted to use " + describe(ds) + " which cannot be "
                    + (ds instanceof MethodSnippet ? "invoked" : "used") + " until " + unresolved(js, ds) + " is declared");
            return out;
        }
        if (ex != null) {
            out.addAll(exception(ex));
            return out;
        }
        if (sn instanceof VarSnippet vs) {
            if (ev.value() != null) out.add(vs.name() + " ==> " + ev.value());
            else out.add(BAR + "created variable " + vs.name() + recoverable(js, vs, ev));
        } else if (sn instanceof ExpressionSnippet es) {
            if (ev.value() != null && !"void".equals(es.typeName())) out.add(es.name() + " ==> " + ev.value());
        } else if (sn instanceof DeclarationSnippet ds) {
            String verb = ev.previousStatus() == Snippet.Status.NONEXISTENT ? "created"
                    : (ev.status() == Snippet.Status.VALID && ev.previousStatus() == Snippet.Status.VALID ? "modified" : "replaced");
            if (ev.previousStatus() == Snippet.Status.NONEXISTENT || ev.previousStatus() == Snippet.Status.DROPPED) verb = "created";
            else if (ev.previousStatus() == Snippet.Status.OVERWRITTEN || ev.previousStatus() == Snippet.Status.REJECTED) verb = "replaced";
            out.add(BAR + verb + " " + describe(ds) + recoverable(js, ds, ev));
        } else if (ev.value() != null && !ev.value().isEmpty()) {
            out.add(ev.value());
        }
        return out;
    }

    private static String recoverable(JShell js, DeclarationSnippet ds, SnippetEvent ev) {
        if (ev.status() != Snippet.Status.RECOVERABLE_DEFINED && ev.status() != Snippet.Status.RECOVERABLE_NOT_DEFINED) return "";
        String verb = ds instanceof MethodSnippet ? "invoked" : (ds instanceof TypeDeclSnippet ? "used" : "referenced");
        return ", however, it cannot be " + verb + " until " + unresolved(js, ds) + " is declared";
    }

    private static String unresolved(JShell js, DeclarationSnippet ds) {
        List<String> u = js.unresolvedDependencies(ds).collect(Collectors.toList());
        if (u.isEmpty()) return "its dependencies are";
        return String.join(", ", u);
    }

    public static String describe(DeclarationSnippet ds) {
        if (ds instanceof MethodSnippet ms) return "method " + ms.name() + "(" + ms.parameterTypes() + ")";
        if (ds instanceof TypeDeclSnippet td) {
            String kind = switch (td.subKind()) {
                case INTERFACE_SUBKIND -> "interface";
                case ENUM_SUBKIND -> "enum";
                case ANNOTATION_TYPE_SUBKIND -> "annotation interface";
                case RECORD_SUBKIND -> "record";
                default -> "class";
            };
            return kind + " " + td.name();
        }
        if (ds instanceof VarSnippet vs) return "variable " + vs.name();
        return ds.name();
    }

    static List<String> diag(Diag d, String source) {
        List<String> out = new ArrayList<>();
        for (String l : d.getMessage(Locale.getDefault()).split("\\R")) out.add(BAR + l);
        long start = d.getStartPosition(), end = d.getEndPosition();
        if (start < 0 || source == null || source.isEmpty()) return out;
        start = Math.min(start, source.length());
        end = Math.max(Math.min(end, source.length()), start);
        int ls = source.lastIndexOf('\n', (int) Math.max(start - 1, 0)) + 1;
        if (start == 0) ls = 0;
        int le = source.indexOf('\n', (int) start);
        if (le < 0) le = source.length();
        String line = source.substring(ls, le);
        out.add(BAR + line);
        int from = (int) start - ls;
        int to = (int) Math.min(end, le) - ls;
        StringBuilder caret = new StringBuilder(BAR);
        for (int k = 0; k < from; k++) caret.append(line.charAt(k) == '\t' ? '\t' : ' ');
        int len = Math.max(to - from, 1);
        if (len == 1) {
            caret.append('^');
        } else {
            caret.append('^').append("-".repeat(len - 2)).append('^');
        }
        out.add(caret.toString());
        return out;
    }

    static List<String> exception(Exception ex) {
        List<String> out = new ArrayList<>();
        Throwable t = ex;
        boolean first = true;
        while (t != null) {
            String cls = t instanceof EvalException ee ? ee.getExceptionClassName() : t.getClass().getName();
            String msg = t.getMessage();
            out.add(BAR + (first ? "Exception " : "Caused by: ") + cls + (msg == null ? "" : ": " + msg));
            StackTraceElement[] st = t.getStackTrace();
            for (StackTraceElement e : st) {
                out.add(BAR + "      at " + frame(e));
            }
            first = false;
            Throwable c = t.getCause();
            t = c == t ? null : c;
        }
        return out;
    }

    static String frame(StackTraceElement e) {
        String file = e.getFileName();
        if (file != null && file.startsWith("#")) {
            String cls = e.getClassName();
            String m = e.getMethodName();
            String where = "(" + file + ":" + e.getLineNumber() + ")";
            if (m.isEmpty() || m.equals("do_it$")) return where;
            int j = cls.lastIndexOf('$');
            String owner = cls.contains("$JShell$") && j >= 0 ? cls.substring(j + 1) : Probe.simple(cls);
            if (owner.matches("\\d+") || owner.isEmpty()) return m + " " + where;
            return owner + "." + m + " " + where;
        }
        return e.toString();
    }
}
