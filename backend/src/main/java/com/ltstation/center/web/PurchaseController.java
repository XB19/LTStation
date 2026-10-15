package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Achats de carburant auprès des fournisseurs (Main/PurchaseOilPriceList, Main/PurchaseOrder d'origine).
 * Les fournisseurs (plateformes Supplier_*) publient des prix ; la station passe commande,
 * le fournisseur expédie, la station confirme la réception.
 * purchaseState : 0 = en cours d'achat, 1 = expédiée, 2 = reçue.
 */
@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private final Db db;
    private final CardAccountService cards;

    public PurchaseController(Db db, CardAccountService cards) {
        this.db = db;
        this.cards = cards;
    }

    /** Plateformes fournisseurs liées à la société de l'utilisateur (PlatformRelation/GetPlatformInfo). */
    @GetMapping("/platforms")
    public List<Map<String, Object>> platforms(@CurrentUser SessionUser user) {
        return db.list("SELECT p.id platform_id, p.platform_name, p.price_unit, p.oil_unit FROM supplier_platform_relation r "
                + "JOIN supplier_platform_manage p ON p.id = r.platform_id WHERE r.joy_fill_platform_id = :c ORDER BY p.id",
                Map.of("c", company(user)));
    }

    /** Points d'approvisionnement d'une plateforme (OilSupply/GetData). */
    @GetMapping("/oil-supplies")
    public List<Map<String, Object>> oilSupplies(@RequestParam int platformId) {
        return db.list("SELECT id, oil_supply_name, connect_personnel, connect_telephone, address FROM supplier_oil_supply "
                + "WHERE platform_id = :p ORDER BY id", Map.of("p", platformId));
    }

    /** Prix publiés par les fournisseurs liés (CommoRelease/GetData, état publié). */
    @GetMapping("/prices")
    public PageResult<Map<String, Object>> prices(@CurrentUser SessionUser user, @RequestParam(defaultValue = "-1") int platformId,
                                                  @RequestParam(defaultValue = "-1") int oilSupplyId,
                                                  @RequestParam(defaultValue = "") String commoName,
                                                  @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        StringBuilder w = new StringBuilder(" WHERE r.release_state = 1 AND r.platform_id IN (SELECT platform_id FROM "
                + "supplier_platform_relation WHERE joy_fill_platform_id = :c)");
        Map<String, Object> p = new HashMap<>();
        p.put("c", company(user));
        if (platformId > -1) {
            w.append(" AND r.platform_id = :p");
            p.put("p", platformId);
        }
        if (oilSupplyId > -1) {
            w.append(" AND r.oil_supply_id = :o");
            p.put("o", oilSupplyId);
        }
        if (!commoName.isBlank()) {
            w.append(" AND COALESCE(m.commo_name, r.commo_name) LIKE :n");
            p.put("n", "%" + commoName.trim() + "%");
        }
        return db.page("SELECT r.id, r.commo_id, COALESCE(m.commo_name, r.commo_name) commo_name, COALESCE(m.specificat, r.specificat) "
                + "specificat, r.unit_price, r.release_time, r.oil_supply_id, o.oil_supply_name, r.platform_id, pm.platform_name, "
                + "pm.price_unit, pm.oil_unit FROM supplier_commo_release r LEFT JOIN supplier_commo_manage m ON m.id = r.commo_id "
                + "LEFT JOIN supplier_oil_supply o ON o.id = r.oil_supply_id LEFT JOIN supplier_platform_manage pm ON pm.id = r.platform_id"
                + w, p, "r.release_time DESC", page, size);
    }

    // ------------------------------------------------------------ Commandes

    private static final String ORDER_SELECT = "SELECT po.*, COALESCE(m.commo_name, po.commo_name) commo_label, "
            + "COALESCE(m.specificat, po.specificat) specificat_label, COALESCE(pm.platform_name, po.platform_name) platform_label, "
            + "COALESCE(o.oil_supply_name, po.oil_supply_name) oil_supply_label FROM supplier_purchase_order po "
            + "LEFT JOIN supplier_commo_manage m ON m.id = po.commo_id LEFT JOIN supplier_platform_manage pm ON pm.id = po.platform_id "
            + "LEFT JOIN supplier_oil_supply o ON o.id = po.oil_supply_id";

    @GetMapping("/orders")
    public PageResult<Map<String, Object>> orders(@CurrentUser SessionUser user, @RequestParam(defaultValue = "-1") int platformId,
                                                  @RequestParam(defaultValue = "-1") int oilSupplyId,
                                                  @RequestParam(defaultValue = "-1") int purchaseState,
                                                  @RequestParam(defaultValue = "") String commoName,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                  @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> p = new HashMap<>();
        return db.page(ORDER_SELECT + orderWhere(user, p, platformId, oilSupplyId, purchaseState, commoName, start, end), p,
                "po.purchase_time DESC", page, size);
    }

    @GetMapping("/orders/export")
    public void exportOrders(@CurrentUser SessionUser user, @RequestParam(defaultValue = "-1") int platformId,
                             @RequestParam(defaultValue = "-1") int oilSupplyId, @RequestParam(defaultValue = "-1") int purchaseState,
                             @RequestParam(defaultValue = "") String commoName,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                             HttpServletResponse response) throws IOException {
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list(ORDER_SELECT + orderWhere(user, p, platformId, oilSupplyId, purchaseState, commoName,
                start, end) + " ORDER BY po.purchase_time DESC", p);
        ExcelExport.write(response, "CommandesAchat", "Commandes", List.of(
                Col.of("N° de commande", "orderNumber"), new Col("État", r -> state(r.get("purchaseState"))),
                Col.of("Station", "stationName"), Col.of("Demandeur", "employeesName"), Col.of("Téléphone", "telephone"),
                Col.of("Date d'achat", "purchaseTime"), Col.of("Réception prévue", "storageTime"),
                new Col("Quantité", r -> r.get("oilValue") + " " + nz(r.get("oilUnit"))), Col.of("Point d'approvisionnement", "oilSupplyLabel"),
                Col.of("Produit", "commoLabel"), Col.of("Spécification", "specificatLabel"),
                new Col("Prix unitaire", r -> r.get("unitPrice") + " " + nz(r.get("priceUnit")) + "/" + nz(r.get("oilUnit"))),
                new Col("Montant", r -> r.get("purchaseAmount") + " " + nz(r.get("priceUnit"))),
                Col.of("Vendeur", "sellerEmployeeName"), Col.of("Tél. vendeur", "sellerTelephone"), Col.of("Expédition", "shipTime"),
                Col.of("Livreur", "deliverName"), Col.of("Tél. livreur", "deliverTelephone"), Col.of("Réception", "realStorageTime"),
                new Col("Quantité reçue", r -> r.get("realOilValue") == null ? "" : r.get("realOilValue") + " " + nz(r.get("oilUnit")))),
                rows);
    }

    public record OrderForm(Integer releaseId, BigDecimal oilValue, LocalDateTime storageTime) {
    }

    /** Commande à partir d'un prix publié (PurchaseOrder/AddData) : montant = quantité × prix unitaire. */
    @PostMapping("/orders")
    @Transactional
    public Map<String, Object> addOrder(@CurrentUser SessionUser user, @RequestBody OrderForm f) {
        if (f.releaseId() == null || f.oilValue() == null || f.oilValue().signum() <= 0) {
            throw new ApiException("Produit et quantité obligatoires");
        }
        Map<String, Object> rel = db.one("SELECT r.*, COALESCE(m.commo_name, r.commo_name) cname, COALESCE(m.specificat, r.specificat) spec, "
                + "COALESCE(m.commo_code, r.commo_code) ccode, pm.platform_name pname, pm.price_unit, pm.oil_unit, o.oil_supply_name oname "
                + "FROM supplier_commo_release r LEFT JOIN supplier_commo_manage m ON m.id = r.commo_id "
                + "LEFT JOIN supplier_platform_manage pm ON pm.id = r.platform_id LEFT JOIN supplier_oil_supply o ON o.id = r.oil_supply_id "
                + "WHERE r.id = :id AND r.release_state = 1", Map.of("id", f.releaseId()));
        if (rel == null) {
            throw new ApiException("Ce prix n'est plus publié");
        }
        int stationId = cards.operator(user).stationId();
        Map<String, Object> st = db.one("SELECT s.name, s.company_id, g.group_name FROM stations s LEFT JOIN login_company_group g "
                + "ON g.id = s.company_id WHERE s.station_id = :s", Map.of("s", stationId));
        BigDecimal price = new BigDecimal(rel.get("unitPrice").toString());
        String no = "ON" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        db.update("INSERT INTO supplier_purchase_order (order_number, platform_id, platform_name, price_unit, oil_unit, oil_supply_id, "
                + "oil_supply_name, commo_id, commo_name, commo_code, specificat, unit_price, company_id, company_name, station_id, "
                + "station_name, login_name, employees_name, telephone, oil_value, storage_time, purchase_state, purchase_time, "
                + "purchase_amount) VALUES (:no, :p, :pn, :pu, :ou, :o, :on, :c, :cn, :cc, :sp, :price, :co, :con, :s, :sn, :ln, :en, "
                + ":tel, :v, :st, 0, now(), :amount)",
                Db.p("no", no, "p", rel.get("platformId"), "pn", rel.get("pname"), "pu", rel.get("priceUnit"), "ou", rel.get("oilUnit"),
                        "o", rel.get("oilSupplyId"), "on", rel.get("oname"), "c", rel.get("commoId"), "cn", rel.get("cname"),
                        "cc", rel.get("ccode"), "sp", rel.get("spec"), "price", price,
                        "co", st == null ? company(user) : st.get("companyId"), "con", st == null ? null : st.get("groupName"),
                        "s", stationId, "sn", st == null ? null : st.get("name"), "ln", user.getLoginName(),
                        "en", user.getEmployeesName(), "tel", user.getTelephone(), "v", f.oilValue(), "st", f.storageTime(),
                        "amount", f.oilValue().multiply(price)));
        return Map.of("orderNumber", no);
    }

    /** Modification de la quantité tant que la commande n'est pas expédiée (PurchaseOrder/ModifyData). */
    @PutMapping("/orders/{id}")
    @Transactional
    public void updateOrder(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody OrderForm f) {
        Map<String, Object> o = order(user, id);
        if (((Number) o.get("purchaseState")).intValue() != 0) {
            throw new ApiException("La commande a déjà été expédiée");
        }
        BigDecimal price = new BigDecimal(o.get("unitPrice").toString());
        db.update("UPDATE supplier_purchase_order SET oil_value = :v, purchase_amount = :a, storage_time = COALESCE(:st, storage_time), "
                + "purchase_time = now() WHERE id = :id",
                Db.p("v", f.oilValue(), "a", f.oilValue().multiply(price), "st", f.storageTime(), "id", id));
    }

    public record ReceiptForm(BigDecimal realOilValue, List<String> files) {
    }

    /** Confirmation de réception (PurchaseOrder/ConfirmReceipt) avec bons de livraison. */
    @PostMapping("/orders/{id}/receipt")
    @Transactional
    public void receipt(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody ReceiptForm f) {
        order(user, id);
        db.update("UPDATE supplier_purchase_order SET purchase_state = 2, real_oil_value = :v, purchase_file_ids = :files, "
                + "real_storage_time = now() WHERE id = :id",
                Db.p("v", f.realOilValue(), "files", f.files() == null ? "" : String.join(",", f.files()), "id", id));
    }

    /** Annulation d'une commande non expédiée (PurchaseOrder/DeleteData). */
    @DeleteMapping("/orders/{id}")
    @Transactional
    public void cancel(@CurrentUser SessionUser user, @PathVariable int id) {
        Map<String, Object> o = order(user, id);
        if (((Number) o.get("purchaseState")).intValue() != 0) {
            throw new ApiException("Une commande expédiée ne peut plus être annulée");
        }
        db.update("DELETE FROM supplier_purchase_order WHERE id = :id", Map.of("id", id));
    }

    private String orderWhere(SessionUser user, Map<String, Object> p, int platformId, int oilSupplyId, int purchaseState,
                              String commoName, LocalDateTime start, LocalDateTime end) {
        StringBuilder w = new StringBuilder(" WHERE po.company_id = :c");
        p.put("c", company(user));
        if (!user.isCenter() && user.getStationId() != null) {
            w.append(" AND po.station_id = :s");
            p.put("s", user.getStationId());
        }
        if (platformId > -1) {
            w.append(" AND po.platform_id = :p");
            p.put("p", platformId);
        }
        if (oilSupplyId > -1) {
            w.append(" AND po.oil_supply_id = :o");
            p.put("o", oilSupplyId);
        }
        if (purchaseState > -1) {
            w.append(" AND po.purchase_state = :ps");
            p.put("ps", purchaseState);
        }
        if (!commoName.isBlank()) {
            w.append(" AND COALESCE(m.commo_name, po.commo_name) LIKE :n");
            p.put("n", "%" + commoName.trim() + "%");
        }
        if (start != null) {
            w.append(" AND po.purchase_time >= :start");
            p.put("start", start);
        }
        if (end != null) {
            w.append(" AND po.purchase_time <= :end");
            p.put("end", end);
        }
        return w.toString();
    }

    private Map<String, Object> order(SessionUser user, int id) {
        Map<String, Object> o = db.one("SELECT * FROM supplier_purchase_order WHERE id = :id AND company_id = :c",
                Map.of("id", id, "c", company(user)));
        if (o == null) {
            throw new ApiException("La commande n'existe pas");
        }
        return o;
    }

    private int company(SessionUser user) {
        try {
            return user.getCompanyId() == null ? user.companyGroupId() : Integer.parseInt(user.getCompanyId());
        } catch (NumberFormatException e) {
            return user.companyGroupId();
        }
    }

    static String state(Object v) {
        return switch (v instanceof Number n ? n.intValue() : -1) {
            case 0 -> "En cours d'achat";
            case 1 -> "Expédiée";
            case 2 -> "Reçue";
            default -> "";
        };
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString();
    }
}
