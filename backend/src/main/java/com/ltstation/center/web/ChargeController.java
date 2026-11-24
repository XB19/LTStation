package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.TimeBuckets;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Historique des recharges en ligne (Main/ChargeList d'origine, table online_deposit). */
@RestController
@RequestMapping("/api/charges")
public class ChargeController {

    static final String[] CHARGE_TYPES = {"Recharge WeChat", "Recharge Alipay", "Recharge administrateur",
            "Recharge Boss (compte public)"};

    private final Db db;
    private final CompanyScopeService scope;
    private final boolean perCompany;

    public ChargeController(Db db, CompanyScopeService scope, @Value("${app.discount-per-company:true}") boolean perCompany) {
        this.db = db;
        this.scope = scope;
        this.perCompany = perCompany;
    }

    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = new HashMap<>();
        p.put("start", start == null ? LocalDate.now().atStartOfDay() : start);
        p.put("end", end == null ? LocalDateTime.now() : end);
        p.put("nodes", scope.nodeList(user));
        String where = " WHERE d.create_time BETWEEN :start AND :end" + (perCompany ? " AND d.company_id IN (:nodes)" : "");
        PageResult<Map<String, Object>> r = db.page("SELECT d.*, cu.vehicle_no car_number FROM online_deposit d "
                + "LEFT JOIN customer_card_infos ci ON ci.asn = d.asn LEFT JOIN customers cu ON cu.customer_id = ci.customer_id"
                + where, p, "d.create_time DESC", page, size);
        r.rows().forEach(row -> row.put("chargeTypeName", chargeType(row.get("chargeType"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        out.put("totalAmount", db.decimal("SELECT COALESCE(sum(d.amount), 0) FROM online_deposit d" + where, p)
                .setScale(2, RoundingMode.HALF_UP));
        return out;
    }

    /** Courbe des recharges (GetChargePie) : 0 heure, 1 jour, 2 mois, 3 trimestre, 4 année. */
    @GetMapping("/chart")
    public Map<String, Object> chart(@CurrentUser SessionUser user, @RequestParam(defaultValue = "0") int type,
                                     @RequestParam(required = false) String date, @RequestParam(required = false) String endDate) {
        TimeBuckets b = TimeBuckets.of(type, TimeBuckets.parseDate(date), endDate == null ? null : TimeBuckets.parseDate(endDate));
        BigDecimal[] values = new BigDecimal[b.size()];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        Map<String, Object> p = new HashMap<>();
        p.put("from", b.from());
        p.put("to", b.to());
        p.put("nodes", scope.nodeList(user));
        for (Map<String, Object> r : db.list("SELECT " + b.expr("create_time") + " dt, sum(amount) a FROM online_deposit "
                + "WHERE create_time >= :from AND create_time < :to" + (perCompany ? " AND company_id IN (:nodes)" : "")
                + " GROUP BY 1", p)) {
            int i = b.index(r.get("dt"));
            if (i >= 0) {
                values[i] = new BigDecimal(r.get("a").toString()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        List<String> labels = b.labels();
        if (type == 3) {
            BigDecimal[] q = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            for (int m = 0; m < 12; m++) {
                q[m / 3] = q[m / 3].add(values[m]);
            }
            values = q;
            labels = List.of("T1", "T2", "T3", "T4");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("values", values);
        return out;
    }

    static String chargeType(Object v) {
        int i = v instanceof Number n ? n.intValue() : -1;
        return i >= 0 && i < CHARGE_TYPES.length ? CHARGE_TYPES[i] : "";
    }
}
