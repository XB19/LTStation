package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fenêtre « Solde » (Deposit d'origine) : recharge, débit, consommation, remboursement de crédit, recharge rapide,
 * report des recharges en ligne et virement / transfert. Règles reprises de BalanceChange, MasterGroupBalanceChange,
 * SaveOrTransfer, OnlineDepositChange et DealBillRepay ; sans puce, les montants sont portés directement au compte.
 */
@RestController
@RequestMapping("/api/station/balance")
public class StationBalanceController {

    private final Db db;
    private final StationContext stations;
    private final StationCards cards;

    public StationBalanceController(Db db, StationContext stations, StationCards cards) {
        this.db = db;
        this.stations = stations;
        this.cards = cards;
    }

    /** Lecture de la carte (GetDespoitUser) : porteur, solde, recharges en ligne et transferts en attente, cartes du groupe. */
    @GetMapping("/card/{asn}")
    public Map<String, Object> card(@CurrentUser SessionUser user, @PathVariable String asn) {
        stations.of(user);
        StationCards.Card c = cards.find(asn);
        if (c.status() != 0) {
            throw new ApiException("L'état de la carte ne permet pas l'opération (" + StationCards.statusLabel(c.status()) + ")");
        }
        Map<String, Object> m = new LinkedHashMap<>(cards.view(c));
        m.put("pendingOnline", db.decimal("SELECT COALESCE(sum(amount), 0) FROM online_deposit WHERE asn = :a AND is_using = 0", Map.of("a", c.asn())));
        m.put("pendingTransfer", db.decimal("SELECT COALESCE(sum(amount), 0) FROM save_transfer WHERE save_to_asn = :a AND is_using = false",
                Map.of("a", c.asn())));
        if (c.asn().startsWith("010002") || c.asn().startsWith("010014")) {
            m.put("members", db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN group_card_infos gc ON gc.group_id = ci.group_id "
                    + "WHERE gc.asn = :a AND ci.card_status_type_id = 0 ORDER BY ci.asn", Map.of("a", c.asn()), String.class));
        }
        return m;
    }

    public record DepositForm(String asn, BigDecimal amount, BigDecimal gift, Integer payType, BigDecimal score) {
    }

    /**
     * Recharge (BalanceChange / MasterGroupBalanceChange, type 2) : solde + montant + bonus ; paiement = indice + 1
     * (0 espèces, 1 banque, 2 paiement en ligne) ; les points saisis s'ajoutent à une carte utilisateur.
     */
    @PostMapping("/deposit")
    @Transactional
    public Map<String, Object> deposit(@CurrentUser SessionUser user, @RequestBody DepositForm f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = usable(f.asn());
        String p = card.asn().substring(0, 6);
        if (!List.of("010001", "010002", "010004", "010006", "010011", "010014").contains(p)) {
            throw new ApiException("Ce type de carte ne se recharge pas ici");
        }
        stations.requireOpenDuty(c.stationId(), c.employeeId());
        BigDecimal amount = positive(f.amount(), "Montant de la recharge");
        BigDecimal gift = f.gift() == null ? BigDecimal.ZERO : f.gift().setScale(2, RoundingMode.HALF_UP);
        int pay = (f.payType() == null ? 0 : f.payType()) + 1;
        BigDecimal bal = cards.move(card, amount.add(gift));
        cards.log(card, amount, bal, 2, gift, c.operator(), pay, c.stationId());
        if (p.equals("010001") && f.score() != null && f.score().signum() > 0) {
            db.update("UPDATE customer_card_infos SET score = COALESCE(score, 0) + :sc, update_datetime = now() WHERE asn = :a",
                    Map.of("sc", f.score().doubleValue(), "a", card.asn()));
        }
        return result(card.asn(), amount, gift, bal);
    }

    public record DebitForm(String asn, BigDecimal amount, String kind, String pin) {
    }

    /** Débit (« Charge », type 12) ou consommation en boutique (« Consumption », type 13), montant enregistré en négatif. */
    @PostMapping("/debit")
    @Transactional
    public Map<String, Object> debit(@CurrentUser SessionUser user, @RequestBody DebitForm f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = cards.authorize(f.asn(), f.pin());
        BigDecimal amount = positive(f.amount(), "Montant");
        BigDecimal floor = card.credit() ? card.creditLine().negate() : BigDecimal.ZERO;
        if (card.balance().subtract(amount).compareTo(floor) < 0) {
            throw new ApiException("Solde insuffisant (solde : " + card.balance().toPlainString() + ")");
        }
        stations.requireOpenDuty(c.stationId(), c.employeeId());
        BigDecimal bal = cards.move(card, amount.negate());
        cards.log(card, amount.negate(), bal, "CONSUME".equals(f.kind()) ? 13 : 12, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        return result(card.asn(), amount, BigDecimal.ZERO, bal);
    }

    /** Report des recharges en ligne faites depuis le Center (OnlineDepositChange). */
    @PostMapping("/online/{asn}")
    @Transactional
    public Map<String, Object> online(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = usable(asn);
        if (card.kind() != StationCards.Kind.USER) {
            throw new ApiException("Seules les cartes utilisateur reçoivent les recharges en ligne");
        }
        BigDecimal amount = db.decimal("SELECT COALESCE(sum(amount), 0) FROM online_deposit WHERE asn = :a AND is_using = 0", Map.of("a", card.asn()));
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException("Aucune recharge en ligne en attente pour cette carte");
        }
        BigDecimal bal = cards.move(card, amount);
        db.update("UPDATE online_deposit SET is_using = 20 WHERE asn = :a AND is_using = 0", Map.of("a", card.asn()));
        cards.log(card, amount, bal, 2, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        return result(card.asn(), amount, BigDecimal.ZERO, bal);
    }

    public record TransferForm(String asn, String toAsn, BigDecimal amount) {
    }

    /** Virement d'une carte de groupe vers une carte du groupe (SaveOrTransfer 10 puis 15 : sans puce, le montant est crédité aussitôt). */
    @PostMapping("/transfer")
    @Transactional
    public Map<String, Object> transfer(@CurrentUser SessionUser user, @RequestBody TransferForm f) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card from = usable(f.asn());
        if (!from.asn().startsWith("010002") && !from.asn().startsWith("010014")) {
            throw new ApiException("Le virement se fait depuis une carte de groupe");
        }
        StationCards.Card to = usable(f.toAsn());
        BigDecimal amount = positive(f.amount(), "Montant du virement").setScale(2, RoundingMode.HALF_UP);
        if (from.balance().compareTo(amount) < 0) {
            throw new ApiException("Solde du groupe insuffisant (solde : " + from.balance().toPlainString() + ")");
        }
        Integer group = db.jdbc().queryForObject("SELECT group_id FROM group_card_infos WHERE asn = :a", Map.of("a", from.asn()), Integer.class);
        BigDecimal fromBal = cards.move(from, amount.negate());
        cards.log(from, amount, fromBal, 10, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        db.update("INSERT INTO save_transfer (group_id, asn, st_type, amount, create_time, save_to_asn, is_using) VALUES (:g, :a, 10, :m, now(), :t, true)",
                Db.p("g", group, "a", from.asn(), "m", amount, "t", to.asn()));
        BigDecimal toBal = cards.move(to, amount);
        cards.log(to, amount, toBal, 16, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        Map<String, Object> r = result(from.asn(), amount, BigDecimal.ZERO, fromBal);
        r.put("toBalance", toBal);
        return r;
    }

    /** Chargement des virements en attente sur une carte utilisateur (SaveOrTransfer 15, « Change »). */
    @PostMapping("/transfer-load/{asn}")
    @Transactional
    public Map<String, Object> transferLoad(@CurrentUser SessionUser user, @PathVariable String asn) {
        StationContext.Ctx c = stations.of(user);
        StationCards.Card card = usable(asn);
        BigDecimal amount = db.decimal("SELECT COALESCE(sum(amount), 0) FROM save_transfer WHERE save_to_asn = :a AND is_using = false",
                Map.of("a", card.asn()));
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException("Aucun virement en attente pour cette carte");
        }
        BigDecimal bal = cards.move(card, amount);
        db.update("UPDATE save_transfer SET is_using = true WHERE save_to_asn = :a AND is_using = false", Map.of("a", card.asn()));
        cards.log(card, amount, bal, card.kind() == StationCards.Kind.GROUP ? 19 : 16, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        return result(card.asn(), amount, BigDecimal.ZERO, bal);
    }

    /** Recherche de cartes pour la recharge rapide (GetCardMessage) : 0 cartes de crédit/utilisateur, 2 cartes de groupe. */
    @GetMapping("/search")
    public List<String> search(@CurrentUser SessionUser user, @RequestParam(defaultValue = "2") int cardType, @RequestParam(defaultValue = "") String name,
                               @RequestParam(defaultValue = "") String phone, @RequestParam(defaultValue = "") String certNo,
                               @RequestParam(defaultValue = "") String carNo) {
        stations.of(user);
        Map<String, Object> p = Map.of("n", name.trim(), "ph", phone.trim(), "cn", certNo.trim(), "car", carNo.trim());
        if (name.isBlank() && phone.isBlank() && certNo.isBlank() && carNo.isBlank()) {
            throw new ApiException("Saisissez au moins un critère de recherche");
        }
        if (cardType == 2) {
            return db.jdbc().queryForList("SELECT gc.asn FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id WHERE gc.card_status_type_id = 0 "
                    + "AND (:n = '' OR g.name = :n) AND (:ph = '' OR g.telephone = :ph) AND (:cn = '' OR g.certificate_no = :cn) ORDER BY gc.asn", p, String.class);
        }
        return db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                + "WHERE ci.card_status_type_id = 0 AND (:n = '' OR cu.name = :n) AND (:ph = '' OR cu.telephone = :ph) AND (:cn = '' OR cu.certificate_no = :cn) "
                + "AND (:car = '' OR cu.vehicle_no = :car) " + (cardType == 1 ? "AND left(ci.asn, 6) = '010011' " : "") + "ORDER BY ci.asn", p, String.class);
    }

    /** Factures de crédit (GetCreditPay). */
    @GetMapping("/bills")
    public PageResult<Map<String, Object>> bills(@CurrentUser SessionUser user, @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        stations.of(user);
        return db.page("SELECT b.id, b.group_id, g.name group_name, b.asn, b.amount, b.repay_amount, b.statue, b.bill_date FROM credit_bill b "
                + "LEFT JOIN groups g ON g.group_id = b.group_id", Map.of(), "b.statue, b.create_date DESC", page, size);
    }

    public record RepayForm(BigDecimal amount) {
    }

    /** Remboursement d'une facture de crédit (DealBillRepay) : le compte du groupe est crédité, les cartes de la facture au prorata. */
    @PostMapping("/bills/{id}/repay")
    @Transactional
    public void repay(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody RepayForm f) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> b = db.one("SELECT * FROM credit_bill WHERE id = :id", Map.of("id", id));
        if (b == null) {
            throw new ApiException("Facture introuvable");
        }
        BigDecimal amount = positive(f.amount(), "Montant");
        BigDecimal billAmount = dec(b.get("amount"));
        BigDecimal repaid = dec(b.get("repayAmount"));
        if (amount.compareTo(billAmount) > 0) {
            throw new ApiException("Montant incorrect");
        }
        StationCards.Card group = cards.find((String) b.get("asn"));
        BigDecimal bal = cards.move(group, amount);
        cards.log(group, amount, bal, 14, BigDecimal.ZERO, c.operator(), 1, c.stationId());
        BigDecimal remaining = billAmount.subtract(repaid);
        boolean partial = amount.compareTo(remaining) < 0;
        for (Map<String, Object> item : db.list("SELECT * FROM create_bill_data WHERE create_bill_id = :id", Map.of("id", id))) {
            BigDecimal due = dec(item.get("amount")).subtract(dec(item.get("repayAmount")));
            BigDecimal part = partial && remaining.signum() > 0 ? due.multiply(amount).divide(remaining, 3, RoundingMode.HALF_UP) : due;
            try {
                StationCards.Card cc = cards.find((String) item.get("asn"));
                cards.move(cc, part);
            } catch (ApiException ignored) {
                // carte supprimée : la facture est soldée quand même, comme l'original
            }
            db.update("UPDATE create_bill_data SET statue = :st, repay_amount = COALESCE(repay_amount, 0) + :p, update_time = now() WHERE id = :i",
                    Db.p("st", partial ? 1 : 2, "p", part, "i", item.get("id")));
        }
        db.update("UPDATE credit_bill SET repay_amount = :r, statue = :st, update_date = now() WHERE id = :id",
                Db.p("r", partial ? repaid.add(amount) : billAmount, "st", partial ? 1 : 2, "id", id));
    }

    private StationCards.Card usable(String asn) {
        StationCards.Card c = cards.find(asn);
        if (c.status() != 0) {
            throw new ApiException("L'état de la carte ne permet pas l'opération (" + StationCards.statusLabel(c.status()) + ")");
        }
        return c;
    }

    private static Map<String, Object> result(String asn, BigDecimal amount, BigDecimal gift, BigDecimal balance) {
        Map<String, Object> r = new HashMap<>();
        r.put("asn", asn);
        r.put("amount", amount);
        r.put("gift", gift);
        r.put("balance", balance);
        r.put("time", java.time.LocalDateTime.now());
        return r;
    }

    private static BigDecimal positive(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0) {
            throw new ApiException(label + " : saisissez un montant supérieur à 0");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
