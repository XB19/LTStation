package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.Stations;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Activation des stations et tarif mensuel (Main/ActiveStation d'origine). */
@RestController
@RequestMapping("/api/station-activation")
public class StationActivationController {

    private final Db db;
    private final StationsRepository stations;
    private final CompanyScopeService scope;

    public StationActivationController(Db db, StationsRepository stations, CompanyScopeService scope) {
        this.db = db;
        this.stations = stations;
        this.scope = scope;
    }

    /** Stations filtrées par région ou ville, triées par état (GetStationActive). */
    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user,
                                          @RequestParam(required = false) Integer regionId,
                                          @RequestParam(required = false) Integer cityId) {
        StringBuilder sql = new StringBuilder("SELECT s.station_id, s.name, s.is_active, s.station_price, s.two_year, "
                + "s.three_year, c.city_desc city_name, r.region_desc region_name FROM stations s "
                + "LEFT JOIN cities c ON c.city_id = s.city_id LEFT JOIN regions r ON r.region_id = c.region_id "
                + "WHERE s.company_id IN (:nodes)");
        Map<String, Object> p = new HashMap<>();
        p.put("nodes", scope.nodeList(user));
        if (cityId != null) {
            sql.append(" AND s.city_id = :c");
            p.put("c", cityId);
        } else if (regionId != null) {
            sql.append(" AND c.region_id = :r");
            p.put("r", regionId);
        }
        sql.append(" ORDER BY s.is_active, s.station_id");
        return db.list(sql.toString(), p);
    }

    public record ActivateForm(BigDecimal price, Integer twoYear, Integer threeYear) {
    }

    /**
     * Activation / changement de tarif (StationActiveSet). Passage d'un tarif nul à payant :
     * les appareils sans date de validité reçoivent la date du jour ; passage à gratuit : la date est effacée.
     */
    @PutMapping("/{stationId}")
    @Transactional
    public void activate(@CurrentUser SessionUser user, @PathVariable int stationId, @RequestBody ActivateForm f) {
        Stations s = stations.findById(stationId).orElseThrow(() -> new ApiException("La station n'existe pas"));
        if (!scope.nodeList(user).contains(s.getCompanyId())) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
        BigDecimal price = f.price() == null ? BigDecimal.ZERO : f.price();
        BigDecimal old = s.getStationPrice() == null ? BigDecimal.ZERO : s.getStationPrice();
        if (old.signum() == 0 && price.signum() > 0) {
            db.update("UPDATE air_device SET valid_time = :now WHERE station_id = :s AND (valid_time IS NULL OR valid_time = '')",
                    Map.of("s", stationId, "now", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        } else if (old.signum() > 0 && price.signum() == 0) {
            db.update("UPDATE air_device SET valid_time = '' WHERE station_id = :s AND valid_time <> ''", Map.of("s", stationId));
        }
        s.setStationPrice(price);
        s.setTwoYear(f.twoYear() == null ? 0 : f.twoYear());
        s.setThreeYear(f.threeYear() == null ? 0 : f.threeYear());
        if (s.getIsActive() != null && s.getIsActive() == -1) {
            s.setIsActive(0);
        }
        stations.save(s);
    }
}
