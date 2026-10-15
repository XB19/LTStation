package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.DataVersionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paramètres de la station (OilTypeAndPrice, StationNozzleSet, ShiftConfig d'origine) : énergies et prix,
 * pistolets (sans équipement, ils servent à ventiler les ventes) et postes de travail.
 */
@RestController
@RequestMapping("/api/station/setup")
public class StationSetupController {

    private final Db db;
    private final StationContext stations;
    private final DataVersionService versions;

    public StationSetupController(Db db, StationContext stations, DataVersionService versions) {
        this.db = db;
        this.stations = stations;
        this.versions = versions;
    }

    // ------------------------------------------------------------------ Énergies et prix

    /** Énergies vendues par la station avec leur prix (price_infos). */
    @GetMapping("/grades")
    public List<Map<String, Object>> grades(@CurrentUser SessionUser user) {
        int s = stations.of(user).stationId();
        return db.list("SELECT g.grade_specific_id grade_id, g.name, g.code, p.id price_id, p.new_ppu price, p.current_ppu previous_price, "
                + "p.validate_time FROM price_infos p JOIN grade_specifics g ON g.grade_specific_id = p.grade_specific_id "
                + "WHERE p.station_id = :s ORDER BY g.grade_specific_id", Map.of("s", s));
    }

    /** Toutes les énergies connues, pour en ajouter une à la station. */
    @GetMapping("/all-grades")
    public List<Map<String, Object>> allGrades(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        return db.list("SELECT grade_specific_id grade_id, name, code FROM grade_specifics WHERE company_id IS NULL OR company_id = 0 "
                + "OR company_id = :c ORDER BY grade_specific_id", Map.of("c", c.companyId()));
    }

    public record PriceForm(Integer gradeId, BigDecimal price, BigDecimal density, java.time.LocalDateTime validTime) {
    }

    @PostMapping("/grades")
    @Transactional
    public void addGrade(@CurrentUser SessionUser user, @RequestBody PriceForm f) {
        StationContext.Ctx c = stations.of(user);
        BigDecimal price = positive(f.price());
        if (f.gradeId() == null || db.one("SELECT 1 FROM grade_specifics WHERE grade_specific_id = :g", Map.of("g", f.gradeId())) == null) {
            throw new ApiException("Énergie introuvable");
        }
        if (db.one("SELECT 1 FROM price_infos WHERE station_id = :s AND grade_specific_id = :g", Map.of("s", c.stationId(), "g", f.gradeId())) != null) {
            throw new ApiException("Cette énergie est déjà vendue par la station");
        }
        db.update("INSERT INTO price_infos (station_id, grade_specific_id, current_ppu, new_ppu, current_density, newdensity, validate_time, "
                + "company_id) VALUES (:s, :g, :p, :p, 0, :d, COALESCE(:t, now()), :c)",
                Db.p("s", c.stationId(), "g", f.gradeId(), "p", price.doubleValue(), "d", f.density() == null ? 0 : f.density().doubleValue(),
                        "t", f.validTime(), "c", c.companyId()));
        logPrice(c.stationId(), f.gradeId(), price);
    }

    /** Changement de prix : l'ancien prix est conservé dans current_ppu et l'historique dans price_logs. */
    @PutMapping("/grades/{gradeId}")
    @Transactional
    public void updatePrice(@CurrentUser SessionUser user, @PathVariable int gradeId, @RequestBody PriceForm f) {
        StationContext.Ctx c = stations.of(user);
        BigDecimal price = positive(f.price());
        Map<String, Object> cur = db.one("SELECT new_ppu FROM price_infos WHERE station_id = :s AND grade_specific_id = :g",
                Map.of("s", c.stationId(), "g", gradeId));
        if (cur == null) {
            throw new ApiException("Cette énergie n'est pas vendue par la station");
        }
        boolean changed = new BigDecimal(cur.get("newPpu").toString()).compareTo(price) != 0;
        // Comme l'original : l'ancien prix passe dans current_ppu, la date d'effet est modifiable
        db.update("UPDATE price_infos SET current_ppu = CASE WHEN :ch THEN new_ppu ELSE current_ppu END, new_ppu = :p, "
                + "validate_time = COALESCE(:t, CASE WHEN :ch THEN now() ELSE validate_time END) WHERE station_id = :s AND grade_specific_id = :g",
                Db.p("ch", changed, "p", price.doubleValue(), "t", f.validTime(), "s", c.stationId(), "g", gradeId));
        db.update("UPDATE station_noz SET price = :p WHERE station_id = :s AND grade_id = :g", Map.of("p", price, "s", c.stationId(), "g", gradeId));
        logPrice(c.stationId(), gradeId, price);
    }

    public record FuelTypeForm(String code, String name) {
    }

    /** Code proposé pour une nouvelle énergie (CreateOilType : plus grand code + 1, 1001 par défaut). */
    @GetMapping("/fuel-types/next-code")
    public Map<String, Object> nextFuelCode(@CurrentUser SessionUser user) {
        stations.of(user);
        Integer max = db.jdbc().queryForObject("SELECT max(CAST(code AS integer)) FROM grade_specifics WHERE code ~ '^[0-9]{1,4}$'",
                Map.of(), Integer.class);
        return Map.of("code", String.valueOf(max == null ? 1001 : max + 1));
    }

    /** Nouvelle énergie (CreateOilType). */
    @PostMapping("/fuel-types")
    @Transactional
    public void addFuelType(@CurrentUser SessionUser user, @RequestBody FuelTypeForm f) {
        StationContext.Ctx c = stations.of(user);
        if (f.code() == null || f.code().isBlank() || f.name() == null || f.name().isBlank()) {
            throw new ApiException("Informations manquantes");
        }
        if (f.code().trim().length() > 4 || f.name().trim().length() > 30) {
            throw new ApiException("Code : 4 caractères maximum ; nom : 30 caractères maximum");
        }
        if (db.one("SELECT 1 FROM grade_specifics WHERE code = :c OR name = :n", Map.of("c", f.code().trim(), "n", f.name().trim())) != null) {
            throw new ApiException("Cette énergie existe déjà");
        }
        db.update("INSERT INTO grade_specifics (name, code, company_id) VALUES (:n, :c, :co)",
                Map.of("n", f.name().trim(), "c", f.code().trim(), "co", c.companyId()));
        versions.bump("grade_spec");
    }

    @DeleteMapping("/grades/{gradeId}")
    @Transactional
    public void removeGrade(@CurrentUser SessionUser user, @PathVariable int gradeId) {
        StationContext.Ctx c = stations.of(user);
        if (db.count("SELECT 1 FROM station_noz WHERE station_id = :s AND grade_id = :g", Map.of("s", c.stationId(), "g", gradeId)) > 0) {
            throw new ApiException("Des pistolets utilisent cette énergie : modifiez-les d'abord");
        }
        db.update("DELETE FROM price_infos WHERE station_id = :s AND grade_specific_id = :g", Map.of("s", c.stationId(), "g", gradeId));
        versions.bump("ppu");
    }

    private void logPrice(int stationId, int gradeId, BigDecimal price) {
        db.update("INSERT INTO price_logs (station_id, grade_specific_id, ppu, density, scope, validate_time, update_time) "
                + "VALUES (:s, :g, :p, 0, 'Station', now(), now())", Map.of("s", stationId, "g", gradeId, "p", price.doubleValue()));
        versions.bump("ppu");
    }

    // ------------------------------------------------------------------ Pistolets

    @GetMapping("/nozzles")
    public List<Map<String, Object>> nozzles(@CurrentUser SessionUser user) {
        int s = stations.of(user).stationId();
        return db.list("SELECT n.id, n.noz_id, n.grade_id, g.name grade_name, p.new_ppu price FROM station_noz n "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = n.grade_id "
                + "LEFT JOIN price_infos p ON p.station_id = n.station_id AND p.grade_specific_id = n.grade_id "
                + "WHERE n.station_id = :s ORDER BY n.noz_id", Map.of("s", s));
    }

    public record NozzleForm(Integer nozId, Integer gradeId) {
    }

    @PostMapping("/nozzles")
    @Transactional
    public void addNozzle(@CurrentUser SessionUser user, @RequestBody NozzleForm f) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> g = checkNozzle(c, f, 0);
        db.update("INSERT INTO station_noz (station_id, noz_id, company_id, grade_id, grade_name, price) VALUES (:s, :n, :c, :g, :gn, :p)",
                Db.p("s", c.stationId(), "n", f.nozId(), "c", c.companyId(), "g", f.gradeId(), "gn", g.get("name"), "p", g.get("price")));
    }

    @PutMapping("/nozzles/{id}")
    @Transactional
    public void updateNozzle(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody NozzleForm f) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> g = checkNozzle(c, f, id);
        if (db.update("UPDATE station_noz SET noz_id = :n, grade_id = :g, grade_name = :gn, price = :p WHERE id = :id AND station_id = :s",
                Db.p("n", f.nozId(), "g", f.gradeId(), "gn", g.get("name"), "p", g.get("price"), "id", id, "s", c.stationId())) == 0) {
            throw new ApiException("Pistolet introuvable");
        }
    }

    @DeleteMapping("/nozzles/{id}")
    @Transactional
    public void deleteNozzle(@CurrentUser SessionUser user, @PathVariable int id) {
        db.update("DELETE FROM station_noz WHERE id = :id AND station_id = :s", Map.of("id", id, "s", stations.of(user).stationId()));
    }

    private Map<String, Object> checkNozzle(StationContext.Ctx c, NozzleForm f, int id) {
        if (f.nozId() == null || f.nozId() < 1) {
            throw new ApiException("Numéro de pistolet incorrect");
        }
        if (db.one("SELECT 1 FROM station_noz WHERE station_id = :s AND noz_id = :n AND id <> :id",
                Map.of("s", c.stationId(), "n", f.nozId(), "id", id)) != null) {
            throw new ApiException("Ce numéro de pistolet existe déjà");
        }
        Map<String, Object> g = db.one("SELECT g.name, p.new_ppu price FROM price_infos p JOIN grade_specifics g "
                + "ON g.grade_specific_id = p.grade_specific_id WHERE p.station_id = :s AND p.grade_specific_id = :g",
                Db.p("s", c.stationId(), "g", f.gradeId()));
        if (g == null) {
            throw new ApiException("Choisissez une énergie vendue par la station");
        }
        return g;
    }

    // ------------------------------------------------------------------ Postes

    @GetMapping("/shifts")
    public List<Map<String, Object>> shifts(@CurrentUser SessionUser user) {
        return db.list("SELECT id, name, start_hour, end_hour, scope FROM station_shift_config WHERE station_id = :s ORDER BY start_hour, id",
                Map.of("s", stations.of(user).stationId()));
    }

    public record ShiftForm(String name, Integer startHour, Integer endHour, Integer scope) {
    }

    @PostMapping("/shifts")
    @Transactional
    public void addShift(@CurrentUser SessionUser user, @RequestBody ShiftForm f) {
        checkShift(f);
        db.update("INSERT INTO station_shift_config (station_id, name, start_hour, end_hour, scope) VALUES (:s, :n, :a, :b, :sc)",
                Map.of("s", stations.of(user).stationId(), "n", f.name().trim(), "a", f.startHour(), "b", f.endHour(), "sc", f.scope() == null ? 0 : f.scope()));
    }

    @PutMapping("/shifts/{id}")
    @Transactional
    public void updateShift(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody ShiftForm f) {
        checkShift(f);
        db.update("UPDATE station_shift_config SET name = :n, start_hour = :a, end_hour = :b, scope = :sc WHERE id = :id AND station_id = :s",
                Map.of("n", f.name().trim(), "a", f.startHour(), "b", f.endHour(), "sc", f.scope() == null ? 0 : f.scope(), "id", id, "s", stations.of(user).stationId()));
    }

    @DeleteMapping("/shifts/{id}")
    @Transactional
    public void deleteShift(@CurrentUser SessionUser user, @PathVariable int id) {
        db.update("DELETE FROM station_shift_config WHERE id = :id AND station_id = :s", Map.of("id", id, "s", stations.of(user).stationId()));
    }

    private static void checkShift(ShiftForm f) {
        if (f.name() == null || f.name().isBlank() || f.startHour() == null || f.endHour() == null
                || f.startHour() < 0 || f.startHour() > 23 || f.endHour() < 1 || f.endHour() > 24) {
            throw new ApiException("Nom et heures du poste obligatoires (début 0–23 h, fin 1–24 h)");
        }
    }

    static BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException("Le montant doit être supérieur à 0");
        }
        return v;
    }
}
