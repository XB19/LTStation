package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.SelectItem;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Plateforme GPRS (contrôleur GPRS d'origine) : clients, zones ICCID, stations, énergies, pistolets, cuves,
 * état des pistolets, transactions et rapports. Les commandes vers les terminaux passent par la table gprscmd
 * (1 = prix, 2 = totalisateur, 3 = étalonnage, 4 = surveillance, 5 = verrouillage).
 *
 * <p>Périmètre (comme l'original) : la configuration porte sur les données du compte (et de ses sous-comptes
 * pour un rôle 1) ; les transactions et rapports d'un rôle 0 couvrent tous les comptes.
 */
@RestController
@RequestMapping("/api/gprs")
public class GprsController {

    private static final DateTimeFormatter SHIFT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Db db;

    public GprsController(Db db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ Périmètre

    private boolean isRoot(SessionUser u) {
        return u.isSystemManager() || u.getRoleId() == 0;
    }

    /** Compte + sous-comptes pour un rôle 1, sinon le compte seul (Station / OilType d'origine). */
    private List<Integer> owned(SessionUser u) {
        List<Integer> ids = new ArrayList<>(List.of(u.getUserId()));
        if (u.getRoleId() == 1) {
            ids.addAll(db.jdbc().queryForList("SELECT id FROM gprs_account WHERE belong_to_id = :u", Map.of("u", u.getUserId()), Integer.class));
        }
        return ids;
    }

    /** Comptes visibles dans les transactions et rapports ; null = tous (rôle 0). */
    private List<Integer> reportScope(SessionUser u) {
        return isRoot(u) ? null : owned(u);
    }

    private void checkOwner(SessionUser u, String table, String key, int id) {
        Map<String, Object> r = db.one("SELECT user_id FROM " + table + " WHERE " + key + " = :id", Map.of("id", id));
        if (r == null) {
            throw new ApiException("L'objet n'existe pas");
        }
        if (!u.isSystemManager() && !owned(u).contains(r.get("userId") instanceof Number n ? n.intValue() : null)) {
            throw new ApiException("Cet élément ne fait pas partie de votre compte");
        }
    }

    private void requireRole(SessionUser u, int maxRole) {
        if (!u.isSystemManager() && u.getRoleId() > maxRole) {
            throw new ApiException("Droits insuffisants");
        }
    }

    private void cmd(String iccid, Object gunId, int type, String value) {
        db.update("INSERT INTO gprscmd (iccid, gunid, cmd_type, cmd_value, is_deal, create_time) VALUES (:i, :g, :t, :v, false, now())",
                Db.p("i", iccid, "g", gunId, "t", type, "v", value));
    }

    // ------------------------------------------------------------------ Listes déroulantes (GetSelect)

    @GetMapping("/select/{key}")
    public List<SelectItem> select(@CurrentUser SessionUser u, @PathVariable String key, @RequestParam(required = false) Integer stationId) {
        List<SelectItem> out = new ArrayList<>();
        switch (key) {
            case "Station" -> db.list("SELECT station_id, station_name FROM gprs_station WHERE is_run = true AND user_id = :u ORDER BY station_id",
                    Map.of("u", u.getUserId())).forEach(r -> out.add(new SelectItem((Integer) r.get("stationId"), (String) r.get("stationName"))));
            case "OilType" -> db.list("SELECT id, oil_name FROM gprs_oil_price WHERE user_id = :u ORDER BY id", Map.of("u", u.getUserId()))
                    .forEach(r -> out.add(new SelectItem((Integer) r.get("id"), (String) r.get("oilName"))));
            case "NozzleSelect" -> {
                if (stationId != null && stationId != 0) {
                    db.jdbc().queryForList("SELECT DISTINCT gun_id FROM gprs_nozzle WHERE station_id = :s ORDER BY gun_id",
                            Map.of("s", stationId), Integer.class).forEach(g -> out.add(new SelectItem(g, String.valueOf(g))));
                }
            }
            default -> throw new ApiException("Liste inconnue : " + key);
        }
        return out;
    }

    // ------------------------------------------------------------------ Clients (CustomerList)

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@CurrentUser SessionUser u) {
        requireRole(u, 1);
        String cols = "SELECT id, user_name, password, role_id, belong_to_id, type_id, phone, create_time FROM gprs_account";
        return isRoot(u) ? db.list(cols + " ORDER BY id", Map.of())
                : db.list(cols + " WHERE belong_to_id = :u OR id = :u ORDER BY id", Map.of("u", u.getUserId()));
    }

    public record CustomerForm(String userName, String password) {
    }

    @PostMapping("/customers")
    @Transactional
    public void addCustomer(@CurrentUser SessionUser u, @RequestBody CustomerForm f) {
        requireRole(u, 1);
        if (f.userName() == null || f.userName().isBlank() || f.password() == null || f.password().isEmpty()) {
            throw new ApiException("Le nom et le mot de passe sont obligatoires");
        }
        if (db.one("SELECT 1 FROM gprs_account WHERE user_name = :n", Map.of("n", f.userName())) != null) {
            throw new ApiException("Ce compte existe déjà");
        }
        db.update("INSERT INTO gprs_account (user_name, password, role_id, belong_to_id, type_id, create_time) VALUES (:n, :p, :r, :b, 0, now())",
                Db.p("n", f.userName().trim(), "p", f.password(), "r", u.getRoleId() + 1, "b", u.getUserId()));
    }

    @PutMapping("/customers/{id}")
    @Transactional
    public void updateCustomer(@CurrentUser SessionUser u, @PathVariable int id, @RequestBody CustomerForm f) {
        Map<String, Object> c = customer(u, id);
        if (db.one("SELECT 1 FROM gprs_account WHERE user_name = :n AND id <> :id", Map.of("n", f.userName(), "id", id)) != null) {
            throw new ApiException("Ce compte existe déjà");
        }
        if (Objects.equals(c.get("typeId"), 1)) {
            // Compte employé : l'identifiant est aussi stocké dans Address / Memo de l'employé
            db.update("UPDATE employees SET address = :n, memo = :p WHERE address = :on AND memo = :op AND is_using = true",
                    Db.p("n", f.userName(), "p", f.password(), "on", c.get("userName"), "op", c.get("password")));
        }
        db.update("UPDATE gprs_account SET user_name = :n, password = :p WHERE id = :id", Db.p("n", f.userName(), "p", f.password(), "id", id));
    }

    @DeleteMapping("/customers/{id}")
    @Transactional
    public void deleteCustomer(@CurrentUser SessionUser u, @PathVariable int id) {
        customer(u, id);
        if (id == u.getUserId()) {
            throw new ApiException("Impossible de supprimer votre propre compte");
        }
        db.update("DELETE FROM gprs_account WHERE id = :id", Map.of("id", id));
    }

    private Map<String, Object> customer(SessionUser u, int id) {
        requireRole(u, 1);
        Map<String, Object> c = db.one("SELECT id, user_name, password, type_id, belong_to_id FROM gprs_account WHERE id = :id", Map.of("id", id));
        if (c == null) {
            throw new ApiException("L'objet n'existe pas");
        }
        if (!isRoot(u) && id != u.getUserId() && !Objects.equals(c.get("belongToId"), u.getUserId())) {
            throw new ApiException("Ce compte ne fait pas partie de votre périmètre");
        }
        return c;
    }

    // ------------------------------------------------------------------ Zones ICCID (ICCIDLenSet)

    @GetMapping("/iccid")
    public List<Map<String, Object>> iccid(@CurrentUser SessionUser u) {
        requireRole(u, 0);
        return db.list("SELECT id, name, iccid_type, iccid_length FROM gprsiccid ORDER BY id", Map.of());
    }

    public record IccidForm(String name, Integer iccidType, Integer iccidLength) {
    }

    @PostMapping("/iccid")
    @Transactional
    public void addIccid(@CurrentUser SessionUser u, @RequestBody IccidForm f) {
        requireRole(u, 0);
        if (db.one("SELECT 1 FROM gprsiccid WHERE iccid_type = :t", Db.p("t", f.iccidType())) != null) {
            throw new ApiException("Ce code existe déjà");
        }
        db.update("INSERT INTO gprsiccid (name, iccid_type, iccid_length) VALUES (:n, :t, :l)",
                Db.p("n", f.name(), "t", f.iccidType(), "l", f.iccidLength()));
    }

    @PutMapping("/iccid/{id}")
    @Transactional
    public void updateIccid(@CurrentUser SessionUser u, @PathVariable int id, @RequestBody IccidForm f) {
        requireRole(u, 0);
        if (db.update("UPDATE gprsiccid SET name = :n, iccid_type = :t, iccid_length = :l WHERE id = :id",
                Db.p("n", f.name(), "t", f.iccidType(), "l", f.iccidLength(), "id", id)) == 0) {
            throw new ApiException("L'objet n'existe pas");
        }
    }

    @DeleteMapping("/iccid/{id}")
    @Transactional
    public void deleteIccid(@CurrentUser SessionUser u, @PathVariable int id) {
        requireRole(u, 0);
        if (db.update("DELETE FROM gprsiccid WHERE id = :id", Map.of("id", id)) == 0) {
            throw new ApiException("L'objet n'existe pas");
        }
    }

    // ------------------------------------------------------------------ Stations (StationSet)

    @GetMapping("/stations")
    public List<Map<String, Object>> stations(@CurrentUser SessionUser u) {
        return db.list("SELECT * FROM gprs_station WHERE is_run = true AND user_id IN (:ids) ORDER BY station_id", Map.of("ids", owned(u)));
    }

    public record StationForm(String stationName, String stationAddress, String managerName, String telePhone, BigDecimal longitude,
                              BigDecimal latitude, Integer amountDigits, Integer volumeDigits, Integer priceDigits) {
    }

    @PostMapping("/stations")
    @Transactional
    public void addStation(@CurrentUser SessionUser u, @RequestBody StationForm f) {
        db.update("INSERT INTO gprs_station (station_name, station_address, manager_name, tele_phone, longitude, latitude, is_run, "
                + "amount_digits, volume_digits, price_digits, create_time, user_id) VALUES (:n, :a, :m, :t, :lng, :lat, true, :ad, :vd, :pd, now(), :u)",
                stationParams(f, "u", u.getUserId()));
    }

    @PutMapping("/stations/{id}")
    @Transactional
    public void updateStation(@CurrentUser SessionUser u, @PathVariable int id, @RequestBody StationForm f) {
        checkOwner(u, "gprs_station", "station_id", id);
        db.update("UPDATE gprs_station SET station_name = :n, station_address = :a, manager_name = :m, tele_phone = :t, longitude = :lng, "
                + "latitude = :lat, amount_digits = :ad, volume_digits = :vd, price_digits = :pd WHERE station_id = :id", stationParams(f, "id", id));
        db.update("UPDATE gprs_nozzle SET station_name = :n WHERE station_id = :id", Db.p("n", f.stationName(), "id", id));
    }

    private static Map<String, Object> stationParams(StationForm f, String k, Object v) {
        return Db.p("n", f.stationName(), "a", f.stationAddress(), "m", f.managerName(), "t", f.telePhone(),
                "lng", f.longitude() == null ? BigDecimal.ZERO : f.longitude(), "lat", f.latitude() == null ? BigDecimal.ZERO : f.latitude(),
                "ad", f.amountDigits(), "vd", f.volumeDigits(), "pd", f.priceDigits(), k, v);
    }

    @DeleteMapping("/stations/{id}")
    @Transactional
    public void deleteStation(@CurrentUser SessionUser u, @PathVariable int id) {
        checkOwner(u, "gprs_station", "station_id", id);
        db.update("DELETE FROM gprs_station WHERE station_id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------------ Énergies et prix (OilType)

    @GetMapping("/oils")
    public List<Map<String, Object>> oils(@CurrentUser SessionUser u) {
        return db.list("SELECT id, oil_name, oil_price_cur, oil_price_pre, update_time FROM gprs_oil_price WHERE user_id IN (:ids) ORDER BY id",
                Map.of("ids", owned(u)));
    }

    public record OilForm(String oilName, BigDecimal price) {
    }

    @PostMapping("/oils")
    @Transactional
    public void addOil(@CurrentUser SessionUser u, @RequestBody OilForm f) {
        db.update("INSERT INTO gprs_oil_price (oil_name, oil_price_cur, oil_price_pre, update_time, create_time, user_id) "
                + "VALUES (:n, :p, :p, now(), now(), :u)", Db.p("n", f.oilName(), "p", f.price(), "u", u.getUserId()));
    }

    /** Modification ; un changement de prix est répercuté sur les pistolets et envoyé aux terminaux (commande 1). */
    @PutMapping("/oils/{id}")
    @Transactional
    public void updateOil(@CurrentUser SessionUser u, @PathVariable int id, @RequestBody OilForm f) {
        checkOwner(u, "gprs_oil_price", "id", id);
        BigDecimal cur = db.decimal("SELECT oil_price_cur FROM gprs_oil_price WHERE id = :id", Map.of("id", id));
        if (cur == null || cur.compareTo(f.price()) != 0) {
            List<Map<String, Object>> nozzles = db.list("SELECT iccid, gun_id FROM gprs_nozzle WHERE oil_id = :id", Map.of("id", id));
            db.update("UPDATE gprs_nozzle SET price = :p WHERE oil_id = :id", Map.of("p", f.price(), "id", id));
            for (Map<String, Object> n : nozzles) {
                cmd((String) n.get("iccid"), n.get("gunId"), 1, f.price().stripTrailingZeros().toPlainString());
            }
        }
        db.update("UPDATE gprs_oil_price SET oil_name = :n, oil_price_pre = :p, oil_price_cur = :p, update_time = now() WHERE id = :id",
                Db.p("n", f.oilName(), "p", f.price(), "id", id));
        db.update("UPDATE gprs_nozzle SET oil_name = :n WHERE oil_id = :id", Db.p("n", f.oilName(), "id", id));
    }

    @DeleteMapping("/oils/{id}")
    @Transactional
    public void deleteOil(@CurrentUser SessionUser u, @PathVariable int id) {
        checkOwner(u, "gprs_oil_price", "id", id);
        if (db.count("SELECT 1 FROM gprs_nozzle WHERE oil_id = :id", Map.of("id", id)) > 0) {
            throw new ApiException("Cette énergie est utilisée par des pistolets, suppression impossible");
        }
        db.update("DELETE FROM gprs_oil_price WHERE id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------------ Pistolets (NozzleSet)

    @GetMapping("/nozzles")
    public List<Map<String, Object>> nozzles(@CurrentUser SessionUser u, @RequestParam(required = false) Integer stationId) {
        Map<String, Object> p = new HashMap<>(Map.of("u", u.getUserId()));
        String sql = "SELECT * FROM gprs_nozzle WHERE user_id = :u";
        if (stationId != null && stationId != 0) {
            sql += " AND station_id = :s";
            p.put("s", stationId);
        }
        return db.list(sql + " ORDER BY station_id, gun_id", p);
    }

    public record NozzleForm(Integer stationId, String iccid, Integer gunId, Integer oilId, BigDecimal totalliser, BigDecimal calibration,
                             Integer lock) {
    }

    @PostMapping("/nozzles")
    @Transactional
    public void addNozzle(@CurrentUser SessionUser u, @RequestBody NozzleForm f) {
        if (f.stationId() == null || f.stationId() == 0 || f.oilId() == null || f.oilId() == 0) {
            throw new ApiException("Veuillez choisir la station et l'énergie");
        }
        checkOwner(u, "gprs_station", "station_id", f.stationId());
        checkOwner(u, "gprs_oil_price", "id", f.oilId());
        Map<String, Object> oil = db.one("SELECT oil_name, oil_price_cur FROM gprs_oil_price WHERE id = :id", Map.of("id", f.oilId()));
        Map<String, Object> st = db.one("SELECT station_name FROM gprs_station WHERE station_id = :id", Map.of("id", f.stationId()));
        if (db.one("SELECT 1 FROM gprs_nozzle WHERE station_id = :s AND iccid = :i AND gun_id = :g",
                Db.p("s", f.stationId(), "i", f.iccid(), "g", f.gunId())) != null) {
            throw new ApiException("Un pistolet identique existe déjà");
        }
        db.update("INSERT INTO gprs_nozzle (station_id, station_name, iccid, gun_id, oil_id, oil_name, price, user_id, create_time, update_time, "
                + "totalliser, calibration, is_connect, is_monitor) VALUES (:s, :sn, :i, :g, :o, :on, :p, :u, now(), now(), 0, 0, false, false)",
                Db.p("s", f.stationId(), "sn", st.get("stationName"), "i", f.iccid(), "g", f.gunId(), "o", f.oilId(), "on", oil.get("oilName"),
                        "p", oil.get("oilPriceCur"), "u", u.getUserId()));
        cmd(f.iccid(), f.gunId(), 1, price(oil.get("oilPriceCur")));
    }

    /** Modification ; chaque changement (prix, totalisateur, étalonnage, verrouillage) génère la commande correspondante. */
    @PutMapping("/nozzles/{id}")
    @Transactional
    public void updateNozzle(@CurrentUser SessionUser u, @PathVariable int id, @RequestBody NozzleForm f) {
        checkOwner(u, "gprs_nozzle", "id", id);
        if (f.oilId() == null || f.oilId() == 0) {
            throw new ApiException("L'énergie est obligatoire");
        }
        checkOwner(u, "gprs_station", "station_id", f.stationId());
        Map<String, Object> oil = db.one("SELECT oil_name, oil_price_cur FROM gprs_oil_price WHERE id = :id", Map.of("id", f.oilId()));
        Map<String, Object> st = db.one("SELECT station_name FROM gprs_station WHERE station_id = :id", Map.of("id", f.stationId()));
        Map<String, Object> n = db.one("SELECT * FROM gprs_nozzle WHERE id = :id", Map.of("id", id));
        if (oil == null || st == null) {
            throw new ApiException("Station ou énergie introuvable");
        }
        String iccid = (String) n.get("iccid");
        Object gun = n.get("gunId");
        BigDecimal newPrice = new BigDecimal(oil.get("oilPriceCur").toString());
        if (n.get("price") == null || newPrice.compareTo(new BigDecimal(n.get("price").toString())) != 0) {
            cmd(iccid, gun, 1, price(newPrice));
        }
        BigDecimal total = f.totalliser() == null ? BigDecimal.ZERO : f.totalliser();
        if (n.get("totalliser") == null || total.compareTo(new BigDecimal(n.get("totalliser").toString())) != 0) {
            cmd(iccid, gun, 2, total.stripTrailingZeros().toPlainString());
        }
        BigDecimal calib = f.calibration() == null ? BigDecimal.ZERO : f.calibration();
        if (calib.signum() != 0) {
            cmd(iccid, gun, 3, calib.stripTrailingZeros().toPlainString());
        }
        int lock = f.lock() == null ? 0 : f.lock();
        if (!Objects.equals(n.get("isLock"), lock)) {
            cmd(iccid, gun, 5, lock == 0 ? "False" : "True");
        }
        db.update("UPDATE gprs_nozzle SET station_id = :s, station_name = :sn, iccid = :i, gun_id = :g, oil_id = :o, oil_name = :on, price = :p, "
                + "totalliser = :t, calibration = :c, is_lock = :l, update_time = now() WHERE id = :id",
                Db.p("s", f.stationId(), "sn", st.get("stationName"), "i", f.iccid(), "g", f.gunId(), "o", f.oilId(), "on", oil.get("oilName"),
                        "p", newPrice, "t", total, "c", calib, "l", lock, "id", id));
    }

    @DeleteMapping("/nozzles/{id}")
    @Transactional
    public void deleteNozzle(@CurrentUser SessionUser u, @PathVariable int id) {
        checkOwner(u, "gprs_nozzle", "id", id);
        db.update("DELETE FROM gprs_nozzle WHERE id = :id", Map.of("id", id));
    }

    private static String price(Object v) {
        return v == null ? "0" : new BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ Cuves (TankSet)

    @GetMapping("/tanks")
    public List<Map<String, Object>> tanks(@CurrentUser SessionUser u) {
        return db.list("SELECT t.*, o.oil_name grade_name, (SELECT count(*) FROM gprs_tank_volume_table v WHERE v.tank_id = t.id) volume_rows "
                + "FROM gprs_tank t LEFT JOIN gprs_oil_price o ON o.id = t.grade_id WHERE t.user_id = :u ORDER BY t.grade_id",
                Map.of("u", u.getUserId()));
    }

    /**
     * Ajout d'une cuve avec son barème de jaugeage (fichier CSV « hauteur,volume » par ligne, comme l'import d'origine).
     */
    @PostMapping("/tanks")
    @Transactional
    public void addTank(@CurrentUser SessionUser u, @RequestParam int gradeId, @RequestParam BigDecimal tankHeight,
                        @RequestParam BigDecimal tankVolume, @RequestPart(required = false) MultipartFile volumeTable) throws IOException {
        checkOwner(u, "gprs_oil_price", "id", gradeId);
        Integer id = db.jdbc().queryForObject("INSERT INTO gprs_tank (user_id, grade_id, tank_height, tank_volume, is_run) "
                + "VALUES (:u, :g, :h, :v, true) RETURNING id", Map.of("u", u.getUserId(), "g", gradeId, "h", tankHeight, "v", tankVolume), Integer.class);
        if (volumeTable != null && !volumeTable.isEmpty()) {
            importVolumeTable(volumeTable, u.getUserId(), id, gradeId);
        }
    }

    @PostMapping("/tanks/{id}")
    @Transactional
    public void updateTank(@CurrentUser SessionUser u, @PathVariable int id, @RequestParam int gradeId, @RequestParam BigDecimal tankHeight,
                           @RequestParam BigDecimal tankVolume, @RequestPart(required = false) MultipartFile volumeTable) throws IOException {
        checkOwner(u, "gprs_tank", "id", id);
        checkOwner(u, "gprs_oil_price", "id", gradeId);
        db.update("UPDATE gprs_tank SET grade_id = :g, tank_height = :h, tank_volume = :v WHERE id = :id",
                Map.of("g", gradeId, "h", tankHeight, "v", tankVolume, "id", id));
        if (volumeTable != null && !volumeTable.isEmpty()) {
            db.update("DELETE FROM gprs_tank_volume_table WHERE tank_id = :id", Map.of("id", id));
            importVolumeTable(volumeTable, u.getUserId(), id, gradeId);
        } else {
            db.update("UPDATE gprs_tank_volume_table SET grade_id = :g WHERE tank_id = :id", Map.of("g", gradeId, "id", id));
        }
    }

    @DeleteMapping("/tanks/{id}")
    @Transactional
    public void deleteTank(@CurrentUser SessionUser u, @PathVariable int id) {
        checkOwner(u, "gprs_tank", "id", id);
        db.update("DELETE FROM gprs_tank WHERE id = :id", Map.of("id", id));
        db.update("DELETE FROM gprs_tank_volume_table WHERE tank_id = :id", Map.of("id", id));
    }

    @GetMapping("/tanks/{id}/volume-table")
    public List<Map<String, Object>> volumeTable(@CurrentUser SessionUser u, @PathVariable int id) {
        checkOwner(u, "gprs_tank", "id", id);
        return db.list("SELECT height, volume FROM gprs_tank_volume_table WHERE tank_id = :id ORDER BY height", Map.of("id", id));
    }

    private void importVolumeTable(MultipartFile file, int userId, int tankId, int gradeId) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int no = 0;
            while ((line = r.readLine()) != null) {
                no++;
                line = line.replace("﻿", "").trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("[,;\t]");
                try {
                    rows.add(Map.of("u", userId, "g", gradeId, "t", tankId, "h", new BigDecimal(parts[0].trim()), "v", new BigDecimal(parts[1].trim())));
                } catch (RuntimeException e) {
                    throw new ApiException("Barème : ligne " + no + " invalide (format attendu « hauteur,volume »)");
                }
            }
        }
        if (rows.isEmpty()) {
            throw new ApiException("Le barème de jaugeage est vide");
        }
        db.jdbc().batchUpdate("INSERT INTO gprs_tank_volume_table (user_id, grade_id, tank_id, height, volume) VALUES (:u, :g, :t, :h, :v)",
                rows.stream().map(m -> new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(m)).toArray(
                        org.springframework.jdbc.core.namedparam.SqlParameterSource[]::new));
    }

    // ------------------------------------------------------------------ État des pistolets (NozzleSearch)

    @GetMapping("/nozzle-status")
    public List<Map<String, Object>> nozzleStatus(@CurrentUser SessionUser u, @RequestParam(required = false) Integer stationId) {
        Map<String, Object> p = new HashMap<>();
        String w;
        if (stationId == null || stationId == 0) {
            w = "n.user_id = :u";
            p.put("u", u.getUserId());
        } else {
            checkOwner(u, "gprs_station", "station_id", stationId);
            w = "n.station_id = :s";
            p.put("s", stationId);
        }
        return db.list("SELECT n.id, n.iccid, n.gun_id, n.station_name, n.oil_name, n.price, n.is_connect, n.is_monitor, n.totalliser, "
                + "n.calibration, COALESCE(r.amount, 0) amount, COALESCE(r.volume, 0) volume FROM gprs_nozzle n "
                + "LEFT JOIN gprs_real_data r ON r.noz_id = n.id WHERE " + w + " ORDER BY n.station_id, n.gun_id", p);
    }

    public record MonitorForm(Integer stationId, boolean monitor) {
    }

    /** Activation / arrêt de la surveillance (commande 4) des pistolets d'une station ou de tout le compte. */
    @PostMapping("/monitor")
    @Transactional
    public void monitor(@CurrentUser SessionUser u, @RequestBody MonitorForm f) {
        List<Map<String, Object>> list = f.stationId() == null || f.stationId() == 0
                ? db.list("SELECT id, iccid, gun_id, is_monitor FROM gprs_nozzle WHERE user_id = :u", Map.of("u", u.getUserId()))
                : db.list("SELECT id, iccid, gun_id, is_monitor FROM gprs_nozzle WHERE station_id = :s AND user_id IN (:ids)",
                Map.of("s", f.stationId(), "ids", owned(u)));
        for (Map<String, Object> n : list) {
            if (!Objects.equals(n.get("isMonitor"), f.monitor())) {
                db.update("UPDATE gprs_nozzle SET is_monitor = :m WHERE id = :id", Map.of("m", f.monitor(), "id", n.get("id")));
                cmd((String) n.get("iccid"), n.get("gunId"), 4, f.monitor() ? "True" : "False");
            }
        }
    }

    // ------------------------------------------------------------------ Transactions

    private String txWhere(SessionUser u, LocalDateTime start, LocalDateTime end, Integer stationId, Integer oilId, Integer gunId,
                           Map<String, Object> p) {
        p.put("start", start);
        p.put("end", end);
        StringBuilder w = new StringBuilder(" WHERE create_time BETWEEN :start AND :end");
        List<Integer> scope = reportScope(u);
        if (scope != null) {
            w.append(" AND user_id IN (:ids)");
            p.put("ids", scope);
        }
        if (stationId != null && stationId != 0) {
            w.append(" AND station_id = :s");
            p.put("s", stationId);
        }
        if (oilId != null && oilId != 0) {
            w.append(" AND oil_id = :o");
            p.put("o", oilId);
        }
        if (gunId != null && gunId != 0) {
            w.append(" AND gun_id = :g");
            p.put("g", gunId);
        }
        return w.toString();
    }

    @GetMapping("/transactions")
    public Map<String, Object> transactions(@CurrentUser SessionUser u,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                        @RequestParam(required = false) Integer stationId, @RequestParam(required = false) Integer oilId,
                                                        @RequestParam(required = false) Integer gunId,
                                                        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> p = new HashMap<>();
        String w = txWhere(u, start, end, stationId, oilId, gunId, p);
        PageResult<Map<String, Object>> r = db.page("SELECT * FROM gprs_transaction" + w, p, "create_time DESC", page, size);
        Map<String, Object> t = db.one("SELECT count(*) cnt, COALESCE(sum(amount), 0) amount, COALESCE(sum(volume), 0) volume FROM gprs_transaction" + w, p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        out.put("sumCount", t.get("cnt"));
        out.put("sumAmount", t.get("amount"));
        out.put("sumVolume", t.get("volume"));
        return out;
    }

    // ------------------------------------------------------------------ Rapports (ReportSearch)

    /** Rapport par poste : postes (n° AAAAMMJJ…) de la période élargie d'un jour, avec le détail par pistolet. */
    @GetMapping("/reports/shifts")
    public List<Map<String, Object>> shifts(@CurrentUser SessionUser u,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                            @RequestParam(required = false) Integer stationId) {
        Map<String, Object> p = new HashMap<>();
        p.put("a", start.minusDays(1).format(SHIFT_DAY));
        p.put("b", end.plusDays(1).format(SHIFT_DAY));
        StringBuilder w = new StringBuilder(" WHERE shift_no BETWEEN :a AND :b");
        List<Integer> scope = reportScope(u);
        if (scope != null) {
            w.append(" AND station_id IN (SELECT station_id FROM gprs_station WHERE user_id IN (:ids))");
            p.put("ids", scope);
        }
        if (stationId != null && stationId != 0) {
            w.append(" AND station_id = :s");
            p.put("s", stationId);
        }
        List<Map<String, Object>> groups = db.list("SELECT g.station_id, s.station_name, g.shift_no, g.amount, g.volume FROM "
                + "(SELECT station_id, shift_no, sum(amount) amount, sum(volume) volume FROM gprs_shift" + w + " GROUP BY station_id, shift_no) g "
                + "LEFT JOIN gprs_station s ON s.station_id = g.station_id ORDER BY g.station_id, g.shift_no", p);
        for (Map<String, Object> g : groups) {
            g.put("details", db.list("SELECT iccid, gun_id, amount, volume, create_time FROM gprs_shift WHERE station_id = :s AND shift_no = :n ORDER BY gun_id",
                    Map.of("s", g.get("stationId"), "n", g.get("shiftNo"))));
        }
        return groups;
    }

    /** Rapport journalier par station. */
    @GetMapping("/reports/days")
    public List<Map<String, Object>> days(@CurrentUser SessionUser u,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                          @RequestParam(required = false) Integer stationId) {
        Map<String, Object> p = new HashMap<>();
        String w = txWhere(u, start, end, stationId, null, null, p);
        return db.list("SELECT d.station_id, s.station_name, d.day_time, d.amount, d.volume FROM (SELECT station_id, CAST(create_time AS date) day_time, "
                + "sum(amount) amount, sum(volume) volume FROM gprs_transaction" + w + " GROUP BY station_id, CAST(create_time AS date)) d "
                + "LEFT JOIN gprs_station s ON s.station_id = d.station_id ORDER BY d.day_time, d.station_id", p);
    }

    /** Totaux par carte d'identification (IDCardTotal). */
    @GetMapping("/reports/id-cards")
    public List<Map<String, Object>> idCards(@CurrentUser SessionUser u,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                             @RequestParam(required = false) String idCard) {
        Map<String, Object> p = new HashMap<>();
        String w = txWhere(u, start, end, null, null, null, p);
        if (idCard != null && !idCard.isBlank()) {
            w += " AND id_card = :c";
            p.put("c", idCard.trim());
        }
        return db.list("SELECT id_card, COALESCE(sum(amount), 0) amount, COALESCE(sum(volume), 0) volume FROM gprs_transaction" + w
                + " GROUP BY id_card ORDER BY id_card", p);
    }

    /** Volumes et montants mensuels de l'année en cours (GetChartData), pour le tableau de bord GPRS. */
    @GetMapping("/chart")
    public Map<String, Object> chart(@CurrentUser SessionUser u) {
        int year = LocalDate.now().getYear();
        Map<String, Object> p = new HashMap<>();
        p.put("a", LocalDateTime.of(year, 1, 1, 0, 0));
        p.put("b", LocalDateTime.of(year + 1, 1, 1, 0, 0));
        String w = " WHERE create_time >= :a AND create_time < :b";
        List<Integer> scope = reportScope(u);
        if (scope != null) {
            w += " AND user_id IN (:ids)";
            p.put("ids", scope);
        }
        BigDecimal[] volume = new BigDecimal[12];
        BigDecimal[] amount = new BigDecimal[12];
        java.util.Arrays.fill(volume, BigDecimal.ZERO);
        java.util.Arrays.fill(amount, BigDecimal.ZERO);
        for (Map<String, Object> r : db.list("SELECT CAST(extract(month FROM create_time) AS integer) m, COALESCE(sum(volume), 0) volume, "
                + "COALESCE(sum(amount), 0) amount FROM gprs_transaction" + w + " GROUP BY 1", p)) {
            int m = ((Number) r.get("m")).intValue() - 1;
            volume[m] = new BigDecimal(r.get("volume").toString());
            amount[m] = new BigDecimal(r.get("amount").toString());
        }
        return Map.of("volume", volume, "amount", amount);
    }
}
