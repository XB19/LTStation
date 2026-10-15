package com.ltstation.center.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Catalogue des droits de menu PC et mobile (dash/json/menu_list.json d'origine). */
@Component
public class MenuCatalog {

    public record MenuEntry(String label, int value) {
    }

    private final List<MenuEntry> pc = new ArrayList<>();
    private final List<MenuEntry> phone = new ArrayList<>();

    public MenuCatalog(ObjectMapper mapper) throws IOException {
        try (InputStream in = new ClassPathResource("menu_list.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            root.path("pc").forEach(n -> pc.add(new MenuEntry(n.path("label").asText(), n.path("value").asInt())));
            root.path("phone").forEach(n -> phone.add(new MenuEntry(n.path("label").asText(), n.path("value").asInt())));
        }
    }

    public List<MenuEntry> pc() {
        return pc;
    }

    public List<MenuEntry> phone() {
        return phone;
    }

    /** Tous les droits PC, séparés par des virgules (droits du super administrateur). */
    public String allPcAuth() {
        return String.join(",", pc.stream().map(e -> String.valueOf(e.value())).toList());
    }

    public String allPhoneAuth() {
        return String.join(",", phone.stream().map(e -> String.valueOf(e.value())).toList());
    }
}
