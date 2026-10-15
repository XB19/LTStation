package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.Labels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historique des opérations des porteurs de carte (Main/LogSearch d'origine) : recharges,
 * consommations, annulations… issues d'account_service_logs, limitées aux cartes émises
 * par les stations du périmètre.
 */
@RestController
@RequestMapping("/api/user-logs")
public class UserLogController {

    private final Db db;
    private final CompanyScopeService scope;

    public UserLogController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    public record Filter(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                         String phone, String certificateNo, String name, Integer type, String carNumber) {
    }

    private record Query(String where, Map<String, Object> params) {
    }

    private Query build(SessionUser user, Filter f) {
        if (f.start() == null || f.end() == null) {
            throw new ApiException("Veuillez choisir une période");
        }
        StringBuilder w = new StringBuilder(" WHERE l.trans_datetime BETWEEN :start AND :end "
                + "AND l.asn IN (SELECT ci.asn FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "WHERE ci.station_id IN (:stations)");
        Map<String, Object> p = new HashMap<>();
        p.put("start", f.start());
        p.put("end", f.end());
        p.put("stations", scope.stationIds(user));
        if (notBlank(f.phone())) {
            w.append(" AND cu.telephone = :phone");
            p.put("phone", f.phone());
        }
        if (notBlank(f.certificateNo())) {
            w.append(" AND cu.certificate_no = :cno");
            p.put("cno", f.certificateNo());
        }
        if (notBlank(f.name())) {
            w.append(" AND cu.name = :name");
            p.put("name", f.name());
        }
        if (notBlank(f.carNumber())) {
            w.append(" AND cu.vehicle_no = :car");
            p.put("car", f.carNumber());
        }
        w.append(")");
        if (f.type() != null) {
            w.append(" AND l.transaction_type_id = :type");
            p.put("type", f.type());
        }
        return new Query(w.toString(), p);
    }

    private static final String SELECT = "SELECT l.id, cu.name user_name, cu.certificate_no, l.asn, s.name station_name, "
            + "cu.vehicle_no car_number, l.transaction_type_id, l.amount, l.fee_amount, l.discount_amount, l.balance, "
            + "l.trans_datetime operate_time, e.name employee_name FROM account_service_logs l "
            + "LEFT JOIN customer_card_infos ci ON ci.asn = l.asn LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
            + "LEFT JOIN stations s ON s.station_id = l.station_id LEFT JOIN employees e ON e.employee_id = l.employee_id";

    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user, @ModelAttribute Filter f) {
        Query q = build(user, f);
        List<Map<String, Object>> rows = db.list(SELECT + q.where() + " ORDER BY l.trans_datetime", q.params());
        rows.forEach(UserLogController::decorate);
        Map<String, Object> totals = totals(q);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("total", rows.size());
        out.put("totals", totals);
        return out;
    }

    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Filter f, HttpServletResponse response) throws IOException {
        Query q = build(user, f);
        List<Map<String, Object>> rows = db.list(SELECT + q.where() + " ORDER BY l.trans_datetime", q.params());
        rows.forEach(UserLogController::decorate);
        ExcelExport.write(response, "HistoriqueUtilisateurs", "Historique", List.of(
                Col.of("Nom", "userName"), Col.of("N° de pièce d'identité", "certificateNo"), Col.of("N° de carte", "asn"),
                Col.of("Station", "stationName"), Col.of("Immatriculation", "carNumber"), Col.of("Opération", "transactionType"),
                Col.of("Montant rechargé", "depositAmount"), Col.of("Montant offert", "disCountAmount"),
                Col.of("Montant consommé", "salerAmount"), Col.of("Volume", "salerVolume"), Col.of("Montant annulé", "reBackAmount"),
                Col.of("Solde", "balance"), Col.of("Date", "operateTime"), Col.of("Opérateur", "employeeName")), rows);
    }

    /** Répartition des montants selon le type d'opération (comme UserLogMessage d'origine). */
    private static void decorate(Map<String, Object> r) {
        int type = r.get("transactionTypeId") instanceof Number n ? n.intValue() : 0;
        r.put("transactionType", Labels.transactionType(type));
        BigDecimal amount = r3(r.get("amount"));
        if (type == 2) {
            r.put("depositAmount", amount);
            r.put("disCountAmount", r3(r.get("discountAmount")));
        } else if (type == 12) {
            r.put("reBackAmount", amount);
        } else {
            r.put("salerAmount", amount);
            r.put("salerVolume", r3(r.get("feeAmount")));
        }
        r.put("balance", r3(r.get("balance")));
    }

    /** Totaux (GetPersonLogTotal) : offert = frais + remise des recharges. */
    private Map<String, Object> totals(Query q) {
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal present = BigDecimal.ZERO;
        BigDecimal consume = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal reverse = BigDecimal.ZERO;
        for (Map<String, Object> r : db.list("SELECT l.transaction_type_id t, sum(l.amount) a, sum(l.fee_amount) f, "
                + "sum(l.discount_amount) d FROM account_service_logs l" + q.where() + " GROUP BY l.transaction_type_id", q.params())) {
            switch (((Number) r.get("t")).intValue()) {
                case 2 -> {
                    deposit = deposit.add(r3(r.get("a")));
                    present = present.add(r3(r.get("f")).add(r3(r.get("d"))));
                }
                case 11 -> {
                    consume = consume.add(r3(r.get("a")));
                    volume = volume.add(r3(r.get("f")));
                }
                case 12 -> reverse = reverse.add(r3(r.get("a")));
                default -> {
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("depositAmount", deposit);
        m.put("disCountAmount", present);
        m.put("salerAmount", consume);
        m.put("salerVolume", volume);
        m.put("reBackAmount", reverse);
        return m;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static BigDecimal r3(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(3, RoundingMode.HALF_UP);
    }
}
