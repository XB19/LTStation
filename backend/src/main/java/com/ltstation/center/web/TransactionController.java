package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.TimeBuckets;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.Labels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transactions des stations (Main/Transaction d'origine). */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final Db db;
    private final CompanyScopeService scope;

    public TransactionController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /**
     * Filtres (GetTransactionSql) : période (début/fin) ou mois de l'année en cours, région / ville / station,
     * énergie (code), nom du porteur, type de carte, immatriculation.
     * cardType : 0 employé, 1 utilisateur, 2 crédit, 3 application, 4 QR code, 5 sans carte.
     */
    public record Filter(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                         Integer month, Integer regionId, Integer cityId, Integer stationId, String oilCode,
                         String person, Integer cardType, String carNumber) {
    }

    private record Query(String where, Map<String, Object> params) {
    }

    private Query build(SessionUser user, Filter f) {
        if (f.start() == null && f.month() == null) {
            throw new ApiException("Veuillez choisir un mois ou une période");
        }
        StringBuilder w = new StringBuilder(" WHERE o.station_id IN (:stations)");
        Map<String, Object> p = new HashMap<>();
        p.put("stations", scope.scopedStations(user, f.regionId(), f.cityId(), f.stationId()));
        if (f.start() != null) {
            w.append(" AND o.true_time BETWEEN :start AND :end");
            p.put("start", f.start());
            p.put("end", f.end() == null ? LocalDateTime.now() : f.end());
        } else {
            // Mois (1-12) de l'année en cours
            w.append(" AND o.true_time >= :mfrom AND o.true_time < :mto");
            LocalDate first = LocalDate.of(LocalDate.now().getYear(), f.month(), 1);
            p.put("mfrom", first.atStartOfDay());
            p.put("mto", first.plusMonths(1).atStartOfDay());
        }
        if (f.oilCode() != null && !f.oilCode().isBlank()) {
            w.append(" AND o.grade_specific_id IN (SELECT grade_specific_id FROM grade_specifics WHERE code = :oil)");
            p.put("oil", f.oilCode());
        }
        if (f.person() != null && !f.person().isBlank()) {
            w.append(" AND o.asn IN (SELECT ci.asn FROM customer_card_infos ci JOIN customers c ON c.customer_id = ci.customer_id "
                    + "WHERE c.name = :person)");
            p.put("person", f.person());
        }
        if (f.cardType() != null) {
            switch (f.cardType()) {
                case 0 -> w.append(" AND substring(o.asn, 1, 6) = '010004'");
                case 1 -> w.append(" AND substring(o.asn, 1, 6) = '010001'");
                case 2 -> w.append(" AND substring(o.asn, 1, 6) = '010011'");
                case 3 -> w.append(" AND substring(o.asn, 1, 6) = '010015'");
                case 4 -> w.append(" AND o.t_type = 10");
                case 5 -> w.append(" AND substring(o.asn, 1, 6) = '000000' AND o.t_type <> 10");
                default -> {
                }
            }
        }
        if (f.carNumber() != null && !f.carNumber().isBlank()) {
            w.append(" AND o.car_number = :car");
            p.put("car", f.carNumber());
        }
        return new Query(w.toString(), p);
    }

    private static final String SELECT = "SELECT o.id, o.asn, o.station_id, s.name station_name, o.gun_id, o.t_type, o.car_number, "
            + "g.name oil_name, o.posttc, o.volume, o.truely_pay amount, o.bal balance, o.volume_total, o.true_time create_time, "
            + "o.error_flg_id, o.device_no FROM oil_transactions o LEFT JOIN stations s ON s.station_id = o.station_id "
            + "LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id";

    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user, @ModelAttribute Filter f,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Query q = build(user, f);
        PageResult<Map<String, Object>> r = db.page(SELECT + q.where(), q.params(), "o.true_time DESC", page, size);
        r.rows().forEach(TransactionController::decorate);
        Map<String, Object> totals = db.one("SELECT COALESCE(sum(o.truely_pay), 0) amount, COALESCE(sum(o.volume), 0) volume "
                + "FROM oil_transactions o" + q.where(), q.params());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        out.put("totalAmount", totals.get("amount"));
        out.put("totalVolume", totals.get("volume"));
        return out;
    }

    /** Export Excel avec les mêmes filtres (ExportExcel). */
    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Filter f, HttpServletResponse response) throws IOException {
        Query q = build(user, f);
        List<Map<String, Object>> rows = db.list(SELECT + q.where() + " ORDER BY o.true_time DESC", q.params());
        rows.forEach(TransactionController::decorate);
        ExcelExport.write(response, "Transactions", "Transactions", List.of(
                Col.of("N° de carte", "asn"), Col.of("Station", "stationName"), Col.of("N° de pistolet", "gunId"),
                Col.of("Type de transaction", "tradeType"), Col.of("Immatriculation", "carNumber"),
                Col.of("Énergie", "oilName"), Col.of("Volume", "volume"), Col.of("Montant", "amount"),
                Col.of("Solde", "balance"), Col.of("Volume cumulé", "volumeTotal"), Col.of("Date", "createTime"),
                Col.of("Erreur", "error")), rows);
    }

    /** Courbe montant / volume (GetTransactionPie) : 0 = par heure, 1 = par jour, 2 = par mois, 4 = par année. */
    @GetMapping("/chart")
    public Map<String, Object> chart(@CurrentUser SessionUser user, @RequestParam(defaultValue = "0") int type,
                                     @RequestParam(required = false) String date, @RequestParam(required = false) String endDate,
                                     @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                     @RequestParam(required = false) Integer stationId) {
        TimeBuckets b = TimeBuckets.of(type == 3 ? 4 : type, TimeBuckets.parseDate(date),
                endDate == null ? null : TimeBuckets.parseDate(endDate));
        BigDecimal[] amount = zeros(b.size());
        BigDecimal[] volume = zeros(b.size());
        for (Map<String, Object> r : db.list("SELECT " + b.expr("true_time") + " dt, sum(truely_pay) a, sum(volume) v "
                + "FROM oil_transactions WHERE station_id IN (:s) AND true_time >= :from AND true_time < :to GROUP BY 1",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId), "from", b.from(), "to", b.to()))) {
            int i = b.index(r.get("dt"));
            if (i >= 0) {
                amount[i] = round(r.get("a"));
                volume[i] = round(r.get("v"));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", b.labels());
        out.put("amount", amount);
        out.put("volume", volume);
        return out;
    }

    public record RetrieveForm(String deviceNo, Integer nozId, String posStart, String posEnd) {
    }

    /**
     * Demande de récupération de transactions manquantes à un appareil (GetRetrieve) :
     * plage POSTTC en hexadécimal, enregistrée dans air_get_ol_d_transaction.
     */
    @PostMapping("/retrieve")
    @Transactional
    public void retrieve(@RequestBody RetrieveForm f) {
        try {
            db.update("INSERT INTO air_get_ol_d_transaction (device_no, ps_start, ps_end) VALUES (:d, :s, :e)",
                    Db.p("d", f.deviceNo(), "s", Integer.parseInt(f.posStart().trim(), 16), "e", Integer.parseInt(f.posEnd().trim(), 16)));
        } catch (NumberFormatException | NullPointerException e) {
            throw new ApiException("Valeurs POSTTC invalides (hexadécimal attendu)");
        }
    }

    /** Ajoute les libellés calculés (type de carte, type de transaction, erreur) et nettoie l'immatriculation. */
    static void decorate(Map<String, Object> r) {
        Integer tType = r.get("tType") instanceof Number n ? n.intValue() : null;
        r.put("cardType", Labels.cardType((String) r.get("asn"), tType));
        r.put("tradeType", "Consommation");
        r.put("error", Labels.transactionError(r.get("errorFlgId") instanceof Number n ? n.intValue() : 0));
        if (tType != null && tType == 10) {
            r.put("asn", "00000000000000000000");
        }
        String car = r.get("carNumber") == null ? "" : r.get("carNumber").toString().replaceAll("\\s+", "");
        r.put("carNumber", car.length() == 15 ? "" : car);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static BigDecimal round(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
