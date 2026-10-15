package com.ltstation.center.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Caméras de vidéosurveillance EZVIZ (Main/MonitorDeviceList, Main/MonitorDevice d'origine).
 * Le jeton d'accès est demandé à open.ys7.com avec la clé d'application configurée.
 */
@RestController
@RequestMapping("/api/monitor-devices")
public class MonitorDeviceController {

    private final Db db;
    private final CompanyScopeService scope;
    private final String appKey;
    private final String appSecret;
    private final RestClient http = RestClient.create();

    public MonitorDeviceController(Db db, CompanyScopeService scope,
                                   @Value("${app.ezviz.app-key:}") String appKey,
                                   @Value("${app.ezviz.app-secret:}") String appSecret) {
        this.db = db;
        this.scope = scope;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user) {
        return db.list("SELECT m.id, m.station_id, s.name station_name, m.device_name, m.device_url FROM monitor_device m "
                + "LEFT JOIN stations s ON s.station_id = m.station_id WHERE m.station_id IN (:s) ORDER BY m.id",
                Map.of("s", scope.stationIds(user)));
    }

    /** Jeton EZVIZ pour les lecteurs vidéo (GetMonitorDevice). */
    @GetMapping("/token")
    public Map<String, String> token() {
        if (appKey.isBlank() || appSecret.isBlank()) {
            throw new ApiException("La vidéosurveillance EZVIZ n'est pas configurée (app.ezviz.app-key / app-secret)");
        }
        JsonNode r = http.post().uri("https://open.ys7.com/api/lapp/token/get?appKey={k}&appSecret={s}", appKey, appSecret)
                .retrieve().body(JsonNode.class);
        String token = r == null ? "" : r.path("data").path("accessToken").asText("");
        if (token.isEmpty()) {
            throw new ApiException("Impossible d'obtenir le jeton EZVIZ : " + (r == null ? "" : r.path("msg").asText()));
        }
        return Map.of("accessToken", token);
    }

    public record MonitorForm(Integer stationId, String deviceName, String deviceUrl) {
    }

    @PostMapping
    @Transactional
    public void add(@CurrentUser SessionUser user, @RequestBody MonitorForm f) {
        check(user, f.stationId());
        db.update("INSERT INTO monitor_device (station_id, device_name, device_url) VALUES (:s, :n, :u)",
                Db.p("s", f.stationId(), "n", f.deviceName(), "u", f.deviceUrl()));
    }

    @PutMapping("/{id}")
    @Transactional
    public void update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody MonitorForm f) {
        check(user, f.stationId());
        if (db.update("UPDATE monitor_device SET station_id = :s, device_name = :n, device_url = :u WHERE id = :id",
                Db.p("s", f.stationId(), "n", f.deviceName(), "u", f.deviceUrl(), "id", id)) == 0) {
            throw new ApiException("L'appareil n'existe pas");
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable int id) {
        if (db.update("DELETE FROM monitor_device WHERE id = :id", Map.of("id", id)) == 0) {
            throw new ApiException("L'appareil n'existe pas");
        }
    }

    private void check(SessionUser user, Integer stationId) {
        if (stationId == null || !scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
    }
}
