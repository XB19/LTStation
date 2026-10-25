package com.ltstation.center.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Correspondance noms d'origine (SQL Server / modèles C#) et noms PostgreSQL (snake_case),
 * générée par database/tools/*schema_gen.py, et clés déclarées par les modèles SqlSugar
 * (database/tools/entity_keys_gen.py).
 */
public final class Naming {

    public record Table(String orig, String snake, Map<String, String> origToSnake, Map<String, String> snakeToOrig,
                        List<String> pk, List<String> identity) {
    }

    private final Map<String, Table> byOrig = new HashMap<>();
    private final Map<String, Table> bySnake = new HashMap<>();

    public Naming(String name) {
        ObjectMapper om = new ObjectMapper();
        JsonNode names = read(om, "naming/" + name + ".json");
        JsonNode keys = read(om, "naming/" + name + "-keys.json");
        names.get("tables").fields().forEachRemaining(e -> {
            String orig = e.getKey();
            String snake = e.getValue().asText();
            Map<String, String> o2s = new LinkedHashMap<>();
            Map<String, String> s2o = new LinkedHashMap<>();
            names.get("columns").get(orig).fields().forEachRemaining(c -> {
                o2s.put(c.getKey(), c.getValue().asText());
                s2o.put(c.getValue().asText(), c.getKey());
            });
            List<String> pk = new ArrayList<>();
            List<String> ident = new ArrayList<>();
            JsonNode k = keys == null ? null : keys.get(orig);
            if (k != null) {
                k.get("pk").forEach(x -> pk.add(x.asText()));
                k.get("identity").forEach(x -> ident.add(x.asText()));
            }
            Table t = new Table(orig, snake, o2s, s2o, pk, ident);
            byOrig.put(orig.toLowerCase(Locale.ROOT), t);
            bySnake.put(snake, t);
        });
    }

    private static JsonNode read(ObjectMapper om, String path) {
        try (InputStream in = Naming.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : om.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Table par son nom d'origine (casse indifférente) ou par son nom snake_case. */
    public Table table(String name) {
        Table t = byOrig.get(name.toLowerCase(Locale.ROOT));
        if (t == null) {
            t = bySnake.get(name);
        }
        if (t == null) {
            throw new IllegalArgumentException("Table inconnue : " + name);
        }
        return t;
    }

    public boolean has(String name) {
        return byOrig.containsKey(name.toLowerCase(Locale.ROOT)) || bySnake.containsKey(name);
    }
}
