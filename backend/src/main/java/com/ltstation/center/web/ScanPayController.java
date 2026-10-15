package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.FileStorageService;
import com.ltstation.center.service.WeChatService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paiement par QR code WeChat (Main/JoyFillScanPayTradeRecord, Main/JoyFillScanPayBarcodeCreate d'origine) :
 * historique des paiements, QR code du mini-programme par pistolet et remboursement manuel.
 * refundStatus : 0 = aucun, 1 = demande acceptée, 2 = demande refusée, 3 = notification reçue, 4 = notification en échec.
 */
@RestController
@RequestMapping("/api/scan-pay")
public class ScanPayController {

    private final Db db;
    private final CompanyScopeService scope;
    private final WeChatService wechat;
    private final FileStorageService files;
    private final String publicUrl;
    private final String defaultSubMchId;

    public ScanPayController(Db db, CompanyScopeService scope, WeChatService wechat, FileStorageService files,
                             @Value("${app.public-url:}") String publicUrl,
                             @Value("${app.wechat.scan-pay-sub-mch-id:}") String defaultSubMchId) {
        this.db = db;
        this.scope = scope;
        this.wechat = wechat;
        this.files = files;
        this.publicUrl = publicUrl;
        this.defaultSubMchId = defaultSubMchId;
    }

    private static final List<Col> EXPORT = List.of(Col.of("Station", "stationName"), Col.of("Pistolet", "gunNumber"),
            Col.of("Téléphone", "phoneNumber"), Col.of("Montant payé", "amount"), Col.of("Montant servi", "realRefuelAmount"),
            Col.of("Litres servis", "realRefuelLiters"), Col.of("Reste à rembourser", "refundBalance"),
            Col.of("Date de paiement", "payTime"), Col.of("N° de commande marchand", "outTradeNo"),
            Col.of("N° de remboursement", "outRefundNo"));

    /** Paiements effectués (GetData_tradeRecord : payment_state > 0). */
    @GetMapping("/orders")
    public PageResult<Map<String, Object>> orders(@CurrentUser SessionUser user, @RequestParam(required = false) Integer stationId,
                                                  @RequestParam(defaultValue = "-1") int refundStatus,
                                                  @RequestParam(defaultValue = "") String phoneNumber,
                                                  @RequestParam(defaultValue = "0") int gunNumber,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                  @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> p = new HashMap<>();
        return db.page("SELECT * FROM joy_fill_scan_pay_order" + where(user, p, stationId, refundStatus, phoneNumber, gunNumber, start, end),
                p, "pay_time DESC", page, size);
    }

    @GetMapping("/orders/export")
    public void export(@CurrentUser SessionUser user, @RequestParam(required = false) Integer stationId,
                       @RequestParam(defaultValue = "-1") int refundStatus, @RequestParam(defaultValue = "") String phoneNumber,
                       @RequestParam(defaultValue = "0") int gunNumber,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                       HttpServletResponse response) throws IOException {
        Map<String, Object> p = new HashMap<>();
        ExcelExport.write(response, "PaiementsQR", "Paiements", EXPORT, db.list("SELECT * FROM joy_fill_scan_pay_order"
                + where(user, p, stationId, refundStatus, phoneNumber, gunNumber, start, end) + " ORDER BY pay_time DESC", p));
    }

    private String where(SessionUser user, Map<String, Object> p, Integer stationId, int refundStatus, String phone, int gun,
                         LocalDateTime start, LocalDateTime end) {
        StringBuilder w = new StringBuilder(" WHERE payment_state > 0 AND station_id IN (:s)");
        p.put("s", scope.scopedStations(user, null, null, stationId));
        if (refundStatus > -1) {
            w.append(" AND refund_status = :rs");
            p.put("rs", refundStatus);
        }
        if (!phone.isBlank()) {
            w.append(" AND phone_number LIKE :ph");
            p.put("ph", "%" + phone.trim() + "%");
        }
        if (gun > 0) {
            w.append(" AND gun_number = :g");
            p.put("g", gun);
        }
        if (start != null) {
            w.append(" AND pay_time >= :start");
            p.put("start", start);
        }
        if (end != null) {
            w.append(" AND pay_time <= :end");
            p.put("end", end);
        }
        return w.toString();
    }

    public record RefundForm(BigDecimal amount, String subMchId) {
    }

    /** Remboursement manuel du reste dû (wxProgramScanPay_manualRefund). */
    @PostMapping("/orders/{id}/refund")
    @Transactional
    public Map<String, Object> refund(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody RefundForm f) {
        Map<String, Object> o = db.one("SELECT * FROM joy_fill_scan_pay_order WHERE id = :id", Map.of("id", id));
        if (o == null || !scope.stationIds(user).contains(((Number) o.get("stationId")).intValue())) {
            throw new ApiException("Commande introuvable : " + id);
        }
        BigDecimal amount = f.amount() != null ? f.amount() : new BigDecimal(String.valueOf(o.get("refundBalance") == null ? "0" : o.get("refundBalance")));
        if (amount.signum() <= 0) {
            throw new ApiException("Aucun montant à rembourser");
        }
        String refundNo = "LTNYTK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String sub = f.subMchId() != null && !f.subMchId().isBlank() ? f.subMchId()
                : (o.get("subMchId") != null ? o.get("subMchId").toString() : defaultSubMchId);
        Map<String, String> r = wechat.refund(sub, String.valueOf(o.get("transactionId")), refundNo,
                new BigDecimal(o.get("amount").toString()).movePointRight(2).intValue(), amount.movePointRight(2).intValue(),
                publicUrl.replaceAll("/+$", "") + "/JoyFillWxProgramScanPay/wxProgramScanPay_refundResult");
        boolean ok = "SUCCESS".equals(r.get("return_code")) && "SUCCESS".equals(r.get("result_code"));
        db.update("UPDATE joy_fill_scan_pay_order SET refund_status = :s, out_refund_no = :no, refund_id = :rid WHERE id = :id",
                Db.p("s", ok ? 1 : 2, "no", refundNo, "rid", r.getOrDefault("refund_id", r.get("result_code")), "id", id));
        if (!ok) {
            throw new ApiException("Remboursement refusé par WeChat : " + r.getOrDefault("err_code_des", r.getOrDefault("return_msg", "")));
        }
        return Map.of("outRefundNo", refundNo);
    }

    /** Pistolets d'une station pour le générateur de QR codes. */
    @GetMapping("/guns")
    public List<Map<String, Object>> guns(@CurrentUser SessionUser user, @RequestParam int stationId) {
        if (!scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
        return db.list("SELECT id, noz_id, device_no, oil_name FROM air_device WHERE station_id = :s AND is_run = true ORDER BY noz_id",
                Map.of("s", stationId));
    }

    /**
     * QR code du mini-programme pour un pistolet (wxProgramScanPay_getBarcode) ; l'image est mise en cache
     * dans /img/wxbarcode/{station}_{pistolet}.png.
     */
    @PostMapping("/barcode")
    public Map<String, String> barcode(@CurrentUser SessionUser user, @RequestBody Map<String, Integer> body) {
        Integer stationId = body.get("stationId");
        Integer gunId = body.get("gunId");
        if (stationId == null || gunId == null || stationId <= 0 || gunId <= 0) {
            throw new ApiException("Paramètres station / pistolet incorrects");
        }
        if (!scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
        String name = stationId + "_" + gunId + ".png";
        Path file = files.root().resolve("img").resolve("wxbarcode").resolve(name);
        try {
            if (!Files.exists(file) || Files.size(file) < 5000) {
                Files.createDirectories(file.getParent());
                Files.write(file, wechat.wxaCode("pages/scanPay/scanPay?stationId=" + stationId + "&gunId=" + gunId));
            }
        } catch (IOException e) {
            throw new ApiException("Enregistrement du QR code impossible : " + e.getMessage());
        }
        return Map.of("url", "/img/wxbarcode/" + name);
    }
}
