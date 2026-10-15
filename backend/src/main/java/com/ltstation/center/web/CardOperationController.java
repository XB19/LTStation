package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.common.SelectItem;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService;
import com.ltstation.center.service.CardAccountService.Operator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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
 * Opérations sur cartes depuis le centre (Main/DepositOnline, Main/MCardTranslate, Main/MCardUpdate) :
 * recharge / annulation en ligne et gestion des cartes M1 prédécoupées.
 */
@RestController
@RequestMapping("/api/card-ops")
public class CardOperationController {

    private final Db db;
    private final CardAccountService cards;
    private final boolean perCompany;
    private final boolean creditPayAdvance;

    public CardOperationController(Db db, CardAccountService cards,
                                   @Value("${app.discount-per-company:true}") boolean perCompany,
                                   @Value("${app.credit-pay-advance:true}") boolean creditPayAdvance) {
        this.db = db;
        this.cards = cards;
        this.perCompany = perCompany;
        this.creditPayAdvance = creditPayAdvance;
    }

    // ------------------------------------------------------------ Recharge en ligne

    /**
     * Recherche des cartes actives d'un porteur (GetDepositUser).
     * cardType : 0 = utilisateur (hors cartes de flotte), 1 = employé, 2 = application.
     * reverse = true : exclut les cartes IC utilisateur 010001 (annulation impossible en ligne).
     */
    @GetMapping("/holder-cards")
    public List<SelectItem> holderCards(@CurrentUser SessionUser user, @RequestParam int cardType,
                                        @RequestParam(defaultValue = "") String userName, @RequestParam(defaultValue = "") String phone,
                                        @RequestParam(defaultValue = "") String cno, @RequestParam(defaultValue = "false") boolean reverse) {
        Map<String, Object> p = Db.p("n", userName.trim(), "ph", phone.trim(), "cno", cno.trim(), "c", companyOf(user));
        List<SelectItem> out = new ArrayList<>();
        switch (cardType) {
            case 1 -> {
                int i = 1;
                for (String asn : db.jdbc().queryForList("SELECT ec.asn FROM employee_card_infos ec JOIN employees e "
                        + "ON e.employee_id = ec.employee_id WHERE ec.card_status_type_id = 0 AND ((e.name = :n AND :n <> '') "
                        + "OR (e.telephone = :ph AND :ph <> '') OR (e.certify_key = :cno AND :cno <> ''))"
                        + (perCompany ? " AND ec.company_id = :c" : ""), p, String.class)) {
                    out.add(new SelectItem(i++, asn));
                }
            }
            case 2 -> db.list("SELECT ci.customer_id id, ci.asn FROM customer_card_infos ci JOIN customers cu "
                    + "ON cu.customer_id = ci.customer_id WHERE ci.card_status_type_id = 0 AND substring(ci.asn, 1, 6) = '010008' "
                    + customerFilter(), p).forEach(r -> out.add(new SelectItem(r.get("id"), (String) r.get("asn"))));
            default -> db.list("SELECT ci.customer_id id, ci.asn FROM customer_card_infos ci JOIN customers cu "
                    + "ON cu.customer_id = ci.customer_id WHERE ci.card_status_type_id = 0 " + customerFilter()
                    + (perCompany ? " AND ci.company_id = :c" : "") + (reverse ? " AND substring(ci.asn, 1, 6) <> '010001'" : "")
                    // Les cartes M1 rattachées à une flotte (team_id) sont exclues (judgeCardType)
                    + " AND NOT EXISTS (SELECT 1 FROM pre_card_m m WHERE substring(ci.asn, 1, 6) = 'FFFFFF' "
                    + "AND m.card_no = right(ci.asn, 10) AND m.team_id IS NOT NULL)", p)
                    .forEach(r -> out.add(new SelectItem(r.get("id"), (String) r.get("asn"))));
        }
        return out;
    }

    private static String customerFilter() {
        return " AND (:n = '' OR cu.name = :n) AND (:ph = '' OR cu.telephone = :ph) AND (:cno = '' OR cu.certificate_no = :cno)"
                + " AND (:n <> '' OR :ph <> '' OR :cno <> '')";
    }

    /** État et solde d'une carte (GetUserMessage). */
    @GetMapping("/cards/{asn}")
    public Map<String, Object> card(@PathVariable String asn) {
        if (asn.length() < 6) {
            throw new ApiException("Numéro de carte incorrect");
        }
        String prefix = asn.substring(0, 6);
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> c;
        switch (prefix) {
            case "010004" -> {
                c = cards.employeeCard(asn);
                out.put("userName", c.get("employeeName"));
                out.put("stationId", c.get("empStationId"));
            }
            case "010014", "010012", "010002" -> {
                c = cards.groupCard(asn);
                out.put("userName", c.get("groupName"));
                out.put("stationId", c.get("stationId"));
            }
            case "010008", "010013", "010011", "010001" -> {
                c = cards.customerCard(asn);
                out.put("userName", c.get("customerName"));
                out.put("stationId", c.get("stationId"));
            }
            default -> {
                if (!prefix.startsWith("FFFF")) {
                    throw new ApiException("Numéro de carte incorrect");
                }
                c = cards.customerCard(asn);
                out.put("userName", c.get("customerName"));
                out.put("stationId", c.get("stationId"));
            }
        }
        if (((Number) c.get("cardStatusTypeId")).intValue() != 0) {
            throw new ApiException("Cette carte n'est pas dans un état normal");
        }
        out.put("asn", asn);
        out.put("balance", c.get("balance"));
        return out;
    }

    public record AmountForm(String cardNo, BigDecimal amount) {
    }

    /**
     * Recharge en ligne (CardDepositAmount) : enregistre une recharge administrateur (online_deposit) avec le
     * cashback du palier applicable. Les cartes sans puce (application 010008, M1 FFFFFF, crédit 010011 si
     * avance autorisée) sont créditées immédiatement ; les cartes IC le seront à la prochaine lecture en station.
     */
    @PostMapping("/deposit")
    @Transactional
    public void deposit(@CurrentUser SessionUser user, @RequestBody AmountForm f) {
        BigDecimal amount = positive(f.amount());
        String asn = f.cardNo() == null ? "" : f.cardNo().trim();
        if (asn.length() < 6) {
            throw new ApiException("Numéro de carte incorrect");
        }
        Operator op = cards.operator(user);
        Map<String, Object> tier = db.one("SELECT radio FROM amount_radio WHERE amount <= :a ORDER BY amount DESC LIMIT 1",
                Map.of("a", amount));
        BigDecimal cashback = tier == null ? BigDecimal.ZERO : amount.multiply(new BigDecimal(tier.get("radio").toString()));
        String prefix = asn.substring(0, 6);
        int isUsing = 0;
        boolean immediate = prefix.equals("010008") || prefix.equals("FFFFFF") || (prefix.equals("010011") && creditPayAdvance);
        if (prefix.equals("010011") && !creditPayAdvance) {
            return; // comme l'original : rien n'est enregistré
        }
        if (immediate) {
            Map<String, Object> c = cards.customerCard(asn);
            BigDecimal balance = cards.addToCustomerBalance(((Number) c.get("accId")).intValue(), amount);
            isUsing = 3;
            cards.addAccountLog((String) c.get("accNo"), amount, asn, balance, 2, BigDecimal.ZERO, BigDecimal.ZERO, op, 1,
                    LocalDateTime.now(), op.stationId());
        }
        db.update("INSERT INTO online_deposit (asn, order_no, amount, is_using, create_time, dis_count_amount, charge_type, "
                + "operate_name, station_id, company_id) VALUES (:asn, :order, :a, :u, now(), :d, 2, :op, :s, :c)",
                Db.p("asn", asn, "order", "OnlineSave" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                        "a", amount, "u", isUsing, "d", cashback, "op", op.name(), "s", op.stationId(), "c", companyOf(user)));
    }

    /** Annulation (contre-passation) d'un montant sur une carte sans puce (CardUnPay). */
    @PostMapping("/reverse")
    @Transactional
    public void reverse(@CurrentUser SessionUser user, @RequestBody AmountForm f) {
        BigDecimal amount = positive(f.amount());
        String asn = f.cardNo() == null ? "" : f.cardNo().trim();
        Map<String, Object> c = cards.customerCard(asn);
        String prefix = asn.substring(0, 6);
        if (prefix.equals("010008") || prefix.equals("FFFFFF")) {
            Operator op = cards.operator(user);
            BigDecimal balance = cards.addToCustomerBalance(((Number) c.get("accId")).intValue(), amount.negate());
            cards.addAccountLog((String) c.get("accNo"), amount.negate(), asn, balance, 12, BigDecimal.ZERO, BigDecimal.ZERO, op, 1,
                    LocalDateTime.now(), op.stationId());
        } else if (!prefix.equals("010011")) {
            throw new ApiException("Numéro de carte incorrect");
        }
    }

    // ------------------------------------------------------------ Cartes M1 prédécoupées

    /** Stock des cartes M1 (GetMCardReport). */
    @GetMapping("/m1")
    public PageResult<Map<String, Object>> m1Cards(@CurrentUser SessionUser user, @RequestParam(defaultValue = "") String cardNo,
                                                   @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        String where = " WHERE (:card = '' OR card_no LIKE :like)" + (perCompany ? " AND company_id = :c" : "");
        return db.page("SELECT id, card_no, \"using\" in_use, team_id FROM pre_card_m" + where,
                Db.p("card", cardNo.trim(), "like", "%" + cardNo.trim() + "%", "c", user.companyGroupId()), "\"using\", id", page, size);
    }

    public record M1Form(String cardNo, String phone, String name, String cno, String password, String carNo) {
    }

    /**
     * Attribution d'une carte M1 libre à un nouveau porteur (App/MCardImport) : crée le client
     * (identifiants 500000001+), la carte (ASN complété par des F à 20 caractères) et le compte.
     */
    @PostMapping("/m1/assign")
    @Transactional
    public Map<String, Object> assign(@CurrentUser SessionUser user, @RequestBody M1Form f) {
        Operator op = cards.operator(user);
        Map<String, Object> st = db.one("SELECT company_id FROM stations WHERE station_id = :s", Map.of("s", op.stationId()));
        if (st == null) {
            throw new ApiException("Station de l'opérateur introuvable");
        }
        Map<String, Object> pre = db.one("SELECT id FROM pre_card_m WHERE card_no = :c AND \"using\" = false",
                Db.p("c", f.cardNo()));
        if (pre == null) {
            throw new ApiException("La carte prédécoupée n'existe pas ou est déjà utilisée");
        }
        Integer max = db.jdbc().queryForObject("SELECT max(customer_id) FROM customer_card_infos WHERE customer_id > 500000000 "
                + "AND customer_id < 600000000", Map.of(), Integer.class);
        int id = max == null || max == 0 ? 500000001 : max + 1;
        String asn = "F".repeat(Math.max(0, 20 - f.cardNo().length())) + f.cardNo();
        Map<String, Object> p = Db.p("id", id, "code", String.valueOf(id), "name", f.name(), "ph", f.phone(), "cno", f.cno(),
                "car", f.carNo(), "asn", asn, "station", op.stationId(), "company", st.get("companyId"), "pwd", f.password(),
                "emp", op.name());
        db.update("INSERT INTO customers (customer_id, name, code, certificate_type_id, certificate_no, telephone, vehicle_no, "
                + "group_id, issue_status) VALUES (:id, :name, :code, 1, :cno, :ph, :car, 100000001, true)", p);
        db.update("INSERT INTO customer_accounts (customer_account_id, account_no, balance, deposit_datetime, book_balance, "
                + "consume_datetime, load_up_limit, service_fee, update_datetime, status, total_times_daily, total_amount_daily, "
                + "total_amount_monthly, total_volume_monthly, mileage_lastest, volume_lastest, trans_time, total_times_daily_store, "
                + "total_amount_daily_store, total_amount_monthly_store) VALUES (:id, :code, 0, now(), 0, now(), 0, 0, now(), '0', "
                + "0, 0, 0, 0, 0, 0, now(), 0, 0, 0)", p);
        db.update("INSERT INTO customer_card_infos (asn, customer_id, customer_account_id, group_id, is_credit_card, credit_lines, "
                + "account_no, cipher_flg, discount, station_id, issue_datetime, valid_datetime, card_status_type_id, "
                + "limit_type_id, is_limit_vehicle_no, media_type_id, company_id, open_accoun_emp_name) VALUES (:asn, :id, :id, "
                + "100000001, false, 0, :code, :pwd, 100, :station, now(), now(), 0, 0, false, 1, :company, :emp)", p);
        db.update("UPDATE pre_card_m SET \"using\" = true WHERE id = :pid", Map.of("pid", pre.get("id")));
        cards.addAccountLog(String.valueOf(id), BigDecimal.ZERO, asn, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, op, 1,
                LocalDateTime.now(), op.stationId());
        return Map.of("asn", asn);
    }

    /** Suppression d'une carte M1 non utilisée (App/MCardDelete). */
    @DeleteMapping("/m1/{id}")
    @Transactional
    public void deleteM1(@PathVariable int id) {
        Map<String, Object> c = db.one("SELECT \"using\" in_use FROM pre_card_m WHERE id = :id", Map.of("id", id));
        if (c == null) {
            throw new ApiException("La carte n'existe pas");
        }
        if (Boolean.TRUE.equals(c.get("inUse"))) {
            throw new ApiException("La carte est déjà utilisée");
        }
        db.update("DELETE FROM pre_card_m WHERE id = :id", Map.of("id", id));
    }

    /** Informations modifiables d'une carte utilisateur (GetUserDetial). */
    @GetMapping("/customer-cards/{asn}")
    public Map<String, Object> customerCard(@PathVariable String asn) {
        Map<String, Object> c = cards.customerCard(asn);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cardNo", asn);
        out.put("name", c.get("customerName"));
        out.put("groupId", c.get("groupId"));
        out.put("carNo", c.get("vehicleNo"));
        out.put("phone", c.get("telephone"));
        out.put("password", "");
        return out;
    }

    /** Modification du porteur et du mot de passe d'une carte (Home/MCardUpdate). */
    @PutMapping("/customer-cards/{asn}")
    @Transactional
    public void updateCustomerCard(@PathVariable String asn, @RequestBody M1Form f) {
        Map<String, Object> c = cards.customerCard(asn);
        db.update("UPDATE customers SET name = :n, telephone = :ph, vehicle_no = :car WHERE customer_id = :id",
                Db.p("n", f.name(), "ph", f.phone(), "car", f.carNo(), "id", c.get("customerId")));
        if (f.password() != null && !f.password().isEmpty()) {
            db.update("UPDATE customer_card_infos SET cipher_flg = :p WHERE asn = :a", Db.p("p", f.password(), "a", asn));
        }
    }

    private int companyOf(SessionUser user) {
        Operator op = cards.operator(user);
        Map<String, Object> st = db.one("SELECT company_id FROM stations WHERE station_id = :s", Map.of("s", op.stationId()));
        return st == null || st.get("companyId") == null ? user.companyGroupId() : ((Number) st.get("companyId")).intValue();
    }

    private static BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException("Montant incorrect");
        }
        return v;
    }
}
