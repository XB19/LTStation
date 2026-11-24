package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Conv;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Équipements et ordres de travail des cartes dans la base du logiciel Station (tables equipment et card_work_order,
 * database/station/schema_extra.sql). Chaque changement d'ordre sur une carte ferme la période en cours et en ouvre
 * une nouvelle : les extractions retrouvent l'ordre en vigueur à la date de chaque transaction.
 */
public final class WorkOrderData {

    public static final Map<Integer, String> STATUS = Map.of(1, "En service", 2, "En maintenance", 3, "Hors service");

    /** Catégories proposées (saisie libre possible). */
    public static final List<String> CATEGORIES = List.of("Portique de quai", "Portique sur pneus (RTG)", "Cavalier", "Reach stacker",
            "Chariot élévateur", "Tracteur de terminal", "Remorque", "Camion", "Véhicule léger", "Groupe électrogène", "Autre");

    /** Cartes de la station : utilisateur, employé, groupe (porteur et immatriculation). */
    public static final String CARDS = "SELECT ci.asn, 'Utilisateur' AS kind, COALESCE(cu.name, ci.name) AS holder, "
            + "COALESCE(ci.vehicle_no, cu.vehicle_no) AS vehicle FROM customer_card_infos ci "
            + "LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
            + "UNION ALL SELECT ec.asn, 'Employé', ec.name, NULL FROM employee_card_infos ec "
            + "UNION ALL SELECT gc.asn, 'Groupe', g.name, NULL FROM group_card_infos gc LEFT JOIN \"group\" g ON g.group_id = gc.group_id";

    /** Ordre en vigueur à la date timeCol pour la carte asnCol (alias wo et eq). */
    public static String joinAt(String asnCol, String timeCol) {
        return " LEFT JOIN card_work_order wo ON wo.asn = " + asnCol + " AND " + timeCol + " >= wo.valid_from"
                + " AND (wo.valid_to IS NULL OR " + timeCol + " < wo.valid_to) LEFT JOIN equipment eq ON eq.id = wo.equipment_id";
    }

    private static volatile boolean ready;

    private WorkOrderData() {
    }

    private static NamedParameterJdbcTemplate jdbc() {
        ensure();
        return SW.db().jdbc();
    }

    /** Crée les tables si la base Station a été installée avant cette fonction. */
    public static void ensure() {
        if (ready) {
            return;
        }
        synchronized (WorkOrderData.class) {
            if (ready) {
                return;
            }
            try (InputStream in = new ClassPathResource("station/db/schema_extra.sql").getInputStream()) {
                SW.db().jdbc().getJdbcTemplate().execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Tables des équipements introuvables : " + e.getMessage());
            }
            ready = true;
        }
    }

    public static Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static List<Map<String, Object>> list(String sql, Map<String, ?> params) {
        return jdbc().queryForList(sql, params);
    }

    public static Map<String, Object> one(String sql, Map<String, ?> params) {
        List<Map<String, Object>> l = list(sql, params);
        return l.isEmpty() ? null : l.get(0);
    }

    public static int exec(String sql, Map<String, ?> params) {
        return jdbc().update(sql, params);
    }

    // ------------------------------------------------------------ équipements

    public static final String EQUIPMENT = "SELECT e.*, g.name AS grade_name, (SELECT count(*) FROM card_work_order w "
            + "WHERE w.equipment_id = e.id AND w.valid_to IS NULL) AS card_count FROM equipment e "
            + "LEFT JOIN LATERAL (SELECT name FROM grade_specifics x WHERE CAST(x.code AS text) = e.grade_code LIMIT 1) g ON true";

    public static List<Map<String, Object>> equipments(String q) {
        String s = q == null ? "" : q.trim();
        return list(EQUIPMENT + " WHERE (:q = '' OR e.name ILIKE :like OR e.work_order_no ILIKE :like OR e.serial_no ILIKE :like "
                + "OR e.fleet_no ILIKE :like OR e.category ILIKE :like OR EXISTS (SELECT 1 FROM card_work_order w WHERE w.equipment_id = e.id "
                + "AND w.valid_to IS NULL AND w.asn LIKE :suffix)) ORDER BY e.name, e.id", p("q", s, "like", "%" + s + "%", "suffix", "%" + s));
    }

    public static Map<String, Object> equipment(int id) {
        Map<String, Object> e = one(EQUIPMENT + " WHERE e.id = :id", p("id", id));
        if (e == null) {
            throw new IllegalStateException("Équipement introuvable");
        }
        return e;
    }

    /** Fiche d'équipement saisie (champs vides = null). */
    public record Equipment(String name, String workOrderNo, String category, String brand, String model, String serialNo, String fleetNo,
                            String gradeCode, BigDecimal tankCapacity, int status, String memo) {
    }

    /** Contrôle et nettoie la saisie ; id = équipement modifié (null en création). */
    public static Equipment clean(Equipment f, Integer id) {
        String name = text(f.name(), 100, "Désignation");
        if (name == null) {
            throw new IllegalStateException("La désignation est obligatoire");
        }
        String serial = text(f.serialNo(), 64, "N° de série");
        if (serial != null && one("SELECT 1 FROM equipment WHERE serial_no = :s AND id <> :id", p("s", serial, "id", id == null ? -1 : id)) != null) {
            throw new IllegalStateException("Un autre équipement porte déjà le n° de série " + serial);
        }
        if (!STATUS.containsKey(f.status())) {
            throw new IllegalStateException("Statut invalide");
        }
        if (f.tankCapacity() != null && f.tankCapacity().signum() < 0) {
            throw new IllegalStateException("La capacité du réservoir ne peut pas être négative");
        }
        return new Equipment(name, normalize(f.workOrderNo()), text(f.category(), 50, "Catégorie"), text(f.brand(), 50, "Marque"),
                text(f.model(), 50, "Modèle"), serial, text(f.fleetNo(), 30, "N° de parc"), text(f.gradeCode(), 10, "Énergie"),
                f.tankCapacity(), f.status(), f.memo() == null || f.memo().isBlank() ? null : f.memo().trim());
    }

    private static MapSqlParameterSource params(Equipment f) {
        return new MapSqlParameterSource().addValue("n", f.name()).addValue("wo", f.workOrderNo()).addValue("cat", f.category())
                .addValue("b", f.brand()).addValue("m", f.model()).addValue("sn", f.serialNo()).addValue("fn", f.fleetNo())
                .addValue("g", f.gradeCode()).addValue("cap", f.tankCapacity()).addValue("s", f.status()).addValue("memo", f.memo())
                .addValue("by", IniInfo.LoginName);
    }

    public static int insert(Equipment f) {
        Integer id = jdbc().queryForObject("INSERT INTO equipment (name, work_order_no, category, brand, model, serial_no, fleet_no, "
                + "grade_code, tank_capacity, status, memo, create_by) VALUES (:n, :wo, :cat, :b, :m, :sn, :fn, :g, :cap, :s, :memo, :by) "
                + "RETURNING id", params(f), Integer.class);
        return Objects.requireNonNull(id);
    }

    /** Modification ; un nouveau n° d'ordre est reporté sur les cartes rattachées. Renvoie le nombre de cartes passées au nouvel ordre. */
    public static int update(int id, Equipment f) {
        Map<String, Object> old = equipment(id);
        jdbc().update("UPDATE equipment SET name = :n, work_order_no = :wo, category = :cat, brand = :b, model = :m, serial_no = :sn, "
                + "fleet_no = :fn, grade_code = :g, tank_capacity = :cap, status = :s, memo = :memo, update_time = now(), update_by = :by "
                + "WHERE id = :id", params(f).addValue("id", id));
        if (f.workOrderNo() != null && !Objects.equals(old.get("work_order_no"), f.workOrderNo())) {
            return followEquipment(id, f.workOrderNo());
        }
        return 0;
    }

    /** Suppression : les cartes rattachées perdent leur ordre à cette date, l'historique est conservé. */
    public static void delete(int id) {
        exec("UPDATE card_work_order SET valid_to = now() WHERE equipment_id = :id AND valid_to IS NULL", p("id", id));
        exec("DELETE FROM equipment WHERE id = :id", p("id", id));
    }

    // ------------------------------------------------------------ cartes

    /**
     * Recherche de cartes : n° complet, 5 derniers chiffres (ou plus), nom du porteur ou immatriculation.
     * Plusieurs cartes peuvent finir par les mêmes chiffres : la liste est renvoyée pour que l'utilisateur choisisse.
     */
    public static List<Map<String, Object>> searchCards(String q) {
        String s = q == null ? "" : q.trim();
        boolean digits = s.matches("[0-9A-Fa-f]+");
        if (s.isEmpty()) {
            // Sans critère : cartes qui ont un ordre de travail en cours
            return list("SELECT c.*, wo.work_order_no, eq.name AS equipment_name, wo.valid_from FROM (" + CARDS + ") c "
                    + "JOIN card_work_order wo ON wo.asn = c.asn AND wo.valid_to IS NULL LEFT JOIN equipment eq ON eq.id = wo.equipment_id "
                    + "ORDER BY wo.valid_from DESC LIMIT 200", p());
        }
        if (digits && s.length() < 5) {
            throw new IllegalStateException("Saisissez au moins les 5 derniers chiffres de la carte");
        }
        String where = digits ? "(c.asn = :q OR c.asn LIKE :suffix)" : "(c.holder ILIKE :like OR c.vehicle ILIKE :like)";
        return list("SELECT c.*, wo.work_order_no, eq.name AS equipment_name, wo.valid_from FROM (" + CARDS + ") c "
                + "LEFT JOIN card_work_order wo ON wo.asn = c.asn AND wo.valid_to IS NULL LEFT JOIN equipment eq ON eq.id = wo.equipment_id "
                + "WHERE " + where + " ORDER BY c.asn LIMIT 100", p("q", s, "suffix", "%" + s, "like", "%" + s + "%"));
    }

    public static Map<String, Object> card(String asn) {
        Map<String, Object> c = one("SELECT * FROM (" + CARDS + ") c WHERE c.asn = :a LIMIT 1", p("a", asn == null ? "" : asn.trim()));
        if (c == null) {
            throw new IllegalStateException("Carte introuvable : " + asn);
        }
        return c;
    }

    public static List<Map<String, Object>> history(String asn) {
        return list("SELECT wo.*, eq.name AS equipment_name FROM card_work_order wo LEFT JOIN equipment eq ON eq.id = wo.equipment_id "
                + "WHERE wo.asn = :a ORDER BY wo.valid_from DESC, wo.id DESC", p("a", asn));
    }

    /**
     * Nouvel ordre de travail pour une carte, en vigueur à partir de validFrom (maintenant par défaut).
     * Sans n° saisi, l'ordre de l'équipement choisi est repris. L'ordre précédent prend fin à cette date.
     */
    public static void assign(String asn, String workOrderNo, Integer equipmentId, LocalDateTime validFrom, String memo) {
        assign(asn, workOrderNo, equipmentId, validFrom, memo, true);
    }

    /**
     * checkCard = false pour une carte que la station vient de créer sur Center et qui n'est pas encore recopiée
     * dans ses tables de cartes (création et mise à jour des cartes employé).
     */
    public static void assign(String asn, String workOrderNo, Integer equipmentId, LocalDateTime validFrom, String memo, boolean checkCard) {
        String a = checkCard ? (String) card(asn).get("asn") : asn == null ? "" : asn.trim();
        if (a.isEmpty()) {
            throw new IllegalStateException("N° de carte manquant");
        }
        String no = normalize(workOrderNo);
        if (equipmentId != null && no == null) {
            no = (String) equipment(equipmentId).get("work_order_no");
        }
        if (no == null && equipmentId == null) {
            throw new IllegalStateException("Saisissez un n° d'ordre de travail ou choisissez un équipement");
        }
        LocalDateTime from = validFrom == null ? LocalDateTime.now() : validFrom;
        Map<String, Object> current = one("SELECT id, valid_from, work_order_no, equipment_id FROM card_work_order WHERE asn = :a AND valid_to IS NULL",
                p("a", a));
        if (current != null) {
            if (Objects.equals(no, current.get("work_order_no")) && Objects.equals(equipmentId, current.get("equipment_id"))
                    && (memo == null || memo.isBlank())) {
                return;
            }
            LocalDateTime start = ((java.sql.Timestamp) current.get("valid_from")).toLocalDateTime();
            if (!from.isAfter(start)) {
                throw new IllegalStateException("La date d'effet doit être postérieure au début de l'ordre en cours ("
                        + start.format(Conv.STD) + ")");
            }
            exec("UPDATE card_work_order SET valid_to = :t WHERE id = :id", p("t", from, "id", current.get("id")));
        } else if (one("SELECT 1 FROM card_work_order WHERE asn = :a AND valid_to > :t", p("a", a, "t", from)) != null) {
            throw new IllegalStateException("Un ordre de travail précédent couvre déjà cette date : choisissez une date plus récente");
        }
        exec("INSERT INTO card_work_order (asn, work_order_no, equipment_id, valid_from, memo, create_by) VALUES (:a, :no, :eq, :f, :m, :by)",
                p("a", a, "no", no, "eq", equipmentId, "f", from, "m", memo == null || memo.isBlank() ? null : memo.trim(), "by", IniInfo.LoginName));
    }

    /** Ordre en cours d'une carte (null s'il n'y en a pas). */
    public static Map<String, Object> current(String asn) {
        return one("SELECT wo.*, eq.name AS equipment_name FROM card_work_order wo LEFT JOIN equipment eq ON eq.id = wo.equipment_id "
                + "WHERE wo.asn = :a AND wo.valid_to IS NULL", p("a", asn == null ? "" : asn.trim()));
    }

    /** Équipements pour une liste déroulante : ID (0 = aucun) et libellé « désignation · OT n° ». */
    public static List<com.ltstation.center.legacy.Row> equipmentItems() {
        List<com.ltstation.center.legacy.Row> items = new java.util.ArrayList<>();
        items.add(com.ltstation.center.legacy.Row.of("ID", 0, "Text", ""));
        for (Map<String, Object> e : equipments("")) {
            String no = str(e.get("work_order_no"));
            items.add(com.ltstation.center.legacy.Row.of("ID", e.get("id"), "Text", e.get("name") + (no.isEmpty() ? "" : " · OT " + no)));
        }
        return items;
    }

    /** Fin de l'ordre en cours d'une carte. */
    public static void end(String asn) {
        if (exec("UPDATE card_work_order SET valid_to = now() WHERE asn = :a AND valid_to IS NULL", p("a", asn)) == 0) {
            throw new IllegalStateException("Cette carte n'a pas d'ordre de travail en cours");
        }
    }

    /** Nouvel ordre sur un équipement : ses cartes passent au nouvel ordre, l'ancien reste dans l'historique. */
    public static int followEquipment(int equipmentId, String newNo) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> current = list("SELECT id, asn, memo FROM card_work_order WHERE equipment_id = :e AND valid_to IS NULL "
                + "AND work_order_no IS DISTINCT FROM :no", p("e", equipmentId, "no", newNo));
        for (Map<String, Object> c : current) {
            exec("UPDATE card_work_order SET valid_to = :t WHERE id = :id", p("t", now, "id", c.get("id")));
            exec("INSERT INTO card_work_order (asn, work_order_no, equipment_id, valid_from, memo, create_by) VALUES (:a, :no, :e, :t, :m, :by)",
                    p("a", c.get("asn"), "no", newNo, "e", equipmentId, "t", now, "m", c.get("memo"), "by", IniInfo.LoginName));
        }
        return current.size();
    }

    /** Ordre de travail en vigueur pour chaque (carte, date) d'une liste de transactions : clé « asn|date ». */
    public static Map<String, String[]> at(List<? extends Map<String, ?>> rows, String asnKey, String timeKey) {
        Map<String, String[]> out = new HashMap<>();
        List<String> asns = rows.stream().map(r -> r.get(asnKey)).filter(Objects::nonNull).map(Object::toString).distinct().toList();
        if (asns.isEmpty()) {
            return out;
        }
        List<Map<String, Object>> periods = list("SELECT wo.asn, wo.work_order_no, eq.name AS equipment_name, wo.valid_from, wo.valid_to "
                + "FROM card_work_order wo LEFT JOIN equipment eq ON eq.id = wo.equipment_id WHERE wo.asn IN (:a)", p("a", asns));
        for (Map<String, ?> r : rows) {
            Object asn = r.get(asnKey);
            LocalDateTime t = Conv.toDateTime(r.get(timeKey));
            if (asn == null || t == null) {
                continue;
            }
            for (Map<String, Object> w : periods) {
                LocalDateTime from = ((java.sql.Timestamp) w.get("valid_from")).toLocalDateTime();
                LocalDateTime to = w.get("valid_to") == null ? null : ((java.sql.Timestamp) w.get("valid_to")).toLocalDateTime();
                if (asn.toString().equals(w.get("asn")) && !t.isBefore(from) && (to == null || t.isBefore(to))) {
                    out.put(asn + "|" + t, new String[] {(String) w.get("work_order_no"), (String) w.get("equipment_name")});
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ consommation

    /** Consommation par ordre de travail / équipement et énergie, d'après l'ordre en vigueur à chaque transaction. */
    public static List<Map<String, Object>> consumption(LocalDateTime start, LocalDateTime end, String workOrderNo) {
        String no = workOrderNo == null ? "" : workOrderNo.trim();
        return list("SELECT wo.work_order_no, eq.name AS equipment_name, eq.category, eq.serial_no, g.name AS oil_name, "
                + "count(*) AS transaction_count, count(DISTINCT o.asn) AS card_count, COALESCE(sum(o.volume), 0) AS volume, "
                + "COALESCE(sum(o.truely_pay), 0) AS amount, min(o.true_time) AS first_time, max(o.true_time) AS last_time "
                + "FROM oil_transactions o" + joinAt("o.asn", "o.true_time")
                + " LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id "
                + "WHERE wo.id IS NOT NULL AND o.true_time BETWEEN :s AND :e AND (:no = '' OR wo.work_order_no ILIKE :like) "
                + "GROUP BY wo.work_order_no, eq.name, eq.category, eq.serial_no, g.name ORDER BY amount DESC",
                p("s", start, "e", end, "no", no, "like", "%" + no + "%"));
    }

    // ------------------------------------------------------------ utilitaires

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
            throw new IllegalStateException("Le n° d'ordre de travail dépasse 64 caractères");
        }
        return s;
    }

    private static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim();
        if (s.length() > max) {
            throw new IllegalStateException("« " + field + " » dépasse " + max + " caractères");
        }
        return s;
    }

    public static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
