package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.domain.entity.AirDevice;
import com.ltstation.center.domain.entity.AirTank;
import com.ltstation.center.domain.entity.Stations;
import com.ltstation.center.domain.repository.AirDeviceRepository;
import com.ltstation.center.domain.repository.AirTankRepository;
import com.ltstation.center.domain.repository.GradeSpecificsRepository;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CommandService;
import com.ltstation.center.service.CompanyScopeService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Appareils cloud : cuves (skids), pistolets, courbes de niveau, barèmes de jaugeage,
 * codes d'activation et textes d'impression (Main/AirSeting d'origine).
 */
@RestController
@RequestMapping("/api/air-setting")
public class AirSettingController {

    private final Db db;
    private final AirTankRepository tanks;
    private final AirDeviceRepository devices;
    private final GradeSpecificsRepository grades;
    private final StationsRepository stations;
    private final CompanyScopeService scope;
    private final CommandService commands;

    public AirSettingController(Db db, AirTankRepository tanks, AirDeviceRepository devices, GradeSpecificsRepository grades,
                                StationsRepository stations, CompanyScopeService scope, CommandService commands) {
        this.db = db;
        this.tanks = tanks;
        this.devices = devices;
        this.grades = grades;
        this.stations = stations;
        this.scope = scope;
        this.commands = commands;
    }

    // ------------------------------------------------------------ Cuves

    /** Cuves actives (GetAirDevice Name=Tank). */
    @GetMapping("/tanks")
    public List<Map<String, Object>> tankList(@CurrentUser SessionUser user,
                                              @RequestParam(required = false) Integer regionId,
                                              @RequestParam(required = false) Integer cityId,
                                              @RequestParam(required = false) Integer stationId) {
        return db.list("SELECT t.*, s.name station_name, g.name oil_name, t.tank_level level_warning, "
                + "t.tankhightemperature temp_warning, 0 water_warning, p.new_ppu price FROM air_tank t "
                + "LEFT JOIN stations s ON s.station_id = t.station_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "LEFT JOIN price_infos p ON p.station_id = t.station_id AND p.grade_specific_id = t.grade_id "
                + "WHERE t.is_run = true AND t.station_id IN (:s) ORDER BY t.station_id, t.grade_id",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId)));
    }

    /** Prix actuel d'une énergie dans une station (GetPrice). */
    @GetMapping("/price")
    public Map<String, Object> price(@RequestParam int stationId, @RequestParam int gradeId) {
        Map<String, Object> r = db.one("SELECT new_ppu FROM price_infos WHERE station_id = :s AND grade_specific_id = :g",
                Map.of("s", stationId, "g", gradeId));
        if (r == null) {
            throw new ApiException("Le prix de cette énergie n'est pas défini");
        }
        return Map.of("price", new BigDecimal(r.get("newPpu").toString()).setScale(3, RoundingMode.HALF_UP));
    }

    public record TankForm(Integer stationId, Integer gradeId, String tankName, BigDecimal totalHeight,
                           BigDecimal totalVolume, BigDecimal overHeight, BigDecimal lowerHeight, BigDecimal overTemp,
                           BigDecimal lowerTemp, BigDecimal overWater, BigDecimal lowerWater) {
    }

    @PostMapping("/tanks")
    @Transactional
    public AirTank addTank(@CurrentUser SessionUser user, @RequestBody TankForm f) {
        Stations st = checkStation(user, f.stationId());
        if (f.gradeId() == null || grades.findById(f.gradeId()).isEmpty()) {
            throw new ApiException("Le type d'énergie n'existe pas");
        }
        AirTank t = new AirTank();
        fill(t, f);
        t.setIsRun(true);
        t.setCompanyId(st.getCompanyId());
        return tanks.save(t);
    }

    @PutMapping("/tanks/{id}")
    @Transactional
    public AirTank updateTank(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody TankForm f) {
        checkStation(user, f.stationId());
        if (f.gradeId() == null || grades.findById(f.gradeId()).isEmpty()) {
            throw new ApiException("L'énergie actuelle n'existe pas");
        }
        if (db.one("SELECT 1 FROM air_tank WHERE tank_name = :n AND id <> :id AND is_run = true",
                Map.of("n", f.tankName(), "id", id)) != null) {
            throw new ApiException("Nom de cuve en double");
        }
        AirTank t = tanks.findById(id).orElseThrow(() -> new ApiException("La cuve n'existe pas"));
        fill(t, f);
        return tanks.save(t);
    }

    @DeleteMapping("/tanks/{id}")
    @Transactional
    public void deleteTank(@CurrentUser SessionUser user, @PathVariable int id) {
        AirTank t = tanks.findById(id).orElseThrow(() -> new ApiException("La cuve n'existe pas"));
        checkStation(user, t.getStationId());
        t.setIsRun(false);
        tanks.save(t);
    }

    /**
     * Courbe de niveau d'une cuve (GetTankPie) : dernière hauteur relevée par heure (type 0),
     * par jour (type 1) ou par mois (type 2).
     */
    @GetMapping("/tanks/{id}/chart")
    public Map<String, Object> tankChart(@PathVariable int id, @RequestParam(defaultValue = "0") int type,
                                         @RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date.substring(0, 10));
        List<String> labels = new ArrayList<>();
        String bucket;
        LocalDateTime from;
        LocalDateTime to;
        int offset;
        switch (type) {
            case 1 -> {
                int days = YearMonth.from(d).lengthOfMonth();
                for (int i = 1; i <= days; i++) {
                    labels.add(String.valueOf(i));
                }
                bucket = "extract(day from create_time)";
                from = d.withDayOfMonth(1).atStartOfDay();
                to = from.plusMonths(1);
                offset = 1;
            }
            case 2 -> {
                for (int m = 1; m <= 12; m++) {
                    labels.add(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH));
                }
                bucket = "extract(month from create_time)";
                from = d.withDayOfYear(1).atStartOfDay();
                to = from.plusYears(1);
                offset = 1;
            }
            default -> {
                for (int h = 0; h < 24; h++) {
                    labels.add(h + "h");
                }
                bucket = "extract(hour from create_time)";
                from = d.atStartOfDay();
                to = from.plusDays(1);
                offset = 0;
            }
        }
        BigDecimal[] values = new BigDecimal[labels.size()];
        for (Map<String, Object> r : db.list("SELECT DISTINCT ON (" + bucket + ") " + bucket + " AS dt, height FROM tank_history "
                + "WHERE tank_id = :t AND create_time >= :from AND create_time < :to ORDER BY " + bucket + ", create_time DESC",
                Map.of("t", id, "from", from, "to", to))) {
            int i = ((Number) r.get("dt")).intValue() - offset;
            if (i >= 0 && i < values.length) {
                values[i] = r.get("height") == null ? null : new BigDecimal(r.get("height").toString());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("values", values);
        return out;
    }

    // ------------------------------------------------------------ Barèmes de jaugeage

    @GetMapping("/tank-volumes/{volumeId}")
    public List<Map<String, Object>> tankVolume(@PathVariable int volumeId) {
        return db.list("SELECT id, tank_volume_name, height, volume FROM tank_volume WHERE tank_volume_id = :v ORDER BY height",
                Map.of("v", volumeId));
    }

    /** Associe un barème à une cuve (BindTankVolume). */
    @PutMapping("/tanks/{id}/volume-table")
    @Transactional
    public void bindTankVolume(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody Map<String, Integer> body) {
        AirTank t = tanks.findById(id).orElseThrow(() -> new ApiException("Information inexistante"));
        checkStation(user, t.getStationId());
        t.setTankVolumeId(body.get("tankVolumeId"));
        tanks.save(t);
    }

    /**
     * Import d'un barème CSV "hauteur,volume" (ImportVolumeTable) ; le nom du fichier devient le nom du barème
     * et remplace un barème existant du même nom.
     */
    @PostMapping("/tank-volumes/import")
    @Transactional
    public Map<String, Object> importTankVolume(@RequestPart("file") MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!fileName.toLowerCase().endsWith(".csv")) {
            throw new ApiException("Format de fichier incorrect (CSV attendu)");
        }
        String name = fileName.substring(0, fileName.length() - 4);
        Integer max = db.jdbc().queryForObject("SELECT COALESCE(max(tank_volume_id), 0) FROM tank_volume", Map.of(), Integer.class);
        int volumeId = (max == null ? 0 : max) + 1;
        db.update("DELETE FROM tank_volume WHERE tank_volume_name = :n", Map.of("n", name));
        int count = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.trim().replace("﻿", "").split(",");
                if (parts.length < 2 || parts[0].isBlank()) {
                    continue;
                }
                try {
                    db.update("INSERT INTO tank_volume (tank_volume_id, tank_volume_name, height, volume) VALUES (:id, :n, :h, :v)",
                            Map.of("id", volumeId, "n", name, "h", new BigDecimal(parts[0].trim()), "v", new BigDecimal(parts[1].trim())));
                    count++;
                } catch (NumberFormatException e) {
                    throw new ApiException("Ligne invalide dans le fichier : " + line);
                }
            }
        }
        return Map.of("tankVolumeId", volumeId, "rows", count);
    }

    // ------------------------------------------------------------ Pistolets

    /** Pistolets actifs dont la validité n'a pas expiré (GetAirDevice Name=Noz). */
    @GetMapping("/nozzles")
    public List<Map<String, Object>> nozzleList(@CurrentUser SessionUser user,
                                                @RequestParam(required = false) Integer regionId,
                                                @RequestParam(required = false) Integer cityId,
                                                @RequestParam(required = false) Integer stationId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return db.list("SELECT d.id, d.station_id, d.station_name, d.noz_id, d.device_no, d.create_time, d.is_online, "
                + "d.amount_digits, d.volume_digits, d.price_digits, d.digits_type, d.tank_no, d.valid_time, g.name oil_name "
                + "FROM air_device d LEFT JOIN air_tank t ON CAST(t.id AS text) = d.tank_no "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "WHERE d.is_run = true AND d.station_id IN (:s) "
                + "AND (d.valid_time IS NULL OR d.valid_time = '' OR d.valid_time >= :now) ORDER BY d.station_id, d.noz_id",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId), "now", now));
    }

    public record ActiveCodeForm(String activeCode) {
    }

    /** Activation hors ligne par code (ActiveAirDeviceCode) : code = AES(n° d'appareil sur 20 car. + date d'échéance). */
    @PutMapping("/nozzles/{id}/active-code")
    @Transactional
    public Map<String, String> activeCode(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody ActiveCodeForm f) {
        AirDevice d = devices.findById(id).orElseThrow(() -> new ApiException("Appareil introuvable"));
        checkStation(user, d.getStationId());
        String plain;
        try {
            plain = LegacyAes.decrypt(f.activeCode().trim());
        } catch (RuntimeException e) {
            throw new ApiException("Code d'activation invalide");
        }
        if (plain.length() <= 20 || !plain.substring(0, 20).equals(d.getDeviceNo())) {
            throw new ApiException("Numéro d'appareil incorrect");
        }
        LocalDateTime expiry = parseDate(plain.substring(20).trim());
        String value = expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        d.setValidFlag(0);
        d.setValidTime(value);
        devices.save(d);
        return Map.of("validTime", value);
    }

    public record PrintForm(String topText, String bottomText) {
    }

    /** Texte d'en-tête / pied des tickets, envoyé à l'appareil (commande E2, JoyFillDevice/BillPrint_ModifyData). */
    @PutMapping("/nozzles/{id}/print")
    @Transactional
    public Map<String, String> printSet(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody PrintForm f) {
        AirDevice d = devices.findById(id).filter(x -> Boolean.TRUE.equals(x.getIsRun()))
                .orElseThrow(() -> new ApiException("Appareil introuvable, modification impossible"));
        checkStation(user, d.getStationId());
        if (!Boolean.TRUE.equals(d.getIsOnline())) {
            throw new ApiException("L'appareil n'est pas en ligne, modification impossible");
        }
        String head = f.topText() == null ? "" : f.topText();
        String foot = f.bottomText() == null ? "" : f.bottomText();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("HeadLength", head.length() / 2);
        data.put("Head", head);
        data.put("FootLength", foot.length() / 2);
        data.put("Foot", foot);
        commands.enqueue("E2", d.getDeviceNo(), d.getStationId(), d.getNozId(), data, 1);
        return Map.of("message", "Commande envoyée");
    }

    // ------------------------------------------------------------ Utilitaires

    private static void fill(AirTank t, TankForm f) {
        t.setStationId(f.stationId());
        t.setGradeId(f.gradeId());
        t.setTankName(f.tankName());
        t.setTotalHeight(r3(f.totalHeight()));
        t.setTotalVolume(r3(f.totalVolume()));
        t.setOverHeight(r3(f.overHeight()));
        t.setLowerHeight(r3(f.lowerHeight()));
        t.setOverTemp(r3(f.overTemp()));
        t.setLowerTemp(r3(f.lowerTemp()));
        t.setOverWater(r3(f.overWater()));
        t.setLowerWater(r3(f.lowerWater()));
    }

    private static BigDecimal r3(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(3, RoundingMode.HALF_UP);
    }

    private Stations checkStation(SessionUser user, Integer stationId) {
        if (stationId == null || !scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
        return stations.findById(stationId).orElseThrow(() -> new ApiException("La station n'existe pas"));
    }

    private static LocalDateTime parseDate(String s) {
        for (String p : List.of("yyyy-MM-dd HH:mm:ss", "yyyy/M/d H:mm:ss", "yyyy-M-d H:mm:ss", "yyyy/MM/dd HH:mm:ss")) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (RuntimeException ignored) {
                // format suivant
            }
        }
        try {
            return LocalDate.parse(s.replace('/', '-')).atStartOfDay();
        } catch (RuntimeException e) {
            throw new ApiException("Date d'échéance invalide dans le code");
        }
    }
}
