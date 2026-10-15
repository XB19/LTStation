package com.ltstation.center.station;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche des transactions (OilTransacation d'origine) : filtres à cocher (énergie, poste clôturé ou non, poste d'une date,
 * tri, type de carte, pistolet, n° de carte, immatriculation, employé, client, période), synthèse par type de carte et export.
 */
@RestController
@RequestMapping("/api/station/transactions")
public class StationTransactionController {

    private final Db db;
    private final StationContext stations;

    public StationTransactionController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    /** Critères (null = filtre non coché). */
    public static class Query {
        public Integer gradeId;
        /** 0 tous, 1 postes clôturés, 2 postes non clôturés */
        public Integer shiftState;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        public LocalDate shiftDate;
        public Integer shiftConfigId;
        /** 0 par date, 1 par pistolet */
        public Integer order;
        /** 0 employé, 1 utilisateur, 2 crédit, 3 secondaire */
        public Integer cardType;
        public Integer nozId;
        public String asn;
        public String carNo;
        public Integer employeeId;
        public String customer;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        public LocalDateTime start;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        public LocalDateTime end;
        public int page = 1;
        public int size = 50;

        public void setGradeId(Integer v) { gradeId = v; }
        public void setShiftState(Integer v) { shiftState = v; }
        public void setShiftDate(LocalDate v) { shiftDate = v; }
        public void setShiftConfigId(Integer v) { shiftConfigId = v; }
        public void setOrder(Integer v) { order = v; }
        public void setCardType(Integer v) { cardType = v; }
        public void setNozId(Integer v) { nozId = v; }
        public void setAsn(String v) { asn = v; }
        public void setCarNo(String v) { carNo = v; }
        public void setEmployeeId(Integer v) { employeeId = v; }
        public void setCustomer(String v) { customer = v; }
        public void setStart(LocalDateTime v) { start = v; }
        public void setEnd(LocalDateTime v) { end = v; }
        public void setPage(int v) { page = v; }
        public void setSize(int v) { size = v; }
    }

    private static final String SELECT = "SELECT o.id, o.gun_id, g.name grade_name, o.ppu price, o.volume, o.amn amount, o.truely_pay, o.true_time, o.asn, o.bal, "
            + "o.t_type, o.payment_id, o.posttc, e.name employee_name, o.volume_total, d.shift, d.status shift_status, o.car_number, o.mileage, o.discount, "
            + "o.coupon, o.coupon_value FROM oil_transactions o LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id "
            + "LEFT JOIN employees e ON e.employee_id = o.employee_id LEFT JOIN station_duty d ON d.id = o.duty_id";

    private String where(int stationId, Query q, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" WHERE o.station_id = :s");
        p.put("s", stationId);
        if (q.start != null && q.end != null) {
            w.append(" AND o.true_time BETWEEN :a AND :b");
            p.put("a", q.start);
            p.put("b", q.end);
        }
        if (q.gradeId != null) {
            w.append(" AND o.grade_specific_id = :g");
            p.put("g", q.gradeId);
        }
        if (q.shiftState != null && q.shiftState == 1) {
            w.append(" AND d.status = 1");
        } else if (q.shiftState != null && q.shiftState == 2) {
            w.append(" AND (d.status IS NULL OR d.status = 0)");
        }
        if (q.shiftDate != null && q.shiftConfigId != null) {
            w.append(" AND d.shift = :sh");
            p.put("sh", stations.shift(stationId, q.shiftDate, q.shiftConfigId).shift());
        }
        if (q.cardType != null) {
            w.append(" AND left(o.asn, 6) = :ct");
            p.put("ct", switch (q.cardType) {
                case 0 -> "010004";
                case 2 -> "010011";
                case 3 -> "010013";
                default -> "010001";
            });
        }
        if (q.nozId != null) {
            w.append(" AND o.gun_id = :n");
            p.put("n", q.nozId);
        }
        if (q.asn != null && !q.asn.isBlank()) {
            w.append(" AND o.asn = :asn");
            p.put("asn", q.asn.replaceAll("\\s", ""));
        }
        if (q.carNo != null && !q.carNo.isBlank()) {
            w.append(" AND o.car_number = :car");
            p.put("car", q.carNo.trim());
        }
        if (q.employeeId != null) {
            w.append(" AND o.employee_id = :e");
            p.put("e", q.employeeId);
        }
        if (q.customer != null && !q.customer.isBlank()) {
            w.append(" AND o.asn IN (SELECT ci.asn FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id WHERE cu.name = :cu)");
            p.put("cu", q.customer.trim());
        }
        return w.toString();
    }

    @GetMapping
    public Map<String, Object> query(@CurrentUser SessionUser user, @ModelAttribute Query q) {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        String w = where(s, q, p);
        String order = q.order != null && q.order == 1 ? "o.gun_id, o.true_time DESC" : "o.true_time DESC, o.id DESC";
        PageResult<Map<String, Object>> page = db.page(SELECT + w, p, order, q.page, Math.min(Math.max(q.size, 1), 500));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", page.rows());
        out.put("total", page.total());
        // Synthèse par type de carte (préfixe du n° de carte), avec la ligne « Total » en tête comme l'original
        List<Map<String, Object>> sum = new ArrayList<>(db.list("SELECT left(o.asn, 6) card_type, COALESCE(sum(o.volume), 0) total_volume, "
                + "COALESCE(sum(o.truely_pay), 0) total_amount, count(*) cnt FROM oil_transactions o LEFT JOIN station_duty d ON d.id = o.duty_id" + w
                + " GROUP BY left(o.asn, 6) ORDER BY 1", p));
        double v = 0;
        double a = 0;
        long n = 0;
        for (Map<String, Object> r : sum) {
            v += ((Number) r.get("totalVolume")).doubleValue();
            a += ((Number) r.get("totalAmount")).doubleValue();
            n += ((Number) r.get("cnt")).longValue();
        }
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("cardType", "TOTAL");
        total.put("totalVolume", v);
        total.put("totalAmount", a);
        total.put("cnt", n);
        sum.add(0, total);
        out.put("summary", sum);
        return out;
    }

    /** Export Excel : la page affichée (page &gt; 0) ou tout le résultat (page = 0). */
    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Query q, HttpServletResponse response) throws IOException {
        int s = stations.of(user).stationId();
        Map<String, Object> p = new HashMap<>();
        String sql = SELECT + where(s, q, p) + (q.order != null && q.order == 1 ? " ORDER BY o.gun_id, o.true_time DESC" : " ORDER BY o.true_time DESC, o.id DESC");
        if (q.page > 0) {
            sql += " LIMIT :lim OFFSET :off";
            p.put("lim", q.size);
            p.put("off", (q.page - 1) * q.size);
        }
        List<Map<String, Object>> rows = db.list(sql, p);
        ExcelExport.write(response, "Transactions", "Transactions", List.of(
                Col.of("Pistolet", "gunId"), Col.of("Énergie", "gradeName"), Col.of("Prix", "price"), Col.of("Volume", "volume"),
                Col.of("Montant", "amount"), Col.of("Payé", "truelyPay"), Col.of("Date de la transaction", "trueTime"), Col.of("N° de carte", "asn"),
                Col.of("Solde", "bal"), Col.of("N° de ticket", "posttc"), Col.of("Employé", "employeeName"), Col.of("Index", "volumeTotal"),
                Col.of("Poste", "shift"), Col.of("Immatriculation", "carNumber"), Col.of("Remise", "discount")), rows);
    }
}
