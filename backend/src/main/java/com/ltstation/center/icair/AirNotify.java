package com.ltstation.center.icair;

import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Appels sortants des appareils : plateforme partenaire (URLPoint), supervision des stations
 * et message d'abonnement WeChat. Jetons et clés viennent de la configuration, vides par défaut :
 * l'appel concerné est alors ignoré.
 */
@Component
public class AirNotify {

    private record Monitor(String companyUuid, String stationUuid, String key) {
    }

    private final Sugar db;
    private final String token;
    private final String monitorUrl;
    private final Map<Integer, Monitor> monitors = new HashMap<>();
    private final String wxAppId;
    private final String wxSecret;
    private final String wxTemplate;
    private final LegacyLog log = new LegacyLog("ICAir");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String wxToken;
    private long wxTokenUntil;

    public AirNotify(@Qualifier("centerSugar") Sugar db,
                     @Value("${app.air.partner-token:}") String token,
                     @Value("${app.air.monitor-url:}") String monitorUrl,
                     @Value("${app.air.monitor-stations:}") String stations,
                     @Value("${app.air.wx-app-id:}") String wxAppId,
                     @Value("${app.air.wx-secret:}") String wxSecret,
                     @Value("${app.air.wx-template:}") String wxTemplate) {
        this.db = db;
        this.token = token;
        this.monitorUrl = monitorUrl;
        this.wxAppId = wxAppId;
        this.wxSecret = wxSecret;
        this.wxTemplate = wxTemplate;
        // stationId:companyUuid:stationUuid:clé, séparés par des virgules
        for (String s : stations.split(",")) {
            String[] f = s.trim().split(":");
            if (f.length == 4) {
                monitors.put(Integer.parseInt(f[0]), new Monitor(f[1], f[2], f[3].substring(0, 16)));
            }
        }
    }

    public String token() {
        return token;
    }

    /** DealServer.HttpService : POST JSON, texte de la réponse, "" en cas d'échec. */
    public String post(String url, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Wire.json(body), StandardCharsets.UTF_8)).build();
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.writeLog("Appel " + url + " : " + ex.getMessage());
            return "";
        }
    }

    /** Notification à la plateforme partenaire d'un appareil. */
    public void partner(String urlPoint, String path, Row body) {
        log.writeLog(post(urlPoint + path, body.set("token", token)));
    }

    public boolean monitored(int stationId) {
        return monitors.containsKey(stationId) && !monitorUrl.isEmpty();
    }

    /** BaseToolMethod.AesEncrypt : AES/ECB, clé de 16 caractères, Base64. */
    private static String aes(String text, String key) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
        return Base64.getEncoder().encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpResponse<String> monitorPost(String path, Monitor m, Row data) throws Exception {
        String body = Wire.json(Row.of("data", aes(Wire.json(data), m.key())));
        return http.send(HttpRequest.newBuilder(URI.create(monitorUrl + path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("COMPANY-UUID", m.companyUuid())
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** monitoringSystem_tankAlarrm : alarme de cuve vers la supervision. */
    public void tankAlarm(int stationId, int tankId, String type, int state) {
        Monitor m = monitors.get(stationId);
        if (m == null || monitorUrl.isEmpty()) {
            return;
        }
        try {
            Row data = Row.of("companyUuid", m.companyUuid(), "stationUuid", m.stationUuid(), "state", state,
                "tankNo", 1, "note", " ", "type", type);
            HttpResponse<String> r = monitorPost("/open-api/station-alarm", m, data);
            db.insert("JoyFill_tankAlarrmData", new Row(data).set("requestTime", LocalDateTime.now())
                .set("requestStatus", r.statusCode()).set("requestResult", r.body()).set("stationId", stationId)
                .set("stationName", "").set("tankId", tankId).set("tankName", ""));
        } catch (Exception ex) {
            log.writeLog("Supervision, envoi de l'alarme : " + ex.getMessage());
        }
    }

    /** Commande de carburant vers la supervision. */
    public void order(Row oil, long id, Object unitPrice, Object amount, Object volume) throws Exception {
        Monitor m = monitors.get(oil.i("StationID"));
        if (m == null || monitorUrl.isEmpty()) {
            return;
        }
        Row data = Row.of("number", String.valueOf(id), "oilType", "OIL_20", "vehicleNo", oil.str("CarNumber"), "unitPrice", unitPrice,
            "amount", amount, "volume", volume, "fueledAt", oil.s("TrueTime"), "companyUuid", m.companyUuid(), "stationUuid", m.stationUuid());
        HttpResponse<String> r = monitorPost("/open-api/station-order", m, data);
        db.insert("JoyFill_refuelOrderData", new Row(data).set("requestStatus", r.statusCode()).set("requestResult", r.body())
            .set("stationId", oil.i("StationID")).set("stationName", "").set("energyId", oil.i("GradeSpecificID")).set("energyName", ""));
    }

    private synchronized String wxToken() throws Exception {
        if (wxToken == null || System.currentTimeMillis() > wxTokenUntil) {
            String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + wxAppId + "&secret=" + wxSecret;
            Row r = Wire.parseRow(http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body());
            wxToken = r.str("access_token");
            wxTokenUntil = System.currentTimeMillis() + 3_600_000L;
        }
        return wxToken;
    }

    /** PushMessage : avis de consommation au titulaire de la carte. */
    public void wxConsume(String openId, String asn, String amount, String time, String stationName) {
        if (openId == null || openId.isEmpty() || wxAppId.isEmpty() || wxSecret.isEmpty() || wxTemplate.isEmpty()) {
            return;
        }
        try {
            post("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + wxToken(), Row.of(
                "touser", openId, "template_id", wxTemplate, "data", Row.of(
                    "thing2", Row.of("value", stationName), "character_string1", Row.of("value", asn),
                    "time3", Row.of("value", time), "amount5", Row.of("value", amount))));
        } catch (Exception ex) {
            log.writeLog("Avis WeChat : " + ex.getMessage());
        }
    }
}
