package com.ltstation.center.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classes d'échange C# d'origine (générées par database/tools/dto_gen.py) : permet de produire
 * un objet avec exactement les propriétés, l'ordre et les valeurs par défaut que
 * JavaScriptSerializer écrirait (int = 0, bool = false, string = null, DateTime = MinValue…).
 */
public final class Dto {

    public record Prop(String name, String type, String init) {
    }

    private final Map<String, List<Prop>> classes = new HashMap<>();

    public Dto(String name) {
        String path = "naming/" + name + "-dto.json";
        try (InputStream in = Dto.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Ressource absente : " + path);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            root.fields().forEachRemaining(e -> {
                List<Prop> props = new ArrayList<>();
                e.getValue().forEach(p -> props.add(new Prop(p.get(0).asText(), p.get(1).asText(),
                    p.size() > 2 ? p.get(2).asText() : null)));
                classes.put(e.getKey(), props);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Prop> props(String cls) {
        List<Prop> p = classes.get(cls);
        if (p == null) {
            throw new IllegalArgumentException("Classe d'échange inconnue : " + cls);
        }
        return p;
    }

    public boolean has(String cls) {
        return classes.containsKey(cls);
    }

    /** new Classe() : toutes les propriétés à leur valeur par défaut. */
    public Row make(String cls) {
        Row r = new Row();
        for (Prop p : props(cls)) {
            r.put(p.name(), initial(p));
        }
        return r;
    }

    /** new Classe { A = .., B = .. } */
    public Row make(String cls, Object... kv) {
        Row r = make(cls);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    /** Objet de la classe rempli depuis une ligne (mêmes noms, casse indifférente). */
    public Row shape(String cls, Map<String, ?> src) {
        if (src == null) {
            return null;
        }
        Row from = src instanceof Row rr ? rr : new Row(src);
        Row r = new Row();
        for (Prop p : props(cls)) {
            r.put(p.name(), from.containsKey(p.name()) ? coerce(p.type(), from.get(p.name())) : initial(p));
        }
        return r;
    }

    public List<Row> shapeAll(String cls, Collection<? extends Map<String, ?>> rows) {
        List<Row> out = new ArrayList<>();
        for (Map<String, ?> r : rows) {
            out.add(shape(cls, r));
        }
        return out;
    }

    private static Object initial(Prop p) {
        if (p.init() != null) {
            Object lit = literal(p.init());
            if (lit != null) {
                return lit;
            }
        }
        return defaultOf(p.type());
    }

    private static Object literal(String s) {
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        if (t.equals("true") || t.equals("false")) {
            return Boolean.parseBoolean(t);
        }
        if (t.matches("-?\\d+")) {
            return Integer.parseInt(t);
        }
        if (t.matches("-?\\d+(\\.\\d+)?[mMdD]?")) {
            return new BigDecimal(t.replaceAll("[mMdD]$", ""));
        }
        return null;
    }

    /** Valeur par défaut d'un type C#. */
    public static Object defaultOf(String type) {
        if (type.endsWith("?")) {
            return null;
        }
        return switch (type) {
            case "int", "long", "short", "byte", "uint", "ulong", "ushort", "sbyte" -> 0;
            case "decimal", "double", "float" -> 0;
            case "bool" -> false;
            case "DateTime" -> Conv.MIN_DATE;
            default -> null;
        };
    }

    /** Conversion vers le type C# déclaré (pour garder la même forme JSON). */
    public static Object coerce(String type, Object v) {
        if (v == null) {
            return type.endsWith("?") ? null : defaultOf(type);
        }
        String t = type.endsWith("?") ? type.substring(0, type.length() - 1) : type;
        try {
            return switch (t) {
                case "int", "short", "byte" -> Conv.toInt(v);
                case "long" -> Conv.toLong(v);
                case "decimal" -> Conv.toDecimal(v);
                case "double", "float" -> Conv.toDouble(v);
                case "bool" -> Conv.toBool(v);
                case "DateTime" -> Conv.toDateTime(v);
                case "string" -> v instanceof String ? v : Conv.toStr(v);
                default -> v;
            };
        } catch (RuntimeException e) {
            return v;
        }
    }
}
