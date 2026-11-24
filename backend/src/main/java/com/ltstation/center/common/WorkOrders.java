package com.ltstation.center.common;

import java.util.Map;

/**
 * Ordre de travail des cartes (table card_work_order, avec historique) et équipement lié, pour les listes et les exports.
 * Alias fixes : wo (card_work_order) et eq (equipment).
 */
public final class WorkOrders {

    /** Colonnes ajoutées au SELECT : n° d'ordre de travail et équipement. */
    public static final String COLUMNS = ", wo.work_order_no, eq.name equipment_name, eq.serial_no equipment_serial";

    private WorkOrders() {
    }

    /** Ordre en vigueur à la date de l'opération (timeCol) pour la carte asnCol. */
    public static String joinAt(String asnCol, String timeCol) {
        return " LEFT JOIN card_work_order wo ON wo.asn = " + asnCol + " AND " + timeCol + " >= wo.valid_from"
                + " AND (wo.valid_to IS NULL OR " + timeCol + " < wo.valid_to)"
                + " LEFT JOIN equipment eq ON eq.id = wo.equipment_id";
    }

    /** Ordre en cours de la carte asnCol. */
    public static String joinCurrent(String asnCol) {
        return " LEFT JOIN card_work_order wo ON wo.asn = " + asnCol + " AND wo.valid_to IS NULL"
                + " LEFT JOIN equipment eq ON eq.id = wo.equipment_id";
    }

    /** Filtres : n° d'ordre de travail (contient, sans tenir compte de la casse) et équipement. */
    public static void filter(StringBuilder where, Map<String, Object> params, String workOrderNo, Integer equipmentId) {
        if (workOrderNo != null && !workOrderNo.isBlank()) {
            where.append(" AND wo.work_order_no ILIKE :woNo");
            params.put("woNo", "%" + workOrderNo.trim() + "%");
        }
        if (equipmentId != null) {
            where.append(" AND wo.equipment_id = :woEq");
            params.put("woEq", equipmentId);
        }
    }

    /** N° d'ordre de travail saisi : espaces superflus retirés, 64 caractères au plus. */
    public static String normalize(String no) {
        if (no == null) {
            return null;
        }
        String s = no.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > 64) {
            throw new ApiException("Le n° d'ordre de travail dépasse 64 caractères");
        }
        return s;
    }
}
