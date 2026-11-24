package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.TimeBuckets;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.Labels;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistiques des cartes (Main/CardSummary d'origine) : bilans par carte utilisateur et employé,
 * relevés de compte individuels et de groupe, soldes dormants.
 * Le lien carte ↔ journal se fait par account_service_logs.account_no = customer_account_id.
 */
@RestController
@RequestMapping("/api/card-summary")
public class CardSummaryController {

    private final Db db;
    private final CompanyScopeService scope;

    public CardSummaryController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    // ------------------------------------------------------------------ Bilans

    /** Bilan des cartes utilisateur actives (GetUserCardSummary). */
    @GetMapping("/users")
    public List<Map<String, Object>> users(@CurrentUser SessionUser user,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String cards = "SELECT ci.station_id, cu.name, cu.customer_id holder_id, ci.asn, CAST(ci.customer_account_id AS text) acc "
                + "FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id "
                + "WHERE ci.card_status_type_id = 0 AND substring(ci.asn, 1, 6) IN ('010001', 'FFFFFF') AND ci.station_id IN (:s)";
        return summarize(cards, start, end, scope.stationIds(user), true);
    }

    /** Bilan des cartes employé actives (GetEmployeeSummary). */
    @GetMapping("/employees")
    public List<Map<String, Object>> employees(@CurrentUser SessionUser user,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                               @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                               @RequestParam(required = false) Integer stationId) {
        String cards = "SELECT e.station_id, e.name, e.employee_id holder_id, ec.asn, CAST(ec.customer_account_id AS text) acc, "
                + "s.name station_name FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id "
                + "JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id "
                + "LEFT JOIN stations s ON s.station_id = e.station_id "
                + "WHERE ec.card_status_type_id = 0 AND substring(ec.asn, 1, 6) = '010004' AND e.station_id IN (:s)";
        return summarize(cards, start, end, scope.scopedStations(user, regionId, cityId, stationId), false);
    }

    /**
     * Pour chaque carte : solde initial (dernier solde avant le début), recharges et bonus (type 2),
     * annulations (type 12), consommations (type 11), solde final et écart.
     * Les comptes dont le numéro commence par 0 sont exclus, comme dans l'original.
     */
    private List<Map<String, Object>> summarize(String cardsSql, LocalDateTime start, LocalDateTime end, List<Integer> stations,
                                                boolean userCards) {
        Map<String, Object> p = Map.of("s", stations, "start", start, "end", end);
        List<Map<String, Object>> cards = db.list(cardsSql + " ORDER BY 3, 4", p);
        Map<String, BigDecimal> ini = lastBalances(start, p, "ini");
        Map<String, BigDecimal> fin = lastBalances(end, p, "fin");
        Map<String, Map<Integer, BigDecimal[]>> sums = new HashMap<>();
        for (Map<String, Object> r : db.list("SELECT account_no, transaction_type_id t, sum(amount) a, sum(fee_amount) f, "
                + "sum(discount_amount) d FROM account_service_logs WHERE trans_datetime BETWEEN :start AND :end "
                + "AND substring(account_no, 1, 1) <> '0' GROUP BY account_no, transaction_type_id", p)) {
            sums.computeIfAbsent((String) r.get("accountNo"), k -> new HashMap<>())
                    .put(((Number) r.get("t")).intValue(), new BigDecimal[]{n(r.get("a")), n(r.get("f")), n(r.get("d"))});
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, BigDecimal> total = new LinkedHashMap<>();
        for (Map<String, Object> c : cards) {
            String acc = (String) c.get("acc");
            Map<Integer, BigDecimal[]> s = sums.getOrDefault(acc, Map.of());
            BigDecimal[] dep = s.getOrDefault(2, zero());
            BigDecimal[] rev = s.getOrDefault(12, zero());
            BigDecimal[] con = s.getOrDefault(11, zero());
            BigDecimal iniB = ini.getOrDefault(acc, BigDecimal.ZERO);
            BigDecimal finB = fin.getOrDefault(acc, BigDecimal.ZERO);
            BigDecimal save = dep[0];
            BigDecimal free = dep[1].add(dep[2]);
            // Annulations : montant + frais/remise pour les utilisateurs, montant seul pour les employés (comme l'original)
            BigDecimal charge = userCards ? rev[0].add(rev[1]).add(rev[2]) : rev[0];
            BigDecimal amount = con[0];
            BigDecimal volume = userCards ? con[1] : con[1].add(con[2]);
            BigDecimal error = iniB.add(save).add(free).add(charge).subtract(amount).subtract(finB);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("asn", c.get("asn"));
            row.put("stationName", c.get("stationName"));
            row.put("name", c.get("name"));
            row.put("accountNo", c.get("holderId"));
            put(row, total, "iniBalance", iniB);
            put(row, total, "saveAmount", save);
            put(row, total, "freeAmount", free);
            put(row, total, "chargeAmount", charge);
            put(row, total, "amount", amount);
            put(row, total, "volume", volume);
            put(row, total, "finalBalance", finB);
            put(row, total, "error", error);
            out.add(row);
        }
        Map<String, Object> totalRow = new LinkedHashMap<>();
        totalRow.put("asn", "__total__");
        totalRow.put("name", "Total");
        total.forEach((k, v) -> totalRow.put(k, v.setScale(3, RoundingMode.HALF_UP)));
        out.add(totalRow);
        return out;
    }

    private Map<String, BigDecimal> lastBalances(LocalDateTime at, Map<String, Object> base, String key) {
        Map<String, Object> p = new HashMap<>(base);
        p.put(key, at);
        Map<String, BigDecimal> out = new HashMap<>();
        for (Map<String, Object> r : db.list("SELECT DISTINCT ON (account_no) account_no, balance FROM account_service_logs "
                + "WHERE trans_datetime <= :" + key + " AND substring(account_no, 1, 1) <> '0' "
                + "ORDER BY account_no, trans_datetime DESC", p)) {
            out.put((String) r.get("accountNo"), n(r.get("balance")));
        }
        return out;
    }

    // ------------------------------------------------------------------ Relevés

    /**
     * Relevé de compte (GetSingleBillReport) : holderClass 1 = utilisateur, 2 = employé, 3 = groupe (carte principale
     * et cartes membres). L'écart compare le solde après opération au solde précédent du même compte.
     */
    @GetMapping("/single-bill")
    public PageResult<Map<String, Object>> singleBill(@CurrentUser SessionUser user,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                      @RequestParam(defaultValue = "1") int holderClass,
                                                      @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String cardNo,
                                                      @RequestParam(defaultValue = "") String phone, @RequestParam(defaultValue = "") String carNo,
                                                      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        if (name.isBlank() && cardNo.isBlank() && phone.isBlank() && carNo.isBlank()) {
            throw new ApiException("Saisissez au moins un critère de recherche");
        }
        Map<String, Object> p = Db.p("n", name, "ph", phone, "car", carNo);
        List<String> asns = new ArrayList<>(switch (holderClass) {
            case 2 -> db.jdbc().queryForList("SELECT ec.asn FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id "
                    + "WHERE (e.name = :n AND :n <> '') OR (e.telephone = :ph AND :ph <> '')", p, String.class);
            case 3 -> groupAsns(Db.p("n", name, "ph", phone, "cno", "", "ct", "", "card", carNo), true);
            default -> db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers c ON c.customer_id = ci.customer_id "
                    + "WHERE (c.name = :n AND :n <> '') OR (c.telephone = :ph AND :ph <> '') OR (c.vehicle_no = :car AND :car <> '')",
                    p, String.class);
        });
        if (!cardNo.isBlank()) {
            asns.add(cardNo.trim());
        }
        return bill(asns, start, end, page, size);
    }

    /** Relevé de groupe (GetGroupBillReport) : carte du groupe et cartes de ses membres. */
    @GetMapping("/group-bill")
    public PageResult<Map<String, Object>> groupBill(@CurrentUser SessionUser user,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                     @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String cardNo,
                                                     @RequestParam(defaultValue = "") String phone,
                                                     @RequestParam(defaultValue = "") String certificateNo,
                                                     @RequestParam(defaultValue = "") String contact,
                                                     @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        List<String> asns = new ArrayList<>(groupAsns(Db.p("n", name, "ph", phone, "cno", certificateNo, "ct", contact, "card", ""), false));
        if (!cardNo.isBlank()) {
            asns.add(cardNo.trim());
        }
        return bill(asns, start, end, page, size);
    }

    private List<String> groupAsns(Map<String, Object> p, boolean mainCardOnly) {
        List<Integer> groups = db.jdbc().queryForList("SELECT g.group_id FROM groups g WHERE ((g.name = :n OR g.short_name = :n) AND :n <> '') "
                + "OR (g.telephone = :ph AND :ph <> '') OR (g.certificate_no = :cno AND :cno <> '') OR (g.contact = :ct AND :ct <> '') "
                + "OR g.group_id IN (SELECT group_id FROM group_card_infos WHERE asn = :card AND :card <> '')", p, Integer.class);
        List<String> asns = new ArrayList<>();
        for (Integer g : groups) {
            List<String> main = db.jdbc().queryForList("SELECT asn FROM group_card_infos WHERE group_id = :g LIMIT 1",
                    Map.of("g", g), String.class);
            if (main.isEmpty() || (mainCardOnly && !main.get(0).startsWith("010014"))) {
                continue;
            }
            asns.addAll(main);
            asns.addAll(db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE group_id = :g", Map.of("g", g), String.class));
        }
        return asns;
    }

    private PageResult<Map<String, Object>> bill(List<String> asns, LocalDateTime start, LocalDateTime end, int page, int size) {
        if (asns.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }
        PageResult<Map<String, Object>> r = db.page("SELECT * FROM (SELECT l.id, l.asn, l.account_no, l.transaction_type_id, "
                + "l.amount, l.fee_amount, l.discount_amount, l.balance, l.trans_datetime create_time, s.name station_name, "
                + "COALESCE(cu.name, e.name, g.name) holder_name, "
                + "lag(l.balance) OVER (PARTITION BY l.account_no ORDER BY l.trans_datetime, l.id) prev_balance "
                + "FROM account_service_logs l LEFT JOIN stations s ON s.station_id = l.station_id "
                + "LEFT JOIN customer_card_infos ci ON ci.asn = l.asn LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "LEFT JOIN employee_card_infos ec ON ec.asn = l.asn LEFT JOIN employees e ON e.employee_id = ec.employee_id "
                + "LEFT JOIN group_card_infos gc ON gc.asn = l.asn LEFT JOIN groups g ON g.group_id = gc.group_id "
                + "WHERE l.asn IN (:asns)) x WHERE x.create_time BETWEEN :start AND :end",
                Map.of("asns", asns, "start", start, "end", end), "x.create_time, x.id", page, size);
        r.rows().forEach(row -> {
            int t = ((Number) row.get("transactionTypeId")).intValue();
            BigDecimal amount = n(row.get("amount"));
            BigDecimal fee = n(row.get("feeAmount"));
            BigDecimal disc = n(row.get("discountAmount"));
            BigDecimal bal = n(row.get("balance"));
            BigDecimal prev = n(row.get("prevBalance"));
            BigDecimal error;
            switch (t) {
                case 2 -> {
                    error = bal.subtract(amount).subtract(fee).subtract(disc).subtract(prev);
                    row.put("discount", r3(fee.add(disc)));
                }
                case 12 -> {
                    error = bal.subtract(amount).subtract(fee).subtract(disc).subtract(prev);
                    amount = amount.add(disc);
                    row.put("discount", BigDecimal.ZERO);
                }
                case 11 -> {
                    error = bal.add(amount.abs()).subtract(prev);
                    row.put("volume", r3(fee));
                }
                default -> error = bal.add(amount).subtract(prev);
            }
            row.put("name", row.get("holderName"));
            row.put("account", row.get("accountNo"));
            row.put("operateType", Labels.transactionType(t));
            row.put("amount", r3(amount));
            row.put("balance", r3(bal));
            row.put("error", r3(error));
        });
        return r;
    }

    // ------------------------------------------------------------------ Soldes dormants

    /** Solde de chaque carte utilisateur active à une date (GetSedimentaryReport). */
    @GetMapping("/sedimentary")
    public Map<String, Object> sedimentary(@CurrentUser SessionUser user,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<Map<String, Object>> rows = db.list("SELECT ci.asn, cu.name, ci.account_no, l.balance final_balance, l.trans_datetime create_time "
                + "FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "LEFT JOIN LATERAL (SELECT balance, trans_datetime FROM account_service_logs x "
                + "WHERE x.account_no = CAST(ci.customer_account_id AS text) AND x.trans_datetime < :end "
                + "ORDER BY x.trans_datetime DESC LIMIT 1) l ON true "
                + "WHERE ci.card_status_type_id = 0 AND substring(ci.asn, 1, 6) = '010001' AND ci.station_id IN (:s) ORDER BY ci.asn",
                Map.of("end", end, "s", scope.stationIds(user)));
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            r.put("finalBalance", r3(n(r.get("finalBalance"))));
            sum = sum.add(n(r.get("finalBalance")));
        }
        return Map.of("rows", rows, "total", rows.size(), "sumAmount", sum.setScale(0, RoundingMode.HALF_UP));
    }

    /**
     * Évolution du solde dormant total (GetSedimentaryPie) : 0 = par heure, 1 = par jour, 2 = par mois.
     * Solde de départ = somme des derniers soldes avant la période, puis recharges (+ bonus) moins consommations et annulations.
     */
    @GetMapping("/sedimentary-chart")
    public Map<String, Object> sedimentaryChart(@CurrentUser SessionUser user, @RequestParam(defaultValue = "1") int type,
                                                @RequestParam(required = false) String date) {
        LocalDate d = TimeBuckets.parseDate(date);
        TimeBuckets b = TimeBuckets.of(type, d, null);
        Map<String, Object> p = Map.of("s", scope.stationIds(user), "from", b.from(), "to", b.to());
        String cards = "SELECT CAST(customer_account_id AS text) FROM customer_card_infos WHERE card_status_type_id = 0 "
                + "AND substring(asn, 1, 6) = '010001' AND station_id IN (:s)";
        BigDecimal balance = db.decimal("SELECT COALESCE(sum(balance), 0) FROM (SELECT DISTINCT ON (account_no) balance FROM "
                + "account_service_logs WHERE trans_datetime < :from AND account_no IN (" + cards + ") "
                + "ORDER BY account_no, trans_datetime DESC) z", p);
        BigDecimal[] delta = new BigDecimal[b.size()];
        java.util.Arrays.fill(delta, BigDecimal.ZERO);
        for (Map<String, Object> r : db.list("SELECT " + b.expr("trans_datetime") + " dt, transaction_type_id t, sum(amount) a, "
                + "sum(discount_amount + fee_amount) f FROM account_service_logs WHERE trans_datetime >= :from "
                + "AND trans_datetime < :to AND account_no IN (" + cards + ") GROUP BY 1, 2", p)) {
            int i = b.index(r.get("dt"));
            if (i < 0) {
                continue;
            }
            switch (((Number) r.get("t")).intValue()) {
                case 2 -> delta[i] = delta[i].add(n(r.get("a"))).add(n(r.get("f")));
                case 11, 12 -> delta[i] = delta[i].subtract(n(r.get("a")).abs());
                default -> {
                }
            }
        }
        BigDecimal[] values = new BigDecimal[b.size()];
        for (int i = 0; i < values.length; i++) {
            balance = balance.add(delta[i]);
            values[i] = r3(balance);
        }
        return Map.of("labels", b.labels(), "values", values);
    }

    // ------------------------------------------------------------------ Utilitaires

    private static void put(Map<String, Object> row, Map<String, BigDecimal> total, String key, BigDecimal v) {
        row.put(key, r3(v));
        total.merge(key, r3(v), BigDecimal::add);
    }

    private static BigDecimal[] zero() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static BigDecimal n(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static BigDecimal r3(BigDecimal v) {
        return v.setScale(3, RoundingMode.HALF_UP);
    }
}
