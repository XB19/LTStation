package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historiques des flottes : recharges du compte principal (JoyFillFleetRechargeRecord), répartitions vers
 * les chauffeurs (JoyFillFleetDistributeRecord) et consommations des cartes flotte (JoyFillFleetCardRecord).
 */
@RestController
@RequestMapping("/api/fleet-records")
public class FleetRecordController {

    private record Spec(String table, String timeColumn, List<Col> export) {
    }

    private static final Map<String, Spec> SPECS = Map.of(
            "recharge", new Spec("joy_fill_fleet_recharge_record", "recharge_time", List.of(
                    Col.of("Station", "stationName"), Col.of("Flotte", "teamName"), Col.of("Date", "rechargeTime"),
                    new Col("Opération", r -> Integer.valueOf(2).equals(r.get("rechargeType")) ? "Remboursement" : "Recharge"),
                    Col.of("Avant", "beforeBalance"), Col.of("Montant", "rechargeAmount"), Col.of("Après", "afterBalance"),
                    Col.of("Opérateur", "loginName"))),
            "distribute", new Spec("joy_fill_fleet_distribute_record", "distribute_time", List.of(
                    Col.of("Station", "stationName"), Col.of("Flotte", "teamName"), Col.of("Chauffeur", "driverName"),
                    Col.of("Date", "distributeTime"),
                    new Col("Opération", r -> Integer.valueOf(2).equals(r.get("rechargeType")) ? "Remboursement" : "Recharge"),
                    Col.of("Montant", "changeAmount"), Col.of("Principal avant", "masterBeforeBalance"),
                    Col.of("Principal après", "masterAfterBalance"), Col.of("Chauffeur avant", "driverBeforeBalance"),
                    Col.of("Chauffeur après", "driverAfterBalance"), Col.of("Opérateur", "loginName"))),
            "card", new Spec("joy_fill_fleet_card_record", "change_time", List.of(
                    Col.of("Station", "stationName"), Col.of("Station de consommation", "consumeStationName"),
                    Col.of("Flotte", "teamName"), Col.of("Chauffeur", "driverName"), Col.of("N° de carte", "mCardId"),
                    Col.of("Immatriculation", "numberPlate"), Col.of("Pistolet", "gunId"), Col.of("Énergie", "energyName"),
                    Col.of("Litres", "liters"), Col.of("Montant", "changeAmount"), Col.of("Avant", "beforeBalance"),
                    Col.of("Après", "afterBalance"), Col.of("Total", "totalLiters"), Col.of("Date", "changeTime"))));

    private final Db db;
    private final CompanyScopeService scope;

    public FleetRecordController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    @GetMapping("/{kind}")
    public PageResult<Map<String, Object>> list(@CurrentUser SessionUser user, @PathVariable String kind,
                                                @RequestParam(defaultValue = "-1") int teamId,
                                                @RequestParam(defaultValue = "-1") int rechargeType,
                                                @RequestParam(defaultValue = "-1") int accountType,
                                                @RequestParam(defaultValue = "") String teamName,
                                                @RequestParam(defaultValue = "") String driverName,
                                                @RequestParam(defaultValue = "") String mCardId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Spec spec = spec(kind);
        Map<String, Object> p = new HashMap<>();
        String sql = "SELECT * FROM " + spec.table() + where(user, spec, kind, p, teamId, rechargeType, accountType, teamName,
                driverName, mCardId, start, end);
        return db.page(sql, p, spec.timeColumn() + " DESC", page, size);
    }

    @GetMapping("/{kind}/export")
    public void export(@CurrentUser SessionUser user, @PathVariable String kind,
                       @RequestParam(defaultValue = "-1") int teamId, @RequestParam(defaultValue = "-1") int rechargeType,
                       @RequestParam(defaultValue = "-1") int accountType, @RequestParam(defaultValue = "") String teamName,
                       @RequestParam(defaultValue = "") String driverName, @RequestParam(defaultValue = "") String mCardId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                       HttpServletResponse response) throws IOException {
        Spec spec = spec(kind);
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list("SELECT * FROM " + spec.table() + where(user, spec, kind, p, teamId, rechargeType,
                accountType, teamName, driverName, mCardId, start, end) + " ORDER BY " + spec.timeColumn() + " DESC", p);
        ExcelExport.write(response, "Flottes", "Flottes", spec.export(), rows);
    }

    private String where(SessionUser user, Spec spec, String kind, Map<String, Object> p, int teamId, int rechargeType,
                         int accountType, String teamName, String driverName, String mCardId, LocalDateTime start, LocalDateTime end) {
        StringBuilder w = new StringBuilder(" WHERE station_id IN (:s)");
        p.put("s", scope.stationIds(user));
        if (teamId > -1) {
            w.append(" AND team_id = :t");
            p.put("t", teamId);
        }
        if (rechargeType > -1 && !kind.equals("card")) {
            w.append(" AND recharge_type = :rt");
            p.put("rt", rechargeType);
        }
        if (accountType > -1) {
            w.append(" AND account_type = :at");
            p.put("at", accountType);
        }
        if (!teamName.isBlank()) {
            w.append(" AND team_name LIKE :tn");
            p.put("tn", "%" + teamName.trim() + "%");
        }
        if (!driverName.isBlank() && !kind.equals("recharge")) {
            w.append(" AND driver_name LIKE :dn");
            p.put("dn", "%" + driverName.trim() + "%");
        }
        if (!mCardId.isBlank() && kind.equals("card")) {
            w.append(" AND m_card_id LIKE :mc");
            p.put("mc", "%" + mCardId.trim() + "%");
        }
        if (start != null) {
            w.append(" AND ").append(spec.timeColumn()).append(" >= :start");
            p.put("start", start);
        }
        if (end != null) {
            w.append(" AND ").append(spec.timeColumn()).append(" <= :end");
            p.put("end", end);
        }
        return w.toString();
    }

    private static Spec spec(String kind) {
        Spec s = SPECS.get(kind);
        if (s == null) {
            throw new com.ltstation.center.common.ApiException("Historique inconnu : " + kind);
        }
        return s;
    }
}
