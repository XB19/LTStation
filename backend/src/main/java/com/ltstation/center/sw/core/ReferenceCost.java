package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Coût de référence au litre de chaque énergie (cartes en litres) : sert seulement aux rapports (« 1 250 L ≈ 812 500 F »),
 * jamais aux cartes ni aux pompes. Table reference_cost de la base Station, créée au premier usage si elle manque.
 */
public final class ReferenceCost {

    private static volatile boolean ready;

    private ReferenceCost() {
    }

    private static void ensure() {
        if (ready) {
            return;
        }
        synchronized (ReferenceCost.class) {
            if (ready) {
                return;
            }
            Sugar db = SW.db();
            Boolean exists = db.jdbc().getJdbcTemplate().queryForObject("SELECT to_regclass('reference_cost') IS NOT NULL", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                db.jdbc().getJdbcTemplate().execute(
                    "CREATE TABLE IF NOT EXISTS reference_cost (code varchar(20) PRIMARY KEY, cost numeric(14,2) NOT NULL DEFAULT 0, "
                        + "update_time timestamp NOT NULL DEFAULT now())");
            }
            ready = true;
        }
    }

    /** Coûts par code d'énergie (GradeSpecifics.Code). */
    public static Map<String, BigDecimal> all() {
        ensure();
        Map<String, BigDecimal> out = new HashMap<>();
        for (Map<String, Object> r : SW.db().jdbc().queryForList("SELECT code, cost FROM reference_cost", Map.of())) {
            out.put(String.valueOf(r.get("code")), Conv.toDecimal(r.get("cost")));
        }
        return out;
    }

    public static BigDecimal of(String code) {
        return all().getOrDefault(code, BigDecimal.ZERO);
    }

    public static void set(String code, BigDecimal cost) {
        ensure();
        SW.db().jdbc().update("INSERT INTO reference_cost (code, cost, update_time) VALUES (:c, :v, now()) "
            + "ON CONFLICT (code) DO UPDATE SET cost = :v, update_time = now()", Map.of("c", code, "v", cost));
    }

    /** Code d'énergie d'une ligne de GradeSpecifics à partir de son identifiant. */
    public static String codeOfGrade(int gradeSpecificId) {
        Row g = SW.db().first("GradeSpecifics", "{GradeSpecificID} = :id", Sugar.p("id", gradeSpecificId));
        return g == null ? "" : g.str("Code");
    }

    /** Coûts par identifiant d'énergie (OilTransactions.GradeSpecificID). */
    public static Map<Integer, BigDecimal> byGradeId() {
        Map<String, BigDecimal> byCode = all();
        Map<Integer, BigDecimal> out = new HashMap<>();
        for (Row g : SW.db().list("GradeSpecifics")) {
            out.put(g.i("GradeSpecificID"), byCode.getOrDefault(g.str("Code"), BigDecimal.ZERO));
        }
        return out;
    }

    /** Coût estimé d'un volume (0 si aucun coût n'est saisi). */
    public static BigDecimal estimate(Object volume, BigDecimal cost) {
        if (cost == null || cost.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return Conv.toDecimal(volume).multiply(cost).setScale(0, RoundingMode.HALF_UP);
    }
}
