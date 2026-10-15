package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ventes de carburant saisies à la caisse (remplace la remontée automatique des pompes).
 * Une vente est enregistrée dans oil_transactions, rattachée au poste ouvert ; un paiement par carte
 * débite le compte de la carte (journal de type 11).
 */
@RestController
@RequestMapping("/api/station/sales")
public class StationSaleController {

    /** Moyens de paiement proposés (def_payments) : 1 espèces, 7 carte bancaire, 8 carte IC (carte client), 10 virement. */
    private static final Set<Integer> PAYMENTS = Set.of(1, 7, 8, 10);
    private static final String NO_CARD = "00000000000000000000";

    private final Db db;
    private final StationContext stations;
    private final StationCards cards;

    public StationSaleController(Db db, StationContext stations, StationCards cards) {
        this.db = db;
        this.stations = stations;
        this.cards = cards;
    }

    public record SaleForm(Integer nozId, Integer gradeId, BigDecimal amount, BigDecimal volume, Integer paymentId, String asn, String pin,
                           String carNumber) {
    }

    /** Aperçu (prix, volume, montant, remise de la carte) sans enregistrer. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@CurrentUser SessionUser user, @RequestBody SaleForm f) {
        StationContext.Ctx c = stations.of(user);
        Calc k = calc(c, f);
        Map<String, Object> m = new HashMap<>();
        m.put("price", k.price);
        m.put("volume", k.volume);
        m.put("amount", k.amount);
        m.put("toPay", k.amount);
        if (f.paymentId() != null && f.paymentId() == 8 && f.asn() != null && !f.asn().isBlank()) {
            StationCards.Card card = cards.find(f.asn());
            m.put("card", cards.view(card));
            m.put("toPay", StationCards.discounted(card, k.amount));
        }
        return m;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> sell(@CurrentUser SessionUser user, @RequestBody SaleForm f) {
        StationContext.Ctx c = stations.of(user);
        int duty = stations.requireOpenDuty(c.stationId(), c.employeeId());
        int payment = f.paymentId() == null ? 1 : f.paymentId();
        if (!PAYMENTS.contains(payment)) {
            throw new ApiException("Moyen de paiement non pris en charge");
        }
        Calc k = calc(c, f);
        String asn = NO_CARD;
        BigDecimal toPay = k.amount;
        BigDecimal balance = BigDecimal.ZERO;
        if (payment == 8) {
            StationCards.Card card = cards.authorize(f.asn(), f.pin());
            if (card.limitOil() != null && !card.limitOil().isBlank() && !card.limitOil().trim().equals(k.gradeCode)) {
                throw new ApiException("Cette carte est limitée à une autre énergie");
            }
            toPay = StationCards.discounted(card, k.amount);
            balance = cards.pay(card, toPay, c.operator(), c.stationId());
            asn = card.asn();
            // Points (règles ScoreDeal) : points = litres ou montant × taux / 100, sur les cartes utilisateur
            if (asn.startsWith("010001")) {
                Map<String, Object> rule = db.one("SELECT rate, score_type FROM score_rate WHERE oil_type_id = :g AND COALESCE(is_run, true) = true "
                        + "AND (company_id IS NULL OR company_id = :c) ORDER BY id DESC LIMIT 1", Map.of("g", k.gradeId, "c", c.companyId()));
                if (rule != null) {
                    BigDecimal base = ((Number) rule.get("scoreType")).intValue() == 1 ? toPay : k.volume;
                    BigDecimal pts = base.multiply(new BigDecimal(rule.get("rate").toString())).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                    Map<String, Object> cur = db.one("SELECT score, customer_account_id FROM customer_card_infos WHERE asn = :a", Map.of("a", asn));
                    BigDecimal before = cur.get("score") == null ? BigDecimal.ZERO : new BigDecimal(cur.get("score").toString());
                    db.update("UPDATE customer_card_infos SET score = :s, score_last_load_time = now() WHERE asn = :a", Map.of("s", before.add(pts), "a", asn));
                    db.update("INSERT INTO score_log (customer_account_id, current_score, deal_score, score_balance, create_time) VALUES (:acc, :c, :d, :b, now())",
                            Map.of("acc", cur.get("customerAccountId"), "c", before, "d", pts, "b", before.add(pts)));
                }
            }
        }
        // Index cumulé du pistolet (VolumeTotal) : dernier index + volume servi
        int gun = f.nozId() == null ? 0 : f.nozId();
        Map<String, Object> lastIdx = db.one("SELECT volume_total FROM oil_transactions WHERE station_id = :s AND gun_id = :g "
                + "ORDER BY true_time DESC, id DESC LIMIT 1", Map.of("s", c.stationId(), "g", gun));
        double volumeTotal = (lastIdx == null ? 0 : ((Number) lastIdx.get("volumeTotal")).doubleValue()) + k.volume.doubleValue();
        Integer seq = db.jdbc().queryForObject("SELECT count(*) + 1 FROM oil_transactions WHERE station_id = :s", Map.of("s", c.stationId()),
                Integer.class);
        Integer id = db.jdbc().queryForObject("INSERT INTO oil_transactions (station_id, gun_id, t_type, true_time, asn, grade_specific_id, "
                + "volume, bal, amn, ppu, employee_id, volume_total, car_number, posttc, ctc, tac, gmac, gtac, psamtid, psamttc, upload, flg, "
                + "discount, preferential_price, truely_pay, payment_id, error_flg_id, company_id, duty_id) VALUES (:s, :gun, 0, now(), :asn, :g, "
                + ":v, :bal, :amn, :ppu, :emp, :vt, :car, :tc, '', '', '', '', '', '', '1', '0', :disc, 0, :pay, :pid, 0, :c, :duty) RETURNING id",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(Db.p("s", c.stationId(),
                        "gun", gun, "vt", Math.round(volumeTotal * 1000d) / 1000d, "asn", asn, "g", k.gradeId, "v", k.volume.doubleValue(), "bal", balance.doubleValue(),
                        "amn", k.amount.doubleValue(), "ppu", k.price.doubleValue(), "emp", c.employeeId(),
                        "car", f.carNumber() == null ? "" : f.carNumber().trim(), "tc", String.format("%08d", seq == null ? 1 : seq % 100_000_000),
                        "disc", k.amount.subtract(toPay).doubleValue(), "pay", toPay.doubleValue(), "pid", payment, "c", c.companyId(),
                        "duty", duty)), Integer.class);
        return receipt(c.stationId(), id);
    }

    /** Ventes du poste ouvert (ou d'un poste donné). */
    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user, @RequestParam(required = false) Integer dutyId) {
        StationContext.Ctx c = stations.of(user);
        Integer duty = dutyId;
        if (duty == null) {
            Map<String, Object> d = stations.openDuty(c.stationId());
            if (d == null) {
                return List.of();
            }
            duty = ((Number) d.get("id")).intValue();
        }
        return db.list(SALE_SQL + " WHERE o.station_id = :s AND o.duty_id = :d ORDER BY o.true_time DESC", Map.of("s", c.stationId(), "d", duty));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@CurrentUser SessionUser user, @PathVariable int id) {
        return receipt(stations.of(user).stationId(), id);
    }

    /**
     * Annulation d'une vente du poste ouvert : la vente est supprimée et, pour un paiement par carte,
     * le montant est recrédité (journal de type 12, montant négatif comme une contre-passation).
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void cancel(@CurrentUser SessionUser user, @PathVariable int id) {
        StationContext.Ctx c = stations.of(user);
        int duty = stations.requireOpenDuty(c.stationId());
        Map<String, Object> o = db.one("SELECT asn, truely_pay, payment_id, duty_id FROM oil_transactions WHERE id = :id AND station_id = :s",
                Map.of("id", id, "s", c.stationId()));
        if (o == null) {
            throw new ApiException("Vente introuvable");
        }
        if (!Integer.valueOf(duty).equals(o.get("dutyId"))) {
            throw new ApiException("Seules les ventes du poste ouvert peuvent être annulées");
        }
        if (((Number) o.get("paymentId")).intValue() == 8) {
            StationCards.Card card = cards.find((String) o.get("asn"));
            BigDecimal amount = new BigDecimal(o.get("truelyPay").toString());
            BigDecimal bal = cards.move(card, amount);
            cards.log(card, amount.negate(), bal, 12, BigDecimal.ZERO, c.operator(), 8, c.stationId());
        }
        db.update("DELETE FROM oil_transactions WHERE id = :id", Map.of("id", id));
    }

    static final String SALE_SQL = "SELECT o.id, o.true_time, o.gun_id, o.grade_specific_id grade_id, g.name grade_name, o.ppu price, o.volume, "
            + "o.amn amount, o.discount, o.truely_pay, o.payment_id, o.asn, o.volume_total, o.t_type, o.bal balance, o.car_number, o.posttc ticket_no, o.duty_id, "
            + "e.name employee_name FROM oil_transactions o LEFT JOIN grade_specifics g ON g.grade_specific_id = o.grade_specific_id "
            + "LEFT JOIN employees e ON e.employee_id = o.employee_id";

    private Map<String, Object> receipt(int stationId, int id) {
        Map<String, Object> r = db.one(SALE_SQL + " WHERE o.id = :id AND o.station_id = :s", Map.of("id", id, "s", stationId));
        if (r == null) {
            throw new ApiException("Vente introuvable");
        }
        String asn = (String) r.get("asn");
        if (asn != null && !asn.equals(NO_CARD)) {
            Map<String, Object> holder = db.one("SELECT cu.name FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                    + "WHERE ci.asn = :a UNION ALL SELECT e.name FROM employee_card_infos ec JOIN employees e ON e.employee_id = ec.employee_id "
                    + "WHERE ec.asn = :a UNION ALL SELECT g.name FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id WHERE gc.asn = :a "
                    + "LIMIT 1", Map.of("a", asn));
            r.put("cardHolder", holder == null ? null : holder.get("name"));
            r.put("cardType", StationCards.typeLabel(asn));
        }
        return r;
    }

    private record Calc(int gradeId, String gradeCode, BigDecimal price, BigDecimal volume, BigDecimal amount) {
    }

    /** Prix de la station ; volume = montant / prix, ou montant = volume × prix (2 décimales). */
    private Calc calc(StationContext.Ctx c, SaleForm f) {
        Integer gradeId = f.gradeId();
        if (f.nozId() != null && f.nozId() > 0) {
            Map<String, Object> n = db.one("SELECT grade_id FROM station_noz WHERE station_id = :s AND noz_id = :n",
                    Map.of("s", c.stationId(), "n", f.nozId()));
            if (n == null) {
                throw new ApiException("Pistolet inconnu");
            }
            gradeId = ((Number) n.get("gradeId")).intValue();
        }
        if (gradeId == null) {
            throw new ApiException("Choisissez le pistolet ou l'énergie");
        }
        Map<String, Object> p = db.one("SELECT p.new_ppu, g.code FROM price_infos p JOIN grade_specifics g ON g.grade_specific_id = p.grade_specific_id "
                + "WHERE p.station_id = :s AND p.grade_specific_id = :g", Map.of("s", c.stationId(), "g", gradeId));
        if (p == null) {
            throw new ApiException("Cette énergie n'a pas de prix dans la station");
        }
        BigDecimal price = new BigDecimal(p.get("newPpu").toString()).setScale(3, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
            throw new ApiException("Prix de l'énergie incorrect");
        }
        BigDecimal amount;
        BigDecimal volume;
        if (f.amount() != null && f.amount().signum() > 0) {
            amount = f.amount().setScale(2, RoundingMode.HALF_UP);
            volume = amount.divide(price, 2, RoundingMode.HALF_UP);
        } else if (f.volume() != null && f.volume().signum() > 0) {
            volume = f.volume().setScale(2, RoundingMode.HALF_UP);
            amount = volume.multiply(price).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new ApiException("Saisissez le montant ou le volume");
        }
        return new Calc(gradeId, p.get("code") == null ? "" : p.get("code").toString().trim(), price, volume, amount);
    }
}
