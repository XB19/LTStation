package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.service.CardAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modification d'une carte utilisateur côté serveur (Main/CardUpdateByServer d'origine) :
 * porteur, remise, restrictions de véhicule, d'énergie, de volumes/montants et de lieux.
 */
@RestController
@RequestMapping("/api/card-server")
public class CardServerController {

    private final Db db;
    private final CardAccountService cards;

    public CardServerController(Db db, CardAccountService cards) {
        this.db = db;
        this.cards = cards;
    }

    /** Cartes d'un porteur trouvé par nom, téléphone ou immatriculation (GetCardByServer). */
    @GetMapping("/search")
    public List<String> search(@RequestParam(defaultValue = "") String userName, @RequestParam(defaultValue = "") String phone,
                               @RequestParam(defaultValue = "") String carNumber) {
        return db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers c ON c.customer_id = ci.customer_id "
                + "WHERE (c.name = :n AND :n <> '') OR (c.telephone = :p AND :p <> '') OR (c.vehicle_no = :v AND :v <> '') "
                + "ORDER BY ci.asn", Db.p("n", userName.trim(), "p", phone.trim(), "v", carNumber.trim()), String.class);
    }

    /** Fiche complète d'une carte (GetCardByAsn) ; la remise est renvoyée sous forme d'identifiant de remise. */
    @GetMapping("/{asn}")
    public Map<String, Object> get(@PathVariable String asn) {
        Map<String, Object> c = cards.customerCard(asn);
        Map<String, Object> cu = db.one("SELECT * FROM customers WHERE customer_id = :id", Map.of("id", c.get("customerId")));
        double discount = c.get("discount") == null ? 100 : ((Number) c.get("discount")).doubleValue();
        Integer discountId = 0;
        if (discount != 100) {
            Map<String, Object> d = db.one("SELECT id FROM dis_count WHERE dis_count_value = :v LIMIT 1",
                    Map.of("v", BigDecimal.valueOf(discount).setScale(2, RoundingMode.HALF_UP)));
            discountId = d == null ? 0 : ((Number) d.get("id")).intValue();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asn", asn);
        out.put("validDatetime", c.get("validDatetime"));
        out.put("accountNo", c.get("accountNo"));
        out.put("password", "");
        out.put("name", cu.get("name"));
        out.put("code", cu.get("code"));
        out.put("certificateTypeId", cu.get("certificateTypeId"));
        out.put("certificateNo", cu.get("certificateNo"));
        out.put("telephone", cu.get("telephone"));
        out.put("postCode", cu.get("postCode"));
        out.put("email", cu.get("email"));
        out.put("address", cu.get("address"));
        out.put("discountId", discountId);
        out.put("isLimitVehicleNo", c.get("isLimitVehicleNo"));
        out.put("limitVehicleNo", c.get("limitVehicleNo"));
        out.put("vehicleColorId", cu.get("vehicleColorId"));
        out.put("vehicleTypeId", cu.get("vehicleTypeId"));
        out.put("limitOil", c.get("limitOil"));
        out.put("limitVolume", c.get("limitVolume"));
        out.put("limitTime", c.get("limitTime"));
        out.put("limitAmount", c.get("limitAmount"));
        out.put("limitVolumeMonthly", c.get("limitVolumeMonthly"));
        out.put("limitAmountMonthly", c.get("limitAmountMonthly"));
        out.put("limitMileage", c.get("limitMileage"));
        out.put("mileageTolerance", c.get("mileageTolerance"));
        out.put("limitTypeId", c.get("limitTypeId"));
        String confine = c.get("useConfine") == null ? "" : c.get("useConfine").toString();
        out.put("useConfine", confine.isBlank() ? List.of() : List.of(confine.split(",")).stream().map(String::trim)
                .filter(s -> s.matches("\\d+")).map(Integer::valueOf).toList());
        return out;
    }

    public record CardForm(String password, String name, Integer certificateTypeId, String certificateNo, String telephone,
                           String postCode, String email, String address, Integer discountId, Boolean isLimitVehicleNo,
                           String limitVehicleNo, Integer vehicleColorId, Integer vehicleTypeId, String limitOil,
                           Double limitVolume, Integer limitTime, Double limitAmount, Double limitVolumeMonthly,
                           Double limitAmountMonthly, Double limitMileage, Double mileageTolerance, Integer limitTypeId,
                           List<Integer> useConfine) {
    }

    /** Mise à jour (UpdateCardServer). */
    @PutMapping("/{asn}")
    @Transactional
    public void update(@PathVariable String asn, @RequestBody CardForm f) {
        Map<String, Object> c = cards.customerCard(asn);
        if (f.name() == null || f.name().isBlank()) {
            throw new ApiException("Le nom du porteur est obligatoire");
        }
        double discount = 100;
        if (f.discountId() != null && f.discountId() != 0) {
            Map<String, Object> d = db.one("SELECT dis_count_value FROM dis_count WHERE id = :id", Map.of("id", f.discountId()));
            if (d != null && d.get("disCountValue") != null) {
                discount = ((Number) d.get("disCountValue")).doubleValue();
            }
        }
        db.update("UPDATE customers SET name = :n, certificate_type_id = :ct, certificate_no = :cn, email = :e, telephone = :t, "
                + "post_code = :pc, address = :a, vehicle_color_id = :vc, vehicle_type_id = :vt WHERE customer_id = :id",
                Db.p("n", f.name(), "ct", f.certificateTypeId() == null ? 1 : f.certificateTypeId(), "cn", f.certificateNo(),
                        "e", f.email(), "t", f.telephone(), "pc", f.postCode(), "a", f.address(), "vc", f.vehicleColorId(),
                        "vt", f.vehicleTypeId(), "id", c.get("customerId")));
        String confine = f.useConfine() == null ? "" : String.join(",", f.useConfine().stream().map(String::valueOf).toList());
        db.update("UPDATE customer_card_infos SET discount = :d, is_limit_vehicle_no = :lv, limit_amount = :la, "
                + "limit_amount_monthly = :lam, limit_mileage = :lm, limit_oil = :lo, limit_time = :lt, limit_type_id = :ltype, "
                + "use_confine = :uc, limit_volume = :lvol, limit_volume_monthly = :lvm, limit_vehicle_no = :lvn, "
                + "mileage_tolerance = :mt" + (f.password() != null && !f.password().isEmpty() ? ", cipher_flg = :pwd" : "")
                + " WHERE asn = :asn",
                Db.p("d", Math.round(discount * 100) / 100.0, "lv", Boolean.TRUE.equals(f.isLimitVehicleNo()), "la", z(f.limitAmount()),
                        "lam", z(f.limitAmountMonthly()), "lm", z(f.limitMileage()), "lo", f.limitOil(),
                        "lt", f.limitTime() == null ? 0 : f.limitTime(), "ltype", f.limitTypeId() == null ? 0 : f.limitTypeId(),
                        "uc", confine, "lvol", z(f.limitVolume()), "lvm", z(f.limitVolumeMonthly()), "lvn", f.limitVehicleNo(),
                        "mt", z(f.mileageTolerance()), "pwd", f.password(), "asn", asn));
    }

    private static double z(Double v) {
        return v == null ? 0 : v;
    }
}
