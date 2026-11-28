package com.ltstation.center.common;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cartes en litres (CARTES_EN_LITRES=1, par défaut) : le solde d'une carte est un nombre de litres. Le boîtier de la pompe
 * retire « volume × prix » : avec un prix de 1, il retire exactement les litres servis. Le prix de chaque énergie est donc
 * verrouillé à 1 (application et Center) et les prix spéciaux sont refusés ; le coût réel ne sert qu'aux rapports
 * (coût de référence au litre). CARTES_EN_LITRES=0 rend le fonctionnement d'origine (soldes en argent).
 */
@Component
public class Litres {

    public static final String PRICE_LOCKED = "Les cartes fonctionnent en litres : le prix reste à 1";
    public static final String SPECIAL_PRICE_LOCKED = "Les cartes fonctionnent en litres : pas de prix spécial";

    private static volatile boolean on = true;

    public Litres(@Value("${app.cartes-en-litres:1}") String value) {
        String v = value == null ? "" : value.trim();
        on = !("0".equals(v) || "false".equalsIgnoreCase(v) || "non".equalsIgnoreCase(v));
    }

    public static boolean on() {
        return on;
    }

    /** Vrai si ce prix est refusé (cartes en litres et prix différent de 1). */
    public static boolean refused(Object price) {
        if (!on) {
            return false;
        }
        if (price == null || String.valueOf(price).isBlank()) {
            return true;
        }
        try {
            return new BigDecimal(String.valueOf(price).trim()).compareTo(BigDecimal.ONE) != 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
