package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.crypto.PinHasher;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.DataVersionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des cartes en station (CardMake / ICBoxCard d'origine, sans lecteur) : émission, recharge, code PIN,
 * perte, remplacement, clôture et historique. Les écritures reprennent celles des actions ServerDeal
 * (CreateCusTomerCardInfo, CreateEmployeeCardInfo, BalanceChange, CardLost, CardChange, ReloadPwd).
 */
@RestController
@RequestMapping("/api/station/cards")
public class StationCardController {

    private static final Set<Integer> DEPOSIT_PAYMENTS = Set.of(1, 7, 10);

    private final Db db;
    private final StationContext stations;
    private final StationCards cards;
    private final DataVersionService versions;

    public StationCardController(Db db, StationContext stations, StationCards cards, DataVersionService versions) {
        this.db = db;
        this.stations = stations;
        this.cards = cards;
        this.versions = versions;
    }

    // ------------------------------------------------------------------ Consultation

    @GetMapping("/{asn}")
    public Map<String, Object> get(@CurrentUser SessionUser user, @PathVariable String asn) {
        stations.of(user);
        return cards.view(cards.find(asn));
    }

    /** Recherche par numéro de carte, nom, téléphone ou immatriculation. */
    @GetMapping
    public List<Map<String, Object>> search(@CurrentUser SessionUser user, @RequestParam String q) {
        stations.of(user);
        String t = q == null ? "" : q.trim();
        if (t.length() < 2) {
            return List.of();
        }
        Map<String, Object> p = Map.of("q", t, "like", "%" + t.toLowerCase() + "%");
        return db.list("SELECT * FROM ("
                + "SELECT ci.asn, cu.name holder, cu.telephone phone, cu.vehicle_no, ci.card_status_type_id status, a.balance FROM customer_card_infos ci "
                + "JOIN customers cu ON cu.customer_id = ci.customer_id LEFT JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id "
                + "WHERE ci.asn = :q OR lower(cu.name) LIKE :like OR cu.telephone LIKE :like OR lower(cu.vehicle_no) LIKE :like "
                + "UNION ALL SELECT ec.asn, e.name, e.telephone, NULL, ec.card_status_type_id, a.balance FROM employee_card_infos ec "
                + "JOIN employees e ON e.employee_id = ec.employee_id LEFT JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id "
                + "WHERE ec.asn = :q OR lower(e.name) LIKE :like OR e.telephone LIKE :like "
                + "UNION ALL SELECT gc.asn, g.name, g.telephone, gc.limit_vehicle_no, gc.card_status_type_id, ga.balance FROM group_card_infos gc "
                + "JOIN groups g ON g.group_id = gc.group_id LEFT JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id "
                + "WHERE gc.asn = :q OR lower(g.name) LIKE :like OR g.telephone LIKE :like) x ORDER BY status, holder LIMIT 50", p);
    }

    /** Historique du compte de la carte (inclut les cartes remplacées du même compte). */
    @GetMapping("/{asn}/history")
    public List<Map<String, Object>> history(@CurrentUser SessionUser user, @PathVariable String asn) {
        stations.of(user);
        StationCards.Card c = cards.find(asn);
        return db.list("SELECT l.id, l.trans_datetime, l.transaction_type_id, t.transaction_type_desc_en type_label, l.asn, l.amount, "
                + "l.discount_amount gift, l.balance, l.payment_id, s.name station_name, l.operator_name FROM account_service_logs l "
                + "LEFT JOIN transaction_types t ON t.transaction_type_id = l.transaction_type_id LEFT JOIN stations s ON s.station_id = l.station_id "
                + "WHERE l.account_no = :acc ORDER BY l.trans_datetime DESC, l.id DESC LIMIT 300", Map.of("acc", c.accountNo()));
    }

    // ------------------------------------------------------------------ Recherches (CardLostGetMess, CardChangeGetMess, CardChangeGetAsnMess)

    /**
     * Cartes d'un porteur selon le type de carte (0 prépayée 010001, 1 employé, 2 crédit 010011, 3 groupe 010002, 4 crédit de groupe 010012,
     * 5 principale 010014, 6 secondaire 010013), la pièce d'identité, le nom ou le téléphone. lost = true : cartes perdues (remplacement).
     */
    @GetMapping("/lookup")
    public List<String> lookup(@CurrentUser SessionUser user, @RequestParam(defaultValue = "0") int cardType,
                               @RequestParam(required = false) Integer certType, @RequestParam(defaultValue = "") String certNo,
                               @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String phone,
                               @RequestParam(defaultValue = "false") boolean lost) {
        stations.of(user);
        if (certNo.isBlank() && name.isBlank() && phone.isBlank()) {
            throw new ApiException("Saisissez au moins un critère de recherche");
        }
        Map<String, Object> p = new java.util.HashMap<>(Map.of("cn", certNo.trim(), "n", name.trim(), "ph", phone.trim(), "st", lost ? 1 : 0));
        p.put("ct", certType == null ? -1 : certType);
        String status = lost ? "IN (1, 2)" : "= 0";
        return switch (cardType) {
            case 1 -> db.jdbc().queryForList("SELECT ec.asn FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id "
                    + "WHERE ec.card_status_type_id " + status + " AND (:n = '' OR e.name = :n) AND (:ph = '' OR e.telephone = :ph) ORDER BY ec.asn", p, String.class);
            case 3, 4, 5 -> {
                p.put("pre", cardType == 3 ? "010002" : cardType == 4 ? "010012" : "010014");
                yield db.jdbc().queryForList("SELECT gc.asn FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id WHERE left(gc.asn, 6) = :pre "
                        + "AND gc.card_status_type_id " + status + " AND (:cn = '' OR g.certificate_no = :cn) AND (:n = '' OR g.name = :n) "
                        + "AND (:ph = '' OR g.telephone = :ph) ORDER BY gc.asn", p, String.class);
            }
            default -> {
                p.put("pre", cardType == 2 ? "010011" : cardType == 6 ? "010013" : "010001");
                yield db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                        + "WHERE left(ci.asn, 6) = :pre AND ci.card_status_type_id " + status + " AND (:cn = '' OR (cu.certificate_no = :cn "
                        + "AND (:ct < 0 OR cu.certificate_type_id = :ct))) AND (:n = '' OR cu.name = :n) AND (:ph = '' OR cu.telephone = :ph) ORDER BY ci.asn",
                        p, String.class);
            }
        };
    }

    /** Porteur, solde, solde du journal, dates et station d'émission d'une carte (CardChangeGetAsnMess). */
    @GetMapping("/{asn}/info")
    public Map<String, Object> info(@CurrentUser SessionUser user, @PathVariable String asn) {
        stations.of(user);
        StationCards.Card c = cards.find(asn);
        Map<String, Object> m = new java.util.LinkedHashMap<>(cards.view(c));
        Map<String, Object> extra = switch (c.kind()) {
            case USER -> db.one("SELECT CAST(ci.customer_id AS text) user_id, ci.issue_datetime, s.name station_name FROM customer_card_infos ci "
                    + "LEFT JOIN stations s ON s.station_id = ci.station_id WHERE ci.asn = :a", Map.of("a", c.asn()));
            case EMPLOYEE -> db.one("SELECT CAST(ec.employee_id AS text) user_id, ec.issue_datetime, s.name station_name FROM employee_card_infos ec "
                    + "LEFT JOIN stations s ON s.station_id = ec.station_id WHERE ec.asn = :a", Map.of("a", c.asn()));
            case GROUP -> db.one("SELECT CAST(gc.group_id AS text) user_id, gc.issue_datetime, s.name station_name FROM group_card_infos gc "
                    + "LEFT JOIN stations s ON s.station_id = gc.station_id WHERE gc.asn = :a", Map.of("a", c.asn()));
        };
        if (extra != null) {
            m.putAll(extra);
        }
        Map<String, Object> last = db.one("SELECT balance FROM account_service_logs WHERE asn = :a ORDER BY trans_datetime DESC, id DESC LIMIT 1",
                Map.of("a", c.asn()));
        m.put("logBalance", last == null ? null : last.get("balance"));
        return m;
    }

    /** Cartes employé en service de la station (CardLostGetMessEmployee). */
    @GetMapping("/employee-cards")
    public List<Map<String, Object>> employeeCards(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        return db.list("SELECT ec.asn, e.name, s.name station_name, ec.issue_datetime, ec.card_status_type_id status, ec.employee_id "
                + "FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id LEFT JOIN stations s ON s.station_id = ec.station_id "
                + "WHERE ec.card_status_type_id = 0 AND (e.station_id = :s OR ec.station_id = :s) ORDER BY ec.asn", Map.of("s", c.stationId()));
    }

    // ------------------------------------------------------------------ Émission

    public record CustomerCardForm(String type, String name, String phone, Integer certificateTypeId, String certificateNo, String address,
                                   String email, String vehicleNo, BigDecimal discount, BigDecimal creditLine, String pin,
                                   BigDecimal initialDeposit, Integer paymentId) {
    }

    /** Carte utilisateur (010001) ou carte de crédit (010011) avec son porteur et son compte. */
    @PostMapping("/customer")
    @Transactional
    public Map<String, Object> issueCustomer(@CurrentUser SessionUser user, @RequestBody CustomerCardForm f) {
        StationContext.Ctx c = stations.of(user);
        boolean credit = "CREDIT".equalsIgnoreCase(f.type());
        String name = required(f.name(), "Nom du porteur");
        maxLen(name, 20, "Nom du porteur");
        maxLen(f.phone(), 20, "Téléphone");
        maxLen(f.certificateNo(), 20, "N° de pièce");
        maxLen(f.vehicleNo(), 20, "Immatriculation");
        maxLen(f.address(), 80, "Adresse");
        maxLen(f.email(), 50, "E-mail");
        checkPinFormat(f.pin());
        int certType = f.certificateTypeId() == null ? 1 : f.certificateTypeId();
        if (f.certificateNo() != null && !f.certificateNo().isBlank() && db.one("SELECT 1 FROM customers WHERE certificate_type_id = :t "
                + "AND certificate_no = :n", Map.of("t", certType, "n", f.certificateNo().trim())) != null) {
            throw new ApiException("Ce numéro de pièce d'identité est déjà utilisé");
        }
        String prefix = credit ? "010011" : "010001";
        String asn = cards.nextAsn("customer_card_infos", prefix);
        int account = credit ? Integer.parseInt("17" + asn.substring(asn.length() - 8)) : cards.nextAccount(2_100_000_001, Integer.MAX_VALUE);
        if (db.one("SELECT 1 FROM customer_accounts WHERE customer_account_id = :a", Map.of("a", account)) != null) {
            account = cards.nextAccount(1_700_000_001, 1_799_999_999);
        }
        String accNo = String.valueOf(account);
        BigDecimal creditLine = credit ? positive(f.creditLine(), "Plafond de crédit") : BigDecimal.ZERO;
        BigDecimal discount = f.discount() == null ? BigDecimal.valueOf(100) : f.discount();
        if (discount.signum() <= 0 || discount.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException("La remise doit être comprise entre 1 et 100 % du prix");
        }
        db.update("INSERT INTO customers (customer_id, name, code, certificate_type_id, certificate_no, address, telephone, email, vehicle_no, "
                + "group_id, issue_status, company_id) VALUES (:id, :n, :code, :ct, :cn, :ad, :ph, :em, :v, 100000001, true, :c)",
                Db.p("id", account, "n", name, "code", accNo.substring(accNo.length() - 6), "ct", certType, "cn", trim(f.certificateNo()),
                        "ad", trim(f.address()), "ph", trim(f.phone()), "em", trim(f.email()), "v", trim(f.vehicleNo()), "c", c.companyId()));
        cards.insertAccount(account, accNo);
        db.update("INSERT INTO customer_card_infos (asn, customer_id, customer_account_id, group_id, is_credit_card, credit_lines, account_no, "
                + "discount, station_id, issue_datetime, valid_datetime, card_status_type_id, used_amount_monthly, limit_type_id, is_limit_vehicle_no, "
                + "media_type_id, company_id, score, open_accoun_emp_name, update_datetime, limit_vehicle_no, special_price, pin_hash) VALUES "
                + "(:asn, :id, :id, 100000001, :cr, :cl, :acc, :d, :s, now(), now() + interval '5 years', 0, 0, 0, false, 1, :c, 0, :op, now(), :v, '', :pin)",
                Db.p("asn", asn, "id", account, "cr", credit, "cl", creditLine.doubleValue(), "acc", accNo, "d", discount.doubleValue(),
                        "s", c.stationId(), "c", c.companyId(), "op", c.employeeName(), "v", trim(f.vehicleNo()), "pin", PinHasher.hash(f.pin())));
        StationCards.Card card = cards.find(asn);
        cards.log(card, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        if (f.initialDeposit() != null && f.initialDeposit().signum() > 0) {
            deposit(c, card, f.initialDeposit(), BigDecimal.ZERO, f.paymentId());
        }
        versions.bump("white");
        return cards.view(cards.find(asn));
    }

    public record EmployeeCardForm(Integer employeeId, String pin) {
    }

    /** Carte employé (010004) pour un employé de la station. */
    @PostMapping("/employee")
    @Transactional
    public Map<String, Object> issueEmployee(@CurrentUser SessionUser user, @RequestBody EmployeeCardForm f) {
        StationContext.Ctx c = stations.of(user);
        checkPinFormat(f.pin());
        Map<String, Object> e = db.one("SELECT employee_id, name FROM employees WHERE employee_id = :e AND station_id = :s AND is_using = true",
                Db.p("e", f.employeeId(), "s", c.stationId()));
        if (e == null) {
            throw new ApiException("Employé introuvable dans cette station");
        }
        if (db.one("SELECT 1 FROM employee_card_infos WHERE employee_id = :e AND card_status_type_id = 0", Map.of("e", f.employeeId())) != null) {
            throw new ApiException("Cet employé a déjà une carte en service");
        }
        String asn = cards.nextAsn("employee_card_infos", "010004");
        int account = cards.nextAccount(2_000_000_001, 2_099_999_999);
        cards.insertAccount(account, String.valueOf(account));
        db.update("INSERT INTO employee_card_infos (asn, employee_id, card_type_id, issue_datetime, valid_datetime, customer_account_id, "
                + "card_status_type_id, station_id, open_station_id, company_id, open_accoun_emp_name, pin_hash) VALUES (:asn, :e, 4, now(), "
                + "now() + interval '5 years', :acc, 0, :s, :s, :c, :op, :pin)",
                Db.p("asn", asn, "e", f.employeeId(), "acc", account, "s", c.stationId(), "c", c.companyId(), "op", c.employeeName(),
                        "pin", PinHasher.hash(f.pin())));
        StationCards.Card card = cards.find(asn);
        cards.log(card, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
        return cards.view(card);
    }

    // ------------------------------------------------------------------ Recharge

    public record DepositForm(BigDecimal amount, BigDecimal gift, Integer paymentId) {
    }

    /** Recharge (BalanceChange, type 2) : le solde augmente du montant + bonus ; nécessite un poste ouvert. */
    @PostMapping("/{asn}/deposit")
    @Transactional
    public Map<String, Object> deposit(@CurrentUser SessionUser user, @PathVariable String asn, @RequestBody DepositForm f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.status() != 0) {
            throw new ApiException("Cette carte n'est pas utilisable (" + StationCards.statusLabel(card.status()) + ")");
        }
        deposit(c, card, f.amount(), f.gift(), f.paymentId());
        return cards.view(cards.find(asn));
    }

    private void deposit(StationContext.Ctx c, StationCards.Card card, BigDecimal rawAmount, BigDecimal rawGift, Integer paymentId) {
        stations.requireOpenDuty(c.stationId());
        BigDecimal amount = positive(rawAmount, "Montant").setScale(2, RoundingMode.HALF_UP);
        BigDecimal gift = rawGift == null ? BigDecimal.ZERO : rawGift.setScale(2, RoundingMode.HALF_UP);
        if (gift.signum() < 0) {
            throw new ApiException("Le bonus ne peut pas être négatif");
        }
        int pay = paymentId == null ? 1 : paymentId;
        if (!DEPOSIT_PAYMENTS.contains(pay)) {
            throw new ApiException("Moyen de paiement non pris en charge");
        }
        BigDecimal bal = cards.move(card, amount.add(gift));
        cards.log(card, amount, bal, 2, gift, c.operator(), pay, c.stationId());
    }

    // ------------------------------------------------------------------ Code PIN, perte, remplacement, clôture

    public record PinForm(String pin) {
    }

    /** Nouveau code PIN (ReloadPwd), en présence du porteur. */
    @PostMapping("/{asn}/pin")
    @Transactional
    public Map<String, Object> pin(@CurrentUser SessionUser user, @PathVariable String asn, @RequestBody PinForm f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        cards.setPin(card, f.pin());
        cards.log(card, BigDecimal.ZERO, card.balance(), 4, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        return cards.view(cards.find(asn));
    }

    /** Déclaration de perte (CardLost) : la carte passe en liste noire. */
    @PostMapping("/{asn}/lost")
    @Transactional
    public Map<String, Object> lost(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.status() != 0) {
            throw new ApiException("Cette carte n'est pas en service");
        }
        setStatus(card, 1);
        db.update("INSERT INTO basic_black_lists (asn, time) VALUES (:a, now())", Map.of("a", card.asn()));
        cards.log(card, BigDecimal.ZERO, card.balance(), 3, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("basic_black");
        return cards.view(cards.find(asn));
    }

    /** Annulation de la perte (carte retrouvée) : la carte est retirée de la liste noire. */
    @PostMapping("/{asn}/found")
    @Transactional
    public Map<String, Object> found(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.status() != 1) {
            throw new ApiException("Cette carte n'est pas déclarée perdue");
        }
        setStatus(card, 0);
        db.update("DELETE FROM basic_black_lists WHERE asn = :a", Map.of("a", card.asn()));
        db.update("INSERT INTO delete_black_lists (asn, time) VALUES (:a, now())", Map.of("a", card.asn()));
        cards.log(card, BigDecimal.ZERO, card.balance(), 6, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("basic_black", "del_black");
        return cards.view(cards.find(asn));
    }

    /**
     * Remplacement d'une carte perdue (CardChange) : nouvelle carte sur le même compte (le solde suit),
     * l'ancienne passe à l'état « remplacée ».
     */
    @PostMapping("/{asn}/replace")
    @Transactional
    public Map<String, Object> replace(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.status() != 1 && card.status() != 2) {
            throw new ApiException("Seule une carte déclarée perdue peut être remplacée");
        }
        String newAsn = cards.nextAsn(card.table(), card.asn().substring(0, 6));
        setStatus(card, 3);
        String cols = String.join(", ", db.jdbc().queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = :t "
                + "AND column_name <> 'asn' ORDER BY ordinal_position", Map.of("t", card.table()), String.class));
        db.update("INSERT INTO " + card.table() + " (asn, " + cols + ") SELECT :n, " + cols + " FROM " + card.table() + " WHERE asn = :o",
                Map.of("n", newAsn, "o", card.asn()));
        StationCards.Card fresh = cards.find(newAsn);
        setStatus(fresh, 0);
        cards.log(fresh, BigDecimal.ZERO, fresh.balance(), 8, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        versions.bump("white");
        return cards.view(cards.find(newAsn));
    }

    /** Clôture : le solde restant est remboursé en espèces (type 9) et la carte est fermée. */
    @PostMapping("/{asn}/close")
    @Transactional
    public Map<String, Object> close(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.find(asn);
        if (card.status() == 5 || card.status() == 3) {
            throw new ApiException("Cette carte est déjà fermée");
        }
        if (card.balance().signum() < 0) {
            throw new ApiException("Le solde est négatif : il doit être remboursé avant la clôture");
        }
        stations.requireOpenDuty(c.stationId());
        BigDecimal refund = card.balance();
        BigDecimal bal = refund.signum() > 0 ? cards.move(card, refund.negate()) : BigDecimal.ZERO;
        setStatus(card, 5);
        cards.log(card, refund.negate(), bal, 9, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        db.update("INSERT INTO basic_black_lists (asn, time) VALUES (:a, now())", Map.of("a", card.asn()));
        versions.bump("basic_black");
        Map<String, Object> v = cards.view(cards.find(asn));
        v.put("refund", refund);
        return v;
    }

    // ------------------------------------------------------------------ Utilitaires

    private void setStatus(StationCards.Card card, int status) {
        String upd = switch (card.kind()) {
            case USER -> ", update_datetime = now()";
            case GROUP -> ", update_date = now()";
            case EMPLOYEE -> "";
        };
        db.update("UPDATE " + card.table() + " SET card_status_type_id = :st" + upd + " WHERE asn = :a", Map.of("st", status, "a", card.asn()));
    }




    private static void checkPinFormat(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new ApiException("Le code PIN doit contenir 4 à 8 chiffres");
        }
    }

    private static BigDecimal positive(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException(label + " : valeur supérieure à 0 obligatoire");
        }
        return v;
    }

    private static String required(String v, String label) {
        if (v == null || v.isBlank()) {
            throw new ApiException(label + " obligatoire");
        }
        return v.trim();
    }

    private static void maxLen(String v, int max, String label) {
        if (v != null && v.trim().length() > max) {
            throw new ApiException(label + " : " + max + " caractères maximum");
        }
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }
}
