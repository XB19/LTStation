package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService.Operator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Contexte du logiciel Station : employé connecté, station, et postes de travail.
 * Comme dans le logiciel d'origine, les postes suivent les horaires configurés (ShiftConfig) :
 * le poste courant est déterminé par l'heure, son numéro est AAAAMMJJ + n° du poste sur 2 chiffres.
 */
@Service
public class StationContext {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Db db;

    public StationContext(Db db) {
        this.db = db;
    }

    public record Ctx(int stationId, String stationName, int companyId, int employeeId, String employeeName) {

        public Operator operator() {
            return new Operator(employeeId, employeeName, stationId);
        }
    }

    /** Poste de travail : configuration, journée de rattachement, numéro et bornes horaires. */
    public record Shift(int configId, int index, String name, LocalDate day, String shift, LocalDateTime start, LocalDateTime end, int scope) {

        public Map<String, Object> view() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("configId", configId);
            m.put("name", name);
            m.put("day", day);
            m.put("shift", shift);
            m.put("start", start);
            m.put("end", end);
            return m;
        }
    }

    public Ctx of(SessionUser user) {
        if (user.getStationId() == null || user.getEmpId() <= 0) {
            throw new ApiException("Connectez-vous avec un compte employé de station");
        }
        Map<String, Object> s = db.one("SELECT name, company_id FROM stations WHERE station_id = :s", Map.of("s", user.getStationId()));
        if (s == null) {
            throw new ApiException("La station de l'employé est introuvable");
        }
        return new Ctx(user.getStationId(), (String) s.get("name"), s.get("companyId") instanceof Number n ? n.intValue() : 0,
                user.getEmpId(), user.getEmployeesName());
    }

    public List<Map<String, Object>> configs(int stationId) {
        return db.list("SELECT id, name, start_hour, end_hour, scope FROM station_shift_config WHERE station_id = :s ORDER BY start_hour, id",
                Map.of("s", stationId));
    }

    /** Poste d'une journée (GetDateTime d'origine) : un poste de nuit (début ≥ fin) se termine le lendemain. */
    public Shift shift(int stationId, LocalDate day, int configId) {
        List<Map<String, Object>> cfg = configs(stationId);
        for (int i = 0; i < cfg.size(); i++) {
            Map<String, Object> c = cfg.get(i);
            if (((Number) c.get("id")).intValue() == configId) {
                int a = ((Number) c.get("startHour")).intValue();
                int b = ((Number) c.get("endHour")).intValue();
                LocalDateTime start = day.atTime(a, 0);
                LocalDateTime end = a >= b ? day.plusDays(1).atTime(b % 24, 0) : (b == 24 ? day.atTime(23, 59, 59) : day.atTime(b, 0));
                return new Shift(configId, i + 1, (String) c.get("name"), day, day.format(DAY) + String.format("%02d", i + 1), start, end,
                        ((Number) c.get("scope")).intValue());
            }
        }
        throw new ApiException("Poste introuvable");
    }

    /** Poste en cours selon l'heure, ou null si aucun poste n'est configuré. */
    public Shift currentShift(int stationId) {
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> c : configs(stationId)) {
            int a = ((Number) c.get("startHour")).intValue();
            int b = ((Number) c.get("endHour")).intValue();
            int h = now.getHour();
            boolean in = a < b ? h >= a && h < b : h >= a || h < b;
            if (in) {
                LocalDate day = a >= b && h < b ? now.toLocalDate().minusDays(1) : now.toLocalDate();
                return shift(stationId, day, ((Number) c.get("id")).intValue());
            }
        }
        return null;
    }

    /** Enregistrement du poste (créé au besoin) ; renvoie la ligne station_duty. */
    public Map<String, Object> duty(int stationId, Shift s, int employeeId) {
        db.update("INSERT INTO station_duty (station_id, shift, config_id, open_time, open_employee_id, status) VALUES (:st, :sh, :c, :t, :e, 0) "
                + "ON CONFLICT (station_id, shift) DO NOTHING",
                Map.of("st", stationId, "sh", s.shift(), "c", s.configId(), "t", s.start(), "e", employeeId));
        return db.one("SELECT d.*, c.name shift_name FROM station_duty d LEFT JOIN station_shift_config c ON c.id = d.config_id "
                + "WHERE d.station_id = :st AND d.shift = :sh", Map.of("st", stationId, "sh", s.shift()));
    }

    /** Poste courant (non clôturé) auquel rattacher une vente ou une recharge. */
    public int requireOpenDuty(int stationId) {
        return requireOpenDuty(stationId, 0);
    }

    public int requireOpenDuty(int stationId, int employeeId) {
        Shift s = currentShift(stationId);
        if (s == null) {
            throw new ApiException("Configurez d'abord les postes (Paramètres système > Gestion des postes)");
        }
        Map<String, Object> d = duty(stationId, s, employeeId);
        if (((Number) d.get("status")).intValue() != 0) {
            throw new ApiException("Le poste " + s.shift() + " est déjà clôturé");
        }
        return ((Number) d.get("id")).intValue();
    }

    /** Poste courant (ligne station_duty), ou null. */
    public Map<String, Object> openDuty(int stationId) {
        Shift s = currentShift(stationId);
        return s == null ? null : duty(stationId, s, 0);
    }
}
