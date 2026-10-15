package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informations des cuves : cuves cloud (Main/TankMessage), cuves des stations jaugées
 * (Main/StationTankMessage) et historique des alarmes (Main/TankAlarmShow).
 */
@RestController
@RequestMapping("/api/tanks")
public class TankController {

    private static final Map<Integer, String> ALARMS = Map.ofEntries(
            Map.entry(1, "Alarme électrostatique"), Map.entry(10, "Électrostatique revenue à la normale"),
            Map.entry(2, "Niveau trop bas"), Map.entry(3, "Niveau trop haut"), Map.entry(32, "Niveau revenu à la normale"),
            Map.entry(4, "Température trop basse"), Map.entry(5, "Température trop haute"),
            Map.entry(45, "Température revenue à la normale"), Map.entry(6, "Fuite de gaz"),
            Map.entry(60, "Fuite de gaz résorbée"), Map.entry(7, "Fuite de cuve"), Map.entry(70, "Fuite de cuve résorbée"));

    private final Db db;
    private final CompanyScopeService scope;

    public TankController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /** État actuel des cuves cloud (GetTankData). */
    @GetMapping("/real")
    public List<Map<String, Object>> real(@CurrentUser SessionUser user, @RequestParam(required = false) Integer regionId,
                                          @RequestParam(required = false) Integer cityId,
                                          @RequestParam(required = false) Integer stationId) {
        return db.list("SELECT t.id, s.name station_name, t.tank_name, g.name grade_type, t.current_height tank_height, "
                + "t.current_oil tank_volume, t.update_time create_time FROM air_tank t "
                + "LEFT JOIN stations s ON s.station_id = t.station_id LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "WHERE t.is_run = true AND t.station_id IN (:s) ORDER BY t.station_id",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId)));
    }

    /** Historique des niveaux des cuves cloud (GetTankHistory). */
    @GetMapping("/history")
    public PageResult<Map<String, Object>> history(@CurrentUser SessionUser user,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                   @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                                   @RequestParam(required = false) Integer stationId, @RequestParam(required = false) Integer tankId,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = period(start, end);
        p.put("s", scope.scopedStations(user, regionId, cityId, stationId));
        String where = " WHERE h.create_time BETWEEN :start AND :end AND h.station_id IN (:s)";
        if (tankId != null) {
            where += " AND h.tank_id = :t";
            p.put("t", tankId);
        }
        return db.page("SELECT h.id, s.name station_name, t.tank_name, h.height tank_height, h.volume tank_volume, "
                + "h.create_time FROM tank_history h LEFT JOIN stations s ON s.station_id = h.station_id "
                + "LEFT JOIN air_tank t ON t.id = h.tank_id" + where, p, "h.create_time DESC", page, size);
    }

    /** État actuel des cuves jaugées des stations (GetStationTankData, table tank_status). */
    @GetMapping("/station-real")
    public List<Map<String, Object>> stationReal(@CurrentUser SessionUser user, @RequestParam(required = false) Integer regionId,
                                                 @RequestParam(required = false) Integer cityId,
                                                 @RequestParam(required = false) Integer stationId) {
        return db.list("SELECT t.id || '-' || t.station_id row_key, t.id tank_name, s.name station_name, g.name grade_type, "
                + "t.oil_height tank_height, t.oil_vol tank_volume, t.time create_time FROM tank_status t "
                + "LEFT JOIN stations s ON s.station_id = t.station_id LEFT JOIN grade_specifics g ON g.code = t.oil_code "
                + "WHERE t.station_id IN (:s) ORDER BY t.station_id, t.id",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId)));
    }

    /** Historique des jaugeages des stations (GetStationTankHistory, table tank_measurements). */
    @GetMapping("/station-history")
    public PageResult<Map<String, Object>> stationHistory(@CurrentUser SessionUser user,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                          @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                                          @RequestParam(required = false) Integer stationId, @RequestParam(required = false) Integer tankId,
                                                          @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = period(start, end);
        p.put("s", scope.scopedStations(user, regionId, cityId, stationId));
        String where = " WHERE m.time BETWEEN :start AND :end AND m.station_id IN (:s)";
        if (tankId != null) {
            where += " AND m.tank_code = :t";
            p.put("t", tankId);
        }
        return db.page("SELECT m.id, s.name station_name, m.tank_code tank_name, m.oil_height tank_height, "
                + "m.oil_volume tank_volume, m.time create_time FROM tank_measurements m "
                + "LEFT JOIN stations s ON s.station_id = m.station_id" + where, p, "m.time DESC", page, size);
    }

    /** Historique des alarmes des cuves cloud actives (GetAirDeviceSafe). */
    @GetMapping("/alarms")
    public List<Map<String, Object>> alarms(@CurrentUser SessionUser user) {
        List<Map<String, Object>> rows = db.list("SELECT h.id, s.name station_name, g.name oil_name, h.tank_name, h.alarm_type_id, "
                + "h.update_time FROM tank_alarm_history h JOIN air_tank t ON t.id = h.tank_id AND t.is_run = true "
                + "LEFT JOIN stations s ON s.station_id = h.station_id LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "WHERE t.station_id IN (:s) ORDER BY h.update_time DESC", Map.of("s", scope.stationIds(user)));
        rows.forEach(r -> {
            r.put("alarmType", ALARMS.getOrDefault(r.get("alarmTypeId") instanceof Number n ? n.intValue() : -1, ""));
            if (r.get("updateTime") instanceof Number n) {
                r.put("updateTime", LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault()));
            }
        });
        return rows;
    }

    private static Map<String, Object> period(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> p = new HashMap<>();
        p.put("start", start == null ? LocalDate.now().atStartOfDay() : start);
        p.put("end", end == null ? LocalDateTime.now() : end);
        return p;
    }
}
