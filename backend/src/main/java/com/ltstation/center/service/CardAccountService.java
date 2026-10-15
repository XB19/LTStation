package com.ltstation.center.service;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.SessionUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Opérations communes sur les comptes de carte (DealServer.AddAccountLog, GetCusCardInfo,
 * GetEmpCardInfo, GetGroupCardInfo de l'original).
 *
 * Préfixes d'ASN : 010001 utilisateur, 010002 gestion de groupe, 010004 employé, 010008 application,
 * 010011 crédit utilisateur, 010012 crédit de groupe, 010013 carte secondaire, 010014 carte principale,
 * FFFFFF… carte M1 (prédécoupée).
 */
@Service
public class CardAccountService {

    private final Db db;

    public CardAccountService(Db db) {
        this.db = db;
    }

    /** Opérateur d'une opération : employé connecté, sinon premier employé (comme l'original). */
    public record Operator(int employeeId, String name, int stationId) {
    }

    public Operator operator(SessionUser user) {
        if (user.getEmpId() > 0) {
            Map<String, Object> e = db.one("SELECT employee_id, name, station_id FROM employees WHERE employee_id = :id",
                    Map.of("id", user.getEmpId()));
            if (e != null) {
                return new Operator(num(e.get("employeeId")), (String) e.get("name"), num(e.get("stationId")));
            }
        }
        Map<String, Object> first = db.one("SELECT employee_id, station_id FROM employees ORDER BY employee_id LIMIT 1", Map.of());
        return new Operator(first == null ? 0 : num(first.get("employeeId")), user.getLoginName(),
                first == null ? 0 : num(first.get("stationId")));
    }

    /** Type de carte du journal selon le préfixe de l'ASN (AddAccountLog). */
    public static int cardTypeId(String asn) {
        if (asn == null || asn.length() <= 6) {
            return 16;
        }
        return switch (asn.substring(0, 6)) {
            case "010001" -> 1;
            case "010002" -> 2;
            case "010011" -> 11;
            case "010012" -> 12;
            case "010013" -> 13;
            case "010014" -> 14;
            case "110003" -> 15;
            case "110001" -> 17;
            default -> 4;
        };
    }

    /**
     * Écrit une ligne dans account_service_logs (AddAccountLog).
     * transactionType : 1 ouverture, 2 recharge, 11 consommation, 12 annulation… (voir Labels.TRANSACTION_TYPES).
     */
    public long addAccountLog(String accountNo, BigDecimal amount, String asn, BigDecimal balance, int transactionType,
                              BigDecimal discount, BigDecimal fee, Operator op, int paymentId, LocalDateTime when, int stationId) {
        Map<String, Object> p = Db.p("acc", accountNo, "amount", r3(amount).doubleValue(), "asn", asn,
                "bal", r3(balance).doubleValue(), "type", transactionType, "ctype", cardTypeId(asn),
                "disc", r3(discount).doubleValue(), "fee", r3(fee).doubleValue(), "emp", op.employeeId(), "op", op.name(),
                "station", stationId == 0 ? op.stationId() : stationId, "pay", paymentId, "when", when, "now", LocalDateTime.now());
        Number id = db.jdbc().queryForObject("INSERT INTO account_service_logs (transaction_type_id, asn, card_type_id, account_no, "
                + "process_code, card_service_log_id, amount, fee_amount, discount_amount, account_datetime, payment_id, cheque_no, "
                + "station_id, employee_id, terminal_no, trans_datetime, balance, credit_count_id, operator_name) VALUES "
                + "(:type, :asn, :ctype, :acc, '', :type, :amount, :fee, :disc, :now, :pay, '', :station, :emp, '', :when, :bal, '', :op) "
                + "RETURNING id", new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(p), Number.class);
        return id == null ? 0 : id.longValue();
    }

    /** Carte utilisateur + porteur + compte (GetCusCardInfo). */
    public Map<String, Object> customerCard(String asn) {
        Map<String, Object> r = db.one("SELECT ci.*, cu.name customer_name, cu.telephone, cu.vehicle_no, cu.certificate_no, "
                + "a.account_no acc_no, a.balance, a.customer_account_id acc_id FROM customer_card_infos ci "
                + "LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "LEFT JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id WHERE ci.asn = :a",
                Map.of("a", asn == null ? "" : asn));
        if (r == null) {
            throw new ApiException("Les informations de l'utilisateur n'existent pas");
        }
        if (r.get("customerName") == null) {
            throw new ApiException("L'utilisateur n'existe pas");
        }
        if (r.get("accId") == null) {
            throw new ApiException("Le compte de l'utilisateur n'existe pas");
        }
        return r;
    }

    /** Carte employé + employé + compte (GetEmpCardInfo). */
    public Map<String, Object> employeeCard(String asn) {
        Map<String, Object> r = db.one("SELECT ec.*, e.name employee_name, e.station_id emp_station_id, a.account_no acc_no, "
                + "a.balance, a.customer_account_id acc_id FROM employee_card_infos ec "
                + "LEFT JOIN employees e ON e.employee_id = ec.employee_id "
                + "LEFT JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id WHERE ec.asn = :a",
                Map.of("a", asn == null ? "" : asn));
        if (r == null || r.get("employeeName") == null || r.get("accId") == null) {
            throw new ApiException("Les informations de la carte employé n'existent pas");
        }
        return r;
    }

    /** Carte de groupe + groupe + compte de groupe (GetGroupCardInfo). */
    public Map<String, Object> groupCard(String asn) {
        Map<String, Object> r = db.one("SELECT gc.*, g.name group_name, ga.account_no acc_no, ga.balance FROM group_card_infos gc "
                + "LEFT JOIN groups g ON g.group_id = gc.group_id "
                + "LEFT JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id WHERE gc.asn = :a",
                Map.of("a", asn == null ? "" : asn));
        if (r == null || r.get("groupName") == null || r.get("accNo") == null) {
            throw new ApiException("Les informations du groupe n'existent pas");
        }
        return r;
    }

    /** Ajoute (ou retire si négatif) un montant au solde d'un compte utilisateur, renvoie le nouveau solde. */
    public BigDecimal addToCustomerBalance(int accountId, BigDecimal delta) {
        Number v = db.jdbc().queryForObject("UPDATE customer_accounts SET balance = round(CAST(balance + :d AS numeric), 3), "
                + "update_datetime = now() WHERE customer_account_id = :id RETURNING balance",
                Db.p("d", delta.doubleValue(), "id", accountId), Number.class);
        if (v == null) {
            throw new ApiException("Le compte de l'utilisateur n'existe pas");
        }
        return BigDecimal.valueOf(v.doubleValue());
    }

    public static BigDecimal r3(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(3, RoundingMode.HALF_UP);
    }

    private static int num(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
