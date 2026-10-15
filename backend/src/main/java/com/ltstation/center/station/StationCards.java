package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.crypto.PinHasher;
import com.ltstation.center.service.CardAccountService;
import com.ltstation.center.service.CardAccountService.Operator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Cartes sans puce : identification par numéro (ASN) + code PIN, débit / crédit du compte associé.
 * Reprend les règles des actions ServerDeal (GetCusCardInfo, GetEmpCardInfo, GetGroupCardInfo, BalanceChange).
 */
@Service
public class StationCards {

    public enum Kind { USER, EMPLOYEE, GROUP }

    /** Carte résolue avec son compte. Pour une carte de groupe, le compte est dans group_accounts. */
    public record Card(String asn, Kind kind, String typeLabel, String holder, int status, LocalDateTime validUntil, String accountNo,
                       int accountId, BigDecimal balance, BigDecimal discount, BigDecimal creditLine, String limitOil, String pinHash,
                       String phone, String vehicleNo, Integer stationId, String table) {

        public boolean credit() {
            return asn.startsWith("010011") || asn.startsWith("010012");
        }
    }

    private final Db db;
    private final CardAccountService accounts;

    public StationCards(Db db, CardAccountService accounts) {
        this.db = db;
        this.accounts = accounts;
    }

    /** Numéro suivant : plus grand ASN du préfixe + 1 sur 20 chiffres (GetAsnMax), ou préfixe + « 11 » + 000…1. */
    public String nextAsn(String table, String prefix) {
        String max = db.jdbc().queryForObject("SELECT max(asn) FROM " + table + " WHERE left(asn, 6) = :p AND asn ~ '^[0-9]{20}$'",
                Map.of("p", prefix), String.class);
        if (max == null) {
            return prefix + "11" + "000000000001";
        }
        String next = new java.math.BigInteger(max).add(java.math.BigInteger.ONE).toString();
        return "0".repeat(Math.max(0, 20 - next.length())) + next;
    }

    /** Numéro de compte client suivant dans une plage (comptes et porteurs partagent le même numéro). */
    public int nextAccount(long min, long max) {
        Integer cur = db.jdbc().queryForObject("SELECT max(customer_account_id) FROM customer_accounts WHERE customer_account_id BETWEEN :a AND :b",
                Map.of("a", min, "b", Math.min(max, Integer.MAX_VALUE)), Integer.class);
        int next = cur == null ? (int) min : cur + 1;
        while (db.one("SELECT 1 FROM customers WHERE customer_id = :i UNION ALL SELECT 1 FROM customer_accounts WHERE customer_account_id = :i",
                Map.of("i", next)) != null) {
            next++;
        }
        return next;
    }

    /** Numéro de groupe suivant dans une plage (19… groupe personnel, 18… crédit, 14… principal). */
    public int nextGroup(long min, long max) {
        Integer cur = db.jdbc().queryForObject("SELECT max(group_id) FROM groups WHERE group_id BETWEEN :a AND :b",
                Map.of("a", min, "b", max), Integer.class);
        return cur == null ? (int) min : cur + 1;
    }

    public void insertAccount(int id, String accNo) {
        db.update("INSERT INTO customer_accounts (customer_account_id, account_no, balance, deposit_datetime, book_balance, consume_datetime, "
                + "load_up_limit, service_fee, update_datetime, status, total_times_daily, total_amount_daily, total_amount_monthly, "
                + "total_volume_monthly, mileage_lastest, volume_lastest, trans_time, total_times_daily_store, total_amount_daily_store, "
                + "total_amount_monthly_store) VALUES (:id, :no, 0, now(), 0, now(), 0, 0, now(), '0', 0, 0, 0, 0, 0, 0, now(), 0, 0, 0)",
                Map.of("id", id, "no", accNo));
    }

    public static String normalize(String asn) {
        String a = asn == null ? "" : asn.replaceAll("\\s", "").toUpperCase();
        if (a.length() < 6) {
            throw new ApiException("Numéro de carte incorrect");
        }
        return a;
    }

    public static String typeLabel(String asn) {
        return switch (asn.substring(0, 6)) {
            case "010001" -> "Carte utilisateur";
            case "010002" -> "Carte de gestion de groupe";
            case "010004" -> "Carte employé";
            case "010006" -> "Carte employé (bons)";
            case "010008" -> "Carte application";
            case "010011" -> "Carte de crédit";
            case "010012" -> "Carte de crédit de groupe";
            case "010013" -> "Carte secondaire";
            case "010014" -> "Carte principale de groupe";
            case "FFFFFF" -> "Carte M1";
            default -> "Carte";
        };
    }

    public Card find(String rawAsn) {
        String asn = normalize(rawAsn);
        String p = asn.substring(0, 6);
        Map<String, Object> r;
        Kind kind;
        String table;
        switch (p) {
            case "010004", "010006" -> {
                kind = Kind.EMPLOYEE;
                table = "employee_card_infos";
                r = db.one("SELECT ec.asn, ec.card_status_type_id status, ec.valid_datetime, ec.pin_hash, ec.station_id, e.name holder, "
                        + "e.telephone phone, NULL vehicle_no, a.account_no, a.customer_account_id account_id, a.balance, 100 discount, "
                        + "0 credit_lines, NULL limit_oil FROM employee_card_infos ec LEFT JOIN employees e ON e.employee_id = ec.employee_id "
                        + "LEFT JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id WHERE ec.asn = :a", Map.of("a", asn));
            }
            case "010002", "010012", "010014" -> {
                kind = Kind.GROUP;
                table = "group_card_infos";
                r = db.one("SELECT gc.asn, gc.card_status_type_id status, gc.valid_datetime, gc.pin_hash, gc.station_id, g.name holder, "
                        + "g.telephone phone, gc.limit_vehicle_no vehicle_no, ga.account_no, ga.group_account_id account_id, ga.balance, "
                        + "gc.discount, gc.credit_lines, gc.limit_oil FROM group_card_infos gc LEFT JOIN groups g ON g.group_id = gc.group_id "
                        + "LEFT JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id WHERE gc.asn = :a", Map.of("a", asn));
            }
            default -> {
                kind = Kind.USER;
                table = "customer_card_infos";
                r = db.one("SELECT ci.asn, ci.card_status_type_id status, ci.valid_datetime, ci.pin_hash, ci.station_id, cu.name holder, "
                        + "cu.telephone phone, cu.vehicle_no, a.account_no, a.customer_account_id account_id, a.balance, ci.discount, "
                        + "ci.credit_lines, ci.limit_oil FROM customer_card_infos ci LEFT JOIN customers cu ON cu.customer_id = ci.customer_id "
                        + "LEFT JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id WHERE ci.asn = :a", Map.of("a", asn));
            }
        }
        if (r == null) {
            throw new ApiException("Carte inconnue : " + asn);
        }
        if (r.get("accountId") == null) {
            throw new ApiException("Le compte de cette carte n'existe pas");
        }
        return new Card(asn, kind, typeLabel(asn), (String) r.get("holder"), num(r.get("status")).intValue(),
                r.get("validDatetime") instanceof LocalDateTime t ? t : null, (String) r.get("accountNo"), num(r.get("accountId")).intValue(),
                num(r.get("balance")).setScale(2, RoundingMode.HALF_UP), num(r.get("discount")), num(r.get("creditLines")),
                (String) r.get("limitOil"), (String) r.get("pinHash"), (String) r.get("phone"), (String) r.get("vehicleNo"),
                r.get("stationId") instanceof Number n ? n.intValue() : null, table);
    }

    /** Carte utilisable pour un paiement : état normal, non expirée, PIN défini et correct. */
    public Card authorize(String asn, String pin) {
        Card c = find(asn);
        if (c.status() != 0) {
            throw new ApiException("Cette carte n'est pas utilisable (" + statusLabel(c.status()) + ")");
        }
        if (c.validUntil() != null && c.validUntil().isBefore(LocalDateTime.now())) {
            throw new ApiException("Carte expirée");
        }
        checkPin(c, pin);
        return c;
    }

    public void checkPin(Card c, String pin) {
        if (c.pinHash() == null || c.pinHash().isBlank()) {
            throw new ApiException("Aucun code PIN n'est défini pour cette carte : définissez-le dans « Cartes » avant de l'utiliser");
        }
        if (!PinHasher.matches(pin, c.pinHash())) {
            throw new ApiException("Code PIN incorrect");
        }
    }

    public void setPin(Card c, String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new ApiException("Le code PIN doit contenir 4 à 8 chiffres");
        }
        db.update("UPDATE " + c.table() + " SET pin_hash = :h WHERE asn = :a", Map.of("h", PinHasher.hash(pin), "a", c.asn()));
    }

    /** Montant réellement payé avec la remise de la carte (remise exprimée en %, 100 = prix normal). */
    public static BigDecimal discounted(Card c, BigDecimal amount) {
        BigDecimal d = c.discount();
        if (d == null || d.signum() <= 0 || d.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return amount;
        }
        return amount.multiply(d).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Ajoute delta au solde du compte de la carte et renvoie le nouveau solde. */
    public BigDecimal move(Card c, BigDecimal delta) {
        if (c.kind() == Kind.GROUP) {
            Number v = db.jdbc().queryForObject("UPDATE group_accounts SET balance = round(CAST(balance + :d AS numeric), 3), "
                    + "update_datetime = now() WHERE group_account_id = :id RETURNING balance",
                    Map.of("d", delta.doubleValue(), "id", c.accountId()), Number.class);
            return BigDecimal.valueOf(v == null ? 0 : v.doubleValue());
        }
        return accounts.addToCustomerBalance(c.accountId(), delta);
    }

    /** Débit d'un paiement (journal de type 11, montant positif) ; contrôle du solde (ou de la ligne de crédit). */
    public BigDecimal pay(Card c, BigDecimal amount, Operator op, int stationId) {
        BigDecimal floor = c.credit() ? c.creditLine().negate() : BigDecimal.ZERO;
        if (c.balance().subtract(amount).compareTo(floor) < 0) {
            throw new ApiException("Solde insuffisant (solde : " + c.balance().toPlainString() + ")");
        }
        BigDecimal bal = move(c, amount.negate());
        accounts.addAccountLog(c.accountNo(), amount, c.asn(), bal, 11, BigDecimal.ZERO, BigDecimal.ZERO, op, 8, LocalDateTime.now(), stationId);
        return bal;
    }

    public void log(Card c, BigDecimal amount, BigDecimal balance, int type, BigDecimal discount, Operator op, int paymentId, int stationId) {
        accounts.addAccountLog(c.accountNo(), amount, c.asn(), balance, type, discount, BigDecimal.ZERO, op, paymentId, LocalDateTime.now(), stationId);
    }

    public Map<String, Object> view(Card c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("asn", c.asn());
        m.put("kind", c.kind());
        m.put("typeLabel", c.typeLabel());
        m.put("holder", c.holder());
        m.put("status", c.status());
        m.put("statusLabel", statusLabel(c.status()));
        m.put("validUntil", c.validUntil());
        m.put("accountNo", c.accountNo());
        m.put("balance", c.balance());
        m.put("discount", c.discount());
        m.put("creditLine", c.creditLine());
        m.put("hasPin", c.pinHash() != null && !c.pinHash().isBlank());
        m.put("phone", c.phone());
        m.put("vehicleNo", c.vehicleNo());
        return m;
    }

    public static String statusLabel(int s) {
        return switch (s) {
            case 0 -> "Normale";
            case 1 -> "Perdue";
            case 2 -> "Perte vérifiée";
            case 3 -> "Remplacée";
            case 4 -> "Pré-clôturée";
            case 5 -> "Clôturée";
            case 6 -> "Pré-ouverte";
            case 7 -> "Pré-remplacée";
            case 8 -> "Suspendue";
            case 9 -> "Impayé";
            case 10 -> "Plafond de crédit dépassé";
            default -> "État " + s;
        };
    }

    private static BigDecimal num(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
