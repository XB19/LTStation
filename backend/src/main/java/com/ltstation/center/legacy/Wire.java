package com.ltstation.center.legacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON compatible avec System.Web.Script.Serialization.JavaScriptSerializer, utilisé par Station,
 * Center et CardMake d'origine : noms de propriétés tels quels, dates "\/Date(ms)\/",
 * caractères &lt; &gt; ' &amp; échappés en \\u00xx.
 */
public final class Wire {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DATE = Pattern.compile("^/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/$");

    private Wire() {
    }

    // ---------------------------------------------------------------- écriture

    public static String json(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            string(sb, s);
        } else if (o instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            sb.append(Double.isFinite(d) ? Conv.num(d) : "0");
        } else if (o instanceof BigDecimal d) {
            sb.append(d.toPlainString());
        } else if (o instanceof Number n) {
            sb.append(n.toString());
        } else if (o instanceof LocalDateTime dt) {
            date(sb, dt);
        } else if (o instanceof Timestamp ts) {
            date(sb, ts.toLocalDateTime());
        } else if (o instanceof LocalDate d) {
            date(sb, d.atStartOfDay());
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                string(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object x : c) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, x);
            }
            sb.append(']');
        } else if (o instanceof Object[] arr) {
            write(sb, List.of(arr));
        } else if (o instanceof Character ch) {
            string(sb, ch.toString());
        } else {
            string(sb, o.toString());
        }
    }

    private static void date(StringBuilder sb, LocalDateTime dt) {
        sb.append("\"\\/Date(").append(Conv.toMillis(dt)).append(")\\/\"");
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<', '>', '\'', '&' -> sb.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- lecture

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return convert(MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON invalide : " + e.getOriginalMessage(), e);
        }
    }

    public static Row parseRow(String json) {
        Object o = parse(json);
        if (o == null) {
            return null;
        }
        if (o instanceof Row r) {
            return r;
        }
        throw new IllegalArgumentException("Objet JSON attendu");
    }

    public static List<Row> parseRows(String json) {
        Object o = parse(json);
        List<Row> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(Conv.toRow(x));
            }
        }
        return out;
    }

    private static Object convert(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isObject()) {
            Row r = new Row();
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                r.put(e.getKey(), convert(e.getValue()));
            }
            return r;
        }
        if (n.isArray()) {
            List<Object> l = new ArrayList<>();
            n.forEach(x -> l.add(convert(x)));
            return l;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isIntegralNumber()) {
            return n.canConvertToInt() ? (Object) n.intValue() : (n.canConvertToLong() ? n.longValue() : n.decimalValue());
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        String s = n.asText();
        LocalDateTime d = parseDate(s);
        return d != null ? d : s;
    }

    /** "/Date(1700000000000)/" -> date locale, sinon null. */
    public static LocalDateTime parseDate(String s) {
        if (s == null || s.length() < 9 || s.charAt(0) != '/') {
            return null;
        }
        Matcher m = DATE.matcher(s);
        return m.matches() ? Conv.fromMillis(Long.parseLong(m.group(1))) : null;
    }
}
