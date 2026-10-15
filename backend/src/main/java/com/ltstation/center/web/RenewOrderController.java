package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Historique des paiements de renouvellement d'activation (Main/JoyFillRenewOrder d'origine). */
@RestController
@RequestMapping("/api/renew-orders")
public class RenewOrderController {

    private final Db db;
    private final CompanyScopeService scope;

    public RenewOrderController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /**
     * paymentState : 2 = payé (valeur par défaut de l'écran d'origine), -1 = tous.
     * Un employé de station ne voit que sa station.
     */
    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user,
                                    @RequestParam(required = false) Integer stationId,
                                    @RequestParam(required = false) Integer gunNumber,
                                    @RequestParam(defaultValue = "2") int paymentState,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        StringBuilder where = new StringBuilder(" WHERE station_id IN (:stations)");
        Map<String, Object> p = new HashMap<>();
        p.put("stations", scope.scopedStations(user, null, null, stationId));
        if (paymentState > -1) {
            where.append(" AND payment_state = :ps");
            p.put("ps", paymentState);
        }
        if (gunNumber != null && gunNumber > 0) {
            where.append(" AND gun_number = :g");
            p.put("g", gunNumber);
        }
        if (start != null) {
            where.append(" AND pay_time >= :start");
            p.put("start", start);
        }
        if (end != null) {
            where.append(" AND pay_time <= :end");
            p.put("end", end);
        }
        PageResult<Map<String, Object>> rows = db.page("SELECT * FROM joy_fill_renew_order" + where, p, "pay_time DESC", page, size);
        BigDecimal total = db.decimal("SELECT COALESCE(sum(amount), 0) FROM joy_fill_renew_order" + where, p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows.rows());
        out.put("total", rows.total());
        out.put("totalAmount", total);
        return out;
    }
}
