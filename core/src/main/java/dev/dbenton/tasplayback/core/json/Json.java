package dev.dbenton.tasplayback.core.json;

import dev.dbenton.tasplayback.core.io.FormatException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON (RFC 8259) reader/writer for the small metadata files in a recording.
 *
 * <p>Mapping: object → {@link LinkedHashMap}, array → {@link ArrayList}, string → {@link String},
 * integral number → {@link Long}, other number → {@link Double}, true/false → {@link Boolean}, null → null.
 */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------------ parsing

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) throw p.error("trailing characters");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new FormatException("expected a JSON object");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 256;
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        FormatException error(String msg) { return new FormatException("JSON: " + msg + " at offset " + i); }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++; else break;
            }
        }

        char peek() { if (i >= s.length()) throw error("unexpected end"); return s.charAt(i); }

        void expect(char c) { if (peek() != c) throw error("expected '" + c + "'"); i++; }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("nesting too deep");
            char c = peek();
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("unexpected character '" + c + "'");
            }
        }

        void literal(String lit) {
            if (!s.startsWith(lit, i)) throw error("expected " + lit);
            i += lit.length();
        }

        Map<String, Object> object(int depth) {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                if (peek() != '"') throw error("expected object key");
                String k = string();
                ws(); expect(':'); ws();
                m.put(k, value(depth + 1));
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; return m; }
                throw error("expected ',' or '}'");
            }
        }

        List<Object> array(int depth) {
            expect('[');
            List<Object> l = new ArrayList<>();
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value(depth + 1));
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; return l; }
                throw error("expected ',' or ']'");
            }
        }

        String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw error("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 0x20) throw error("control character in string");
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw error("unterminated escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("short unicode escape");
                        try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                        catch (NumberFormatException ex) { throw error("bad unicode escape"); }
                        i += 4;
                        break;
                    default: throw error("bad escape '\\" + e + "'");
                }
            }
        }

        Object number() {
            int start = i;
            if (peek() == '-') i++;
            if (i >= s.length()) throw error("bad number");
            if (s.charAt(i) == '0') i++;
            else if (s.charAt(i) >= '1' && s.charAt(i) <= '9') { while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
            else throw error("bad number");
            boolean integral = true;
            if (i < s.length() && s.charAt(i) == '.') {
                integral = false; i++;
                int d = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                if (d == i) throw error("bad fraction");
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                integral = false; i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                int d = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                if (d == i) throw error("bad exponent");
            }
            String t = s.substring(start, i);
            if (integral) {
                try { return Long.parseLong(t); } catch (NumberFormatException ignored) { /* too big: fall through */ }
            }
            return Double.parseDouble(t);
        }
    }

    // ------------------------------------------------------------------ writing

    public static String write(Object value) {
        StringBuilder b = new StringBuilder();
        write(b, value, 0);
        b.append('\n');
        return b.toString();
    }

    private static void indent(StringBuilder b, int n) { for (int k = 0; k < n; k++) b.append("  "); }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int depth) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String) { quote(b, (String) v); return; }
        if (v instanceof Boolean) { b.append(v.toString()); return; }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) { b.append("null"); return; }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) { b.append((long) d).append(".0"); return; }
            b.append(Double.toString(d));
            return;
        }
        if (v instanceof Number) { b.append(((Number) v).longValue()); return; }
        if (v instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>) v;
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append("{\n");
            int k = 0;
            for (Map.Entry<Object, Object> e : m.entrySet()) {
                indent(b, depth + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), depth + 1);
                if (++k < m.size()) b.append(',');
                b.append('\n');
            }
            indent(b, depth);
            b.append('}');
            return;
        }
        if (v instanceof Iterable) {
            List<Object> l = new ArrayList<>();
            for (Object o : (Iterable<Object>) v) l.add(o);
            if (l.isEmpty()) { b.append("[]"); return; }
            b.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                indent(b, depth + 1);
                write(b, l.get(k), depth + 1);
                if (k + 1 < l.size()) b.append(',');
                b.append('\n');
            }
            indent(b, depth);
            b.append(']');
            return;
        }
        throw new IllegalArgumentException("cannot serialize " + v.getClass().getName());
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ typed accessors

    /** Typed view over a parsed object with clear errors for missing/mistyped fields. */
    public static final class Obj {
        private final Map<String, Object> m;
        private final String path;

        public Obj(Map<String, Object> m) { this(m, "$"); }
        private Obj(Map<String, Object> m, String path) { this.m = m; this.path = path; }

        public Map<String, Object> raw() { return m; }
        public boolean has(String k) { return m.containsKey(k) && m.get(k) != null; }

        private Object req(String k) {
            if (!has(k)) throw new FormatException("missing field " + path + "." + k);
            return m.get(k);
        }
        private FormatException type(String k, String want) {
            return new FormatException("field " + path + "." + k + " is not " + want);
        }

        public String str(String k) { Object v = req(k); if (!(v instanceof String)) throw type(k, "a string"); return (String) v; }
        public String str(String k, String def) { return has(k) ? str(k) : def; }
        public long lng(String k) { Object v = req(k); if (!(v instanceof Number)) throw type(k, "a number"); return ((Number) v).longValue(); }
        public long lng(String k, long def) { return has(k) ? lng(k) : def; }
        public int integer(String k) { return (int) lng(k); }
        public int integer(String k, int def) { return has(k) ? integer(k) : def; }
        public double dbl(String k) { Object v = req(k); if (!(v instanceof Number)) throw type(k, "a number"); return ((Number) v).doubleValue(); }
        public double dbl(String k, double def) { return has(k) ? dbl(k) : def; }
        public boolean bool(String k) { Object v = req(k); if (!(v instanceof Boolean)) throw type(k, "a boolean"); return (Boolean) v; }
        public boolean bool(String k, boolean def) { return has(k) ? bool(k) : def; }

        @SuppressWarnings("unchecked")
        public Obj obj(String k) {
            Object v = req(k);
            if (!(v instanceof Map)) throw type(k, "an object");
            return new Obj((Map<String, Object>) v, path + "." + k);
        }

        @SuppressWarnings("unchecked")
        public List<Obj> objList(String k) {
            List<Obj> out = new ArrayList<>();
            if (!has(k)) return out;
            Object v = m.get(k);
            if (!(v instanceof List)) throw type(k, "an array");
            int idx = 0;
            for (Object o : (List<Object>) v) {
                if (!(o instanceof Map)) throw new FormatException("element " + path + "." + k + "[" + idx + "] is not an object");
                out.add(new Obj((Map<String, Object>) o, path + "." + k + "[" + idx++ + "]"));
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        public List<String> strList(String k) {
            List<String> out = new ArrayList<>();
            if (!has(k)) return out;
            Object v = m.get(k);
            if (!(v instanceof List)) throw type(k, "an array");
            for (Object o : (List<Object>) v) {
                if (!(o instanceof String)) throw type(k, "an array of strings");
                out.add((String) o);
            }
            return out;
        }
    }
}
