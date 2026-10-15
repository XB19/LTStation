package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.TimeBuckets;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rapports des stations (Main/StationReport d'origine) : rapport de poste par pistolet,
 * rapports jour / mois / trimestre / année par station, et courbes comparatives.
 */
@RestController
@RequestMapping("/api/station-report")
public class StationReportController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Db db;
    private final CompanyScopeService scope;

    public StationReportController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    /** Rapport de poste : montant et volume par pistolet sur la période (GetSDMReport "shift"). */
    @GetMapping("/shift")
    public List<Map<String, Object>> shift(@CurrentUser SessionUser user,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                           @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                           @RequestParam(required = false) Integer stationId) {
        List<Map<String, Object>> rows = db.list("SELECT a.id, a.device_no, a.station_id, s.name station_name, a.noz_id, "
                + "COALESCE(sum(b.truely_pay), 0) truely_pay, COALESCE(sum(b.volume), 0) volume FROM air_device a "
                + "LEFT JOIN oil_transactions b ON b.station_id = a.station_id AND b.gun_id = a.noz_id "
                + "AND b.true_time BETWEEN :start AND :end LEFT JOIN stations s ON s.station_id = a.station_id "
                + "WHERE a.is_run = true AND a.station_id IN (:s) GROUP BY a.id, a.device_no, a.station_id, s.name, a.noz_id "
                + "ORDER BY a.station_id, a.noz_id",
                Map.of("start", start, "end", end, "s", scope.scopedStations(user, regionId, cityId, stationId)));
        String check = start.format(TS) + " → " + end.format(TS);
        rows.forEach(r -> {
            r.put("truelyPay", round(r.get("truelyPay")));
            r.put("volume", round(r.get("volume")));
            r.put("checkTime", check);
        });
        return rows;
    }

    /**
     * Synthèse par station (GetSDMReport Day/Month/Season/Year) : consommation carte (type 11) + ventes
     * en espèces, recharges (type 2) et annulations (type 12). Le trimestre ne compte que les opérations carte.
     */
    @GetMapping("/summary")
    public List<Map<String, Object>> summary(@CurrentUser SessionUser user, @RequestParam String period,
                                             @RequestParam(required = false) String date,
                                             @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                                             @RequestParam(required = false) Integer stationId) {
        LocalDate d = TimeBuckets.parseDate(date);
        LocalDateTime from;
        LocalDateTime to;
        String season = null;
        switch (period) {
            case "day" -> {
                from = d.atStartOfDay();
                to = from.plusDays(1);
            }
            case "month" -> {
                from = d.withDayOfMonth(1).atStartOfDay();
                to = from.plusMonths(1);
            }
            case "season" -> {
                int q = (d.getMonthValue() - 1) / 3;
                from = LocalDate.of(d.getYear(), q * 3 + 1, 1).atStartOfDay();
                to = from.plusMonths(3);
                season = "T" + (q + 1) + " " + d.getYear();
            }
            case "year" -> {
                from = d.withDayOfYear(1).atStartOfDay();
                to = from.plusYears(1);
            }
            default -> throw new ApiException("Période inconnue : " + period);
        }
        List<Integer> stations = scope.scopedStations(user, regionId, cityId, stationId);
        Map<String, Object> p = Map.of("s", stations, "from", from, "to", to);
        Map<Integer, Map<String, Object>> byStation = new LinkedHashMap<>();
        for (Map<String, Object> st : db.list("SELECT station_id, name FROM stations WHERE station_id IN (:s) ORDER BY station_id", p)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stationId", st.get("stationId"));
            row.put("stationName", st.get("name"));
            row.put("season", season);
            row.put("sumSalerAmount", BigDecimal.ZERO);
            row.put("sumSalerVolume", BigDecimal.ZERO);
            row.put("sumChargeAmount", BigDecimal.ZERO);
            row.put("sumRepayAmount", BigDecimal.ZERO);
            row.put("sumDisCount", BigDecimal.ZERO);
            byStation.put(((Number) st.get("stationId")).intValue(), row);
        }
        for (Map<String, Object> r : db.list("SELECT station_id, transaction_type_id t, sum(amount) a, sum(fee_amount) v, "
                + "sum(discount_amount) d FROM account_service_logs WHERE station_id IN (:s) AND trans_datetime >= :from "
                + "AND trans_datetime < :to GROUP BY station_id, transaction_type_id", p)) {
            Map<String, Object> row = byStation.get(((Number) r.get("stationId")).intValue());
            if (row == null) {
                continue;
            }
            switch (((Number) r.get("t")).intValue()) {
                case 2 -> {
                    add(row, "sumChargeAmount", r.get("a"));
                    add(row, "sumDisCount", r.get("d"));
                }
                case 11 -> {
                    add(row, "sumSalerAmount", r.get("a"));
                    add(row, "sumSalerVolume", r.get("v"));
                }
                case 12 -> add(row, "sumRepayAmount", r.get("a"));
                default -> {
                }
            }
        }
        if (season == null) {
            for (Map<String, Object> r : db.list("SELECT station_id, sum(truely_pay) a, sum(volume) v FROM oil_transactions "
                    + "WHERE asn = '00000000000000000000' AND station_id IN (:s) AND true_time >= :from AND true_time < :to "
                    + "GROUP BY station_id", p)) {
                Map<String, Object> row = byStation.get(((Number) r.get("stationId")).intValue());
                if (row != null) {
                    add(row, "sumSalerAmount", r.get("a"));
                    add(row, "sumSalerVolume", r.get("v"));
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(byStation.values());
        out.forEach(r -> List.of("sumSalerAmount", "sumSalerVolume", "sumChargeAmount", "sumRepayAmount", "sumDisCount")
                .forEach(k -> r.put(k, round(r.get(k)))));
        return out;
    }

    /** Export Excel du rapport de poste ou de synthèse (ExportSDMReport). */
    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @RequestParam String report,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                       @RequestParam(required = false) String date,
                       @RequestParam(required = false) Integer regionId, @RequestParam(required = false) Integer cityId,
                       @RequestParam(required = false) Integer stationId, HttpServletResponse response) throws IOException {
        if ("shift".equals(report)) {
            if (start == null || end == null) {
                throw new ApiException("Veuillez choisir une période");
            }
            ExcelExport.write(response, "RapportPoste", "Poste", List.of(
                    Col.of("Station", "stationName"), Col.of("N° d'appareil", "deviceNo"), Col.of("Pistolet", "nozId"),
                    Col.of("Montant total", "truelyPay"), Col.of("Volume total", "volume"), Col.of("Période", "checkTime")),
                    shift(user, start, end, regionId, cityId, stationId));
            return;
        }
        List<Col> cols = new ArrayList<>();
        if ("season".equals(report)) {
            cols.add(Col.of("Trimestre", "season"));
        }
        cols.addAll(List.of(Col.of("Station", "stationName"), Col.of("Montant consommé", "sumSalerAmount"),
                Col.of("Volume consommé", "sumSalerVolume"), Col.of("Montant rechargé", "sumChargeAmount"),
                Col.of("Montant annulé", "sumRepayAmount")));
        ExcelExport.write(response, "RapportStations", "Rapport", cols, summary(user, report, date, regionId, cityId, stationId));
    }

    /**
     * Courbes de ventes comparées par station (GetStationPieData) :
     * 0 = jours du mois, 1 = mois de l'année, 2 = années depuis la date jusqu'à aujourd'hui.
     */
    @GetMapping("/station-chart")
    public Map<String, Object> stationChart(@CurrentUser SessionUser user, @RequestParam(defaultValue = "0") int type,
                                            @RequestParam(required = false) String date) {
        LocalDate d = TimeBuckets.parseDate(date);
        TimeBuckets b = switch (type) {
            case 1 -> TimeBuckets.of(2, d, null);
            case 2 -> TimeBuckets.of(4, d, LocalDate.now());
            default -> TimeBuckets.of(1, d, null);
        };
        List<Map<String, Object>> stations = db.list("SELECT station_id, name FROM stations WHERE company_id IN (:n) "
                + "ORDER BY station_id", Map.of("n", scope.nodeList(user)));
        if (stations.isEmpty()) {
            throw new ApiException("Aucune station n'a été créée");
        }
        Map<Integer, BigDecimal[]> series = new LinkedHashMap<>();
        for (Map<String, Object> s : stations) {
            BigDecimal[] arr = new BigDecimal[b.size()];
            java.util.Arrays.fill(arr, BigDecimal.ZERO);
            series.put(((Number) s.get("stationId")).intValue(), arr);
        }
        for (Map<String, Object> r : db.list("SELECT station_id, " + b.expr("true_time") + " dt, sum(truely_pay) a "
                + "FROM oil_transactions WHERE station_id IN (:s) AND true_time >= :from AND true_time < :to GROUP BY 1, 2",
                Map.of("s", new ArrayList<>(series.keySet()), "from", b.from(), "to", b.to()))) {
            BigDecimal[] arr = series.get(((Number) r.get("stationId")).intValue());
            int i = b.index(r.get("dt"));
            if (arr != null && i >= 0) {
                arr[i] = round(r.get("a"));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : stations) {
            out.add(Map.of("name", s.get("name"), "data", series.get(((Number) s.get("stationId")).intValue())));
        }
        return Map.of("labels", b.labels(), "series", out);
    }

    private static void add(Map<String, Object> row, String key, Object v) {
        if (v != null) {
            row.put(key, ((BigDecimal) row.get(key)).add(new BigDecimal(v.toString())));
        }
    }

    private static BigDecimal round(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
