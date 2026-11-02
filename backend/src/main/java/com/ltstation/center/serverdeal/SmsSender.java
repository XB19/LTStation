package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.LegacyLog;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Envoi de SMS côté Center : même service que Nozzle.SendNote du logiciel Station ; identifiants dans la configuration. */
@Component
public class SmsSender {

    private static final String URL = "http://api.sierrahive.com/v1/messages/sms";

    private final String clientId;
    private final String clientSecret;
    private final String token;
    private final LegacyLog log = new LegacyLog("Sms");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SmsSender(@Value("${app.sms.client-id:}") String clientId, @Value("${app.sms.client-secret:}") String clientSecret,
                     @Value("${app.sms.token:}") String token) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.token = token;
    }

    /** false si le service n'est pas configuré ou répond en erreur ; l'appelant décide de la suite. */
    public boolean send(String to, String content) {
        if (clientId.isEmpty() || to == null || to.isEmpty()) {
            log.writeLog("SMS non envoyé (service non configuré ou numéro vide)");
            return false;
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("clientid", clientId);
        q.put("clientsecret", clientSecret);
        q.put("token", token);
        q.put("from", "NP");
        q.put("to", to);
        q.put("reference", "TXNREF123");
        q.put("content", content);
        String query = q.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(URL + "?" + query)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return r.statusCode() / 100 == 2;
        } catch (Exception ex) {
            log.writeLog("SMS : " + ex.getMessage());
            return false;
        }
    }
}
