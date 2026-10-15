package com.ltstation.center.station;

import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fenêtre principale « Station SoftWare » : transactions du poste (grille de gauche), totaux par employé,
 * état de chaque pistolet (onglets « Noz Message » et « Noz-Table ») et poste en cours (barre d'état).
 */
@RestController
@RequestMapping("/api/station/main")
public class StationMainController {

    private final Db db;
    private final StationContext stations;

    public StationMainController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    @GetMapping
    public Map<String, Object> main(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        StationContext.Shift shift = stations.currentShift(c.stationId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shift", shift == null ? null : shift.view());
        out.put("employee", c.employeeName());
        out.put("station", c.stationName());
        // Transactions non clôturées (postes ouverts), les plus récentes en premier
        String open = "o.station_id = :s AND (o.duty_id IS NULL OR o.duty_id IN (SELECT id FROM station_duty WHERE station_id = :s AND status = 0))";
        Map<String, Object> p = Map.of("s", c.stationId());
        out.put("transactions", db.list("SELECT o.id, o.gun_id noz, o.volume, o.truely_pay amount, o.true_time time, o.asn, o.payment_id "
                + "FROM oil_transactions o WHERE " + open + " AND o.true_time >= now() - interval '7 days' ORDER BY o.true_time DESC, o.id DESC LIMIT 300", p));
        out.put("employees", db.list("SELECT e.name emp, COALESCE(sum(o.volume), 0) total_volume, COALESCE(sum(o.truely_pay), 0) total_amount "
                + "FROM employees e LEFT JOIN oil_transactions o ON o.employee_id = e.employee_id AND " + open
                + " WHERE e.station_id = :s AND e.is_using = true GROUP BY e.name ORDER BY e.name", p));
        List<Map<String, Object>> nozzles = db.list("SELECT n.noz_id, n.grade_id, g.name grade_name, p.new_ppu price, "
                + "l.true_time last_time, l.volume last_volume, l.truely_pay last_amount, l.volume_total, l.asn last_asn, e.name last_employee "
                + "FROM station_noz n LEFT JOIN grade_specifics g ON g.grade_specific_id = n.grade_id "
                + "LEFT JOIN price_infos p ON p.station_id = n.station_id AND p.grade_specific_id = n.grade_id "
                + "LEFT JOIN LATERAL (SELECT * FROM oil_transactions o WHERE o.station_id = n.station_id AND o.gun_id = n.noz_id "
                + "ORDER BY o.true_time DESC, o.id DESC LIMIT 1) l ON true LEFT JOIN employees e ON e.employee_id = l.employee_id "
                + "WHERE n.station_id = :s ORDER BY n.noz_id", p);
        out.put("nozzles", nozzles);
        return out;
    }
}
