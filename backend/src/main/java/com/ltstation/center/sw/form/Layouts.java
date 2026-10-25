package com.ltstation.center.sw.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mises en page des fenêtres d'origine (station/layouts/*.json) : types de contrôles et colonnes des tableaux. */
public final class Layouts {

    /** Colonne d'un DataGridView : nom, propriété liée (DataPropertyName), en-tête. */
    public record Column(String name, String prop, String header) {
    }

    public record Layout(String name, String title, Map<String, String> types, Map<String, List<Column>> columns,
                         Map<String, Map<String, String>> events) {
    }

    private static final Map<String, Layout> CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper OM = new ObjectMapper();

    private Layouts() {
    }

    public static Layout get(String name) {
        return CACHE.computeIfAbsent(name, Layouts::load);
    }

    private static Layout load(String name) {
        try (InputStream in = Layouts.class.getClassLoader().getResourceAsStream("station/layouts/" + name + ".json")) {
            if (in == null) {
                return new Layout(name, name, Map.of(), Map.of(), Map.of());
            }
            JsonNode root = OM.readTree(in);
            Map<String, String> types = new ConcurrentHashMap<>();
            root.path("controls").forEach(c -> types.put(c.path("name").asText(), c.path("type").asText()));
            Map<String, List<Column>> cols = new ConcurrentHashMap<>();
            root.path("columns").fields().forEachRemaining(e -> {
                List<Column> l = new ArrayList<>();
                e.getValue().forEach(c -> l.add(new Column(c.path("name").asText(),
                    c.path("prop").isNull() || c.path("prop").isMissingNode() ? null : c.path("prop").asText(), c.path("header").asText())));
                cols.put(e.getKey(), l);
            });
            Map<String, Map<String, String>> events = new ConcurrentHashMap<>();
            root.path("events").fields().forEachRemaining(e -> {
                Map<String, String> m = new ConcurrentHashMap<>();
                e.getValue().fields().forEachRemaining(x -> m.put(x.getKey(), x.getValue().asText()));
                events.put(e.getKey(), m);
            });
            return new Layout(name, root.path("title").asText(name), types, cols, events);
        } catch (Exception e) {
            throw new IllegalStateException("Mise en page illisible : " + name, e);
        }
    }
}
