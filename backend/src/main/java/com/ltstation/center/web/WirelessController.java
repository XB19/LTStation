package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CommandService;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Communication sans fil (comptes groupe, Main/TCP* et Main/BoxConfigure d'origine) : stations équipées
 * d'un boîtier (n° de boîtier dans stations.email3), énergies, prix, cuves, liaison boîtier/pistolets,
 * données en temps réel, commandes à distance et transactions.
 * Les commandes vers les boîtiers passent par cmd_list (cmd_plat_type = 2).
 */
@RestController
@RequestMapping("/api/wireless")
public class WirelessController {

    private final Db db;
    private final CommandService commands;
    private final CompanyScopeService scope;

    public WirelessController(Db db, CommandService commands, CompanyScopeService scope) {
        this.db = db;
        this.commands = commands;
        this.scope = scope;
    }

    // ------------------------------------------------------------ Plateforme (TCPPlatform)

    public record PlatformForm(String name, Integer limitStation) {
    }

    /** Création d'un sous-compte avec nombre maximal de stations (CreatePlatform). */
    @PostMapping("/platforms/{parentId}/children")
    @Transactional
    public void createPlatform(@CurrentUser SessionUser user, @PathVariable int parentId, @RequestBody PlatformForm f) {
        if (!scope.nodeList(user).contains(parentId)) {
            throw new ApiException("Ce nœud ne fait pas partie de votre périmètre");
        }
        if (f.name() == null || f.name().isBlank()) {
            throw new ApiException("Le nom du compte est obligatoire");
        }
        if (db.one("SELECT 1 FROM login_company_group WHERE group_name = :n", Map.of("n", f.name())) != null) {
            throw new ApiException("Ce nom de compte existe déjà");
        }
        String key = LegacyAes.encrypt(System.currentTimeMillis() + "" + (1 + new Random().nextInt(9999)));
        db.update("INSERT INTO login_company_group (group_id, group_name, valid_count, valid_secret_key, creatre_time) "
                + "VALUES (:g, :n, :c, :k, now())", Db.p("g", parentId, "n", f.name().trim(), "c", f.limitStation() == null ? 0 : f.limitStation(), "k", key));
    }

    public record ManagerForm(String userName, String password) {
    }

    /** Création d'un gestionnaire (compte GPRSAccount de type 2) pour un nœud (AddPlatManager). */
    @PostMapping("/platforms/{nodeId}/managers")
    @Transactional
    public void addManager(@CurrentUser SessionUser user, @PathVariable int nodeId, @RequestBody ManagerForm f) {
        if (!scope.nodeList(user).contains(nodeId)) {
            throw new ApiException("Ce nœud ne fait pas partie de votre périmètre");
        }
        if (f.userName() == null || f.userName().isBlank() || f.password() == null || f.password().isEmpty()) {
            throw new ApiException("Le nom d'utilisateur et le mot de passe sont obligatoires");
        }
        if (db.one("SELECT 1 FROM gprs_account WHERE user_name = :u", Map.of("u", f.userName())) != null) {
            throw new ApiException("Ce compte existe déjà");
        }
        db.update("INSERT INTO gprs_account (company_id, user_name, password, type_id, role_id, create_time) "
                + "VALUES (:c, :u, :p, 2, 0, now())", Db.p("c", nodeId, "u", f.userName().trim(), "p", f.password()));
    }

    // ------------------------------------------------------------ Stations à boîtier

    @GetMapping("/stations")
    public PageResult<Map<String, Object>> stations(@CurrentUser SessionUser user, @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return db.page("SELECT station_id, code, name, address, phone, manager, email3 box_code FROM stations "
                + "WHERE email3 <> '' AND company_id = :c", Map.of("c", user.companyGroupId()), "station_id", page, size);
    }

    public record StationForm(String code, String name, String address, String phone, String boxCode, String manager) {
    }

    /** Ajout (AddTcpStation) : contrôle du nombre maximal de stations du compte. */
    @PostMapping("/stations")
    @Transactional
    public void addStation(@CurrentUser SessionUser user, @RequestBody StationForm f) {
        int company = user.companyGroupId();
        Map<String, Object> g = db.one("SELECT valid_count FROM login_company_group WHERE id = :c", Map.of("c", company));
        if (g == null) {
            throw new ApiException("N° d'application incorrect");
        }
        int limit = g.get("validCount") instanceof Number n ? n.intValue() : 0;
        if (limit != 0 && db.count("SELECT 1 FROM stations WHERE company_id = :c", Map.of("c", company)) >= limit) {
            throw new ApiException("Le nombre maximal de stations est atteint");
        }
        if (db.one("SELECT 1 FROM stations WHERE name = :n AND is_active = 0", Db.p("n", f.name())) != null) {
            throw new ApiException("Cette station existe déjà");
        }
        String code = f.code();
        if (code == null || code.isBlank()) {
            Long max = db.jdbc().queryForObject("SELECT max(CAST(trim(code) AS bigint)) FROM stations WHERE trim(code) ~ '^[0-9]{1,10}$'",
                    Map.of(), Long.class);
            code = max == null ? "1000001" : String.valueOf(max + 1);
        }
        db.update("INSERT INTO stations (code, name, address, phone, email3, manager, is_active, station_type_id, city_id, company_id) "
                + "VALUES (:code, :n, :a, :p, :b, :m, 0, 1, 0, :c)",
                Db.p("code", code, "n", f.name(), "a", f.address(), "p", f.phone(), "b", f.boxCode(), "m", f.manager(), "c", company));
    }

    @PutMapping("/stations/{id}")
    @Transactional
    public void updateStation(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody StationForm f) {
        own("stations", "station_id", id, user);
        if (db.one("SELECT 1 FROM stations WHERE name = :n AND station_id <> :id AND is_active = 0", Db.p("n", f.name(), "id", id)) != null) {
            throw new ApiException("Nom de station en double");
        }
        db.update("UPDATE stations SET code = :code, name = :n, address = :a, phone = :p, manager = :m, email3 = :b WHERE station_id = :id",
                Db.p("code", f.code(), "n", f.name(), "a", f.address(), "p", f.phone(), "m", f.manager(), "b", f.boxCode(), "id", id));
    }

    @DeleteMapping("/stations/{id}")
    @Transactional
    public void deleteStation(@CurrentUser SessionUser user, @PathVariable int id) {
        own("stations", "station_id", id, user);
        db.update("DELETE FROM stations WHERE station_id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------ Énergies

    @GetMapping("/energies")
    public List<Map<String, Object>> energies(@CurrentUser SessionUser user) {
        return db.list("SELECT grade_specific_id, code, name FROM grade_specifics WHERE company_id = :c ORDER BY grade_specific_id",
                Map.of("c", user.companyGroupId()));
    }

    public record EnergyForm(String code, String name) {
    }

    @PostMapping("/energies")
    @Transactional
    public void addEnergy(@CurrentUser SessionUser user, @RequestBody EnergyForm f) {
        if (db.one("SELECT 1 FROM grade_specifics WHERE name = :n AND company_id = :c", Db.p("n", f.name(), "c", user.companyGroupId())) != null) {
            throw new ApiException("Nom d'énergie en double");
        }
        db.update("INSERT INTO grade_specifics (code, name, company_id) VALUES (:code, :n, :c)",
                Db.p("code", f.code(), "n", f.name(), "c", user.companyGroupId()));
    }

    @PutMapping("/energies/{id}")
    @Transactional
    public void updateEnergy(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody EnergyForm f) {
        own("grade_specifics", "grade_specific_id", id, user);
        if (db.one("SELECT 1 FROM grade_specifics WHERE name = :n AND grade_specific_id <> :id", Db.p("n", f.name(), "id", id)) != null) {
            throw new ApiException("Nom d'énergie en double");
        }
        db.update("UPDATE grade_specifics SET code = :code, name = :n WHERE grade_specific_id = :id",
                Db.p("code", f.code(), "n", f.name(), "id", id));
    }

    @DeleteMapping("/energies/{id}")
    @Transactional
    public void deleteEnergy(@CurrentUser SessionUser user, @PathVariable int id) {
        own("grade_specifics", "grade_specific_id", id, user);
        db.update("DELETE FROM grade_specifics WHERE grade_specific_id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------ Prix (avec commande A2)

    @GetMapping("/prices")
    public List<Map<String, Object>> prices(@CurrentUser SessionUser user) {
        return db.list("SELECT p.id, p.station_id, s.name station_name, p.grade_specific_id, g.name grade_name, p.new_ppu price "
                + "FROM price_infos p LEFT JOIN stations s ON s.station_id = p.station_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = p.grade_specific_id WHERE p.company_id = :c ORDER BY p.station_id",
                Map.of("c", user.companyGroupId()));
    }

    public record PriceForm(Integer stationId, Integer gradeId, BigDecimal price) {
    }

    /** Ajout (TCPPriceUpdate Add) puis envoi du prix aux pistolets concernés (commande A2). */
    @PostMapping("/prices")
    @Transactional
    public void addPrice(@CurrentUser SessionUser user, @RequestBody PriceForm f) {
        checkStation(user, f.stationId());
        if (db.one("SELECT 1 FROM price_infos WHERE station_id = :s AND grade_specific_id = :g", Db.p("s", f.stationId(), "g", f.gradeId())) != null) {
            throw new ApiException("Le prix de cette énergie existe déjà");
        }
        db.update("INSERT INTO price_infos (station_id, grade_specific_id, current_ppu, new_ppu, current_density, newdensity, "
                + "validate_time, company_id) VALUES (:s, :g, :p, :p, 0, 0, now(), :c)",
                Db.p("s", f.stationId(), "g", f.gradeId(), "p", f.price().doubleValue(), "c", user.companyGroupId()));
        sendPrice(f.stationId(), f.gradeId(), f.price());
    }

    @PutMapping("/prices/{id}")
    @Transactional
    public void updatePrice(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody PriceForm f) {
        own("price_infos", "id", id, user);
        checkStation(user, f.stationId());
        if (db.one("SELECT 1 FROM price_infos WHERE station_id = :s AND grade_specific_id = :g AND id <> :id",
                Db.p("s", f.stationId(), "g", f.gradeId(), "id", id)) != null) {
            throw new ApiException("Le prix de cette énergie existe déjà");
        }
        db.update("UPDATE price_infos SET station_id = :s, grade_specific_id = :g, new_ppu = :p WHERE id = :id",
                Db.p("s", f.stationId(), "g", f.gradeId(), "p", f.price().doubleValue(), "id", id));
        sendPrice(f.stationId(), f.gradeId(), f.price());
    }

    @DeleteMapping("/prices/{id}")
    @Transactional
    public void deletePrice(@CurrentUser SessionUser user, @PathVariable int id) {
        own("price_infos", "id", id, user);
        db.update("DELETE FROM price_infos WHERE id = :id", Map.of("id", id));
    }

    private void sendPrice(int stationId, int gradeId, BigDecimal price) {
        Map<String, Object> st = db.one("SELECT email3 FROM stations WHERE station_id = :s", Map.of("s", stationId));
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> d : db.list("SELECT noz_id, price_digits FROM air_device WHERE station_id = :s AND oil_id = :g",
                Map.of("s", stationId, "g", gradeId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("Address", d.get("nozId"));
            m.put("Price", price);
            m.put("PriceDigits", d.get("priceDigits"));
            data.add(m);
        }
        commands.enqueue("A2", st == null ? "" : (String) st.get("email3"), stationId, null, data, 2);
    }

    // ------------------------------------------------------------ Cuves

    @GetMapping("/tanks")
    public List<Map<String, Object>> tanks(@CurrentUser SessionUser user) {
        return db.list("SELECT t.id, t.station_id, s.name station_name, t.tank_name, t.grade_id, g.name grade_name, "
                + "t.current_oil current_vol, t.device_no, t.total_height, t.total_volume FROM air_tank t "
                + "LEFT JOIN stations s ON s.station_id = t.station_id LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "WHERE t.is_run = true AND t.company_id = :c ORDER BY t.id", Map.of("c", user.companyGroupId()));
    }

    public record TankForm(Integer stationId, String deviceNo, Integer gradeId, String tankName, BigDecimal tankHeight,
                           BigDecimal tankVolume) {
    }

    @PostMapping("/tanks")
    @Transactional
    public void addTank(@CurrentUser SessionUser user, @RequestBody TankForm f) {
        checkStation(user, f.stationId());
        if (db.one("SELECT 1 FROM air_tank WHERE is_run = true AND tank_name = :n AND company_id = :c",
                Db.p("n", f.tankName(), "c", user.companyGroupId())) != null) {
            throw new ApiException("Nom de cuve en double");
        }
        db.update("INSERT INTO air_tank (company_id, station_id, device_no, grade_id, tank_name, total_height, total_volume, is_run) "
                + "VALUES (:c, :s, :d, :g, :n, :h, :v, true)",
                Db.p("c", user.companyGroupId(), "s", f.stationId(), "d", f.deviceNo(), "g", f.gradeId(), "n", f.tankName(),
                        "h", f.tankHeight(), "v", f.tankVolume()));
    }

    @PutMapping("/tanks/{id}")
    @Transactional
    public void updateTank(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody TankForm f) {
        own("air_tank", "id", id, user);
        if (db.one("SELECT 1 FROM air_tank WHERE id <> :id AND tank_name = :n AND company_id = :c",
                Db.p("id", id, "n", f.tankName(), "c", user.companyGroupId())) != null) {
            throw new ApiException("Nom de cuve en double");
        }
        db.update("UPDATE air_tank SET station_id = :s, device_no = :d, grade_id = COALESCE(:g, grade_id), tank_name = :n, "
                + "total_height = :h, total_volume = :v WHERE id = :id",
                Db.p("s", f.stationId(), "d", f.deviceNo(), "g", f.gradeId(), "n", f.tankName(), "h", f.tankHeight(),
                        "v", f.tankVolume(), "id", id));
    }

    @DeleteMapping("/tanks/{id}")
    @Transactional
    public void deleteTank(@CurrentUser SessionUser user, @PathVariable int id) {
        own("air_tank", "id", id, user);
        db.update("DELETE FROM air_tank WHERE id = :id", Map.of("id", id));
    }

    /** Énergie et prix d'une cuve, pour pré-remplir les pistolets d'un boîtier (GetTCPTankMessage). */
    @GetMapping("/tanks/{id}/price")
    public Map<String, Object> tankPrice(@PathVariable int id) {
        Map<String, Object> r = db.one("SELECT t.id tank_id, t.station_id, t.grade_id, g.name grade_name, p.new_ppu price FROM air_tank t "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                + "LEFT JOIN price_infos p ON p.station_id = t.station_id AND p.grade_specific_id = t.grade_id WHERE t.id = :id",
                Map.of("id", id));
        if (r == null) {
            throw new ApiException("La cuve n'existe pas");
        }
        if (r.get("price") == null) {
            throw new ApiException("Le prix de l'énergie de cette cuve n'est pas défini");
        }
        r.put("price", new BigDecimal(r.get("price").toString()).setScale(3, RoundingMode.HALF_UP));
        return r;
    }

    // ------------------------------------------------------------ Boîtiers

    @GetMapping("/boxes")
    public List<Map<String, Object>> boxes(@CurrentUser SessionUser user) {
        return db.list("SELECT b.id, b.device_no, b.station_id, s.name station_name, "
                + "(SELECT string_agg(CAST(port AS text), ',' ORDER BY port) FROM box_port_set p WHERE p.devcie_no = b.device_no) ports "
                + "FROM box_set b LEFT JOIN stations s ON s.station_id = b.station_id WHERE b.company_id IN (:n) ORDER BY b.id",
                Map.of("n", scope.nodeList(user)));
    }

    /** Configuration d'un port de boîtier et de ses pistolets (GetBoxDeviceDetial). */
    @GetMapping("/boxes/{deviceNo}/ports/{port}")
    public Map<String, Object> boxPort(@PathVariable String deviceNo, @PathVariable int port) {
        Map<String, Object> box = db.one("SELECT station_id FROM box_set WHERE device_no = :d", Map.of("d", deviceNo));
        if (box == null) {
            throw new ApiException("Appareil introuvable");
        }
        Map<String, Object> ps = db.one("SELECT * FROM box_port_set WHERE devcie_no = :d AND port = :p", Map.of("d", deviceNo, "p", port));
        if (ps == null) {
            throw new ApiException("Port non configuré");
        }
        Map<String, Object> out = new LinkedHashMap<>(ps);
        out.put("stationId", box.get("stationId"));
        out.put("deviceNo", deviceNo);
        out.put("nozzles", db.list("SELECT noz_id, address, tank_no, oil_name, amount_digits, volume_digits, price_digits, auth_type_id "
                + "FROM air_device WHERE device_no = :d AND box_port = :p ORDER BY noz_id", Map.of("d", deviceNo, "p", port)));
        return out;
    }

    public record Nozzle(Integer nozId, Integer address, Integer tankId, Integer amountDigits, Integer volumeDigits,
                         Integer priceDigits, Integer authTypeId) {
    }

    public record BoxForm(String oldDeviceNo, Integer stationId, String deviceNo, Integer port, Integer baud, Integer interval,
                          Integer deviceType, Integer stop, Integer parity, Integer protocol, List<Nozzle> nozzles) {
    }

    /**
     * Création / modification d'un boîtier et d'un de ses ports (CreateBox) : les pistolets du port sont
     * recréés, puis une commande A3 demande au boîtier de recharger sa configuration.
     */
    @PostMapping("/boxes")
    @Transactional
    public void saveBox(@CurrentUser SessionUser user, @RequestBody BoxForm f) {
        checkStation(user, f.stationId());
        if (f.deviceNo() == null || f.deviceNo().isBlank() || f.port() == null) {
            throw new ApiException("Boîtier et port obligatoires");
        }
        int company = user.companyGroupId();
        String old = f.oldDeviceNo() == null || f.oldDeviceNo().isBlank() ? f.deviceNo() : f.oldDeviceNo();
        if (db.one("SELECT 1 FROM box_set WHERE device_no = :d", Map.of("d", old)) != null) {
            db.update("UPDATE box_set SET station_id = :s, device_no = :d, company_id = :c WHERE device_no = :o",
                    Map.of("s", f.stationId(), "d", f.deviceNo(), "c", company, "o", old));
        } else {
            db.update("INSERT INTO box_set (station_id, device_no, create_time, company_id) VALUES (:s, :d, :t, :c)",
                    Map.of("s", f.stationId(), "d", f.deviceNo(), "t", System.currentTimeMillis(), "c", company));
        }
        Map<String, Object> pp = Db.p("d", f.deviceNo(), "o", old, "p", f.port(), "b", f.baud(), "st", f.stop(), "pa", f.parity(),
                "dt", f.deviceType(), "a", f.protocol(), "i", f.interval(), "t", System.currentTimeMillis(), "c", company);
        if (db.update("UPDATE box_port_set SET devcie_no = :d, buad = :b, stop = :st, parity = :pa, device_type = :dt, "
                + "agree_ment_type = :a, interval = :i, company_id = :c WHERE devcie_no = :o AND port = :p", pp) == 0) {
            db.update("INSERT INTO box_port_set (devcie_no, port, buad, stop, parity, device_type, agree_ment_type, interval, create_time, "
                    + "company_id) VALUES (:d, :p, :b, :st, :pa, :dt, :a, :i, :t, :c)", pp);
        }
        db.update("DELETE FROM air_device WHERE device_no IN (:d, :o) AND box_port = :p", Map.of("d", f.deviceNo(), "o", old, "p", f.port()));
        if (f.nozzles() != null) {
            for (Nozzle n : f.nozzles()) {
                Map<String, Object> tank = n.tankId() == null ? null
                        : db.one("SELECT t.grade_id, g.name FROM air_tank t LEFT JOIN grade_specifics g ON g.grade_specific_id = t.grade_id "
                        + "WHERE t.id = :id", Map.of("id", n.tankId()));
                if (tank == null) {
                    continue;
                }
                db.update("INSERT INTO air_device (device_no, noz_id, station_id, tank_no, oil_id, oil_name, address, box_port, create_time, "
                        + "company_id, is_run, amount_digits, volume_digits, price_digits, auth_type_id) VALUES (:d, :n, :s, :t, :g, :gn, "
                        + ":a, :p, now(), :c, true, :ad, :vd, :pd, :au)",
                        Db.p("d", f.deviceNo(), "n", n.nozId(), "s", f.stationId(), "t", String.valueOf(n.tankId()),
                                "g", tank.get("gradeId"), "gn", tank.get("name"), "a", n.address(), "p", f.port(), "c", company,
                                "ad", n.amountDigits(), "vd", n.volumeDigits(), "pd", n.priceDigits(), "au", n.authTypeId()));
            }
        }
        commands.enqueue("A3", f.deviceNo(), null, f.port(), null, 2);
    }

    @DeleteMapping("/boxes/{deviceNo}")
    @Transactional
    public void deleteBox(@PathVariable String deviceNo) {
        db.update("DELETE FROM air_device WHERE device_no = :d", Map.of("d", deviceNo));
        db.update("DELETE FROM box_port_set WHERE devcie_no = :d", Map.of("d", deviceNo));
        db.update("DELETE FROM box_set WHERE device_no = :d", Map.of("d", deviceNo));
    }

    // ------------------------------------------------------------ Temps réel et commandes

    /** Pistolets et cuves d'une station en temps réel (GetDeviceShow). Statut : -1 hors ligne, 0 en ligne, 1 levé, 2 autorisé, 3 en distribution. */
    @GetMapping("/live/{stationId}")
    public Map<String, Object> live(@CurrentUser SessionUser user, @PathVariable int stationId) {
        checkStation(user, stationId);
        List<Map<String, Object>> tanks = db.list("SELECT * FROM tank_view WHERE station_id = :s", Map.of("s", stationId));
        for (Map<String, Object> t : tanks) {
            BigDecimal vol = num(t.get("currentVolume"));
            BigDecimal total = num(t.get("totalVolume"));
            BigDecimal water = num(t.get("currentWaterHeight"));
            BigDecimal height = num(t.get("totalHeight"));
            t.put("oilVolumeRatio", total.signum() == 0 ? BigDecimal.ZERO : vol.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
            t.put("waterHeightRatio", height.signum() == 0 ? BigDecimal.ZERO : water.multiply(BigDecimal.valueOf(100)).divide(height, 2, RoundingMode.HALF_UP));
            if (t.get("updateTime") instanceof Number n) {
                t.put("updateTime", LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nozzles", db.list("SELECT * FROM noz_real_date_view WHERE station_id = :s ORDER BY noz_id", Map.of("s", stationId)));
        out.put("tanks", tanks);
        return out;
    }

    public record CommandForm(String deviceNo, Integer nozId, String type, Integer startStop, Integer preSetType,
                              BigDecimal preSetValue, BigDecimal price) {
    }

    /**
     * Commande à distance (CreateTCPCmd) : type "StartOrStop" (startStop 0 = démarrer A4-Start, 1 = arrêter A4-Stop)
     * ou "PreSet" (prédétermination A1 : preSetType 0 = montant, 1 = volume).
     */
    @PostMapping("/commands")
    @Transactional
    public void command(@CurrentUser SessionUser user, @RequestBody CommandForm f) {
        Map<String, Object> d = db.one("SELECT station_id, oil_id, noz_id FROM air_device WHERE device_no = :d AND noz_id = :n LIMIT 1",
                Db.p("d", f.deviceNo(), "n", f.nozId()));
        if (d == null) {
            throw new ApiException("L'appareil n'existe pas");
        }
        int station = ((Number) d.get("stationId")).intValue();
        checkStation(user, station);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("DeviceNo", f.deviceNo());
        data.put("NozID", f.nozId());
        data.put("AuthType", false);
        if ("PreSet".equals(f.type())) {
            if (f.preSetValue() == null || f.price() == null) {
                throw new ApiException("Valeur prédéterminée et prix obligatoires");
            }
            data.put("PreSetType", f.preSetType() == null ? 0 : f.preSetType());
            data.put("PreSetValue", f.preSetValue());
            data.put("PreSetPrice", f.price());
            commands.enqueue("A1", f.deviceNo(), station, f.nozId(), data, 2);
            return;
        }
        Map<String, Object> p = db.one("SELECT new_ppu FROM price_infos WHERE grade_specific_id = :g AND station_id = :s",
                Map.of("g", d.get("oilId"), "s", station));
        double ppu = p == null ? 0 : ((Number) p.get("newPpu")).doubleValue();
        boolean stop = f.startStop() != null && f.startStop() != 0;
        if (stop) {
            db.update("DELETE FROM cmd_list WHERE id = (SELECT id FROM cmd_list WHERE device_no = :d AND cmd_plat_type = 2 AND deal <> 99 "
                    + "AND cmd_type IN ('A1', 'A4-Start') LIMIT 1)", Map.of("d", f.deviceNo()));
        }
        data.put("PreSetType", ppu > 1.0 ? 0 : 1);
        data.put("PreSetValue", 9000);
        data.put("PreSetPrice", ppu);
        commands.enqueue(stop ? "A4-Stop" : "A4-Start", f.deviceNo(), station, f.nozId(), data, 2);
    }

    // ------------------------------------------------------------ Transactions

    /** Transactions des stations du compte (GetNormalTransactionData). */
    @GetMapping("/transactions")
    public PageResult<Map<String, Object>> transactions(@CurrentUser SessionUser user,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                        @RequestParam(required = false) Integer gradeId,
                                                        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = new HashMap<>();
        p.put("start", start);
        p.put("end", end);
        p.put("c", user.companyGroupId());
        String w = " WHERE o.true_time BETWEEN :start AND :end AND o.station_id IN (SELECT station_id FROM stations WHERE company_id = :c)";
        if (gradeId != null) {
            w += " AND o.grade_specific_id = :g";
            p.put("g", gradeId);
        }
        return db.page("SELECT o.id, o.asn, s.name station_name, o.gun_id, o.true_time create_time, o.truely_pay amount, o.volume, "
                + "g.name oil_name, o.volume_total FROM oil_transactions o LEFT JOIN stations s ON s.station_id = o.station_id "
                + "LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id" + w, p, "o.true_time DESC", page, size);
    }

    // ------------------------------------------------------------ Utilitaires

    private void own(String table, String key, int id, SessionUser user) {
        Map<String, Object> r = db.one("SELECT company_id FROM " + table + " WHERE " + key + " = :id", Map.of("id", id));
        if (r == null) {
            throw new ApiException("Élément introuvable");
        }
        Object c = r.get("companyId");
        if (c == null || !scope.nodeList(user).contains(((Number) c).intValue())) {
            throw new ApiException("Cet élément ne fait pas partie de votre périmètre");
        }
    }

    private void checkStation(SessionUser user, Integer stationId) {
        if (stationId == null || !scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
