package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Réglages de la station (table station_settings) : lignes du ticket (StationPrintForm : First…Sixth)
 * et autres réglages (OtherSetForm).
 */
@RestController
@RequestMapping("/api/station/settings")
public class StationSettingsController {

    private final Db db;
    private final StationContext stations;

    public StationSettingsController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    @GetMapping
    public Map<String, String> get(@CurrentUser SessionUser user) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, Object> r : db.list("SELECT name, value FROM station_settings WHERE station_id = :s ORDER BY name",
                Map.of("s", stations.of(user).stationId()))) {
            out.put((String) r.get("name"), (String) r.get("value"));
        }
        return out;
    }

    @PutMapping
    @Transactional
    public Map<String, String> put(@CurrentUser SessionUser user, @RequestBody Map<String, String> values) {
        int s = stations.of(user).stationId();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!e.getKey().matches("[A-Za-z0-9_.]{1,40}")) {
                throw new ApiException("Réglage invalide : " + e.getKey());
            }
            db.update("INSERT INTO station_settings (station_id, name, value) VALUES (:s, :n, :v) ON CONFLICT (station_id, name) "
                    + "DO UPDATE SET value = EXCLUDED.value", Db.p("s", s, "n", e.getKey(), "v", e.getValue()));
        }
        return get(user);
    }
}
