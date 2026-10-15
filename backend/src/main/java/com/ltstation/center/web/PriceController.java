package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.PriceInfos;
import com.ltstation.center.domain.repository.PriceInfosRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.DataVersionService;
import java.time.LocalDate;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration des prix unitaires par station et énergie (Main/PriceConfigure d'origine). */
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final Db db;
    private final PriceInfosRepository prices;
    private final CompanyScopeService scope;
    private final DataVersionService versions;

    public PriceController(Db db, PriceInfosRepository prices, CompanyScopeService scope, DataVersionService versions) {
        this.db = db;
        this.prices = prices;
        this.scope = scope;
        this.versions = versions;
    }

    /** Prix de la société de l'utilisateur, filtrés par région / ville / station (GetPriceList). */
    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user,
                                          @RequestParam(required = false) Integer regionId,
                                          @RequestParam(required = false) Integer cityId,
                                          @RequestParam(required = false) Integer stationId) {
        List<Integer> filter = scope.filterStations(user, regionId, cityId, stationId);
        StringBuilder sql = new StringBuilder("SELECT p.id, p.station_id, s.name station_name, p.grade_specific_id, "
                + "g.name grade_name, p.current_ppu, p.new_ppu, p.validate_time FROM price_infos p "
                + "LEFT JOIN stations s ON s.station_id = p.station_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = p.grade_specific_id WHERE p.company_id = :c");
        Map<String, Object> params = new HashMap<>();
        params.put("c", user.companyGroupId());
        if (filter != null) {
            sql.append(" AND p.station_id IN (:s)");
            params.put("s", filter);
        }
        sql.append(" ORDER BY p.station_id, p.grade_specific_id");
        return db.list(sql.toString(), params);
    }

    public record PriceForm(Integer stationId, Integer gradeId, Double newPrice, LocalDate validTime) {
    }

    /** Ajout (ICPriceAdd) : prix actuel = nouveau prix ; incrémente les versions de configuration et de prix. */
    @PostMapping
    @Transactional
    public PriceInfos add(@CurrentUser SessionUser user, @RequestBody PriceForm f) {
        validate(user, f);
        if (db.one("SELECT 1 FROM price_infos WHERE station_id = :s AND grade_specific_id = :g",
                Map.of("s", f.stationId(), "g", f.gradeId())) != null) {
            throw new ApiException("Ce prix existe déjà");
        }
        PriceInfos p = new PriceInfos();
        p.setStationId(f.stationId());
        p.setGradeSpecificId(f.gradeId());
        p.setCurrentPpu(f.newPrice());
        p.setNewPpu(f.newPrice());
        p.setCurrentDensity(0d);
        p.setNewdensity(0d);
        p.setValidateTime(f.validTime().atStartOfDay());
        p.setCompanyId(user.companyGroupId());
        p = prices.save(p);
        versions.bump("machine_cfg", "ppu");
        return p;
    }

    /**
     * Modification (ICPriceUpdate) : l'ancien nouveau prix devient le prix actuel ; les appareils
     * liés à l'ancien couple station/énergie sont reportés sur le nouveau.
     */
    @PutMapping("/{id}")
    @Transactional
    public PriceInfos update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody PriceForm f) {
        PriceInfos p = prices.findById(id).orElseThrow(() -> new ApiException("Information inexistante"));
        validate(user, f);
        if (db.one("SELECT 1 FROM price_infos WHERE id <> :id AND station_id = :s AND grade_specific_id = :g",
                Map.of("id", id, "s", f.stationId(), "g", f.gradeId())) != null) {
            throw new ApiException("Cette information existe déjà");
        }
        int oldStation = p.getStationId();
        int oldGrade = p.getGradeSpecificId();
        p.setStationId(f.stationId());
        p.setGradeSpecificId(f.gradeId());
        p.setCurrentPpu(p.getNewPpu());
        p.setNewPpu(f.newPrice());
        p.setValidateTime(f.validTime().atStartOfDay());
        p = prices.save(p);
        db.update("UPDATE air_device SET oil_id = :g, station_id = :s WHERE station_id = :os AND oil_id = :og",
                Map.of("g", f.gradeId(), "s", f.stationId(), "os", oldStation, "og", oldGrade));
        versions.bump("machine_cfg", "ppu");
        return p;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        PriceInfos p = prices.findById(id).orElseThrow(() -> new ApiException("Information inexistante"));
        if (!scope.stationIds(user).contains(p.getStationId())) {
            throw new ApiException("Ce prix ne fait pas partie de votre périmètre");
        }
        prices.delete(p);
    }

    private void validate(SessionUser user, PriceForm f) {
        if (f.stationId() == null || f.gradeId() == null || f.newPrice() == null || f.validTime() == null) {
            throw new ApiException("Informations incomplètes");
        }
        if (!scope.stationIds(user).contains(f.stationId())) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
    }
}
