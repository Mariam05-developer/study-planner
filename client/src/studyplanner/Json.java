package studyplanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Simple JSON parser so we don't need an external library.
// Objects -> Map, arrays -> List, numbers -> Long or Double.
public class Json {
    private String text;
    private int pos = 0;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        Object result = parser.readValue();
        parser.skipSpaces();
        if (parser.pos < text.length()) {
            throw parser.error("extra text at the end");
        }
        return result;
    }

    public static String write(Object value) {
        if (value == null || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Number) {
            if (!Double.isFinite(((Number) value).doubleValue())) {
                throw new IllegalArgumentException("JSON can't store " + value);
            }
            return value.toString();
        }
        if (value instanceof String) {
            return quote((String) value);
        }
        if (value instanceof Map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                parts.add(quote(entry.getKey().toString()) + ":" + write(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object item : (List<?>) value) {
                parts.add(write(item));
            }
            return "[" + String.join(",", parts) + "]";
        }
        throw new IllegalArgumentException("Can't convert " + value.getClass() + " to JSON");
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append("\"").toString();
    }

    private Object readValue() {
        skipSpaces();
        char c = peek();
        if (c == '{') {
            return readObject();
        } else if (c == '[') {
            return readArray();
        } else if (c == '"') {
            return readString();
        } else if (text.startsWith("true", pos)) {
            pos += 4;
            return true;
        } else if (text.startsWith("false", pos)) {
            pos += 5;
            return false;
        } else if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipSpaces();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpaces();
            String key = readString();
            skipSpaces();
            expect(':');
            map.put(key, readValue());
            skipSpaces();
            if (peek() != ',') {
                break;
            }
            pos++;
        }
        expect('}');
        return map;
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipSpaces();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(readValue());
            skipSpaces();
            if (peek() != ',') {
                break;
            }
            pos++;
        }
        expect(']');
        return list;
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            // escape sequence
            char e = next();
            if (e == '"' || e == '\\' || e == '/') {
                out.append(e);
            } else if (e == 'n') {
                out.append('\n');
            } else if (e == 't') {
                out.append('\t');
            } else if (e == 'r') {
                out.append('\r');
            } else if (e == 'b') {
                out.append('\b');
            } else if (e == 'f') {
                out.append('\f');
            } else if (e == 'u' && pos + 4 <= text.length()) {
                // NumberFormatException is an IllegalArgumentException
                out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                pos += 4;
            } else {
                throw error("bad escape");
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String number = text.substring(start, pos);
        if (number.matches("-?\\d+")) {
            return Long.parseLong(number);
        }
        return Double.parseDouble(number);
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected " + c);
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Bad JSON at position " + pos + ": " + message);
    }
}
