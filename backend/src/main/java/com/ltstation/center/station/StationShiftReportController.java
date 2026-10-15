package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rapports de la station : rapport et clôture de poste (ShiftForm), annulation de clôture (CancleDuty),
 * rapports journalier et mensuel avec « Clearing » (RDaily). Les index de début et de fin des pistolets
 * sont calculés comme dans l'original à partir de l'index cumulé (volume_total) des ventes.
 */
@RestController
@RequestMapping("/api/station/shift-reports")
public class StationShiftReportController {

    private static final String CUSTOMER = "('010001', '010008', '010013', 'FFFFFF', '010002', '010014')";

    private final Db db;
    private final StationContext stations;

    public StationShiftReportController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    // ------------------------------------------------------------------ Calculs communs

    /** Par pistolet et énergie : index de début (1re vente : index - volume), index de fin (dernière vente), écart. */
    private List<Map<String, Object>> nozzles(String w, Map<String, Object> p) {
        return db.list("SELECT x.gun_id, x.grade_id, g.name grade_name, x.volume, x.amount, x.start_time, x.end_time, "
                + "round(CAST(f.volume_total - f.volume AS numeric), 3) start_pump, round(CAST(l.volume_total AS numeric), 3) end_pump, "
                + "round(CAST(l.volume_total - (f.volume_total - f.volume) - x.volume AS numeric), 3) error FROM ("
                + "SELECT o.gun_id, o.grade_specific_id grade_id, sum(o.volume) volume, sum(o.truely_pay) amount, min(o.true_time) start_time, "
                + "max(o.true_time) end_time, min(o.id) FILTER (WHERE true) first_id, max(o.id) last_id FROM oil_transactions o" + w
                + " GROUP BY o.gun_id, o.grade_specific_id) x "
                + "LEFT JOIN LATERAL (SELECT volume_total, volume FROM oil_transactions WHERE station_id = :s AND gun_id = x.gun_id AND true_time = x.start_time "
                + "ORDER BY id LIMIT 1) f ON true "
                + "LEFT JOIN LATERAL (SELECT volume_total FROM oil_transactions WHERE station_id = :s AND gun_id = x.gun_id AND true_time = x.end_time "
                + "ORDER BY id DESC LIMIT 1) l ON true LEFT JOIN grade_specifics g ON g.grade_specific_id = x.grade_id ORDER BY x.gun_id", p);
    }

    /** Par énergie et prix, ventilé par type de carte (TypeShift / TankShow d'origine). */
    private List<Map<String, Object>> types(String w, Map<String, Object> p) {
        return db.list("SELECT o.grade_specific_id grade_id, g.name grade_name, o.ppu price, COALESCE(max(pi.newdensity), 0) density, count(*) cnt, "
                + "sum(o.volume) total_volume, sum(o.truely_pay) total_amount, "
                + "sum(CASE WHEN left(o.asn, 6) = '010004' THEN o.volume ELSE 0 END) emp_volume, sum(CASE WHEN left(o.asn, 6) = '010004' THEN o.truely_pay ELSE 0 END) emp_amount, "
                + "sum(CASE WHEN left(o.asn, 6) IN " + CUSTOMER + " THEN o.volume ELSE 0 END) user_volume, "
                + "sum(CASE WHEN left(o.asn, 6) IN " + CUSTOMER + " THEN o.truely_pay ELSE 0 END) user_amount, "
                + "sum(CASE WHEN left(o.asn, 6) IN ('010011', '010012') THEN o.volume ELSE 0 END) credit_volume, "
                + "sum(CASE WHEN left(o.asn, 6) IN ('010011', '010012') THEN o.truely_pay ELSE 0 END) credit_amount, "
                + "sum(CASE WHEN left(o.asn, 6) = '000000' THEN o.volume ELSE 0 END) no_card_volume, "
                + "sum(CASE WHEN left(o.asn, 6) = '000000' THEN o.truely_pay ELSE 0 END) no_card_amount, "
                + "sum(CASE WHEN left(o.asn, 6) = '010006' THEN o.volume ELSE 0 END) repair_volume, "
                + "sum(CASE WHEN left(o.asn, 6) = '010006' THEN o.truely_pay ELSE 0 END) repair_amount "
                + "FROM oil_transactions o LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id "
                + "LEFT JOIN price_infos pi ON pi.station_id = o.station_id AND pi.grade_specific_id = o.grade_specific_id" + w
                + " GROUP BY o.grade_specific_id, g.name, o.ppu ORDER BY g.name, o.ppu", p);
    }

    private List<Map<String, Object>> employees(String w, Map<String, Object> p) {
        return db.list("SELECT e.name employee_name, sum(o.truely_pay) total_amount, sum(o.volume) total_volume FROM oil_transactions o "
                + "LEFT JOIN employees e ON e.employee_id = o.employee_id" + w + " GROUP BY e.name ORDER BY e.name", p);
    }

    private List<Map<String, Object>> cards(String w, Map<String, Object> p) {
        return db.list("SELECT left(o.asn, 6) card_type, count(*) cnt, sum(o.volume) total_volume, sum(o.truely_pay) total_amount, min(o.true_time) start_time, "
                + "max(o.true_time) end_time FROM oil_transactions o" + w + " GROUP BY left(o.asn, 6) ORDER BY 1", p);
    }

    private Map<String, Object> totals(String w, Map<String, Object> p) {
        return db.one("SELECT count(*) cnt, COALESCE(sum(o.volume), 0) total_volume, COALESCE(sum(o.truely_pay), 0) total_amount, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) IN " + CUSTOMER + " THEN o.volume END), 0) user_volume, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) IN " + CUSTOMER + " THEN o.truely_pay END), 0) user_amount, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '010004' THEN o.volume END), 0) emp_volume, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '010004' THEN o.truely_pay END), 0) emp_amount, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) IN ('010011', '010012') THEN o.volume END), 0) credit_volume, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) IN ('010011', '010012') THEN o.truely_pay END), 0) credit_amount, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '010006' THEN o.volume END), 0) repair_volume, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '010006' THEN o.truely_pay END), 0) repair_amount, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '000000' THEN o.volume END), 0) no_card_volume, "
                + "COALESCE(sum(CASE WHEN left(o.asn, 6) = '000000' THEN o.truely_pay END), 0) no_card_amount FROM oil_transactions o" + w, p);
    }

    // ------------------------------------------------------------------ Rapport de poste

    private record DutyRef(StationContext.Shift shift, Map<String, Object> duty) {
    }

    private DutyRef dutyRef(int stationId, LocalDate date, int configId) {
        StationContext.Shift s = stations.shift(stationId, date, configId);
        Map<String, Object> d = db.one("SELECT * FROM station_duty WHERE station_id = :st AND shift = :sh", Map.of("st", stationId, "sh", s.shift()));
        return new DutyRef(s, d);
    }

    /** Rapport d'un poste (date + n° de poste) : pistolets, détail paginé, employés et synthèse par énergie. */
    @GetMapping
    public Map<String, Object> report(@CurrentUser SessionUser user, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam int configId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        int s = stations.of(user).stationId();
        DutyRef r = dutyRef(s, date, configId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shift", r.shift().view());
        out.put("closed", r.duty() != null && ((Number) r.duty().get("status")).intValue() == 1);
        int dutyId = r.duty() == null ? -1 : ((Number) r.duty().get("id")).intValue();
        Map<String, Object> p = new HashMap<>(Map.of("s", s, "d", dutyId));
        String w = " WHERE o.station_id = :s AND o.duty_id = :d";
        out.put("nozzles", nozzles(w, p));
        out.put("types", types(w, p));
        out.put("employees", employees(w, p));
        PageResult<Map<String, Object>> tx = db.page("SELECT o.id, o.posttc, o.gun_id, e.employee_no, o.asn, o.volume, o.truely_pay, o.true_time, o.volume_total, "
                + "g.name grade_name, o.ppu, o.t_type, o.bal, o.payment_id FROM oil_transactions o LEFT JOIN employees e ON e.employee_id = o.employee_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id" + w, p, "o.gun_id, o.posttc", page, size);
        out.put("transactions", tx.rows());
        out.put("total", tx.total());
        return out;
    }

    public record ShiftForm(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Integer configId) {
    }

    /** Clôture du poste (« Make Shift ») : écrit duty_gun_info et duty_brand_sales. */
    @PostMapping("/close")
    @Transactional
    public Map<String, Object> close(@CurrentUser SessionUser user, @RequestBody ShiftForm f) {
        StationContext.Ctx c = stations.of(user);
        DutyRef r = dutyRef(c.stationId(), f.date(), f.configId());
        if (r.duty() == null) {
            throw new ApiException("Aucune donnée pour ce poste");
        }
        if (((Number) r.duty().get("status")).intValue() == 1) {
            throw new ApiException("Ce poste est déjà clôturé");
        }
        int dutyId = ((Number) r.duty().get("id")).intValue();
        String shift = r.shift().shift();
        Map<String, Object> p = new HashMap<>(Map.of("s", c.stationId(), "d", dutyId));
        String w = " WHERE o.station_id = :s AND o.duty_id = :d";
        List<Map<String, Object>> noz = nozzles(w, p);
        if (noz.isEmpty()) {
            throw new ApiException("Aucune donnée pour ce poste");
        }
        for (Map<String, Object> n : noz) {
            db.update("INSERT INTO duty_gun_info (station_id, gun_code, startpt, end_pt, vol, amount, shift, grade_specific_id, start_time, end_time, ptc, time, memo) "
                    + "VALUES (:s, :g, :sp, :ep, :v, :a, :sh, :gr, :st, :et, :ptc, now(), '')",
                    Db.p("s", c.stationId(), "g", n.get("gunId"), "sp", dbl(n.get("startPump")), "ep", dbl(n.get("endPump")), "v", dbl(n.get("volume")),
                            "a", dbl(n.get("amount")), "sh", shift, "gr", n.get("gradeId"), "st", n.get("startTime"), "et", n.get("endTime"), "ptc", dbl(n.get("error"))));
        }
        for (Map<String, Object> t : types(w, p)) {
            double ev = dbl(t.get("empVolume")), em = dbl(t.get("empAmount")), uv = dbl(t.get("userVolume")), um = dbl(t.get("userAmount"));
            double cv = dbl(t.get("creditVolume")), cm = dbl(t.get("creditAmount")), tv = dbl(t.get("noCardVolume")), tm = dbl(t.get("noCardAmount"));
            double rv = dbl(t.get("repairVolume")), rm = dbl(t.get("repairAmount"));
            db.update("INSERT INTO duty_brand_sales (shift, grade_specific_id, station_id, ppu, density, employee_v, employee_m, user_v, user_m, credit_v, credit_m, "
                    + "test_v, test_m, rcv, rcm, main_po_v, main_po_m, subpo_v, subpo_m, credit_coupons_v, credit_coupons_m, cash_coupons_v, cash_coupons_m, bank_v, bank_m, "
                    + "upload) VALUES (:sh, :g, :s, :ppu, :de, :ev, :em, :uv, :um, :cv, :cm, :tv, :tm, :rcv, :rcm, 0, 0, 0, 0, 0, 0, :rv, :rm, 0, 0, 0) "
                    + "ON CONFLICT (shift, grade_specific_id, station_id) DO UPDATE SET employee_v = duty_brand_sales.employee_v + EXCLUDED.employee_v, "
                    + "employee_m = duty_brand_sales.employee_m + EXCLUDED.employee_m, user_v = duty_brand_sales.user_v + EXCLUDED.user_v, "
                    + "user_m = duty_brand_sales.user_m + EXCLUDED.user_m, credit_v = duty_brand_sales.credit_v + EXCLUDED.credit_v, "
                    + "credit_m = duty_brand_sales.credit_m + EXCLUDED.credit_m, test_v = duty_brand_sales.test_v + EXCLUDED.test_v, "
                    + "test_m = duty_brand_sales.test_m + EXCLUDED.test_m, rcv = duty_brand_sales.rcv + EXCLUDED.rcv, rcm = duty_brand_sales.rcm + EXCLUDED.rcm, "
                    + "cash_coupons_v = duty_brand_sales.cash_coupons_v + EXCLUDED.cash_coupons_v, cash_coupons_m = duty_brand_sales.cash_coupons_m + EXCLUDED.cash_coupons_m",
                    Db.p("sh", shift, "g", t.get("gradeId"), "s", c.stationId(), "ppu", dbl(t.get("price")), "de", dbl(t.get("density")), "ev", ev, "em", em,
                            "uv", uv, "um", um, "cv", cv, "cm", cm, "tv", tv, "tm", tm, "rcv", r3(ev + uv + cv + tv + rv), "rcm", r3(em + um + cm + tm + rm),
                            "rv", rv, "rm", rm));
        }
        db.update("UPDATE station_duty SET status = 1, close_time = now(), close_employee_id = :e WHERE id = :id", Map.of("e", c.employeeId(), "id", dutyId));
        return report(user, f.date(), f.configId(), 1, 50);
    }

    /** Annulation de la clôture (CancleDuty) : suppression des rapports du poste, le poste redevient ouvert. */
    @PostMapping("/cancel")
    @Transactional
    public void cancel(@CurrentUser SessionUser user, @RequestBody ShiftForm f) {
        int s = stations.of(user).stationId();
        DutyRef r = dutyRef(s, f.date(), f.configId());
        String shift = r.shift().shift();
        db.update("DELETE FROM duty_gun_info WHERE station_id = :s AND shift = :sh", Map.of("s", s, "sh", shift));
        db.update("DELETE FROM duty_brand_sales WHERE station_id = :s AND shift = :sh", Map.of("s", s, "sh", shift));
        if (r.duty() != null) {
            db.update("UPDATE station_duty SET status = 0, close_time = NULL, close_employee_id = NULL WHERE id = :id", Map.of("id", r.duty().get("id")));
        }
    }

    // ------------------------------------------------------------------ Rapports journalier et mensuel (RDaily)

    /** Période : les 24 h (ou le mois) qui précèdent la date et l'heure de fin choisies. */
    private Map<String, Object> period(int stationId, LocalDate date, LocalTime time, boolean month, Map<String, Object> p) {
        LocalDateTime end = date.atTime(time);
        LocalDateTime start = month ? end.minusMonths(1) : end.minusDays(1);
        p.put("s", stationId);
        p.put("a", start);
        p.put("b", end);
        return Map.of("start", start, "end", end);
    }

    @GetMapping("/daily")
    public Map<String, Object> daily(@CurrentUser SessionUser user, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(defaultValue = "00:00") String time, @RequestParam(defaultValue = "false") boolean month) {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        Map<String, Object> per = period(s, date, LocalTime.parse(time), month, p);
        String w = " WHERE o.station_id = :s AND o.true_time > :a AND o.true_time <= :b";
        Map<String, Object> out = new LinkedHashMap<>(per);
        out.put("nozzles", nozzles(w, p));
        out.put("types", types(w, p));
        out.put("cards", cards(w, p));
        out.put("totals", totals(w, p));
        String key = key(date, month);
        out.put("cleared", month
                ? db.one("SELECT 1 FROM monthly_sales_reports WHERE station_id = :s AND month = :k LIMIT 1", Map.of("s", s, "k", key)) != null
                : db.one("SELECT 1 FROM daily_sales_reports WHERE station_id = :s AND shift = :k LIMIT 1", Map.of("s", s, "k", key)) != null);
        return out;
    }

    public record ClearForm(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, String time, boolean month) {
    }

    /** « Clearing » : enregistre le rapport journalier (daily_*_reports) ou mensuel (monthly_*_reports). */
    @PostMapping("/daily/clear")
    @Transactional
    public Map<String, Object> clear(@CurrentUser SessionUser user, @RequestBody ClearForm f) {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        Map<String, Object> per = period(s, f.date(), LocalTime.parse(f.time() == null ? "00:00" : f.time()), f.month(), p);
        String w = " WHERE o.station_id = :s AND o.true_time > :a AND o.true_time <= :b";
        String key = key(f.date(), f.month());
        String gunTable = f.month() ? "monthly_gun_reports" : "daily_gun_reports";
        String salesTable = f.month() ? "monthly_sales_reports" : "daily_sales_reports";
        String col = f.month() ? "month" : "shift";
        if (db.one("SELECT 1 FROM " + salesTable + " WHERE station_id = :s AND " + col + " = :k LIMIT 1", Map.of("s", s, "k", key)) != null) {
            throw new ApiException("Ce rapport a déjà été enregistré");
        }
        List<Map<String, Object>> noz = nozzles(w, p);
        if (noz.isEmpty()) {
            throw new ApiException("Aucune donnée sur la période");
        }
        for (Map<String, Object> n : noz) {
            if (f.month()) {
                db.update("INSERT INTO monthly_gun_reports (station_id, month, start_time, end_time, gun_code, grade_specific_id, start_pt, end_pt, total_sales, amount, gap, upload) "
                        + "VALUES (:s, :k, :st, :et, :g, :gr, :sp, :ep, :v, :a, :e, 0)",
                        Db.p("s", s, "k", key, "st", per.get("start"), "et", per.get("end"), "g", n.get("gunId"), "gr", n.get("gradeId"), "sp", dbl(n.get("startPump")),
                                "ep", dbl(n.get("endPump")), "v", dbl(n.get("volume")), "a", dbl(n.get("amount")), "e", dbl(n.get("error"))));
            } else {
                db.update("INSERT INTO daily_gun_reports (station_id, shift, gun_code, grade_specific_id, start_pt, end_pt, ptc, volume, amount, start_time, end_time, account_time, upload) "
                        + "VALUES (:s, :k, :g, :gr, :sp, :ep, :e, :v, :a, :st, :et, now(), 0)",
                        Db.p("s", s, "k", key, "g", n.get("gunId"), "gr", n.get("gradeId"), "sp", dbl(n.get("startPump")), "ep", dbl(n.get("endPump")),
                                "e", dbl(n.get("error")), "v", dbl(n.get("volume")), "a", dbl(n.get("amount")), "st", n.get("startTime"), "et", n.get("endTime")));
            }
        }
        for (Map<String, Object> t : types(w, p)) {
            Map<String, Object> q = Db.p("s", s, "k", key, "g", t.get("gradeId"), "ppu", dbl(t.get("price")), "de", dbl(t.get("density")),
                    "cv", dbl(t.get("noCardVolume")), "cm", dbl(t.get("noCardAmount")), "dv", dbl(t.get("userVolume")) + dbl(t.get("empVolume")),
                    "dm", dbl(t.get("userAmount")) + dbl(t.get("empAmount")), "crv", dbl(t.get("creditVolume")), "crm", dbl(t.get("creditAmount")),
                    "mv", dbl(t.get("repairVolume")), "mm", dbl(t.get("repairAmount")), "tv", dbl(t.get("totalVolume")), "tm", dbl(t.get("totalAmount")),
                    "st", per.get("start"), "et", per.get("end"));
            String cols = "grade_specific_id, ppu, density, cash_v, cash_m, deposit_v, deposit_m, credit_v, credit_m, maint_v, maint_m, verify_v, verify_m, other_v, other_m, "
                    + "rcv, rcm, main_po_v, main_po_m, subpo_v, subpo_m, credit_coupons_v, credit_coupons_m, cash_coupons_v, cash_coupons_m, bank_v, bank_m, upload";
            String vals = ":g, :ppu, :de, :cv, :cm, :dv, :dm, :crv, :crm, :mv, :mm, 0, 0, 0, 0, :tv, :tm, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0";
            if (f.month()) {
                db.update("INSERT INTO monthly_sales_reports (station_id, month, start_time, end_time, " + cols + ") VALUES (:s, :k, :st, :et, " + vals + ")", q);
            } else {
                db.update("INSERT INTO daily_sales_reports (station_id, shift, account_time, " + cols + ") VALUES (:s, :k, now(), " + vals + ")", q);
            }
        }
        return daily(user, f.date(), f.time() == null ? "00:00" : f.time(), f.month());
    }

    private static String key(LocalDate date, boolean month) {
        return month ? date.format(DateTimeFormatter.ofPattern("yyyyMM")) : date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static double dbl(Object v) {
        return v == null ? 0 : Double.parseDouble(v.toString());
    }

    private static double r3(double v) {
        return Math.round(v * 1000d) / 1000d;
    }
}
