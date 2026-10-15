package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService;
import com.ltstation.center.service.CardAccountService.Operator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit de certification (Main/OrderSubmit d'origine) : un employé soumet une demande de
 * recharge sur une carte de crédit, un employé disposant du droit 20 la valide ou la refuse.
 * statue : 0 = en attente, 1 = approuvée, 2 = refusée.
 */
@RestController
@RequestMapping("/api/order-checks")
public class OrderCheckController {

    private final Db db;
    private final CardAccountService cards;

    public OrderCheckController(Db db, CardAccountService cards) {
        this.db = db;
        this.cards = cards;
    }

    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user) {
        boolean canCheck = user.hasMenu("20");
        List<Map<String, Object>> rows = db.list("SELECT o.*, cu.name customer_name, se.name submit_name, ce.name check_name "
                + "FROM order_check o JOIN customer_card_infos ci ON ci.asn = o.asn JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "LEFT JOIN employees se ON se.employee_id = o.submit_emp LEFT JOIN employees ce ON ce.employee_id = o.check_emp "
                + "ORDER BY o.statue, o.create_time DESC", Map.of());
        rows.forEach(r -> r.put("needCheck", canCheck));
        return rows;
    }

    public record OrderForm(String asn, BigDecimal amount) {
    }

    @PostMapping
    @Transactional
    public void add(@CurrentUser SessionUser user, @RequestBody OrderForm f) {
        int emp = employee(user);
        validate(f);
        Map<String, Object> c = db.one("SELECT cu.name FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "WHERE ci.asn = :a", Map.of("a", f.asn()));
        db.update("INSERT INTO order_check (amount, asn, create_time, statue, user_name, order_no, submit_emp) "
                + "VALUES (:a, :asn, now(), 0, :n, :no, :e)",
                Db.p("a", f.amount(), "asn", f.asn(), "n", c == null ? "" : c.get("name"),
                        "no", "OrderCheck" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), "e", emp));
    }

    @PutMapping("/{id}")
    @Transactional
    public void update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody OrderForm f) {
        int emp = employee(user);
        validate(f);
        Map<String, Object> o = pending(id);
        if (((Number) o.get("submitEmp")).intValue() != emp) {
            throw new ApiException("Seul l'auteur de la demande peut la modifier");
        }
        db.update("UPDATE order_check SET asn = :a, amount = :m WHERE id = :id", Db.p("a", f.asn(), "m", f.amount(), "id", id));
    }

    public record CheckForm(boolean pass) {
    }

    /** Validation : crédite immédiatement la carte (010011 ou 010016), journalise et trace la recharge. */
    @PostMapping("/{id}/check")
    @Transactional
    public void check(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody CheckForm f) {
        int emp = employee(user);
        Map<String, Object> o = pending(id);
        String asn = (String) o.get("asn");
        if (f.pass()) {
            if (asn == null || !(asn.startsWith("010016") || asn.startsWith("010011"))) {
                throw new ApiException("Cette carte ne permet pas cette opération");
            }
            Operator op = cards.operator(user);
            BigDecimal amount = new BigDecimal(o.get("amount").toString()).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> c = cards.customerCard(asn);
            BigDecimal balance = cards.addToCustomerBalance(((Number) c.get("accId")).intValue(), amount);
            cards.addAccountLog((String) c.get("accountNo"), amount, asn, balance, 2, BigDecimal.ZERO, BigDecimal.ZERO, op, 1,
                    LocalDateTime.now(), op.stationId());
            Map<String, Object> submitter = db.one("SELECT name FROM employees WHERE employee_id = :e", Map.of("e", o.get("submitEmp")));
            Map<String, Object> st = db.one("SELECT company_id FROM stations WHERE station_id = :s", Map.of("s", op.stationId()));
            db.update("INSERT INTO online_deposit (amount, asn, create_time, order_no, charge_type, operate_name, station_id, company_id) "
                    + "VALUES (:a, :asn, now(), :no, 2, :op, :s, :c)",
                    Db.p("a", amount, "asn", asn, "no", "YZSH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                            "op", submitter == null ? "" : submitter.get("name"), "s", op.stationId(),
                            "c", st == null ? null : st.get("companyId")));
        }
        db.update("UPDATE order_check SET statue = :s, check_emp = :e WHERE id = :id", Map.of("s", f.pass() ? 1 : 2, "e", emp, "id", id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        int emp = employee(user);
        Map<String, Object> o = pending(id);
        if (((Number) o.get("submitEmp")).intValue() != emp) {
            throw new ApiException("Seul l'auteur de la demande peut la supprimer");
        }
        db.update("DELETE FROM order_check WHERE id = :id", Map.of("id", id));
    }

    private Map<String, Object> pending(int id) {
        Map<String, Object> o = db.one("SELECT * FROM order_check WHERE id = :id", Map.of("id", id));
        if (o == null) {
            throw new ApiException("La demande n'existe pas");
        }
        if (o.get("statue") instanceof Number s && s.intValue() != 0) {
            throw new ApiException("Cette demande a déjà été traitée");
        }
        return o;
    }

    /** Ces opérations sont réservées aux employés (comme l'original). */
    private int employee(SessionUser user) {
        if (user.getEmpId() <= 0) {
            throw new ApiException("Le compte actuel n'a pas ce droit d'exécution");
        }
        return user.getEmpId();
    }

    private static void validate(OrderForm f) {
        if (f.asn() == null || f.asn().isBlank() || f.amount() == null || f.amount().signum() <= 0) {
            throw new ApiException("Carte et montant obligatoires");
        }
    }
}
