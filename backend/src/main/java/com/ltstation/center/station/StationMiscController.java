package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fenêtres secondaires du logiciel Station : journal des cartes (CardLog), activité (BusinessReportMess), règles de points
 * (ScoreDeal), remises (CenterDisCount), stock (StockManager), informations et liste des clients (UserCardMessage,
 * CustomerUserList), points virtuels (VirtualScore), points des cartes (CardScore) et informations de la station (BaseMessage).
 */
@RestController
@RequestMapping("/api/station/misc")
public class StationMiscController {

    private final Db db;
    private final StationContext stations;

    public StationMiscController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    // ------------------------------------------------------------------ Journal des cartes / activité

    private static final String LOG_SELECT = "SELECT l.id, l.asn, COALESCE(cu.name, e.name, g.name) user_name, l.transaction_type_id, l.amount, l.discount_amount, "
            + "l.trans_datetime, l.balance, (SELECT o.volume FROM oil_transactions o WHERE o.asn = l.asn AND o.station_id = l.station_id AND l.transaction_type_id = 11 "
            + "AND abs(EXTRACT(EPOCH FROM (o.true_time - l.trans_datetime))) < 5 ORDER BY o.id DESC LIMIT 1) volume FROM account_service_logs l "
            + "LEFT JOIN customer_card_infos ci ON ci.asn = l.asn LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
            + "LEFT JOIN employee_card_infos ec ON ec.asn = l.asn LEFT JOIN employees e ON e.employee_id = ec.employee_id "
            + "LEFT JOIN group_card_infos gc ON gc.asn = l.asn LEFT JOIN groups g ON g.group_id = gc.group_id";

    private String logWhere(int stationId, LocalDateTime start, LocalDateTime end, String asn, String certNo, String carNo, Integer cardType, Integer opType,
                            Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" WHERE l.station_id = :s AND l.trans_datetime BETWEEN :a AND :b");
        p.put("s", stationId);
        p.put("a", start);
        p.put("b", end);
        if (asn != null && !asn.isBlank()) {
            w.append(" AND l.asn = :asn");
            p.put("asn", asn.replaceAll("\\s", ""));
        }
        if (certNo != null && !certNo.isBlank()) {
            w.append(" AND cu.certificate_no = :cn");
            p.put("cn", certNo.trim());
        }
        if (carNo != null && !carNo.isBlank()) {
            w.append(" AND cu.vehicle_no = :car");
            p.put("car", carNo.trim());
        }
        if (cardType != null && cardType > 0) {
            w.append(" AND left(l.asn, 6) = :ct");
            p.put("ct", switch (cardType) {
                case 1 -> "010001";
                case 2 -> "010004";
                case 3 -> "010011";
                case 4 -> "010002";
                case 5 -> "010014";
                default -> "010013";
            });
        }
        if (opType != null && opType > 0) {
            w.append(" AND l.transaction_type_id = :op");
            p.put("op", opType);
        }
        return w.toString();
    }

    /** Journal des cartes (CardLog) et rapport d'activité (BusinessReportMess). */
    @GetMapping("/card-logs")
    public Map<String, Object> cardLogs(@CurrentUser SessionUser user,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                        @RequestParam(required = false) String asn, @RequestParam(required = false) String certNo,
                                        @RequestParam(required = false) String carNo, @RequestParam(required = false) Integer cardType,
                                        @RequestParam(required = false) Integer opType, @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        String w = logWhere(s, start, end, asn, certNo, carNo, cardType, opType, p);
        PageResult<Map<String, Object>> r = db.page(LOG_SELECT + w, p, "l.trans_datetime DESC, l.id DESC", page, size);
        Map<String, Object> out = new HashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        out.put("byUser", db.list("SELECT x.user_name, sum(CASE WHEN x.transaction_type_id = 2 THEN x.amount ELSE 0 END) deposit, "
                + "sum(CASE WHEN x.transaction_type_id IN (12, 13) THEN -x.amount ELSE 0 END) charge, sum(CASE WHEN x.transaction_type_id = 2 THEN x.discount_amount ELSE 0 END) gift, "
                + "sum(CASE WHEN x.transaction_type_id = 11 THEN x.amount ELSE 0 END) consume, sum(CASE WHEN x.transaction_type_id = 11 THEN COALESCE(x.volume, 0) ELSE 0 END) volume "
                + "FROM (" + LOG_SELECT + w + ") x GROUP BY x.user_name ORDER BY x.user_name", p));
        out.put("totals", db.one("SELECT count(*) cnt, COALESCE(sum(CASE WHEN x.transaction_type_id = 2 THEN x.amount END), 0) deposit, "
                + "COALESCE(sum(CASE WHEN x.transaction_type_id = 2 THEN x.discount_amount END), 0) gift, "
                + "COALESCE(sum(CASE WHEN x.transaction_type_id = 12 THEN -x.amount END), 0) charge, "
                + "COALESCE(sum(CASE WHEN x.transaction_type_id = 13 THEN -x.amount END), 0) non_fuel, "
                + "COALESCE(sum(CASE WHEN x.transaction_type_id = 11 THEN x.amount END), 0) consume, "
                + "COALESCE(sum(CASE WHEN x.transaction_type_id = 11 THEN x.volume END), 0) volume FROM (" + LOG_SELECT + w + ") x", p));
        return out;
    }

    @GetMapping("/card-logs/export")
    public void exportLogs(@CurrentUser SessionUser user, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end, @RequestParam(required = false) String asn,
                           @RequestParam(required = false) String certNo, @RequestParam(required = false) String carNo,
                           @RequestParam(required = false) Integer cardType, @RequestParam(required = false) Integer opType,
                           HttpServletResponse response) throws IOException {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list(LOG_SELECT + logWhere(s, start, end, asn, certNo, carNo, cardType, opType, p) + " ORDER BY l.trans_datetime DESC", p);
        ExcelExport.write(response, "JournalCartes", "Journal", List.of(Col.of("N° de carte", "asn"), Col.of("Nom", "userName"),
                Col.of("Opération", "transactionTypeId"), Col.of("Montant", "amount"), Col.of("Bonus", "discountAmount"), Col.of("Volume", "volume"),
                Col.of("Date", "transDatetime"), Col.of("Solde", "balance")), rows);
    }

    // ------------------------------------------------------------------ Règles de points (ScoreDeal / AddScoreSet)

    @GetMapping("/score-rules")
    public List<Map<String, Object>> scoreRules(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        return db.list("SELECT id, oil_type_id, oil_type_name, score_type, rate FROM score_rate WHERE COALESCE(is_run, true) = true "
                + "AND (company_id IS NULL OR company_id = :c) ORDER BY id", Map.of("c", c.companyId()));
    }

    public record ScoreRuleForm(Integer gradeId, Integer scoreType, BigDecimal rate) {
    }

    @PostMapping("/score-rules")
    @Transactional
    public void addScoreRule(@CurrentUser SessionUser user, @RequestBody ScoreRuleForm f) {
        StationContext.Ctx c = stations.of(user);
        if (f.gradeId() == null || f.rate() == null || f.rate().signum() <= 0) {
            throw new ApiException("Énergie et taux obligatoires");
        }
        Map<String, Object> g = db.one("SELECT name FROM grade_specifics WHERE grade_specific_id = :g", Map.of("g", f.gradeId()));
        if (g == null) {
            throw new ApiException("Énergie introuvable");
        }
        db.update("UPDATE score_rate SET is_run = false WHERE oil_type_id = :g AND (company_id IS NULL OR company_id = :c)", Map.of("g", f.gradeId(), "c", c.companyId()));
        db.update("INSERT INTO score_rate (oil_type_id, oil_type_name, rate, create_time, is_run, score_type, company_id) VALUES (:g, :n, :r, :t, true, :st, :c)",
                Db.p("g", f.gradeId(), "n", g.get("name"), "r", f.rate(), "t", System.currentTimeMillis(), "st", f.scoreType() == null ? 0 : f.scoreType(),
                        "c", c.companyId()));
    }

    @DeleteMapping("/score-rules/{id}")
    @Transactional
    public void deleteScoreRule(@CurrentUser SessionUser user, @PathVariable int id) {
        stations.of(user);
        db.update("UPDATE score_rate SET is_run = false WHERE id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------------ Remises (CenterDisCount / AddDisCountForm)

    @GetMapping("/discounts")
    public List<Map<String, Object>> discounts(@CurrentUser SessionUser user) {
        stations.of(user);
        return db.list("SELECT id, name, dis_count FROM dis_count WHERE COALESCE(is_run, true) = true ORDER BY id", Map.of());
    }

    public record DiscountForm(String name, Integer value) {
    }

    @PostMapping("/discounts")
    @Transactional
    public void addDiscount(@CurrentUser SessionUser user, @RequestBody DiscountForm f) {
        StationContext.Ctx c = stations.of(user);
        checkDiscount(f);
        db.update("INSERT INTO dis_count (name, dis_count, company_id, create_time, is_run) VALUES (:n, :v, :c, :t, true)",
                Map.of("n", f.name().trim(), "v", f.value(), "c", c.companyId(), "t", System.currentTimeMillis()));
    }

    @PutMapping("/discounts/{id}")
    @Transactional
    public void updateDiscount(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody DiscountForm f) {
        stations.of(user);
        checkDiscount(f);
        db.update("UPDATE dis_count SET name = :n, dis_count = :v WHERE id = :id", Map.of("n", f.name().trim(), "v", f.value(), "id", id));
    }

    @DeleteMapping("/discounts/{id}")
    @Transactional
    public void deleteDiscount(@CurrentUser SessionUser user, @PathVariable int id) {
        stations.of(user);
        db.update("UPDATE dis_count SET is_run = false WHERE id = :id", Map.of("id", id));
    }

    private static void checkDiscount(DiscountForm f) {
        if (f.name() == null || f.name().isBlank() || f.name().trim().length() > 20) {
            throw new ApiException("Nom obligatoire (20 caractères maximum)");
        }
        if (f.value() == null || f.value() < 1 || f.value() > 100) {
            throw new ApiException("Remise en pourcentage du prix : entre 1 et 100 (90 = 90 % du prix)");
        }
    }

    // ------------------------------------------------------------------ Stock (StockManager)

    @GetMapping("/stock")
    public List<Map<String, Object>> stock(@CurrentUser SessionUser user) {
        int s = stations.of(user).stationId();
        return db.list("SELECT t.id, t.tank_name, g.name grade_name, t.total_height, t.total_volume, t.current_oil FROM air_tank t "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id WHERE t.station_id = :s AND COALESCE(t.is_run, true) = true ORDER BY t.id",
                Map.of("s", s));
    }

    // ------------------------------------------------------------------ Clients (UserCardMessage / CustomerUserList / CardScore)

    /** Cartes et porteurs : type (0 utilisateur, 1 crédit, 2 secondaire, 3 employé), nom, téléphone, immatriculation, état. */
    @GetMapping("/customers")
    public PageResult<Map<String, Object>> customers(@CurrentUser SessionUser user, @RequestParam(required = false) Integer cardType,
                                                     @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String phone,
                                                     @RequestParam(defaultValue = "") String carNo, @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        stations.of(user);
        Map<String, Object> p = new HashMap<>(Map.of("n", "%" + name.trim().toLowerCase() + "%", "ph", "%" + phone.trim() + "%", "car", "%" + carNo.trim().toLowerCase() + "%"));
        StringBuilder w = new StringBuilder(" WHERE lower(x.name) LIKE :n AND COALESCE(x.phone, '') LIKE :ph AND lower(COALESCE(x.vehicle_no, '')) LIKE :car");
        if (cardType != null) {
            w.append(" AND left(x.asn, 6) = :ct");
            p.put("ct", switch (cardType) {
                case 1 -> "010011";
                case 2 -> "010013";
                case 3 -> "010004";
                default -> "010001";
            });
        }
        if (status != null) {
            w.append(" AND x.status = :st");
            p.put("st", status);
        }
        return db.page("SELECT * FROM (SELECT cu.customer_id, cu.name, ci.asn, cu.telephone phone, cu.vehicle_no, a.balance, ci.card_status_type_id status, ci.score "
                + "FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id LEFT JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id "
                + "UNION ALL SELECT e.employee_id, e.name, ec.asn, e.telephone, NULL, a.balance, ec.card_status_type_id, 0 FROM employee_card_infos ec "
                + "JOIN employees e ON e.employee_id = ec.employee_id LEFT JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id) x" + w,
                p, "x.asn", page, size);
    }

    // ------------------------------------------------------------------ Points virtuels (VirtualScore)

    @GetMapping("/virtual-users")
    public PageResult<Map<String, Object>> virtualUsers(@CurrentUser SessionUser user, @RequestParam(defaultValue = "") String name,
                                                        @RequestParam(defaultValue = "") String phone, @RequestParam(defaultValue = "1") int page) {
        StationContext.Ctx c = stations.of(user);
        return db.page("SELECT id, user_name, phone, score FROM virtual_score_user WHERE (company_id IS NULL OR company_id = :c) AND (:n = '' OR user_name = :n) "
                + "AND (:ph = '' OR phone = :ph)", Map.of("c", c.companyId(), "n", name.trim(), "ph", phone.trim()), "id DESC", page, 20);
    }

    public record VirtualUserForm(String userName, String phone) {
    }

    @PostMapping("/virtual-users")
    @Transactional
    public void addVirtualUser(@CurrentUser SessionUser user, @RequestBody VirtualUserForm f) {
        StationContext.Ctx c = stations.of(user);
        if (f.userName() == null || f.userName().isBlank() || f.phone() == null || f.phone().isBlank()) {
            throw new ApiException("Nom et téléphone obligatoires");
        }
        if (db.one("SELECT 1 FROM virtual_score_user WHERE phone = :p", Map.of("p", f.phone().trim())) != null) {
            throw new ApiException("Ce téléphone existe déjà");
        }
        db.update("INSERT INTO virtual_score_user (user_name, phone, score, create_time, company_id) VALUES (:n, :p, 0, :t, :c)",
                Map.of("n", f.userName().trim(), "p", f.phone().trim(), "t", System.currentTimeMillis(), "c", c.companyId()));
    }

    @PutMapping("/virtual-users/{id}")
    @Transactional
    public void updateVirtualUser(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody VirtualUserForm f) {
        stations.of(user);
        db.update("UPDATE virtual_score_user SET user_name = :n, phone = :p WHERE id = :id", Db.p("n", f.userName(), "p", f.phone(), "id", id));
    }

    public record ScoreOpForm(int type, BigDecimal value) {
    }

    /** Opération sur les points : 0 ajout, 1 retrait (VirtualScoreUpdate). */
    @PostMapping("/virtual-users/{id}/score")
    @Transactional
    public Map<String, Object> scoreOp(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody ScoreOpForm f) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> u = db.one("SELECT * FROM virtual_score_user WHERE id = :id", Map.of("id", id));
        if (u == null) {
            throw new ApiException("Utilisateur introuvable");
        }
        if (f.value() == null || f.value().signum() <= 0) {
            throw new ApiException("Saisissez le nombre de points");
        }
        BigDecimal cur = u.get("score") == null ? BigDecimal.ZERO : new BigDecimal(u.get("score").toString());
        BigDecimal fin = f.type() == 1 ? cur.subtract(f.value()) : cur.add(f.value());
        if (fin.signum() < 0) {
            throw new ApiException("Points insuffisants (" + cur.toPlainString() + ")");
        }
        db.update("UPDATE virtual_score_user SET score = :s WHERE id = :id", Map.of("s", fin, "id", id));
        db.update("INSERT INTO virtual_score_log (score_id, score_name, score_phone, score_type, score_value, final_score, create_time, operate_id) "
                + "VALUES (:id, :n, :p, :t, :v, :f, :time, :e)", Db.p("id", id, "n", u.get("userName"), "p", u.get("phone"), "t", f.type(), "v", f.value(),
                "f", fin, "time", System.currentTimeMillis(), "e", c.employeeId()));
        return Map.of("score", fin);
    }

    @GetMapping("/virtual-users/{id}/history")
    public PageResult<Map<String, Object>> scoreHistory(@CurrentUser SessionUser user, @PathVariable int id, @RequestParam(defaultValue = "1") int page) {
        stations.of(user);
        return db.page("SELECT l.id, l.score_name, l.score_phone, l.score_type, l.score_value, l.final_score, to_timestamp(l.create_time / 1000.0) create_time, "
                + "e.name employee FROM virtual_score_log l LEFT JOIN employees e ON e.employee_id = l.operate_id WHERE l.score_id = :id", Map.of("id", id), "l.id DESC", page, 20);
    }

    // ------------------------------------------------------------------ Informations de la station (BaseMessage)

    @GetMapping("/station")
    public Map<String, Object> station(@CurrentUser SessionUser user) {
        int s = stations.of(user).stationId();
        return db.one("SELECT st.station_id, st.code, st.name, st.address, st.phone, st.manager, st.email1 email, st.city_id, c.region_id, c.city_desc, r.region_desc "
                + "FROM stations st LEFT JOIN cities c ON c.city_id = st.city_id LEFT JOIN regions r ON r.region_id = c.region_id WHERE st.station_id = :s", Map.of("s", s));
    }

    public record StationForm(String name, String address, String manager, String phone, String email) {
    }

    @PutMapping("/station")
    @Transactional
    public Map<String, Object> updateStation(@CurrentUser SessionUser user, @RequestBody StationForm f) {
        int s = stations.of(user).stationId();
        if (f.name() == null || f.name().isBlank() || f.name().trim().length() > 20) {
            throw new ApiException("Nom de la station obligatoire (20 caractères maximum)");
        }
        db.update("UPDATE stations SET name = :n, address = :a, manager = :m, phone = :p, email1 = :e WHERE station_id = :s",
                Db.p("n", f.name().trim(), "a", f.address(), "m", f.manager(), "p", f.phone(), "e", f.email(), "s", s));
        return station(user);
    }
}
