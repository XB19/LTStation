package com.ltstation.center.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltstation.center.common.ApiException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Intégration WeChat : jeton du mini-programme (scanPay_appid), QR codes wxacode et
 * API WeChat Pay v2 (signature MD5, XML, certificat client pour les remboursements).
 */
@Service
public class WeChatService {

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String miniAppId;
    private final String miniAppSecret;
    private final String serviceAppId;
    private final String serviceMchId;
    private final String signKey;
    private final String certPath;
    private final String certPassword;

    private String miniToken = "";
    private Instant miniTokenExpiry = Instant.EPOCH;

    public WeChatService(ObjectMapper mapper,
                         @Value("${app.wechat.mini-app-id:}") String miniAppId,
                         @Value("${app.wechat.mini-app-secret:}") String miniAppSecret,
                         @Value("${app.wechat.app-id:}") String serviceAppId,
                         @Value("${app.wechat.service-mch-id:}") String serviceMchId,
                         @Value("${app.wechat.sign-key:}") String signKey,
                         @Value("${app.wechat.cert-path:}") String certPath,
                         @Value("${app.wechat.cert-password:}") String certPassword) {
        this.mapper = mapper;
        this.miniAppId = miniAppId;
        this.miniAppSecret = miniAppSecret;
        this.serviceAppId = serviceAppId;
        this.serviceMchId = serviceMchId;
        this.signKey = signKey;
        this.certPath = certPath;
        this.certPassword = certPassword.isBlank() ? serviceMchId : certPassword;
    }

    public String miniAppId() {
        return miniAppId;
    }

    /** Jeton d'accès stable du mini-programme (BaseAPI_new.GetTokenSeesion). */
    public synchronized String miniProgramToken() {
        if (!miniToken.isEmpty() && Instant.now().isBefore(miniTokenExpiry)) {
            return miniToken;
        }
        if (miniAppId.isBlank() || miniAppSecret.isBlank()) {
            throw new ApiException("Mini-programme WeChat non configuré (app.wechat.mini-app-id / mini-app-secret)");
        }
        try {
            String body = mapper.writeValueAsString(Map.of("grant_type", "client_credential", "appid", miniAppId,
                    "secret", miniAppSecret, "force_refresh", false));
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/stable_token"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode n = mapper.readTree(r.body());
            if (!n.hasNonNull("access_token")) {
                throw new ApiException("Jeton WeChat refusé : " + n.path("errmsg").asText(r.body()));
            }
            miniToken = n.get("access_token").asText();
            miniTokenExpiry = Instant.now().plusSeconds(Math.max(60, n.path("expires_in").asLong(7200) - 300));
            return miniToken;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Impossible de joindre WeChat : " + e.getMessage());
        }
    }

    /** QR code de mini-programme (wxa/getwxacode) pour une page donnée ; renvoie l'image PNG. */
    public byte[] wxaCode(String page) {
        try {
            String body = mapper.writeValueAsString(Map.of("path", page, "width", 430, "auto_color", false, "is_hyaline", true,
                    "env_version", "release"));
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(
                            "https://api.weixin.qq.com/wxa/getwxacode?access_token=" + miniProgramToken()))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String type = r.headers().firstValue("Content-Type").orElse("");
            if (r.statusCode() != 200 || type.contains("json") || r.body().length < 1000) {
                throw new ApiException("WeChat n'a pas généré le QR code : " + new String(r.body(), StandardCharsets.UTF_8));
            }
            return r.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Impossible de générer le QR code WeChat : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ WeChat Pay v2

    public static String nonce() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** Signature MD5 en majuscules des paramètres triés (WxPayData.MakeSign). */
    public static String sign(Map<String, String> params, String key) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> {
            if (!"sign".equals(k) && v != null && !v.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : "&").append(k).append('=').append(v);
            }
        });
        sb.append("&key=").append(key);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(sb.toString().getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toXml(Map<String, String> params) {
        StringBuilder sb = new StringBuilder("<xml>");
        params.forEach((k, v) -> sb.append('<').append(k).append("><![CDATA[").append(v).append("]]></").append(k).append('>'));
        return sb.append("</xml>").toString();
    }

    public static Map<String, String> fromXml(String xml) {
        Map<String, String> out = new TreeMap<>();
        Matcher m = Pattern.compile("<(\\w+)>(?:<!\\[CDATA\\[(.*?)]]>|([^<]*))</\\1>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            out.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return out;
    }

    /**
     * Remboursement d'une commande payée par QR code (wxProgramScanPay_manualRefund) en mode prestataire :
     * appid / mch_id du prestataire, sub_appid du mini-programme, sub_mch_id de la station.
     */
    public Map<String, String> refund(String subMchId, String transactionId, String outRefundNo, int totalFee, int refundFee,
                                      String notifyUrl) {
        if (certPath.isBlank() || !Files.exists(Path.of(certPath))) {
            throw new ApiException("Certificat WeChat Pay introuvable (app.wechat.cert-path)");
        }
        Map<String, String> p = new TreeMap<>();
        p.put("appid", serviceAppId);
        p.put("mch_id", serviceMchId);
        p.put("sub_appid", miniAppId);
        p.put("sub_mch_id", subMchId);
        p.put("nonce_str", nonce());
        p.put("sign_type", "MD5");
        p.put("transaction_id", transactionId);
        p.put("out_refund_no", outRefundNo);
        p.put("total_fee", String.valueOf(totalFee));
        p.put("refund_fee", String.valueOf(refundFee));
        p.put("refund_fee_type", "CNY");
        p.put("notify_url", notifyUrl);
        p.put("sign", sign(p, signKey));
        try (InputStream in = Files.newInputStream(Path.of(certPath))) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, certPassword.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, certPassword.toCharArray());
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(kmf.getKeyManagers(), null, null);
            HttpClient client = HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com/secapi/pay/refund"))
                    .header("Content-Type", "text/xml").POST(HttpRequest.BodyPublishers.ofString(toXml(p))).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return fromXml(r.body());
        } catch (Exception e) {
            throw new ApiException("Remboursement WeChat impossible : " + e.getMessage());
        }
    }
}
