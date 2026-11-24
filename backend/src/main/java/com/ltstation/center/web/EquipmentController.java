package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.ExcelImport;
import com.ltstation.center.common.ExcelImport.Column;
import com.ltstation.center.common.ExcelImport.Line;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.WorkOrders;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardWorkOrderService;
import com.ltstation.center.service.CompanyScopeService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Équipements (engins, véhicules, machines) et leur ordre de travail, propres à LTStation.
 * Les cartes sont rattachées à un équipement par card_work_order (voir CardWorkOrderController).
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    /** Droit de menu « Équipements et ordres de travail » (menu_list.json). */
    static final String RIGHT = "57";

    public static final Map<Integer, String> STATUS = Map.of(1, "En service", 2, "En maintenance", 3, "Hors service");

    /** Catégories proposées (saisie libre possible). */
    static final List<String> CATEGORIES = List.of("Portique de quai", "Portique sur pneus (RTG)", "Cavalier", "Reach stacker",
            "Chariot élévateur", "Tracteur de terminal", "Remorque", "Camion", "Véhicule léger", "Groupe électrogène", "Autre");

    private final Db db;
    private final CompanyScopeService scope;
    private final CardWorkOrderService workOrders;

    public EquipmentController(Db db, CompanyScopeService scope, CardWorkOrderService workOrders) {
        this.db = db;
        this.scope = scope;
        this.workOrders = workOrders;
    }

    public record Filter(String q, String category, Integer status, Integer stationId) {
    }

    private static final String SELECT = "SELECT e.id, e.name, e.work_order_no, e.category, e.brand, e.model, e.serial_no, e.fleet_no, "
            + "e.grade_code, g.name grade_name, e.tank_capacity, e.status, e.station_id, s.name station_name, e.memo, "
            + "e.create_time, e.create_by, e.update_time, e.update_by, "
            + "(SELECT count(*) FROM card_work_order w WHERE w.equipment_id = e.id AND w.valid_to IS NULL) card_count "
            + "FROM equipment e LEFT JOIN stations s ON s.station_id = e.station_id "
            + "LEFT JOIN LATERAL (SELECT name FROM grade_specifics x WHERE x.code = e.grade_code LIMIT 1) g ON true";

    private String where(SessionUser user, Filter f, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" WHERE e.company_id IN (:nodes)");
        p.put("nodes", scope.nodeList(user));
        if (f.q() != null && !f.q().isBlank()) {
            w.append(" AND (e.name ILIKE :q OR e.work_order_no ILIKE :q OR e.serial_no ILIKE :q OR e.fleet_no ILIKE :q "
                    + "OR e.brand ILIKE :q OR e.model ILIKE :q OR EXISTS (SELECT 1 FROM card_work_order w WHERE w.equipment_id = e.id "
                    + "AND w.valid_to IS NULL AND w.asn LIKE :card))");
            p.put("q", "%" + f.q().trim() + "%");
            p.put("card", "%" + f.q().trim());
        }
        if (f.category() != null && !f.category().isBlank()) {
            w.append(" AND e.category = :cat");
            p.put("cat", f.category());
        }
        if (f.status() != null) {
            w.append(" AND e.status = :st");
            p.put("st", f.status());
        }
        if (f.stationId() != null) {
            w.append(" AND e.station_id = :sid");
            p.put("sid", f.stationId());
        }
        return w.toString();
    }

    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user, @ModelAttribute Filter f,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = new HashMap<>();
        PageResult<Map<String, Object>> r = db.page(SELECT + where(user, f, p), p, "e.name, e.id", page, size);
        r.rows().forEach(EquipmentController::decorate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        return out;
    }

    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Filter f, HttpServletResponse response) throws IOException {
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list(SELECT + where(user, f, p) + " ORDER BY e.name, e.id", p);
        rows.forEach(EquipmentController::decorate);
        ExcelExport.write(response, "Equipements", "Équipements", List.of(
                Col.of("Désignation", "name"), Col.of("Ordre de travail", "workOrderNo"), Col.of("Catégorie", "category"),
                Col.of("Marque", "brand"), Col.of("Modèle", "model"), Col.of("N° de série", "serialNo"),
                Col.of("N° de parc / immatriculation", "fleetNo"), Col.of("Énergie", "gradeName"),
                Col.of("Capacité du réservoir (L)", "tankCapacity"), Col.of("Station", "stationName"), Col.of("Statut", "statusName"),
                Col.of("Cartes rattachées", "cardCount"), Col.of("Observations", "memo"), Col.of("Créé le", "createTime"),
                Col.of("Créé par", "createBy")), rows);
    }

    /** Détail : équipement, cartes rattachées en cours et historique de ses ordres de travail sur les cartes. */
    @GetMapping("/{id}")
    public Map<String, Object> get(@CurrentUser SessionUser user, @PathVariable int id) {
        Map<String, Object> e = load(user, id);
        e.put("cards", db.list("SELECT wo.asn, c.kind, c.holder, wo.work_order_no, wo.valid_from, wo.memo FROM card_work_order wo "
                + "LEFT JOIN (" + CardWorkOrderService.CARDS + ") c ON c.asn = wo.asn "
                + "WHERE wo.equipment_id = :id AND wo.valid_to IS NULL ORDER BY wo.asn", Map.of("id", id)));
        return e;
    }

    /** Équipements du périmètre pour une liste déroulante (désignation · ordre de travail · n° de série). */
    @GetMapping("/options")
    public List<Map<String, Object>> options(@CurrentUser SessionUser user, @RequestParam(defaultValue = "") String q) {
        return db.list("SELECT e.id, e.name, e.work_order_no, e.serial_no, e.fleet_no FROM equipment e "
                + "WHERE e.company_id IN (:n) AND (:q = '' OR e.name ILIKE :like OR e.work_order_no ILIKE :like "
                + "OR e.serial_no ILIKE :like OR e.fleet_no ILIKE :like) ORDER BY e.name LIMIT 50",
                Db.p("n", scope.nodeList(user), "q", q.trim(), "like", "%" + q.trim() + "%"));
    }

    /** Catégories : liste proposée + catégories déjà saisies. */
    @GetMapping("/categories")
    public Set<String> categories(@CurrentUser SessionUser user) {
        Set<String> out = new LinkedHashSet<>(CATEGORIES);
        out.addAll(db.jdbc().queryForList("SELECT DISTINCT category FROM equipment WHERE company_id IN (:n) AND category <> '' "
                + "ORDER BY 1", Map.of("n", scope.nodeList(user)), String.class));
        return out;
    }

    public record EquipmentForm(String name, String workOrderNo, String category, String brand, String model, String serialNo,
                                String fleetNo, String gradeCode, BigDecimal tankCapacity, Integer stationId, Integer status,
                                String memo) {
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@CurrentUser SessionUser user, @RequestBody EquipmentForm f) {
        checkRight(user);
        int id = insert(user, clean(user, f, null));
        return get(user, id);
    }

    /** Modification ; un nouveau n° d'ordre de travail est reporté sur les cartes rattachées (l'ancien reste dans l'historique). */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody EquipmentForm f) {
        checkRight(user);
        Map<String, Object> old = load(user, id);
        EquipmentForm c = clean(user, f, id);
        write(user, id, c);
        Map<String, Object> out = get(user, id);
        if (!Objects.equals(old.get("workOrderNo"), c.workOrderNo()) && c.workOrderNo() != null) {
            out.put("cardsUpdated", workOrders.followEquipment(user, id, c.workOrderNo()));
        }
        return out;
    }

    /** Suppression ; les cartes rattachées en cours perdent leur ordre de travail à cette date, l'historique est conservé. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        checkRight(user);
        load(user, id);
        db.update("UPDATE card_work_order SET valid_to = now() WHERE equipment_id = :id AND valid_to IS NULL", Map.of("id", id));
        db.update("DELETE FROM equipment WHERE id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------------ Consommation

    public record ConsumptionFilter(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                    String workOrderNo, Integer equipmentId, String category) {
    }

    private static final String CONSUMPTION = "SELECT wo.work_order_no, eq.id equipment_id, eq.name equipment_name, eq.category, "
            + "eq.serial_no, g.name oil_name, count(*) transaction_count, count(DISTINCT o.asn) card_count, "
            + "COALESCE(sum(o.volume), 0) volume, COALESCE(sum(o.truely_pay), 0) amount, min(o.true_time) first_time, "
            + "max(o.true_time) last_time FROM oil_transactions o" + WorkOrders.joinAt("o.asn", "o.true_time")
            + " LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id";

    private String consumptionWhere(SessionUser user, ConsumptionFilter f, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" WHERE wo.id IS NOT NULL AND o.station_id IN (:s) AND o.true_time BETWEEN :start AND :end");
        p.put("s", scope.stationIds(user));
        p.put("start", f.start() == null ? LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay() : f.start());
        p.put("end", f.end() == null ? LocalDateTime.now() : f.end());
        WorkOrders.filter(w, p, f.workOrderNo(), f.equipmentId());
        if (f.category() != null && !f.category().isBlank()) {
            w.append(" AND eq.category = :cat");
            p.put("cat", f.category());
        }
        return w + " GROUP BY wo.work_order_no, eq.id, eq.name, eq.category, eq.serial_no, g.name";
    }

    /** Consommation de carburant par ordre de travail / équipement et énergie, d'après l'ordre en vigueur à chaque transaction. */
    @GetMapping("/consumption")
    public Map<String, Object> consumption(@CurrentUser SessionUser user, @ModelAttribute ConsumptionFilter f,
                                           @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = new HashMap<>();
        String sql = CONSUMPTION + consumptionWhere(user, f, p);
        PageResult<Map<String, Object>> r = db.page(sql, p, "amount DESC", page, size);
        Map<String, Object> totals = db.one("SELECT COALESCE(sum(volume), 0) volume, COALESCE(sum(amount), 0) amount, "
                + "COALESCE(sum(transaction_count), 0) transaction_count FROM (" + sql + ") t", p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        out.put("totalVolume", totals.get("volume"));
        out.put("totalAmount", totals.get("amount"));
        out.put("totalTransactions", totals.get("transactionCount"));
        return out;
    }

    @GetMapping("/consumption/export")
    public void consumptionExport(@CurrentUser SessionUser user, @ModelAttribute ConsumptionFilter f, HttpServletResponse response)
            throws IOException {
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list(CONSUMPTION + consumptionWhere(user, f, p) + " ORDER BY amount DESC", p);
        ExcelExport.write(response, "Consommation_par_ordre_de_travail", "Consommation", List.of(
                Col.of("Ordre de travail", "workOrderNo"), Col.of("Équipement", "equipmentName"), Col.of("Catégorie", "category"),
                Col.of("N° de série", "serialNo"), Col.of("Énergie", "oilName"), Col.of("Transactions", "transactionCount"),
                Col.of("Cartes", "cardCount"), Col.of("Volume", "volume"), Col.of("Montant", "amount"),
                Col.of("Première transaction", "firstTime"), Col.of("Dernière transaction", "lastTime")), rows);
    }

    // ------------------------------------------------------------------ Import Excel

    static final List<Column> IMPORT_COLUMNS = List.of(
            new Column("Désignation", "name", true, false),
            new Column("Ordre de travail", "workOrderNo", false, true),
            new Column("Catégorie", "category", false, false),
            new Column("Marque", "brand", false, false),
            new Column("Modèle", "model", false, false),
            new Column("N° de série", "serialNo", false, true),
            new Column("N° de parc / immatriculation", "fleetNo", false, true),
            new Column("Énergie", "grade", false, false),
            new Column("Capacité du réservoir (L)", "tankCapacity", false, false),
            new Column("Station", "station", false, false),
            new Column("Statut", "status", false, false),
            new Column("N° de carte", "cards", false, true),
            new Column("Observations", "memo", false, false));

    @GetMapping("/import-template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelImport.template(response, "Modele_import_equipements", IMPORT_COLUMNS,
                List.of(List.of("Reach stacker RS-04", "4500012873345", "Reach stacker", "Kalmar", "DRG450", "KAL-DRG450-77812",
                                "RS-04", "Gasoil", "300", "", "En service", "", "Quai nord"),
                        List.of("Groupe électrogène GE-2", "4500012873346", "Groupe électrogène", "Caterpillar", "C18",
                                "CAT0C18GE2", "GE-2", "Gasoil", "1200", "", "En maintenance", "", "")),
                List.of("Import des équipements : une ligne par équipement, les colonnes marquées * sont obligatoires.",
                        "Ordre de travail, N° de série, N° de parc et N° de carte sont au format Texte : gardez-les ainsi, "
                                + "sinon Excel arrondit les numéros de plus de 15 chiffres.",
                        "Un équipement dont le N° de série existe déjà est mis à jour au lieu d'être créé.",
                        "Énergie : nom ou code de l'énergie (ex. Gasoil). Station : nom exact d'une station de votre périmètre (facultatif).",
                        "Statut : En service (par défaut), En maintenance ou Hors service.",
                        "N° de carte : facultatif, une ou plusieurs cartes séparées par des virgules ; elles sont rattachées à "
                                + "l'équipement et prennent son ordre de travail à partir de l'import.",
                        "Supprimez les lignes d'exemple avant d'importer."));
    }

    /** Import : dryRun=true contrôle sans rien enregistrer ; sinon tout est enregistré, ou rien s'il reste une erreur. */
    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importFile(@CurrentUser SessionUser user, @RequestPart("file") MultipartFile file,
                                          @RequestParam(defaultValue = "true") boolean dryRun) {
        checkRight(user);
        List<Line> lines = ExcelImport.read(file, IMPORT_COLUMNS);
        Map<String, Integer> stations = new HashMap<>();
        for (Map<String, Object> s : db.list("SELECT station_id, name FROM stations WHERE station_id IN (:s)",
                Map.of("s", scope.stationIds(user)))) {
            stations.put(ExcelImport.norm((String) s.get("name")), (Integer) s.get("stationId"));
        }
        Map<String, String> grades = new HashMap<>();
        for (Map<String, Object> g : db.list("SELECT code, name FROM grade_specifics", Map.of())) {
            grades.put(ExcelImport.norm(String.valueOf(g.get("name"))), String.valueOf(g.get("code")));
            grades.put(ExcelImport.norm(String.valueOf(g.get("code"))), String.valueOf(g.get("code")));
        }
        Set<String> serialsInFile = new HashSet<>();
        Set<String> cardsInFile = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        List<Runnable> writes = new ArrayList<>();
        for (Line l : lines) {
            List<String> errors = new ArrayList<>(l.errors());
            Integer station = null;
            if (l.get("station") != null) {
                station = stations.get(ExcelImport.norm(l.get("station")));
                if (station == null) {
                    errors.add("Station inconnue ou hors de votre périmètre : " + l.get("station"));
                }
            }
            String grade = null;
            if (l.get("grade") != null) {
                grade = grades.get(ExcelImport.norm(l.get("grade")));
                if (grade == null) {
                    errors.add("Énergie inconnue : " + l.get("grade"));
                }
            }
            Integer status = 1;
            if (l.get("status") != null) {
                status = STATUS.entrySet().stream().filter(e -> ExcelImport.norm(e.getValue()).equals(ExcelImport.norm(l.get("status"))))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (status == null) {
                    errors.add("Statut inconnu : " + l.get("status") + " (En service, En maintenance ou Hors service)");
                }
            }
            BigDecimal capacity = null;
            if (l.get("tankCapacity") != null) {
                try {
                    capacity = new BigDecimal(l.get("tankCapacity").replace(" ", "").replace(',', '.'));
                } catch (NumberFormatException e) {
                    errors.add("Capacité du réservoir invalide : " + l.get("tankCapacity"));
                }
            }
            String serial = l.get("serialNo");
            Integer existing = null;
            if (serial != null) {
                if (!serialsInFile.add(ExcelImport.norm(serial))) {
                    errors.add("N° de série en double dans le fichier : " + serial);
                }
                Map<String, Object> e = db.one("SELECT id FROM equipment WHERE company_id IN (:n) AND serial_no = :s",
                        Db.p("n", scope.nodeList(user), "s", serial));
                existing = e == null ? null : (Integer) e.get("id");
            }
            List<String> cards = new ArrayList<>();
            if (l.get("cards") != null) {
                for (String c : l.get("cards").split("[,;\\s]+")) {
                    if (c.isBlank()) {
                        continue;
                    }
                    if (!cardsInFile.add(c)) {
                        errors.add("Carte présente sur plusieurs lignes : " + c);
                        continue;
                    }
                    try {
                        workOrders.card(user, c);
                        cards.add(c);
                    } catch (ApiException e) {
                        errors.add(e.getMessage());
                    }
                }
            }
            EquipmentForm form = null;
            // Sans désignation, l'erreur « obligatoire » est déjà signalée par la lecture du fichier
            if (l.get("name") != null) {
                try {
                    form = clean(user, new EquipmentForm(l.get("name"), l.get("workOrderNo"), l.get("category"), l.get("brand"),
                            l.get("model"), serial, l.get("fleetNo"), grade, capacity, station, status, l.get("memo")), existing);
                } catch (ApiException e) {
                    if (!errors.contains(e.getMessage())) {
                        errors.add(e.getMessage());
                    }
                }
            }
            if (!cards.isEmpty() && form != null && form.workOrderNo() == null && errors.isEmpty()) {
                errors.add("Un ordre de travail est nécessaire pour rattacher des cartes");
            }
            Map<String, Object> row = new LinkedHashMap<>(l.values());
            row.put("line", l.number());
            row.put("action", existing == null ? "Création" : "Mise à jour");
            row.put("errors", errors);
            out.add(row);
            if (errors.isEmpty()) {
                EquipmentForm ok = form;
                Integer target = existing;
                writes.add(() -> {
                    int id;
                    if (target == null) {
                        id = insert(user, ok);
                    } else {
                        id = target;
                        write(user, id, ok);
                        if (ok.workOrderNo() != null) {
                            workOrders.followEquipment(user, id, ok.workOrderNo());
                        }
                    }
                    for (String c : cards) {
                        workOrders.assign(user, c, ok.workOrderNo(), id, null, null);
                    }
                });
            }
        }
        boolean hasErrors = out.stream().anyMatch(r -> !((List<?>) r.get("errors")).isEmpty());
        if (dryRun || hasErrors) {
            return ExcelImport.result(out, dryRun, 0);
        }
        // Une erreur à l'enregistrement annule tout l'import (transaction)
        writes.forEach(Runnable::run);
        return ExcelImport.result(out, false, writes.size());
    }

    // ------------------------------------------------------------------ Utilitaires

    private int insert(SessionUser user, EquipmentForm f) {
        int company = user.companyGroupId();
        return db.jdbc().queryForObject("INSERT INTO equipment (company_id, station_id, name, work_order_no, category, brand, model, "
                + "serial_no, fleet_no, grade_code, tank_capacity, status, memo, create_by) VALUES (:c, :st, :n, :wo, :cat, :b, :m, "
                + ":sn, :fn, :g, :cap, :s, :memo, :by) RETURNING id", params(f).addValue("c", company)
                .addValue("by", user.getLoginName()), Integer.class);
    }

    private void write(SessionUser user, int id, EquipmentForm f) {
        db.jdbc().update("UPDATE equipment SET station_id = :st, name = :n, work_order_no = :wo, category = :cat, brand = :b, "
                + "model = :m, serial_no = :sn, fleet_no = :fn, grade_code = :g, tank_capacity = :cap, status = :s, memo = :memo, "
                + "update_time = now(), update_by = :by WHERE id = :id", params(f).addValue("id", id).addValue("by", user.getLoginName()));
    }

    private static org.springframework.jdbc.core.namedparam.MapSqlParameterSource params(EquipmentForm f) {
        return new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("st", f.stationId()).addValue("n", f.name()).addValue("wo", f.workOrderNo()).addValue("cat", f.category())
                .addValue("b", f.brand()).addValue("m", f.model()).addValue("sn", f.serialNo()).addValue("fn", f.fleetNo())
                .addValue("g", f.gradeCode()).addValue("cap", f.tankCapacity()).addValue("s", f.status()).addValue("memo", f.memo());
    }

    /** Contrôle et nettoie la saisie (champs vides → null, longueurs, station du périmètre, n° de série unique). */
    private EquipmentForm clean(SessionUser user, EquipmentForm f, Integer id) {
        String name = text(f.name(), 100, "Désignation");
        if (name == null) {
            throw new ApiException("La désignation est obligatoire");
        }
        String serial = text(f.serialNo(), 64, "N° de série");
        if (serial != null && db.one("SELECT 1 FROM equipment WHERE company_id IN (:n) AND serial_no = :s AND id <> :id",
                Db.p("n", scope.nodeList(user), "s", serial, "id", id == null ? -1 : id)) != null) {
            throw new ApiException("Un autre équipement porte déjà le n° de série " + serial);
        }
        if (f.stationId() != null && !scope.stationIds(user).contains(f.stationId())) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
        int status = f.status() == null ? 1 : f.status();
        if (!STATUS.containsKey(status)) {
            throw new ApiException("Statut invalide");
        }
        if (f.tankCapacity() != null && f.tankCapacity().signum() < 0) {
            throw new ApiException("La capacité du réservoir ne peut pas être négative");
        }
        return new EquipmentForm(name, WorkOrders.normalize(f.workOrderNo()), text(f.category(), 50, "Catégorie"),
                text(f.brand(), 50, "Marque"), text(f.model(), 50, "Modèle"), serial, text(f.fleetNo(), 30, "N° de parc"),
                text(f.gradeCode(), 10, "Énergie"), f.tankCapacity(), f.stationId(), status, f.memo() == null || f.memo().isBlank()
                ? null : f.memo().trim());
    }

    private static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim();
        if (s.length() > max) {
            throw new ApiException("« " + field + " » dépasse " + max + " caractères");
        }
        return s;
    }

    private Map<String, Object> load(SessionUser user, int id) {
        Map<String, Object> e = db.one(SELECT + " WHERE e.id = :id AND e.company_id IN (:n)", Db.p("id", id, "n", scope.nodeList(user)));
        if (e == null) {
            throw new ApiException("Équipement introuvable");
        }
        decorate(e);
        return e;
    }

    static void checkRight(SessionUser user) {
        if (!user.isSystemManager() && !user.hasMenu(RIGHT)) {
            throw new ApiException("Vous n'avez pas le droit « Équipements et ordres de travail »");
        }
    }

    private static void decorate(Map<String, Object> r) {
        r.put("statusName", STATUS.getOrDefault(r.get("status") instanceof Number n ? n.intValue() : 1, ""));
    }
}
