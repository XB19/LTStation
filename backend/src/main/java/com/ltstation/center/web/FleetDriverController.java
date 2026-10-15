package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService;
import com.ltstation.center.service.CardAccountService.Operator;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Chauffeurs des flottes (Main/JoyFillFleetDriver, JoyFillFleetDriverController d'origine) :
 * chaque chauffeur reçoit une carte M1 de sa flotte et un compte client ; répartition
 * du compte principal vers les chauffeurs (et inversement).
 */
@RestController
@RequestMapping("/api/fleet-drivers")
public class FleetDriverController {

    private final Db db;
    private final CardAccountService cards;
    private final CompanyScopeService scope;
    private final FleetController fleets;

    public FleetDriverController(Db db, CardAccountService cards, CompanyScopeService scope, FleetController fleets) {
        this.db = db;
        this.cards = cards;
        this.scope = scope;
        this.fleets = fleets;
    }

    @GetMapping
    public PageResult<Map<String, Object>> list(@CurrentUser SessionUser user, @RequestParam(defaultValue = "-1") int teamId,
                                                @RequestParam(defaultValue = "-1") int cardState,
                                                @RequestParam(defaultValue = "") String teamName,
                                                @RequestParam(defaultValue = "") String driverName,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        StringBuilder w = new StringBuilder(" WHERE station_id IN (:s)");
        Map<String, Object> p = new HashMap<>();
        p.put("s", scope.stationIds(user));
        if (teamId > -1) {
            w.append(" AND team_id = :t");
            p.put("t", teamId);
        }
        if (cardState > -1) {
            w.append(" AND card_state = :cs");
            p.put("cs", cardState);
        }
        if (!teamName.isBlank()) {
            w.append(" AND team_name LIKE :tn");
            p.put("tn", "%" + teamName.trim() + "%");
        }
        if (!driverName.isBlank()) {
            w.append(" AND driver_name LIKE :dn");
            p.put("dn", "%" + driverName.trim() + "%");
        }
        if (start != null) {
            w.append(" AND create_time >= :start");
            p.put("start", start);
        }
        if (end != null) {
            w.append(" AND create_time <= :end");
            p.put("end", end);
        }
        PageResult<Map<String, Object>> r = db.page("SELECT * FROM joy_fill_fleet_driver" + w, p, "id DESC", page, size);
        r.rows().forEach(row -> row.remove("password"));
        return r;
    }

    public record DriverForm(Integer teamId, String driverName, String phoneNumber, String numberPlate, String remark,
                             String mCardId, String password, Integer cardState, BigDecimal maxOilValue) {
    }

    /** Création (AddData + FleetDriver_MCardImport) : client 500000001+, carte FFF…n° M1, compte à zéro. */
    @PostMapping
    @Transactional
    public Map<String, Object> add(@CurrentUser SessionUser user, @RequestBody DriverForm f) {
        Map<String, Object> fl = fleets.fleet(user, f.teamId() == null ? 0 : f.teamId());
        if (f.driverName() == null || f.driverName().isBlank() || f.mCardId() == null || f.mCardId().isBlank()) {
            throw new ApiException("Nom du chauffeur et carte obligatoires");
        }
        if (db.one("SELECT 1 FROM joy_fill_fleet_driver WHERE driver_name = :n AND team_id = :t", Db.p("n", f.driverName(), "t", f.teamId())) != null) {
            throw new ApiException("Données en double");
        }
        Map<String, Object> pre = db.one("SELECT id FROM pre_card_m WHERE card_no = :c AND \"using\" = false", Db.p("c", f.mCardId()));
        if (pre == null) {
            throw new ApiException("La carte prédécoupée n'existe pas ou est déjà utilisée");
        }
        Operator op = cards.operator(user);
        String asn = "F".repeat(Math.max(0, 20 - f.mCardId().length())) + f.mCardId();
        Integer max = db.jdbc().queryForObject("SELECT max(customer_id) FROM customer_card_infos WHERE customer_id > 500000000 "
                + "AND customer_id < 600000000", Map.of(), Integer.class);
        int cid = max == null || max == 0 ? 500000001 : max + 1;
        Map<String, Object> p = Db.p("id", cid, "code", String.valueOf(cid), "name", f.driverName(), "ph", f.phoneNumber(),
                "car", f.numberPlate(), "asn", asn, "station", op.stationId(), "company", fl.get("companyId"),
                "pwd", f.password(), "emp", op.name());
        db.update("INSERT INTO customers (customer_id, name, code, certificate_type_id, certificate_no, telephone, vehicle_no, group_id, "
                + "issue_status) VALUES (:id, :name, :code, 1, '', :ph, :car, 100000001, true)", p);
        db.update("INSERT INTO customer_accounts (customer_account_id, account_no, balance, deposit_datetime, book_balance, "
                + "consume_datetime, load_up_limit, service_fee, update_datetime, status, total_times_daily, total_amount_daily, "
                + "total_amount_monthly, total_volume_monthly, mileage_lastest, volume_lastest, trans_time, total_times_daily_store, "
                + "total_amount_daily_store, total_amount_monthly_store) VALUES (:id, :code, 0, now(), 0, now(), 0, 0, now(), '0', "
                + "0, 0, 0, 0, 0, 0, now(), 0, 0, 0)", p);
        db.update("INSERT INTO customer_card_infos (asn, customer_id, customer_account_id, group_id, is_credit_card, credit_lines, "
                + "account_no, cipher_flg, discount, station_id, issue_datetime, valid_datetime, card_status_type_id, limit_type_id, "
                + "is_limit_vehicle_no, media_type_id, company_id, open_accoun_emp_name) VALUES (:asn, :id, :id, 100000001, false, 0, "
                + ":code, :pwd, 100, :station, now(), now(), 0, 0, false, 1, :company, :emp)", p);
        db.update("UPDATE pre_card_m SET \"using\" = true WHERE id = :pid", Map.of("pid", pre.get("id")));
        cards.addAccountLog(String.valueOf(cid), BigDecimal.ZERO, asn, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, op, 1,
                LocalDateTime.now(), op.stationId());
        Number id = db.jdbc().queryForObject("INSERT INTO joy_fill_fleet_driver (company_id, company_name, station_id, station_name, "
                + "team_id, team_name, card_state, driver_name, phone_number, number_plate, remark, balance, max_oil_value, m_card_id, "
                + "password, create_time, asn, account_type) VALUES (:c, :cn, :s, :sn, :t, :tn, :cs, :dn, :ph, :np, :r, 0, :mo, :m, "
                + ":pwd, now(), :asn, :at) RETURNING id", new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                Db.p("c", fl.get("companyId"), "cn", fl.get("companyName"), "s", fl.get("stationId"), "sn", fl.get("stationName"),
                        "t", f.teamId(), "tn", fl.get("teamName"), "cs", f.cardState() == null ? 1 : f.cardState(),
                        "dn", f.driverName(), "ph", f.phoneNumber(), "np", f.numberPlate(), "r", f.remark(), "mo", f.maxOilValue(),
                        "m", f.mCardId(), "pwd", f.password(), "asn", asn, "at", fl.get("accountType"))), Number.class);
        return Map.of("id", id, "asn", asn);
    }

    /** Modification (ModifyData + FleetDriver_MCardUpdate) : met à jour le porteur de la carte. */
    @PutMapping("/{id}")
    @Transactional
    public void update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody DriverForm f) {
        Map<String, Object> d = driver(user, id);
        if (f.driverName() != null && !f.driverName().equals(d.get("driverName"))) {
            db.update("UPDATE joy_fill_fleet_distribute_record SET driver_name = :n WHERE driver_id = :id", Db.p("n", f.driverName(), "id", id));
            db.update("UPDATE joy_fill_fleet_card_record SET driver_name = :n WHERE driver_id = :id", Db.p("n", f.driverName(), "id", id));
        }
        boolean pwd = f.password() != null && !f.password().isEmpty();
        db.update("UPDATE joy_fill_fleet_driver SET card_state = :cs, driver_name = :dn, phone_number = :ph, remark = :r, "
                + "number_plate = :np, max_oil_value = :mo" + (pwd ? ", password = :pwd" : "") + " WHERE id = :id",
                Db.p("cs", f.cardState(), "dn", f.driverName(), "ph", f.phoneNumber(), "r", f.remark(), "np", f.numberPlate(),
                        "mo", f.maxOilValue(), "pwd", f.password(), "id", id));
        Map<String, Object> c = cards.customerCard((String) d.get("asn"));
        db.update("UPDATE customers SET name = :n, telephone = :ph, vehicle_no = :car WHERE customer_id = :id",
                Db.p("n", f.driverName(), "ph", f.phoneNumber(), "car", f.numberPlate(), "id", c.get("customerId")));
        if (pwd) {
            db.update("UPDATE customer_card_infos SET cipher_flg = :p WHERE asn = :a", Db.p("p", f.password(), "a", d.get("asn")));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        driver(user, id);
        db.update("DELETE FROM joy_fill_fleet_driver WHERE id = :id", Map.of("id", id));
    }

    public record Item(int id, BigDecimal amount) {
    }

    /** type 1 = du compte principal vers les chauffeurs, 2 = des chauffeurs vers le compte principal. */
    public record BatchForm(int type, int teamId, List<Item> items) {
    }

    /** Répartition groupée (BatchRecharge). */
    @PostMapping("/batch")
    @Transactional
    public Map<String, Object> batch(@CurrentUser SessionUser user, @RequestBody BatchForm f) {
        Map<String, Object> fl = fleets.fleet(user, f.teamId());
        if (f.items() == null || f.items().isEmpty()) {
            throw new ApiException("Aucun chauffeur sélectionné");
        }
        Operator op = cards.operator(user);
        String opName = user.getEmployeesName() == null ? user.getLoginName() : user.getEmployeesName();
        BigDecimal master = new BigDecimal(fl.get("masterBalance").toString());
        BigDecimal total = f.items().stream().map(i -> positive(i.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (f.type() == 1 && master.subtract(total).signum() < 0) {
            throw new ApiException("Solde du compte principal insuffisant, recharge impossible");
        }
        BigDecimal running = master;
        for (Item it : f.items()) {
            Map<String, Object> d = db.one("SELECT * FROM joy_fill_fleet_driver WHERE id = :id AND team_id = :t",
                    Map.of("id", it.id(), "t", f.teamId()));
            if (d == null) {
                throw new ApiException("Chauffeur introuvable");
            }
            BigDecimal amount = it.amount();
            BigDecimal driverBefore = new BigDecimal(d.get("balance").toString());
            BigDecimal driverAfter = f.type() == 1 ? driverBefore.add(amount) : driverBefore.subtract(amount);
            if (driverAfter.signum() < 0) {
                throw new ApiException("Solde de la carte du chauffeur " + d.get("driverName") + " insuffisant");
            }
            BigDecimal masterAfter = f.type() == 1 ? running.subtract(amount) : running.add(amount);
            db.update("INSERT INTO joy_fill_fleet_distribute_record (company_id, company_name, station_id, station_name, team_id, "
                    + "team_name, driver_id, driver_name, distribute_time, recharge_type, change_amount, master_before_balance, "
                    + "master_after_balance, driver_before_balance, driver_after_balance, login_name, account_type) VALUES "
                    + "(:c, :cn, :s, :sn, :t, :tn, :d, :dn, now(), :rt, :a, :mb, :ma, :db, :da, :op, :at)",
                    Db.p("c", fl.get("companyId"), "cn", fl.get("companyName"), "s", fl.get("stationId"), "sn", fl.get("stationName"),
                            "t", f.teamId(), "tn", fl.get("teamName"), "d", it.id(), "dn", d.get("driverName"), "rt", f.type(),
                            "a", amount, "mb", running, "ma", masterAfter, "db", driverBefore, "da", driverAfter, "op", opName,
                            "at", d.get("accountType")));
            db.update("UPDATE joy_fill_fleet_driver SET balance = :b WHERE id = :id", Map.of("b", driverAfter, "id", it.id()));
            running = masterAfter;
            // Le compte client de la carte suit le solde du chauffeur, avec journal (recharge 2 ou annulation 12)
            Map<String, Object> c = cards.customerCard((String) d.get("asn"));
            BigDecimal balance = driverAfter.setScale(3, RoundingMode.HALF_UP);
            db.update("UPDATE customer_accounts SET balance = :b, update_datetime = now() WHERE customer_account_id = :id",
                    Map.of("b", balance.doubleValue(), "id", c.get("accId")));
            cards.addAccountLog((String) c.get("accNo"), f.type() == 1 ? amount : amount.negate(), (String) d.get("asn"), balance,
                    f.type() == 1 ? 2 : 12, BigDecimal.ZERO, BigDecimal.ZERO, op, 1, LocalDateTime.now(), op.stationId());
            if (f.type() == 1) {
                Map<String, Object> tier = db.one("SELECT radio FROM amount_radio WHERE amount <= :a ORDER BY amount DESC LIMIT 1",
                        Map.of("a", amount));
                db.update("INSERT INTO online_deposit (asn, order_no, amount, is_using, create_time, dis_count_amount, charge_type, "
                        + "operate_name, station_id, company_id) VALUES (:asn, :o, :a, 3, now(), :d, 2, :op, :s, :c)",
                        Db.p("asn", d.get("asn"), "o", "OnlineSave" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                                "a", amount, "d", tier == null ? BigDecimal.ZERO : amount.multiply(new BigDecimal(tier.get("radio").toString())),
                                "op", op.name(), "s", op.stationId(), "c", fl.get("companyId")));
            }
        }
        db.update("UPDATE joy_fill_fleet_list SET master_balance = :b WHERE id = :id", Map.of("b", running, "id", f.teamId()));
        return Map.of("masterBalance", running);
    }

    private Map<String, Object> driver(SessionUser user, int id) {
        Map<String, Object> d = db.one("SELECT * FROM joy_fill_fleet_driver WHERE id = :id", Map.of("id", id));
        if (d == null) {
            throw new ApiException("Le chauffeur n'existe pas");
        }
        if (!scope.stationIds(user).contains(((Number) d.get("stationId")).intValue())) {
            throw new ApiException("Ce chauffeur ne fait pas partie de votre périmètre");
        }
        return d;
    }

    private static BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException("Montant incorrect");
        }
        return v;
    }
}
