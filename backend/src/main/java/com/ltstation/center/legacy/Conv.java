package com.ltstation.center.legacy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conversions avec la sémantique de System.Convert (.NET) utilisée partout dans le code d'origine :
 * null donne 0 / "" / DateTime.MinValue, les doubles sont arrondis « au pair le plus proche »
 * (arrondi bancaire de Convert.ToInt32), les chaînes invalides lèvent une exception.
 */
public final class Conv {

    /** DateTime.MinValue */
    public static final LocalDateTime MIN_DATE = LocalDateTime.of(1, 1, 1, 0, 0);
    public static final DateTimeFormatter STD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter[] FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:m:s"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    };
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),
        DateTimeFormatter.ofPattern("yyyyMMdd"),
    };

    private Conv() {
    }

    public static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Long || v instanceof Short || v instanceof Byte || v instanceof BigInteger) {
            return Math.toIntExact(((Number) v).longValue());
        }
        if (v instanceof Number n) {
            return toDecimal(n).setScale(0, RoundingMode.HALF_EVEN).intValueExact();
        }
        String s = v.toString().trim();
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        return Integer.parseInt(s);
    }

    public static long toLong(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return ((Number) v).longValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Number n) {
            return toDecimal(n).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
        }
        String s = v.toString().trim();
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        return Long.parseLong(s);
    }

    public static double toDouble(Object v) {
        if (v == null) {
            return 0d;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1d : 0d;
        }
        return Double.parseDouble(v.toString().trim());
    }

    public static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal d) {
            return d;
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            return new BigDecimal(Double.toString(d));
        }
        if (v instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.longValue());
        }
        if (v instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        return new BigDecimal(v.toString().trim());
    }

    public static boolean toBool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String s = v.toString().trim();
        if (s.equalsIgnoreCase("true")) {
            return true;
        }
        if (s.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("String was not recognized as a valid Boolean.");
    }

    /** Convert.ToString(object) : null donne "". */
    public static String toStr(Object v) {
        String s = toStrOrNull(v);
        return s == null ? "" : s;
    }

    public static String toStrOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (v instanceof Double || v instanceof Float) {
            return num(((Number) v).doubleValue());
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (v instanceof LocalDateTime dt) {
            return dt.format(STD);
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(STD);
        }
        return v.toString();
    }

    /** Représentation d'un double comme .NET (« R ») : 12.5, 3, 0.001. */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }

    public static LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return MIN_DATE;
        }
        if (v instanceof LocalDateTime dt) {
            return dt;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if (v instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        String s = v.toString().trim();
        LocalDateTime wire = Wire.parseDate(s);
        if (wire != null) {
            return wire;
        }
        for (DateTimeFormatter f : FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        throw new IllegalArgumentException("String '" + s + "' was not recognized as a valid DateTime.");
    }

    /** Horodatage en millisecondes (ConvertDateTimeToInt de l'original). */
    public static long toMillis(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromMillis(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    @SuppressWarnings("unchecked")
    public static Row toRow(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Row r) {
            return r;
        }
        if (v instanceof Map<?, ?> m) {
            return new Row((Map<String, ?>) m);
        }
        if (v instanceof String s) {
            return Wire.parseRow(s);
        }
        throw new IllegalArgumentException("Objet attendu : " + v);
    }

    public static List<Row> toRows(Object v) {
        List<Row> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                out.add(toRow(o));
            }
        } else if (v instanceof String s) {
            return Wire.parseRows(s);
        }
        return out;
    }

    /** Math.Round(decimal, n) de .NET : arrondi bancaire. */
    public static BigDecimal round(BigDecimal d, int n) {
        return d.setScale(n, RoundingMode.HALF_EVEN);
    }

    /** Math.Round(decimal, n) de .NET sans ajout de zéros : l'échelle n'augmente jamais (12.5 arrondi à 3 reste 12.5). */
    public static BigDecimal netRound(BigDecimal d, int n) {
        return d == null ? BigDecimal.ZERO : d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    /** Math.Round(double, n) de .NET : arrondi bancaire. */
    public static double round(double d, int n) {
        return new BigDecimal(Double.toString(d)).setScale(n, RoundingMode.HALF_EVEN).doubleValue();
    }
}
