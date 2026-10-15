package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.domain.entity.GPRSAccount;
import com.ltstation.center.domain.repository.GPRSAccountRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tableau de bord (Main/Index d'origine). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final Db db;
    private final CompanyScopeService scope;
    private final GPRSAccountRepository accounts;

    public DashboardController(Db db, CompanyScopeService scope, GPRSAccountRepository accounts) {
        this.db = db;
        this.scope = scope;
        this.accounts = accounts;
    }

    public record Stats(long online, long offline, BigDecimal todayVolume, BigDecimal todayAmount) {
    }

    /**
     * Compteurs du haut de page (MainController.Index).
     * RoleID 0 : appareils et ventes du périmètre de société ; RoleID 1 : comptes GPRS
     * rattachés ; sinon : son propre compte GPRS.
     */
    @GetMapping("/stats")
    public Stats stats(@CurrentUser SessionUser user) {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = LocalDate.now().atTime(23, 59, 59);
        Optional<GPRSAccount> cus = accounts.findById(user.getUserId());
        int roleId = cus.map(GPRSAccount::getRoleId).orElse(user.isSystemManager() ? 0 : -1);
        if (cus.isEmpty() && !user.isSystemManager()) {
            return new Stats(0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Map<String, Object> p = new HashMap<>();
        p.put("from", from);
        p.put("to", to);
        long online = 0;
        long offline = 0;
        BigDecimal volume;
        BigDecimal amount;
        if (roleId == 0) {
            p.put("nodes", scope.nodeList(user));
            p.put("stations", scope.stationIds(user));
            for (Map<String, Object> r : db.list("SELECT is_online, count(*) n FROM noz_real_date_view "
                    + "WHERE company_id IN (:nodes) GROUP BY is_online", p)) {
                long n = ((Number) r.get("n")).longValue();
                if (Boolean.TRUE.equals(r.get("isOnline"))) {
                    online += n;
                } else {
                    offline += n;
                }
            }
            amount = db.decimal("SELECT COALESCE(sum(truely_pay), 0) FROM oil_transactions WHERE station_id IN (:stations) "
                    + "AND true_time BETWEEN :from AND :to", p);
            volume = db.decimal("SELECT COALESCE(sum(volume), 0) FROM oil_transactions WHERE station_id IN (:stations) "
                    + "AND true_time BETWEEN :from AND :to", p);
        } else {
            List<Integer> users = roleId == 1
                    ? accounts.findByBelongToIdOrId(user.getUserId(), user.getUserId()).stream().map(GPRSAccount::getId).toList()
                    : List.of(user.getUserId());
            p.put("users", users);
            for (Map<String, Object> r : db.list("SELECT is_connect, count(*) n FROM gprs_nozzle WHERE user_id IN (:users) "
                    + "GROUP BY is_connect", p)) {
                long n = ((Number) r.get("n")).longValue();
                if (Boolean.TRUE.equals(r.get("isConnect"))) {
                    online = n;
                } else {
                    offline = n;
                }
            }
            volume = db.decimal("SELECT COALESCE(sum(volume), 0) FROM gprs_transaction WHERE user_id IN (:users) "
                    + "AND create_time BETWEEN :from AND :to", p);
            amount = db.decimal("SELECT COALESCE(sum(amount), 0) FROM gprs_transaction WHERE user_id IN (:users) "
                    + "AND create_time BETWEEN :from AND :to", p);
        }
        return new Stats(online, offline, volume.setScale(2, RoundingMode.HALF_EVEN), amount.setScale(2, RoundingMode.HALF_EVEN));
    }

    /** Jauges de remplissage des cuves, 6 par page (Home/GetTankIndexPie). */
    @GetMapping("/tank-gauges")
    public PageResult<Map<String, Object>> tankGauges(@CurrentUser SessionUser user,
                                                      @RequestParam(required = false) Integer regionId,
                                                      @RequestParam(required = false) Integer cityId,
                                                      @RequestParam(required = false) Integer stationId,
                                                      @RequestParam(defaultValue = "1") int page) {
        List<Integer> stations = scope.scopedStations(user, regionId, cityId, stationId);
        return db.page("SELECT id, tank_name, station_id, CASE WHEN total_volume > 0 "
                        + "THEN round(current_oil / total_volume * 100) ELSE 0 END AS percent "
                        + "FROM air_tank WHERE is_run = true AND station_id IN (:s)",
                Map.of("s", stations), "current_height", page, 6);
    }

    /** Dernières transactions (Home/GetTransactionDatalater) : 10 plus récentes. */
    @GetMapping("/latest-transactions")
    public List<Map<String, Object>> latestTransactions(@CurrentUser SessionUser user) {
        List<Integer> stations = scope.scopedStations(user, null, null, null);
        return db.list("SELECT o.id, s.name station_name, o.truely_pay amount, o.volume, o.true_time create_time "
                + "FROM oil_transactions o LEFT JOIN stations s ON s.station_id = o.station_id "
                + "WHERE o.station_id IN (:s) ORDER BY o.true_time DESC LIMIT 10", Map.of("s", stations));
    }

    /** Appareils hors ligne (Home/GetOfflineTable). */
    @GetMapping("/offline-devices")
    public List<Map<String, Object>> offlineDevices(@CurrentUser SessionUser user) {
        return db.list("SELECT id, station_name, device_no, noz_id, connec_time create_time FROM air_device "
                + "WHERE is_online = false AND is_run = true AND station_id IN (:s)", Map.of("s", scope.stationIds(user)));
    }

    /**
     * Courbes montant / volume / recharge / annulation (Home/GetReportViewPie).
     * chartType : 0 = par heure (jour), 1 = par jour (mois), 2 = par mois (année), 3 = par trimestre, 4 = par année.
     * Montant et volume = consommations par carte (type 11) + ventes en espèces (ASN vide) ;
     * recharge = type 2 ; annulation = type 12 (valeur absolue).
     */
    @GetMapping("/report-chart")
    public Map<String, Object> reportChart(@CurrentUser SessionUser user,
                                           @RequestParam(defaultValue = "0") int chartType,
                                           @RequestParam(required = false) String date,
                                           @RequestParam(required = false) String endDate,
                                           @RequestParam(required = false) Integer regionId,
                                           @RequestParam(required = false) Integer cityId,
                                           @RequestParam(required = false) Integer stationId) {
        LocalDate start = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date.substring(0, 10));
        LocalDate end = endDate == null || endDate.isBlank() ? start : LocalDate.parse(endDate.substring(0, 10));
        List<Integer> stations = scope.scopedStations(user, regionId, cityId, stationId);

        List<String> labels = new ArrayList<>();
        String bucket; // expression SQL du compartiment, à partir d'une colonne {c}
        LocalDateTime from;
        LocalDateTime to;
        int offset; // valeur du premier compartiment
        switch (chartType) {
            case 1 -> {
                int days = YearMonth.from(start).lengthOfMonth();
                for (int d = 1; d <= days; d++) {
                    labels.add(d + "");
                }
                bucket = "extract(day from {c})";
                from = start.withDayOfMonth(1).atStartOfDay();
                to = from.plusMonths(1);
                offset = 1;
            }
            case 2, 3 -> {
                for (int m = 1; m <= 12; m++) {
                    labels.add(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH));
                }
                bucket = "extract(month from {c})";
                from = start.withDayOfYear(1).atStartOfDay();
                to = from.plusYears(1);
                offset = 1;
            }
            case 4 -> {
                for (int y = start.getYear(); y <= end.getYear(); y++) {
                    labels.add(y + "");
                }
                bucket = "extract(year from {c})";
                from = LocalDate.of(start.getYear(), 1, 1).atStartOfDay();
                to = LocalDate.of(end.getYear() + 1, 1, 1).atStartOfDay();
                offset = start.getYear();
            }
            default -> {
                for (int h = 0; h < 24; h++) {
                    labels.add(h + "h");
                }
                bucket = "extract(hour from {c})";
                from = start.atStartOfDay();
                to = from.plusDays(1);
                offset = 0;
            }
        }
        int n = labels.size();
        BigDecimal[] amount = zeros(n);
        BigDecimal[] volume = zeros(n);
        BigDecimal[] deposit = zeros(n);
        BigDecimal[] reverse = zeros(n);
        Map<String, Object> p = Map.of("s", stations, "from", from, "to", to);

        String b1 = bucket.replace("{c}", "trans_datetime");
        for (Map<String, Object> r : db.list("SELECT " + b1 + " AS dt, transaction_type_id, sum(amount) sum_amount, "
                + "sum(fee_amount) sum_volume FROM account_service_logs WHERE station_id IN (:s) "
                + "AND trans_datetime >= :from AND trans_datetime < :to GROUP BY 1, transaction_type_id", p)) {
            int i = ((Number) r.get("dt")).intValue() - offset;
            if (i < 0 || i >= n) {
                continue;
            }
            BigDecimal a = round(r.get("sumAmount"));
            switch (((Number) r.get("transactionTypeId")).intValue()) {
                case 2 -> deposit[i] = deposit[i].add(a);
                case 11 -> {
                    amount[i] = amount[i].add(a);
                    volume[i] = volume[i].add(round(r.get("sumVolume")));
                }
                case 12 -> reverse[i] = reverse[i].add(a.abs());
                default -> {
                }
            }
        }
        String b2 = bucket.replace("{c}", "true_time");
        for (Map<String, Object> r : chartType == 3 ? List.<Map<String, Object>>of() : db.list("SELECT " + b2 + " AS dt, sum(truely_pay) sum_amount, sum(volume) sum_volume "
                + "FROM oil_transactions WHERE station_id IN (:s) AND asn = '00000000000000000000' AND amn <> 0 AND volume <> 0 "
                + "AND true_time >= :from AND true_time < :to GROUP BY 1", p)) {
            int i = ((Number) r.get("dt")).intValue() - offset;
            if (i >= 0 && i < n) {
                amount[i] = amount[i].add(round(r.get("sumAmount")));
                volume[i] = volume[i].add(round(r.get("sumVolume")));
            }
        }
        if (chartType == 3) {
            // Regroupement trimestriel (comme l'original : seules les données carte sont utilisées)
            labels = List.of("T1", "T2", "T3", "T4");
            amount = quarters(amount);
            volume = quarters(volume);
            deposit = quarters(deposit);
            reverse = quarters(reverse);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("amount", amount);
        out.put("volume", volume);
        out.put("deposit", deposit);
        out.put("reverse", reverse);
        return out;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static BigDecimal[] quarters(BigDecimal[] months) {
        BigDecimal[] q = zeros(4);
        for (int m = 0; m < 12; m++) {
            q[m / 3] = q[m / 3].add(months[m]);
        }
        return q;
    }

    private static BigDecimal round(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(Objects.toString(v)).setScale(2, RoundingMode.HALF_EVEN);
    }
}
