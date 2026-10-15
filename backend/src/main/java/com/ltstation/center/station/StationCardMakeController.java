package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.crypto.PinHasher;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.DataVersionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Création de carte (CardMake d'origine) sans écriture de puce : carte utilisateur (010001), carte de crédit (010011),
 * carte secondaire de groupe (010013), carte employé (010004 / 010006 réparation) et cartes de groupe
 * (010002 personnelle, 010012 crédit, 010014 principale). Les écritures reprennent CreateCusTomerCardInfo,
 * CreateCreditCardInfo, CreateEmployeeCardInfo et CreateGroupCardInfo.
 */
@RestController
@RequestMapping("/api/station/cardmake")
public class StationCardMakeController {

    private final Db db;
    private final StationContext stations;
    private final StationCards cards;
    private final DataVersionService versions;

    public StationCardMakeController(Db db, StationContext stations, StationCards cards, DataVersionService versions) {
        this.db = db;
        this.stations = stations;
        this.cards = cards;
        this.versions = versions;
    }

    // ------------------------------------------------------------------ Listes et numéros proposés

    /** Listes des combos (groupes, remises, énergies, limites, employés) et numéros proposés pour chaque onglet. */
    @GetMapping("/prepare")
    public Map<String, Object> prepare(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groups", db.list("SELECT group_id value, CASE WHEN group_id = 100000001 THEN 'Particuliers' ELSE name END label FROM groups WHERE group_id = 100000001 OR group_id BETWEEN 1900000001 AND 1999999999 "
                + "ORDER BY group_id", Map.of()));
        out.put("creditGroups", db.list("SELECT DISTINCT g.group_id value, g.name label FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id "
                + "WHERE gc.credit_lines > 0 ORDER BY g.group_id", Map.of()));
        out.put("masterGroups", db.list("SELECT g.group_id value, g.name label FROM groups g WHERE g.group_id BETWEEN 1400000001 AND 1499999999 "
                + "ORDER BY g.group_id", Map.of()));
        List<Map<String, Object>> discounts = new ArrayList<>();
        discounts.add(Map.of("value", 100, "label", "Sans remise"));
        discounts.addAll(db.list("SELECT dis_count value, name label FROM dis_count WHERE COALESCE(is_run, true) = true ORDER BY dis_count DESC", Map.of()));
        out.put("discounts", discounts);
        List<Map<String, Object>> grades = new ArrayList<>();
        grades.add(Map.of("value", "", "label", "Sans limite"));
        grades.addAll(db.list("SELECT code value, name label FROM grade_specifics WHERE code IS NOT NULL AND code <> '' ORDER BY grade_specific_id", Map.of()));
        out.put("grades", grades);
        out.put("stations", db.list("SELECT station_id value, name label FROM stations WHERE station_id = :s", Map.of("s", c.stationId())));
        out.put("employees", db.list("SELECT employee_id value, name label FROM employees WHERE station_id = :s AND is_using = true "
                + "ORDER BY employee_id DESC", Map.of("s", c.stationId())));
        out.put("regions", db.list("SELECT code_inner value, region_desc label FROM regions ORDER BY region_id", Map.of()));
        out.put("cities", db.list("SELECT code_inner value, city_desc label FROM cities ORDER BY city_id", Map.of()));
        out.put("limitStations", db.list("SELECT code value, name label FROM stations WHERE is_active = 0 OR is_active IS NULL ORDER BY station_id", Map.of()));
        out.put("validTime", LocalDate.now().plusYears(5).toString());
        // Numéros proposés (PreaperCusCardInfo, PreaperCreditCardInfo, PreaperChilrenCardInfo, PreaperEmpCardInfo, PreaperGroupMessage)
        String person = cards.nextAsn("customer_card_infos", "010001");
        int personAcc = cards.nextAccount(2_100_000_001L, 2_147_483_647L);
        out.put("person", Map.of("asn", person, "account", String.valueOf(personAcc), "id", tail(String.valueOf(personAcc), 6)));
        String credit = cards.nextAsn("customer_card_infos", "010011");
        out.put("credit", Map.of("asn", credit, "account", "17" + tail(credit, 8), "id", tail(credit, 6)));
        String child = cards.nextAsn("customer_card_infos", "010013");
        int childAcc = cards.nextAccount(1_300_000_001L, 1_399_999_999L);
        out.put("child", Map.of("asn", child, "account", String.valueOf(childAcc), "id", "6" + tail(String.valueOf(childAcc), 5)));
        out.put("employee", Map.of("asn", cards.nextAsn("employee_card_infos", "010004"), "account", String.valueOf(cards.nextAccount(2_000_000_001L, 2_099_999_999L))));
        out.put("repair", Map.of("asn", cards.nextAsn("employee_card_infos", "010006"), "account", String.valueOf(cards.nextAccount(1_600_000_001L, 1_699_999_999L))));
        List<Map<String, Object>> groupNos = new ArrayList<>();
        String[][] gt = {{"010002", "1900000001", "1999999999", "9"}, {"010012", "1800000001", "1899999999", "8"}, {"010014", "1400000001", "1499999999", "7"}};
        for (String[] t : gt) {
            int gid = cards.nextGroup(Long.parseLong(t[1]), Long.parseLong(t[2]));
            groupNos.add(Map.of("asn", cards.nextAsn("group_card_infos", t[0]), "account", String.valueOf(gid), "id", t[3] + tail(String.valueOf(gid), 5)));
        }
        out.put("group", groupNos);
        return out;
    }

    /** Informations d'un groupe principal (carte secondaire) ou d'un groupe de crédit (carte de crédit). */
    @GetMapping("/group-info")
    public Map<String, Object> groupInfo(@CurrentUser SessionUser user, @RequestParam int groupId) {
        stations.of(user);
        Map<String, Object> g = db.one("SELECT g.group_id, g.name, g.code, gc.credit_lines, gc.date_account, gc.date_repayment, ga.balance "
                + "FROM groups g LEFT JOIN group_card_infos gc ON gc.group_id = g.group_id AND gc.card_status_type_id = 0 "
                + "LEFT JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id WHERE g.group_id = :g LIMIT 1", Map.of("g", groupId));
        if (g == null) {
            throw new ApiException("Groupe introuvable");
        }
        return g;
    }

    // ------------------------------------------------------------------ Création

    /** Champs communs aux cartes avec porteur (utilisateur, crédit, secondaire). */
    public record HolderCard(String asn, String account, String password, String name, String code, Integer certificateTypeId, String certificateNo,
                             String phone, String vehicleNo, String postCode, String address, String email, LocalDateTime birthday, Integer groupId,
                             Integer vehicleColorId, Integer vehicleTypeId, BigDecimal discount, Boolean limitVehicleNo, Boolean posUse, String limitOil,
                             BigDecimal limitVolume, Integer limitTime, BigDecimal limitAmount, BigDecimal limitVolumeMonth, BigDecimal limitAmountMonth,
                             BigDecimal limitMileage, BigDecimal mileageTolerance, Integer limitTypeId, String useConfine, String specialPrice,
                             BigDecimal creditLine, Integer billDate, Integer repayDay) {
    }

    @PostMapping("/person")
    @Transactional
    public Map<String, Object> person(@CurrentUser SessionUser user, @RequestBody HolderCard f) {
        return holderCard(user, f, "010001", false);
    }

    @PostMapping("/credit")
    @Transactional
    public Map<String, Object> credit(@CurrentUser SessionUser user, @RequestBody HolderCard f) {
        return holderCard(user, f, "010011", true);
    }

    @PostMapping("/child")
    @Transactional
    public Map<String, Object> child(@CurrentUser SessionUser user, @RequestBody HolderCard f) {
        if (f.groupId() == null) {
            throw new ApiException("Choisissez le groupe principal");
        }
        return holderCard(user, f, "010013", false);
    }

    private Map<String, Object> holderCard(SessionUser user, HolderCard f, String prefix, boolean credit) {
        StationContext.Ctx c = stations.of(user);
        String name = req(f.name(), "Nom du porteur", 20);
        checkPin(f.password());
        len(f.phone(), 20, "Téléphone");
        len(f.certificateNo(), 20, "N° de pièce");
        len(f.vehicleNo(), 20, "Immatriculation");
        len(f.address(), 80, "Adresse");
        len(f.email(), 50, "E-mail");
        len(f.postCode(), 8, "Code postal");
        int certType = f.certificateTypeId() == null ? 1 : f.certificateTypeId();
        // CheckTypeAndNo : une pièce d'identité ne peut servir qu'une fois
        if (f.certificateNo() != null && !f.certificateNo().isBlank() && db.one("SELECT 1 FROM customers WHERE certificate_type_id = :t "
                + "AND certificate_no = :n", Map.of("t", certType, "n", f.certificateNo().trim())) != null) {
            throw new ApiException("Ce numéro de pièce d'identité est déjà utilisé");
        }
        String asn = f.asn() != null && f.asn().matches("[0-9]{20}") && f.asn().startsWith(prefix)
                && db.one("SELECT 1 FROM customer_card_infos WHERE asn = :a", Map.of("a", f.asn())) == null ? f.asn() : cards.nextAsn("customer_card_infos", prefix);
        int account;
        if (credit) {
            account = Integer.parseInt("17" + tail(asn, 8));
        } else if (prefix.equals("010013")) {
            account = cards.nextAccount(1_300_000_001L, 1_399_999_999L);
        } else {
            account = cards.nextAccount(2_100_000_001L, 2_147_483_647L);
        }
        if (db.one("SELECT 1 FROM customer_accounts WHERE customer_account_id = :a", Map.of("a", account)) != null) {
            throw new ApiException("Le compte " + account + " existe déjà");
        }
        String accNo = String.valueOf(account);
        int group = f.groupId() == null ? 100000001 : f.groupId();
        BigDecimal discount = f.discount() == null ? BigDecimal.valueOf(100) : f.discount();
        String code = f.code() == null || f.code().isBlank() ? tail(accNo, 6) : f.code().trim();
        db.update("INSERT INTO customers (customer_id, name, code, certificate_type_id, certificate_no, address, post_code, telephone, email, vehicle_no, "
                + "group_id, issue_status, birthday, vehicle_type_id, vehicle_color_id, company_id) VALUES (:id, :n, :code, :ct, :cn, :ad, :pc, :ph, :em, :v, "
                + ":g, true, :bd, :vt, :vc, :c)",
                Db.p("id", account, "n", name, "code", code, "ct", certType, "cn", trim(f.certificateNo()), "ad", trim(f.address()),
                        "pc", trim(f.postCode()), "ph", trim(f.phone()), "em", trim(f.email()), "v", trim(f.vehicleNo()), "g", group,
                        "bd", f.birthday(), "vt", f.vehicleTypeId(), "vc", f.vehicleColorId(), "c", c.companyId()));
        cards.insertAccount(account, accNo);
        db.update("INSERT INTO customer_card_infos (asn, customer_id, customer_account_id, group_id, is_credit_card, credit_lines, date_account, "
                + "date_repayment, account_no, discount, use_confine, limit_vehicle_no, limit_oil, limit_volume, limit_time, limit_amount, limit_volume_monthly, "
                + "limit_amount_monthly, limit_mileage, mileage_tolerance, station_id, issue_datetime, valid_datetime, card_status_type_id, used_amount_monthly, "
                + "limit_type_id, is_limit_vehicle_no, media_type_id, company_id, score, open_accoun_emp_name, update_datetime, special_price, memo1, pin_hash) "
                + "VALUES (:asn, :id, :id, :g, :cr, :cl, :bill, :repay, :acc, :d, :uc, :lv, :lo, :lvol, :lt, :la, :lvm, :lam, :lm, :mt, :s, now(), "
                + "now() + interval '5 years', 0, 0, :ltype, :islv, 1, :c, 0, :op, now(), :sp, :pos, :pin)",
                Db.p("asn", asn, "id", account, "g", group, "cr", credit, "cl", credit ? dbl(f.creditLine()) : 0,
                        "bill", f.billDate(), "repay", f.repayDay(), "acc", accNo, "d", discount.doubleValue(), "uc", trim(f.useConfine()),
                        "lv", trim(f.vehicleNo()), "lo", blankToNull(f.limitOil()), "lvol", dbl(f.limitVolume()), "lt", f.limitTime() == null ? 0 : f.limitTime(),
                        "la", dbl(f.limitAmount()), "lvm", dbl(f.limitVolumeMonth()), "lam", dbl(f.limitAmountMonth()), "lm", dbl(f.limitMileage()),
                        "mt", dbl(f.mileageTolerance()), "s", c.stationId(), "ltype", f.limitTypeId() == null ? 0 : f.limitTypeId(),
                        "islv", Boolean.TRUE.equals(f.limitVehicleNo()), "c", c.companyId(), "op", c.employeeName(), "sp", f.specialPrice() == null ? "" : f.specialPrice(),
                        "pos", Boolean.TRUE.equals(f.posUse()) ? "POS" : null, "pin", PinHasher.hash(f.password())));
        StationCards.Card card = cards.find(asn);
        cards.log(card, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
        return result(card, accNo);
    }

    public record EmployeeCard(Integer employeeId, boolean repair, String password, Boolean posUse, String specialPrice) {
    }

    @PostMapping("/employee")
    @Transactional
    public Map<String, Object> employee(@CurrentUser SessionUser user, @RequestBody EmployeeCard f) {
        StationContext.Ctx c = stations.of(user);
        checkPin(f.password());
        Map<String, Object> e = db.one("SELECT employee_id, name FROM employees WHERE employee_id = :e AND station_id = :s AND is_using = true",
                Db.p("e", f.employeeId(), "s", c.stationId()));
        if (e == null) {
            throw new ApiException("Choisissez un employé de la station");
        }
        if (db.one("SELECT 1 FROM employee_card_infos WHERE employee_id = :e AND card_status_type_id = 0", Map.of("e", f.employeeId())) != null) {
            throw new ApiException("Cet employé a déjà une carte en service");
        }
        String prefix = f.repair() ? "010006" : "010004";
        String asn = cards.nextAsn("employee_card_infos", prefix);
        int account = f.repair() ? cards.nextAccount(1_600_000_001L, 1_699_999_999L) : cards.nextAccount(2_000_000_001L, 2_099_999_999L);
        cards.insertAccount(account, String.valueOf(account));
        db.update("INSERT INTO employee_card_infos (asn, employee_id, card_type_id, issue_datetime, valid_datetime, customer_account_id, card_status_type_id, "
                + "station_id, open_station_id, company_id, open_accoun_emp_name, special_price, memo1, pin_hash) VALUES (:asn, :e, :t, now(), "
                + "now() + interval '5 years', :acc, 0, :s, :s, :c, :op, :sp, :pos, :pin)",
                Db.p("asn", asn, "e", f.employeeId(), "t", f.repair() ? 6 : 4, "acc", account, "s", c.stationId(), "c", c.companyId(), "op", c.employeeName(),
                        "sp", f.specialPrice() == null ? "" : f.specialPrice(), "pos", Boolean.TRUE.equals(f.posUse()) ? "POS" : null, "pin", PinHasher.hash(f.password())));
        StationCards.Card card = cards.find(asn);
        cards.log(card, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
        return result(card, String.valueOf(account));
    }

    public record GroupCard(int groupType, String password, String companyName, String certificateNo, String manager, String phone, String fax,
                            String postCode, String address, String email, BigDecimal discount, String limitOil, BigDecimal limitVolume, Integer limitTime,
                            BigDecimal limitAmountDay, BigDecimal limitVolumeMonth, BigDecimal limitAmountMonth, BigDecimal creditLine, Integer billDate,
                            Integer repayDay, BigDecimal limitMinBalance) {
    }

    /** Carte de groupe : 0 personnelle (010002, groupe 19…), 1 crédit (010012, 18…), 2 principale (010014, 14…). */
    @PostMapping("/group")
    @Transactional
    public Map<String, Object> group(@CurrentUser SessionUser user, @RequestBody GroupCard f) {
        StationContext.Ctx c = stations.of(user);
        checkPin(f.password());
        String name = req(f.companyName(), "Nom de la société", 50);
        req(f.certificateNo(), "Carte professionnelle", 20);
        len(f.manager(), 10, "Contact");
        len(f.phone(), 20, "Téléphone");
        len(f.fax(), 20, "Fax");
        len(f.postCode(), 8, "Code postal");
        len(f.address(), 80, "Adresse");
        len(f.email(), 50, "E-mail");
        String[] t = switch (f.groupType()) {
            case 1 -> new String[] {"010012", "1800000001", "1899999999", "8"};
            case 2 -> new String[] {"010014", "1400000001", "1499999999", "7"};
            default -> new String[] {"010002", "1900000001", "1999999999", "9"};
        };
        if (db.one("SELECT 1 FROM groups WHERE name = :n", Map.of("n", name)) != null) {
            throw new ApiException("Ce groupe existe déjà");
        }
        int gid = cards.nextGroup(Long.parseLong(t[1]), Long.parseLong(t[2]));
        String code = t[3] + tail(String.valueOf(gid), 5);
        String asn = cards.nextAsn("group_card_infos", t[0]);
        boolean credit = f.groupType() == 1;
        db.update("INSERT INTO groups (group_id, name, short_name, code, address, telephone, fax, post_code, contact, certificate_no, email, company_id) "
                + "VALUES (:g, :n, :sn, :code, :ad, :ph, :fax, :pc, :ct, :cn, :em, :c)",
                Db.p("g", gid, "n", name, "sn", name.length() > 15 ? name.substring(0, 15) : name, "code", code, "ad", trim(f.address()), "ph", trim(f.phone()),
                        "fax", trim(f.fax()), "pc", trim(f.postCode()), "ct", trim(f.manager()), "cn", trim(f.certificateNo()), "em", trim(f.email()),
                        "c", c.companyId()));
        db.update("INSERT INTO group_accounts (group_account_id, account_no, balance, deposit_datetime, book_balance, consume_datetime, load_up_limit, "
                + "service_fee, update_datetime, status) VALUES (:g, :no, 0, now(), 0, now(), 0, 0, now(), '0')", Map.of("g", gid, "no", String.valueOf(gid)));
        db.update("INSERT INTO group_card_infos (group_id, asn, valid_datetime, is_credit_card, credit_lines, date_account, date_repayment, card_status_type_id, "
                + "station_id, update_date, issue_datetime, group_account_id, used_amount_monthly, limit_type_id, limit_oil, limit_volume, limit_time, limit_amount, "
                + "limit_volume_monthly, limit_amount_monthly, discount, preferential_price, is_limit_vehicle_no, media_type_id, min_balance, company_id, pin_hash) "
                + "VALUES (:g, :asn, now() + interval '5 years', :cr, :cl, :bill, :repay, 0, :s, now(), now(), :g, 0, 0, :lo, :lv, :lt, :la, :lvm, :lam, :d, 0, "
                + "false, 1, :mb, :c, :pin)",
                Db.p("g", gid, "asn", asn, "cr", credit, "cl", credit ? positive(f.creditLine(), "Plafond de crédit").doubleValue() : 0,
                        "bill", credit ? f.billDate() : null, "repay", credit ? f.repayDay() : null, "s", c.stationId(), "lo", blankToNull(f.limitOil()),
                        "lv", dbl(f.limitVolume()), "lt", f.limitTime() == null ? 0 : f.limitTime(), "la", dbl(f.limitAmountDay()),
                        "lvm", dbl(f.limitVolumeMonth()), "lam", dbl(f.limitAmountMonth()), "d", f.discount() == null ? 100 : f.discount().doubleValue(),
                        "mb", f.groupType() == 2 ? (f.limitMinBalance() == null ? BigDecimal.valueOf(500) : f.limitMinBalance()) : null,
                        "c", c.companyId(), "pin", PinHasher.hash(f.password())));
        StationCards.Card card = cards.find(asn);
        cards.log(card, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
        Map<String, Object> r = result(card, String.valueOf(gid));
        r.put("groupId", code);
        return r;
    }

    // ------------------------------------------------------------------ Mise à jour (CardUpDate)

    /** Fiche complète d'une carte pour la mise à jour (GetUserMessage). */
    @GetMapping("/details/{asn}")
    public Map<String, Object> details(@CurrentUser SessionUser user, @PathVariable String asn) {
        stations.of(user);
        String a = StationCards.normalize(asn);
        String p = a.substring(0, 6);
        Map<String, Object> r;
        String kind;
        switch (p) {
            case "010004", "010006" -> {
                kind = "EMPLOYEE";
                r = db.one("SELECT ec.asn, ec.employee_id, ec.valid_datetime, ec.special_price, ec.memo1, ec.station_id, ec.card_status_type_id, "
                        + "ec.customer_account_id account, e.name employee_name FROM employee_card_infos ec LEFT JOIN employees e ON e.employee_id = ec.employee_id "
                        + "WHERE ec.asn = :a", Map.of("a", a));
            }
            case "010002", "010012", "010014" -> {
                kind = "GROUP";
                r = db.one("SELECT gc.*, g.name company_name, g.code group_code, g.certificate_no, g.contact manager, g.telephone phone, g.fax, g.post_code, "
                        + "g.address, g.email, ga.balance, ga.account_no FROM group_card_infos gc LEFT JOIN groups g ON g.group_id = gc.group_id "
                        + "LEFT JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id WHERE gc.asn = :a", Map.of("a", a));
            }
            default -> {
                kind = p.equals("010011") ? "CREDIT" : p.equals("010013") ? "CHILD" : "PERSON";
                r = db.one("SELECT ci.*, cu.name, cu.code, cu.certificate_type_id, cu.certificate_no, cu.telephone phone, cu.vehicle_no, cu.post_code, cu.address, "
                        + "cu.email, cu.birthday, cu.vehicle_color_id, cu.vehicle_type_id, a.balance FROM customer_card_infos ci "
                        + "LEFT JOIN customers cu ON cu.customer_id = ci.customer_id LEFT JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id "
                        + "WHERE ci.asn = :a", Map.of("a", a));
            }
        }
        if (r == null) {
            throw new ApiException("Carte inconnue : " + a);
        }
        Map<String, Object> out = new LinkedHashMap<>(r);
        out.remove("pinHash");
        out.put("kind", kind);
        return out;
    }

    @PutMapping("/holder/{asn}")
    @Transactional
    public Map<String, Object> updateHolder(@CurrentUser SessionUser user, @PathVariable String asn, @RequestBody HolderCard f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.kind() != StationCards.Kind.USER) {
            throw new ApiException("Cette carte n'est pas une carte avec porteur");
        }
        String name = req(f.name(), "Nom du porteur", 20);
        len(f.phone(), 20, "Téléphone");
        len(f.certificateNo(), 20, "N° de pièce");
        len(f.vehicleNo(), 20, "Immatriculation");
        len(f.address(), 80, "Adresse");
        len(f.email(), 50, "E-mail");
        len(f.postCode(), 8, "Code postal");
        Map<String, Object> ci = db.one("SELECT customer_id FROM customer_card_infos WHERE asn = :a", Map.of("a", card.asn()));
        int cid = ((Number) ci.get("customerId")).intValue();
        if (f.certificateNo() != null && !f.certificateNo().isBlank() && db.one("SELECT 1 FROM customers WHERE certificate_type_id = :t "
                + "AND certificate_no = :n AND customer_id <> :id", Db.p("t", f.certificateTypeId(), "n", f.certificateNo().trim(), "id", cid)) != null) {
            throw new ApiException("Ce numéro de pièce d'identité est déjà utilisé");
        }
        db.update("UPDATE customers SET name = :n, code = COALESCE(:code, code), certificate_type_id = COALESCE(:ct, certificate_type_id), certificate_no = :cn, "
                + "address = :ad, post_code = :pc, telephone = :ph, email = :em, vehicle_no = :v, group_id = COALESCE(:g, group_id), birthday = :bd, "
                + "vehicle_type_id = :vt, vehicle_color_id = :vc WHERE customer_id = :id",
                Db.p("n", name, "code", trim(f.code()), "ct", f.certificateTypeId(), "cn", trim(f.certificateNo()), "ad", trim(f.address()),
                        "pc", trim(f.postCode()), "ph", trim(f.phone()), "em", trim(f.email()), "v", trim(f.vehicleNo()), "g", f.groupId(), "bd", f.birthday(),
                        "vt", f.vehicleTypeId(), "vc", f.vehicleColorId(), "id", cid));
        db.update("UPDATE customer_card_infos SET group_id = COALESCE(:g, group_id), credit_lines = CASE WHEN is_credit_card THEN COALESCE(:cl, credit_lines) ELSE credit_lines END, "
                + "date_account = COALESCE(:bill, date_account), date_repayment = COALESCE(:repay, date_repayment), discount = COALESCE(:d, discount), "
                + "use_confine = :uc, limit_vehicle_no = :lv, limit_oil = :lo, limit_volume = :lvol, limit_time = :lt, limit_amount = :la, "
                + "limit_volume_monthly = :lvm, limit_amount_monthly = :lam, limit_mileage = :lm, mileage_tolerance = :mt, limit_type_id = :ltype, "
                + "is_limit_vehicle_no = :islv, special_price = COALESCE(:sp, special_price), memo1 = :pos, update_datetime = now() WHERE asn = :a",
                Db.p("g", f.groupId(), "cl", f.creditLine() == null ? null : f.creditLine().doubleValue(), "bill", f.billDate(), "repay", f.repayDay(),
                        "d", f.discount() == null ? null : f.discount().doubleValue(), "uc", trim(f.useConfine()), "lv", trim(f.vehicleNo()),
                        "lo", blankToNull(f.limitOil()), "lvol", dbl(f.limitVolume()), "lt", f.limitTime() == null ? 0 : f.limitTime(), "la", dbl(f.limitAmount()),
                        "lvm", dbl(f.limitVolumeMonth()), "lam", dbl(f.limitAmountMonth()), "lm", dbl(f.limitMileage()), "mt", dbl(f.mileageTolerance()),
                        "ltype", f.limitTypeId() == null ? 0 : f.limitTypeId(), "islv", Boolean.TRUE.equals(f.limitVehicleNo()), "sp", f.specialPrice(),
                        "pos", Boolean.TRUE.equals(f.posUse()) ? "POS" : null, "a", card.asn()));
        afterUpdate(c, card, f.password());
        return details(user, card.asn());
    }

    @PutMapping("/employee/{asn}")
    @Transactional
    public Map<String, Object> updateEmployee(@CurrentUser SessionUser user, @PathVariable String asn, @RequestBody EmployeeCard f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.kind() != StationCards.Kind.EMPLOYEE) {
            throw new ApiException("Cette carte n'est pas une carte employé");
        }
        db.update("UPDATE employee_card_infos SET special_price = COALESCE(:sp, special_price), memo1 = :pos WHERE asn = :a",
                Db.p("sp", f.specialPrice(), "pos", Boolean.TRUE.equals(f.posUse()) ? "POS" : null, "a", card.asn()));
        afterUpdate(c, card, f.password());
        return details(user, card.asn());
    }

    @PutMapping("/group/{asn}")
    @Transactional
    public Map<String, Object> updateGroup(@CurrentUser SessionUser user, @PathVariable String asn, @RequestBody GroupCard f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.kind() != StationCards.Kind.GROUP) {
            throw new ApiException("Cette carte n'est pas une carte de groupe");
        }
        String name = req(f.companyName(), "Nom de la société", 50);
        Map<String, Object> gc = db.one("SELECT group_id FROM group_card_infos WHERE asn = :a", Map.of("a", card.asn()));
        int gid = ((Number) gc.get("groupId")).intValue();
        db.update("UPDATE groups SET name = :n, address = :ad, telephone = :ph, fax = :fax, post_code = :pc, contact = :ct, certificate_no = :cn, email = :em "
                + "WHERE group_id = :g", Db.p("n", name, "ad", trim(f.address()), "ph", trim(f.phone()), "fax", trim(f.fax()), "pc", trim(f.postCode()),
                "ct", trim(f.manager()), "cn", trim(f.certificateNo()), "em", trim(f.email()), "g", gid));
        db.update("UPDATE group_card_infos SET discount = COALESCE(:d, discount), limit_oil = :lo, limit_volume = :lv, limit_time = :lt, limit_amount = :la, "
                + "limit_volume_monthly = :lvm, limit_amount_monthly = :lam, credit_lines = CASE WHEN is_credit_card THEN COALESCE(:cl, credit_lines) ELSE credit_lines END, "
                + "date_account = COALESCE(:bill, date_account), date_repayment = COALESCE(:repay, date_repayment), min_balance = COALESCE(:mb, min_balance), "
                + "update_date = now() WHERE asn = :a",
                Db.p("d", f.discount() == null ? null : f.discount().doubleValue(), "lo", blankToNull(f.limitOil()), "lv", dbl(f.limitVolume()),
                        "lt", f.limitTime() == null ? 0 : f.limitTime(), "la", dbl(f.limitAmountDay()), "lvm", dbl(f.limitVolumeMonth()),
                        "lam", dbl(f.limitAmountMonth()), "cl", f.creditLine() == null ? null : f.creditLine().doubleValue(), "bill", f.billDate(),
                        "repay", f.repayDay(), "mb", f.limitMinBalance(), "a", card.asn()));
        afterUpdate(c, card, f.password());
        return details(user, card.asn());
    }

    /** Nouveau code PIN si saisi ; journal de modification (type 6). */
    private void afterUpdate(StationContext.Ctx c, StationCards.Card card, String password) {
        if (password != null && !password.isBlank()) {
            cards.setPin(card, password.trim());
        }
        cards.log(card, BigDecimal.ZERO, card.balance(), 6, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
    }

    // ------------------------------------------------------------------ Utilitaires

    private Map<String, Object> result(StationCards.Card card, String account) {
        Map<String, Object> r = new LinkedHashMap<>(cards.view(card));
        r.put("account", account);
        return r;
    }

    private static void checkPin(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new ApiException("Le mot de passe de la carte (code PIN) doit contenir 4 à 8 chiffres");
        }
    }

    private static String req(String v, String label, int max) {
        if (v == null || v.isBlank()) {
            throw new ApiException(label + " obligatoire");
        }
        len(v, max, label);
        return v.trim();
    }

    private static void len(String v, int max, String label) {
        if (v != null && v.trim().length() > max) {
            throw new ApiException(label + " : " + max + " caractères maximum");
        }
    }

    private static BigDecimal positive(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException(label + " obligatoire");
        }
        return v;
    }

    private static double dbl(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
