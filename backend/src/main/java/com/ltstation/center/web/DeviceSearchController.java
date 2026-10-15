package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.TimeBuckets;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche d'appareils (Main/DeviceSearch d'origine) : carte des appareils, transactions
 * et statistiques par pistolet. La carte Baidu d'origine est remplacée par OpenStreetMap
 * côté frontend : les coordonnées GPS (WGS84) des appareils sont utilisées telles quelles.
 */
@RestController
@RequestMapping("/api/device-search")
public class DeviceSearchController {

    /** Transactions d'un appareil : par n° d'appareil, ou station + pistolet si le n° n'est pas renseigné. */
    private static final String DEVICE_TX = "(o.device_no = :dev OR (o.station_id = :st AND o.gun_id = :noz "
            + "AND COALESCE(o.device_no, '') = ''))";

    private final Db db;
    private final CompanyScopeService scope;

    public DeviceSearchController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /** Appareils actifs avec position et prix actuel (GetStationLocation). */
    @GetMapping("/locations")
    public List<Map<String, Object>> locations(@CurrentUser SessionUser user) {
        return db.list("SELECT d.id, d.device_no, d.noz_id, d.station_id, s.name station_name, d.oil_name, d.is_online, "
                + "CASE WHEN d.lng ~ '^-?[0-9]+([.][0-9]+)?$' THEN CAST(d.lng AS double precision) END lng, "
                + "CASE WHEN d.lat ~ '^-?[0-9]+([.][0-9]+)?$' THEN CAST(d.lat AS double precision) END lat, "
                + "round(CAST(p.new_ppu AS numeric), COALESCE(d.price_digits, 2)) price FROM air_device d "
                + "LEFT JOIN stations s ON s.station_id = d.station_id "
                + "LEFT JOIN price_infos p ON p.station_id = d.station_id AND p.grade_specific_id = d.oil_id "
                + "WHERE d.is_run = true AND d.station_id IN (:s) ORDER BY d.station_id, d.noz_id",
                Map.of("s", scope.stationIds(user)));
    }

    /** Totaux de ventes d'une région et position de son premier appareil (GetRegionMap). */
    @GetMapping("/regions/{regionId}")
    public Map<String, Object> region(@CurrentUser SessionUser user, @PathVariable int regionId) {
        List<Integer> stations = scope.scopedStations(user, regionId, null, null);
        if (stations.isEmpty()) {
            throw new ApiException("Les informations de cette région ne sont pas encore complètes");
        }
        Map<String, Object> first = db.one("SELECT CASE WHEN lng ~ '^-?[0-9]+([.][0-9]+)?$' THEN CAST(lng AS double precision) END lng, "
                + "CASE WHEN lat ~ '^-?[0-9]+([.][0-9]+)?$' THEN CAST(lat AS double precision) END lat FROM air_device WHERE station_id IN (:s) ORDER BY id LIMIT 1",
                Map.of("s", stations));
        if (first == null) {
            throw new ApiException("Aucun appareil dans cette région");
        }
        Map<String, Object> sums = db.one("SELECT COALESCE(sum(truely_pay), 0) a, COALESCE(sum(volume), 0) v FROM oil_transactions "
                + "WHERE station_id IN (:s)", Map.of("s", stations));
        Map<String, Object> out = new LinkedHashMap<>(first);
        out.put("sumAmount", round(sums.get("a")));
        out.put("sumVolume", round(sums.get("v")));
        return out;
    }

    /** Transactions d'un appareil (GetTranasactionDataSingleNoz). */
    @GetMapping("/{id}/transactions")
    public PageResult<Map<String, Object>> transactions(@CurrentUser SessionUser user, @PathVariable int id,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> p = device(user, id);
        String where = " WHERE " + DEVICE_TX;
        if (start != null && end != null) {
            where += " AND o.true_time BETWEEN :start AND :end";
            p.put("start", start);
            p.put("end", end);
        }
        PageResult<Map<String, Object>> r = db.page("SELECT o.id, o.asn, s.name station_name, o.gun_id, o.true_time create_time, "
                + "o.truely_pay amount, o.volume, g.name oil_name, o.bal balance, o.volume_total, "
                + "COALESCE(NULLIF(o.device_no, ''), :dev) device_no FROM oil_transactions o "
                + "LEFT JOIN stations s ON s.station_id = o.station_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id" + where, p, "o.true_time DESC", page, size);
        return r;
    }

    /** Nombre, montant et volume du jour, du mois, de l'année et depuis l'origine (GetSingleNozTotal). */
    @GetMapping("/{id}/totals")
    public Map<String, Object> totals(@CurrentUser SessionUser user, @PathVariable int id) {
        Map<String, Object> p = device(user, id);
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        put(out, "day", p, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        put(out, "month", p, today.withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).plusMonths(1).atStartOfDay());
        put(out, "year", p, today.withDayOfYear(1).atStartOfDay(), today.withDayOfYear(1).plusYears(1).atStartOfDay());
        put(out, "total", p, null, null);
        return out;
    }

    /** Courbes nombre / montant / volume d'un appareil (GetTransactionSiglePie) : 1 = jours du mois, 2 = mois, 3 = années. */
    @GetMapping("/{id}/chart")
    public Map<String, Object> chart(@CurrentUser SessionUser user, @PathVariable int id, @RequestParam(defaultValue = "1") int type,
                                     @RequestParam(required = false) String date) {
        Map<String, Object> p = device(user, id);
        LocalDate d = TimeBuckets.parseDate(date);
        TimeBuckets b = switch (type) {
            case 2 -> TimeBuckets.of(2, d, null);
            case 3 -> TimeBuckets.of(4, d, LocalDate.now());
            default -> TimeBuckets.of(1, d, null);
        };
        p.put("from", b.from());
        p.put("to", b.to());
        int[] count = new int[b.size()];
        BigDecimal[] amount = new BigDecimal[b.size()];
        BigDecimal[] volume = new BigDecimal[b.size()];
        java.util.Arrays.fill(amount, BigDecimal.ZERO);
        java.util.Arrays.fill(volume, BigDecimal.ZERO);
        for (Map<String, Object> r : db.list("SELECT " + b.expr("o.true_time") + " dt, count(*) c, sum(o.truely_pay) a, sum(o.volume) v "
                + "FROM oil_transactions o WHERE " + DEVICE_TX + " AND o.true_time >= :from AND o.true_time < :to GROUP BY 1", p)) {
            int i = b.index(r.get("dt"));
            if (i >= 0) {
                count[i] = ((Number) r.get("c")).intValue();
                amount[i] = round(r.get("a"));
                volume[i] = round(r.get("v"));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", b.labels());
        out.put("amount", amount);
        out.put("volume", volume);
        out.put("count", count);
        return out;
    }

    private void put(Map<String, Object> out, String prefix, Map<String, Object> base, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> p = new HashMap<>(base);
        String where = DEVICE_TX;
        if (from != null) {
            where += " AND o.true_time >= :from AND o.true_time < :to";
            p.put("from", from);
            p.put("to", to);
        }
        Map<String, Object> r = db.one("SELECT count(*) c, COALESCE(sum(o.truely_pay), 0) a, COALESCE(sum(o.volume), 0) v "
                + "FROM oil_transactions o WHERE " + where, p);
        out.put(prefix + "Count", r.get("c"));
        out.put(prefix + "Amount", round(r.get("a")));
        out.put(prefix + "Volume", round(r.get("v")));
    }

    private Map<String, Object> device(SessionUser user, int id) {
        Map<String, Object> d = db.one("SELECT device_no, station_id, noz_id FROM air_device WHERE id = :id", Map.of("id", id));
        if (d == null) {
            throw new ApiException("L'appareil n'existe pas");
        }
        if (!scope.stationIds(user).contains(((Number) d.get("stationId")).intValue())) {
            throw new ApiException("Cet appareil ne fait pas partie de votre périmètre");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("dev", d.get("deviceNo") == null ? "" : d.get("deviceNo"));
        p.put("st", d.get("stationId"));
        p.put("noz", d.get("nozId"));
        return p;
    }

    private static BigDecimal round(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
