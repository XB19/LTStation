package com.ltstation.center.service;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.WorkOrders;
import com.ltstation.center.security.SessionUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Ordre de travail des cartes (card_work_order). Chaque changement ferme la période en cours et en ouvre une nouvelle,
 * pour que les extractions retrouvent l'ordre en vigueur à la date de chaque opération.
 */
@Service
public class CardWorkOrderService {

    /** Toutes les cartes (utilisateur, employé, groupe) avec porteur, station et état. */
    public static final String CARDS = "SELECT ci.asn, 'Utilisateur' kind, cu.name holder, ci.station_id, ci.card_status_type_id "
            + "FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
            + "UNION ALL SELECT ec.asn, 'Employé', e.name, COALESCE(ec.station_id, ec.open_station_id), ec.card_status_type_id "
            + "FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id "
            + "UNION ALL SELECT gc.asn, 'Groupe', g.name, gc.station_id, gc.card_status_type_id "
            + "FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id";

    private final Db db;
    private final CompanyScopeService scope;

    public CardWorkOrderService(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /**
     * Recherche de cartes du périmètre : n° complet, 5 derniers chiffres (ou plus), ou nom du porteur.
     * Plusieurs cartes peuvent finir par les mêmes chiffres : la liste est renvoyée pour que l'utilisateur choisisse.
     */
    public List<Map<String, Object>> search(SessionUser user, String q) {
        String s = q == null ? "" : q.trim();
        if (s.length() < 3) {
            return List.of();
        }
        boolean digits = s.matches("[0-9A-Fa-f]+");
        if (digits && s.length() < 5) {
            throw new ApiException("Saisissez au moins les 5 derniers chiffres de la carte");
        }
        String where = digits ? "(c.asn = :q OR c.asn LIKE :suffix)" : "c.holder ILIKE :name";
        List<Map<String, Object>> rows = db.list("SELECT c.asn, c.kind, c.holder, c.card_status_type_id, s.name station_name"
                + WorkOrders.COLUMNS + ", wo.equipment_id, wo.valid_from FROM (" + CARDS + ") c "
                + "LEFT JOIN stations s ON s.station_id = c.station_id" + WorkOrders.joinCurrent("c.asn")
                + " WHERE c.station_id IN (:st) AND " + where + " ORDER BY c.asn LIMIT 30",
                Db.p("st", scope.stationIds(user), "q", s, "suffix", "%" + s, "name", "%" + s + "%"));
        rows.forEach(r -> r.put("cardStatus", Labels.cardStatus(r.get("cardStatusTypeId"))));
        return rows;
    }

    /** Carte du périmètre (erreur sinon). */
    public Map<String, Object> card(SessionUser user, String asn) {
        Map<String, Object> c = db.one("SELECT c.asn, c.kind, c.holder, c.card_status_type_id, s.name station_name FROM (" + CARDS + ") c "
                + "LEFT JOIN stations s ON s.station_id = c.station_id WHERE c.asn = :a AND c.station_id IN (:st) LIMIT 1",
                Db.p("a", asn == null ? "" : asn.trim(), "st", scope.stationIds(user)));
        if (c == null) {
            throw new ApiException("Carte introuvable dans votre périmètre : " + asn);
        }
        c.put("cardStatus", Labels.cardStatus(c.get("cardStatusTypeId")));
        return c;
    }

    /** Historique des ordres de travail d'une carte, le plus récent d'abord. */
    public List<Map<String, Object>> history(String asn) {
        return db.list("SELECT wo.id, wo.asn, wo.work_order_no, wo.equipment_id, eq.name equipment_name, eq.serial_no equipment_serial, "
                + "wo.valid_from, wo.valid_to, wo.memo, wo.create_time, wo.create_by FROM card_work_order wo "
                + "LEFT JOIN equipment eq ON eq.id = wo.equipment_id WHERE wo.asn = :a ORDER BY wo.valid_from DESC, wo.id DESC",
                Map.of("a", asn));
    }

    /**
     * Nouvel ordre de travail pour une carte, en vigueur à partir de validFrom (maintenant par défaut).
     * Sans n° saisi, l'ordre de l'équipement choisi est repris. L'ordre précédent prend fin à cette date.
     */
    public void assign(SessionUser user, String asn, String workOrderNo, Integer equipmentId, LocalDateTime validFrom, String memo) {
        Map<String, Object> card = card(user, asn);
        String a = (String) card.get("asn");
        String no = WorkOrders.normalize(workOrderNo);
        if (equipmentId != null) {
            Map<String, Object> eq = db.one("SELECT work_order_no FROM equipment WHERE id = :id AND company_id IN (:n)",
                    Db.p("id", equipmentId, "n", scope.nodeList(user)));
            if (eq == null) {
                throw new ApiException("Équipement introuvable dans votre périmètre");
            }
            if (no == null) {
                no = (String) eq.get("workOrderNo");
            }
        }
        if (no == null && equipmentId == null) {
            throw new ApiException("Saisissez un n° d'ordre de travail ou choisissez un équipement");
        }
        LocalDateTime from = validFrom == null ? LocalDateTime.now() : validFrom;
        Map<String, Object> current = db.one("SELECT id, valid_from, work_order_no, equipment_id FROM card_work_order "
                + "WHERE asn = :a AND valid_to IS NULL", Map.of("a", a));
        if (current != null) {
            if (no != null && no.equals(current.get("workOrderNo")) && java.util.Objects.equals(equipmentId, current.get("equipmentId"))
                    && memo == null) {
                return;
            }
            if (!from.isAfter((LocalDateTime) current.get("validFrom"))) {
                throw new ApiException("La date d'effet doit être postérieure au début de l'ordre en cours ("
                        + current.get("validFrom").toString().replace('T', ' ') + ")");
            }
            db.update("UPDATE card_work_order SET valid_to = :t WHERE id = :id", Db.p("t", from, "id", current.get("id")));
        } else if (db.one("SELECT 1 FROM card_work_order WHERE asn = :a AND valid_to > :t", Map.of("a", a, "t", from)) != null) {
            throw new ApiException("Un ordre de travail précédent couvre déjà cette date : choisissez une date plus récente");
        }
        db.update("INSERT INTO card_work_order (asn, work_order_no, equipment_id, valid_from, memo, create_by) "
                + "VALUES (:a, :no, :eq, :f, :m, :by)",
                Db.p("a", a, "no", no, "eq", equipmentId, "f", from, "m", memo == null || memo.isBlank() ? null : memo.trim(),
                        "by", user.getLoginName()));
    }

    /** Fin de l'ordre de travail en cours d'une carte. */
    public void end(SessionUser user, String asn, LocalDateTime at) {
        String a = (String) card(user, asn).get("asn");
        LocalDateTime t = at == null ? LocalDateTime.now() : at;
        Map<String, Object> current = db.one("SELECT id, valid_from FROM card_work_order WHERE asn = :a AND valid_to IS NULL", Map.of("a", a));
        if (current == null) {
            throw new ApiException("Cette carte n'a pas d'ordre de travail en cours");
        }
        if (!t.isAfter((LocalDateTime) current.get("validFrom"))) {
            throw new ApiException("La date de fin doit être postérieure au début de l'ordre");
        }
        db.update("UPDATE card_work_order SET valid_to = :t WHERE id = :id", Db.p("t", t, "id", current.get("id")));
    }

    /**
     * Nouvel ordre de travail sur un équipement : les cartes rattachées à cet équipement passent au nouvel ordre
     * (période close maintenant, nouvelle période ouverte), l'historique garde l'ancien.
     */
    public int followEquipment(SessionUser user, int equipmentId, String newNo) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> current = db.list("SELECT id, asn, memo FROM card_work_order WHERE equipment_id = :e AND valid_to IS NULL "
                + "AND work_order_no IS DISTINCT FROM :no", Db.p("e", equipmentId, "no", newNo));
        for (Map<String, Object> c : current) {
            db.update("UPDATE card_work_order SET valid_to = :t WHERE id = :id", Db.p("t", now, "id", c.get("id")));
            db.update("INSERT INTO card_work_order (asn, work_order_no, equipment_id, valid_from, memo, create_by) "
                    + "VALUES (:a, :no, :e, :t, :m, :by)",
                    Db.p("a", c.get("asn"), "no", newNo, "e", equipmentId, "t", now, "m", c.get("memo"), "by", user.getLoginName()));
        }
        return current.size();
    }
}
