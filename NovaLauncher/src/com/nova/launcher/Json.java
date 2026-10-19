package com.nova.launcher;

import java.util.*;

/** Tiny JSON reader/writer so the launcher needs no external libraries. */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        return j.value();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> o(Object x) { return x instanceof Map ? (Map<String, Object>) x : new LinkedHashMap<>(); }

    @SuppressWarnings("unchecked")
    public static List<Object> a(Object x) { return x instanceof List ? (List<Object>) x : new ArrayList<>(); }

    public static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof String ? (String) v : null; }
    public static long num(Map<String, Object> m, String k) { Object v = m.get(k); return v instanceof Number ? ((Number) v).longValue() : 0; }

    // ---------- parsing ----------
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object value() {
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws(); i++; // ':'
            ws();
            m.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') break;
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') break;
        }
        return l;
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
        return b.toString();
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.valueOf(s.substring(st, i));
    }

    // ---------- writing ----------
    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(b, o, 0);
        return b.toString();
    }

    private static void write(StringBuilder b, Object o, int ind) {
        if (o == null) b.append("null");
        else if (o instanceof String) quote(b, (String) o);
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d); else b.append(d);
        } else if (o instanceof Boolean) b.append(o);
        else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(b, ind + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), ind + 1);
                if (++n < m.size()) b.append(',');
                b.append('\n');
            }
            pad(b, ind); b.append('}');
        } else if (o instanceof List) {
            List<?> l = (List<?>) o;
            if (l.isEmpty()) { b.append("[]"); return; }
            b.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                pad(b, ind + 1);
                write(b, l.get(k), ind + 1);
                if (k < l.size() - 1) b.append(',');
                b.append('\n');
            }
            pad(b, ind); b.append(']');
        } else quote(b, o.toString());
    }

    private static void pad(StringBuilder b, int n) { for (int k = 0; k < n; k++) b.append("  "); }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }
}
