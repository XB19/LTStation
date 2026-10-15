package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
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
 * Remises & points (Main/EmpoyeeScore d'origine) : remises, cashback sur recharge,
 * taux de points utilisateurs et employés, niveaux de membre et consommation de points.
 */
@RestController
@RequestMapping("/api/discount-score")
public class DiscountScoreController {

    private final Db db;
    /** SecretSame=1 d'origine : les remises sont propres à chaque société. */
    private final boolean discountPerCompany;

    public DiscountScoreController(Db db, @Value("${app.discount-per-company:true}") boolean discountPerCompany) {
        this.db = db;
        this.discountPerCompany = discountPerCompany;
    }

    // ------------------------------------------------------------ Remises (table dis_count)

    @GetMapping("/discounts")
    public List<Map<String, Object>> discounts(@CurrentUser SessionUser user) {
        String sql = "SELECT id, name, dis_count_value, create_time, company_id FROM dis_count WHERE is_run = true"
                + (discountPerCompany ? " AND company_id = :c" : "") + " ORDER BY id";
        return db.list(sql, Db.p("c", user.companyGroupId())).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("createTime", millis(r.get("createTime")));
            return m;
        }).toList();
    }

    public record DiscountForm(String name, BigDecimal value) {
    }

    @PostMapping("/discounts")
    @Transactional
    public void addDiscount(@CurrentUser SessionUser user, @RequestBody DiscountForm f) {
        if (db.one("SELECT 1 FROM dis_count WHERE dis_count_value = :v AND is_run = true", Db.p("v", f.value())) != null) {
            throw new ApiException("Cette valeur de remise existe déjà");
        }
        db.update("INSERT INTO dis_count (name, dis_count_value, is_run, create_time, company_id) VALUES (:n, :v, true, :t, :c)",
                Db.p("n", f.name(), "v", f.value(), "t", System.currentTimeMillis(), "c", user.companyGroupId()));
    }

    @PutMapping("/discounts/{id}")
    @Transactional
    public void updateDiscount(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody DiscountForm f) {
        if (db.one("SELECT 1 FROM dis_count WHERE dis_count_value = :v AND id <> :id AND is_run = true",
                Db.p("v", f.value(), "id", id)) != null) {
            throw new ApiException("Cette valeur de remise existe déjà");
        }
        exists("dis_count", id, "La remise n'existe pas");
        db.update("UPDATE dis_count SET name = :n, dis_count_value = :v, company_id = :c WHERE id = :id",
                Db.p("n", f.name(), "v", f.value(), "c", user.companyGroupId(), "id", id));
    }

    @DeleteMapping("/discounts/{id}")
    @Transactional
    public void deleteDiscount(@PathVariable int id) {
        exists("dis_count", id, "La remise n'existe pas");
        db.update("UPDATE dis_count SET is_run = false WHERE id = :id", Db.p("id", id));
    }

    // ------------------------------------------------------------ Cashback sur recharge (amount_radio)

    @GetMapping("/deposit-returns")
    public List<Map<String, Object>> depositReturns() {
        return db.list("SELECT * FROM amount_radio ORDER BY amount", Db.p());
    }

    public record DepositReturnForm(String name, BigDecimal amount, BigDecimal radio) {
    }

    @PostMapping("/deposit-returns")
    @Transactional
    public void addDepositReturn(@RequestBody DepositReturnForm f) {
        db.update("INSERT INTO amount_radio (name, amount, radio, create_time) VALUES (:n, :a, :r, :t)",
                Db.p("n", f.name(), "a", f.amount(), "r", f.radio(), "t", LocalDateTime.now()));
    }

    @PutMapping("/deposit-returns/{id}")
    @Transactional
    public void updateDepositReturn(@PathVariable int id, @RequestBody DepositReturnForm f) {
        exists("amount_radio", id, "Le palier de cashback n'existe pas");
        db.update("UPDATE amount_radio SET name = :n, amount = :a, radio = :r WHERE id = :id",
                Db.p("n", f.name(), "a", f.amount(), "r", f.radio(), "id", id));
    }

    @DeleteMapping("/deposit-returns/{id}")
    @Transactional
    public void deleteDepositReturn(@PathVariable int id) {
        exists("amount_radio", id, "Le palier de cashback n'existe pas");
        db.update("DELETE FROM amount_radio WHERE id = :id", Db.p("id", id));
    }

    // ------------------------------------------------------------ Taux de points utilisateurs (score_rate)

    /** scoreType : 0 = consommation (par énergie), 1 = recharge. */
    @GetMapping("/score-rates")
    public List<Map<String, Object>> scoreRates() {
        return db.list("SELECT * FROM score_rate WHERE is_run = true ORDER BY id", Db.p());
    }

    public record ScoreRateForm(Integer scoreType, Integer oilTypeId, String oilTypeName, BigDecimal rate) {
    }

    @PostMapping("/score-rates")
    @Transactional
    public void addScoreRate(@RequestBody ScoreRateForm f) {
        int type = f.scoreType() == null ? 0 : f.scoreType();
        int oil = -1;
        String oilName = "";
        if (type == 0) {
            oil = f.oilTypeId() == null ? -1 : f.oilTypeId();
            oilName = f.oilTypeName() == null ? "" : f.oilTypeName();
            if (db.one("SELECT 1 FROM score_rate WHERE oil_type_id = :o AND is_run = true", Db.p("o", oil)) != null) {
                throw new ApiException("Le taux de conversion des points existe déjà");
            }
        } else if (db.one("SELECT 1 FROM score_rate WHERE score_type = :t AND is_run = true", Db.p("t", type)) != null) {
            throw new ApiException("Le taux de conversion des points existe déjà");
        }
        db.update("INSERT INTO score_rate (oil_type_id, oil_type_name, rate, create_time, is_run, score_type) "
                + "VALUES (:o, :on, :r, :t, true, :st)",
                Db.p("o", oil, "on", oilName, "r", f.rate(), "t", System.currentTimeMillis(), "st", type));
    }

    @PutMapping("/score-rates/{id}")
    @Transactional
    public void updateScoreRate(@PathVariable int id, @RequestBody ScoreRateForm f) {
        int oil = f.oilTypeId() == null ? -1 : f.oilTypeId();
        if (db.one("SELECT 1 FROM score_rate WHERE is_run = true AND id <> :id AND oil_type_id = :o",
                Db.p("id", id, "o", oil)) != null) {
            throw new ApiException("Le taux de ce type existe déjà");
        }
        if (db.one("SELECT 1 FROM score_rate WHERE is_run = true AND id = :id", Db.p("id", id)) == null) {
            throw new ApiException("Taux de points introuvable");
        }
        db.update("UPDATE score_rate SET oil_type_id = :o, oil_type_name = :on, rate = :r WHERE id = :id",
                Db.p("o", oil, "on", f.oilTypeName() == null ? "" : f.oilTypeName(), "r", f.rate(), "id", id));
    }

    @DeleteMapping("/score-rates/{id}")
    @Transactional
    public void deleteScoreRate(@PathVariable int id) {
        if (db.update("UPDATE score_rate SET is_run = false WHERE id = :id AND is_run = true", Db.p("id", id)) == 0) {
            throw new ApiException("Taux de points introuvable");
        }
    }

    // ------------------------------------------------------------ Points des employés (emp_score_rate)

    @GetMapping("/employee-rates")
    public List<Map<String, Object>> employeeRates() {
        return db.list("SELECT e.*, a.id authority_id FROM emp_score_rate e LEFT JOIN authority_mess a "
                + "ON a.user_id_mess = e.auth_id WHERE e.is_run = true ORDER BY e.id", Db.p());
    }

    /** authorityId = authority_mess.id (le niveau stocké est son user_id_mess, comme l'original). */
    public record EmployeeRateForm(Integer authorityId, BigDecimal rate) {
    }

    @PostMapping("/employee-rates")
    @Transactional
    public void addEmployeeRate(@RequestBody EmployeeRateForm f) {
        Map<String, Object> auth = authority(f.authorityId());
        if (db.one("SELECT 1 FROM emp_score_rate WHERE auth_id = :a AND is_run = true", Db.p("a", auth.get("userIdMess"))) != null) {
            throw new ApiException("Les points de ce niveau existent déjà");
        }
        db.update("INSERT INTO emp_score_rate (auth_id, auth_name, rate, emp_type, create_time, is_run) "
                + "VALUES (:a, :n, :r, 0, :t, true)",
                Db.p("a", auth.get("userIdMess"), "n", auth.get("name"), "r", f.rate(), "t", LocalDateTime.now()));
    }

    @PutMapping("/employee-rates/{id}")
    @Transactional
    public void updateEmployeeRate(@PathVariable int id, @RequestBody EmployeeRateForm f) {
        Map<String, Object> auth = authority(f.authorityId());
        exists("emp_score_rate", id, "Objet inexistant");
        if (db.one("SELECT 1 FROM emp_score_rate WHERE id <> :id AND auth_id = :a AND is_run = true",
                Db.p("id", id, "a", auth.get("userIdMess"))) != null) {
            throw new ApiException("Les points de ce niveau existent déjà");
        }
        db.update("UPDATE emp_score_rate SET auth_id = :a, auth_name = :n, emp_type = 0, rate = :r WHERE id = :id",
                Db.p("a", auth.get("userIdMess"), "n", auth.get("name"), "r", f.rate(), "id", id));
    }

    @DeleteMapping("/employee-rates/{id}")
    @Transactional
    public void deleteEmployeeRate(@PathVariable int id) {
        exists("emp_score_rate", id, "Objet inexistant");
        db.update("UPDATE emp_score_rate SET is_run = false WHERE id = :id", Db.p("id", id));
    }

    // ------------------------------------------------------------ Niveaux de membre (member_discount)

    @GetMapping("/member-rates")
    public List<Map<String, Object>> memberRates() {
        return db.list("SELECT * FROM member_discount WHERE is_using = true ORDER BY id", Db.p());
    }

    public record MemberRateForm(String memberName, BigDecimal discount, BigDecimal totalConsume) {
    }

    @PostMapping("/member-rates")
    @Transactional
    public void addMemberRate(@RequestBody MemberRateForm f) {
        db.update("INSERT INTO member_discount (member_name, discount, total_consume, is_using, create_time) "
                + "VALUES (:n, :d, :c, true, :t)",
                Db.p("n", f.memberName(), "d", f.discount(), "c", f.totalConsume(), "t", LocalDateTime.now()));
    }

    @PutMapping("/member-rates/{id}")
    @Transactional
    public void updateMemberRate(@PathVariable int id, @RequestBody MemberRateForm f) {
        exists("member_discount", id, "Objet inexistant");
        db.update("UPDATE member_discount SET member_name = :n, discount = :d, total_consume = :c WHERE id = :id",
                Db.p("n", f.memberName(), "d", f.discount(), "c", f.totalConsume(), "id", id));
    }

    @DeleteMapping("/member-rates/{id}")
    @Transactional
    public void deleteMemberRate(@PathVariable int id) {
        exists("member_discount", id, "Objet inexistant");
        db.update("UPDATE member_discount SET is_using = false WHERE id = :id", Db.p("id", id));
    }

    // ------------------------------------------------------------ Consommation de points

    /**
     * Recherche (GetCardScore) : type 0 = utilisateur (liste de ses cartes), 1 = employé
     * (compte + points), 2 = groupe (liste de ses cartes).
     */
    @GetMapping("/holders")
    public Map<String, Object> holder(@RequestParam int type, @RequestParam(defaultValue = "") String name,
                                      @RequestParam(defaultValue = "") String phone) {
        Map<String, Object> p = new HashMap<>();
        p.put("n", name);
        p.put("p", phone);
        Map<String, Object> out = new LinkedHashMap<>();
        switch (type) {
            case 1 -> {
                Map<String, Object> e = db.one("SELECT employee_id, name, score FROM employees WHERE (name = :n AND :n <> '') "
                        + "OR (telephone = :p AND :p <> '') LIMIT 1", p);
                if (e == null) {
                    throw new ApiException("L'employé n'existe pas");
                }
                out.put("account", e.get("employeeId"));
                out.put("score", round(e.get("score")));
            }
            case 2 -> {
                Map<String, Object> g = db.one("SELECT group_id FROM groups WHERE ((name = :n OR short_name = :n) AND :n <> '') "
                        + "OR (telephone = :p AND :p <> '') LIMIT 1", p);
                if (g == null) {
                    throw new ApiException("Le groupe n'existe pas");
                }
                out.put("cards", db.jdbc().queryForList("SELECT asn FROM group_card_infos WHERE group_id = :g",
                        Db.p("g", g.get("groupId")), String.class));
            }
            default -> {
                Map<String, Object> c = db.one("SELECT customer_id FROM customers WHERE (telephone = :p AND :p <> '') "
                        + "OR (name = :n AND :n <> '') LIMIT 1", p);
                if (c == null) {
                    throw new ApiException("L'utilisateur n'existe pas");
                }
                out.put("cards", db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE customer_id = :c",
                        Db.p("c", c.get("customerId")), String.class));
            }
        }
        return out;
    }

    /** Points d'une carte (GetCardScoreByCardNo). */
    @GetMapping("/cards/{asn}")
    public Map<String, Object> card(@PathVariable String asn, @RequestParam int type) {
        Map<String, Object> c = type == 2
                ? db.one("SELECT asn, group_account_id account, 0 score FROM group_card_infos WHERE asn = :a", Db.p("a", asn))
                : db.one("SELECT asn, customer_account_id account, score FROM customer_card_infos WHERE asn = :a", Db.p("a", asn));
        if (c == null) {
            throw new ApiException("L'utilisateur n'existe pas");
        }
        c.put("score", round(c.get("score")));
        return c;
    }

    public record ScoreDealForm(Integer type, String cardNo, Integer employeeId, BigDecimal score) {
    }

    /** Déduction de points (ScoreDeal) avec journal dans score_log. */
    @PostMapping("/consume")
    @Transactional
    public Map<String, Object> consume(@RequestBody ScoreDealForm f) {
        BigDecimal deal = f.score() == null ? BigDecimal.ZERO : f.score().abs();
        if (deal.signum() == 0) {
            throw new ApiException("Saisissez le nombre de points à consommer");
        }
        int type = f.type() == null ? 0 : f.type();
        int accountId;
        BigDecimal current;
        if (type == 1) {
            Map<String, Object> e = db.one("SELECT employee_id, score FROM employees WHERE employee_id = :id",
                    Db.p("id", f.employeeId() == null ? 0 : f.employeeId()));
            if (e == null) {
                throw new ApiException("L'employé n'existe pas");
            }
            accountId = ((Number) e.get("employeeId")).intValue();
            current = num(e.get("score"));
            check(current, deal);
            db.update("UPDATE employees SET score = :s WHERE employee_id = :id",
                    Db.p("s", current.subtract(deal), "id", accountId));
        } else if (type == 0) {
            Map<String, Object> c = db.one("SELECT customer_account_id, score FROM customer_card_infos WHERE asn = :a",
                    Db.p("a", f.cardNo() == null ? "" : f.cardNo()));
            if (c == null) {
                throw new ApiException("L'utilisateur n'existe pas");
            }
            accountId = ((Number) c.get("customerAccountId")).intValue();
            current = num(c.get("score"));
            check(current, deal);
            db.update("UPDATE customer_card_infos SET score = :s WHERE asn = :a",
                    Db.p("s", current.subtract(deal).doubleValue(), "a", f.cardNo()));
        } else {
            throw new ApiException("Les cartes de groupe n'ont pas de points");
        }
        BigDecimal balance = current.subtract(deal);
        db.update("INSERT INTO score_log (customer_account_id, current_score, deal_score, score_balance, create_time) "
                + "VALUES (:a, :c, :d, :b, :t)",
                Db.p("a", accountId, "c", current, "d", deal.negate(), "b", balance, "t", LocalDateTime.now()));
        return Db.p("score", balance);
    }

    // ------------------------------------------------------------ Utilitaires

    private Map<String, Object> authority(Integer id) {
        Map<String, Object> a = id == null ? null
                : db.one("SELECT id, user_id_mess, name FROM authority_mess WHERE id = :id", Db.p("id", id));
        if (a == null) {
            throw new ApiException("Niveau de droits introuvable");
        }
        return a;
    }

    private void exists(String table, int id, String message) {
        if (db.one("SELECT 1 FROM " + table + " WHERE id = :id", Db.p("id", id)) == null) {
            throw new ApiException(message);
        }
    }

    private static void check(BigDecimal current, BigDecimal deal) {
        if (current.compareTo(deal) < 0) {
            throw new ApiException("Les points actuels sont inférieurs aux points à consommer");
        }
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static BigDecimal round(Object v) {
        return num(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDateTime millis(Object v) {
        if (!(v instanceof Number n) || n.longValue() <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(n.longValue()), java.time.ZoneId.systemDefault());
    }
}
