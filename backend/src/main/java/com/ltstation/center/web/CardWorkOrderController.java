package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardWorkOrderService;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.Labels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ordre de travail des cartes : recherche de carte, ordre en cours, historique, rattachement à un équipement. */
@RestController
@RequestMapping("/api/card-work-orders")
public class CardWorkOrderController {

    private final Db db;
    private final CompanyScopeService scope;
    private final CardWorkOrderService service;

    public CardWorkOrderController(Db db, CompanyScopeService scope, CardWorkOrderService service) {
        this.db = db;
        this.scope = scope;
        this.service = service;
    }

    /** Recherche par n° complet, 5 derniers chiffres ou nom du porteur ; plusieurs résultats possibles. */
    @GetMapping("/cards")
    public List<Map<String, Object>> cards(@CurrentUser SessionUser user, @RequestParam(defaultValue = "") String q) {
        return service.search(user, q);
    }

    /** Carte, ordre en cours et historique. */
    @GetMapping("/card")
    public Map<String, Object> card(@CurrentUser SessionUser user, @RequestParam String asn) {
        Map<String, Object> out = new LinkedHashMap<>(service.card(user, asn));
        List<Map<String, Object>> history = service.history((String) out.get("asn"));
        out.put("current", history.stream().filter(h -> h.get("validTo") == null).findFirst().orElse(null));
        out.put("history", history);
        return out;
    }

    public record AssignForm(String asn, String workOrderNo, Integer equipmentId,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime validFrom, String memo) {
    }

    @PostMapping
    @Transactional
    public Map<String, Object> assign(@CurrentUser SessionUser user, @RequestBody AssignForm f) {
        EquipmentController.checkRight(user);
        service.assign(user, f.asn(), f.workOrderNo(), f.equipmentId(), f.validFrom(), f.memo());
        return card(user, f.asn());
    }

    /** Fin de l'ordre en cours (la carte n'a plus d'ordre de travail à partir de cette date). */
    @DeleteMapping
    @Transactional
    public Map<String, Object> end(@CurrentUser SessionUser user, @RequestParam String asn,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        EquipmentController.checkRight(user);
        service.end(user, asn, at);
        return card(user, asn);
    }

    public record Filter(String q, Integer equipmentId, Boolean history) {
    }

    private static final String SELECT = "SELECT wo.id, wo.asn, c.kind, c.holder, c.card_status_type_id, s.name station_name, "
            + "wo.work_order_no, wo.equipment_id, eq.name equipment_name, eq.serial_no equipment_serial, eq.category, "
            + "wo.valid_from, wo.valid_to, wo.memo, wo.create_by FROM card_work_order wo "
            + "JOIN (" + CardWorkOrderService.CARDS + ") c ON c.asn = wo.asn LEFT JOIN stations s ON s.station_id = c.station_id "
            + "LEFT JOIN equipment eq ON eq.id = wo.equipment_id";

    private String where(SessionUser user, Filter f, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" WHERE c.station_id IN (:st)");
        p.put("st", scope.stationIds(user));
        if (f.history() == null || !f.history()) {
            w.append(" AND wo.valid_to IS NULL");
        }
        if (f.q() != null && !f.q().isBlank()) {
            w.append(" AND (wo.asn LIKE :suffix OR wo.work_order_no ILIKE :q OR c.holder ILIKE :q OR eq.name ILIKE :q "
                    + "OR eq.serial_no ILIKE :q)");
            p.put("suffix", "%" + f.q().trim());
            p.put("q", "%" + f.q().trim() + "%");
        }
        if (f.equipmentId() != null) {
            w.append(" AND wo.equipment_id = :eq");
            p.put("eq", f.equipmentId());
        }
        return w.toString();
    }

    /** Rattachements en cours (ou tout l'historique) des cartes du périmètre. */
    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user, @ModelAttribute Filter f,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "15") int size) {
        Map<String, Object> p = new HashMap<>();
        PageResult<Map<String, Object>> r = db.page(SELECT + where(user, f, p), p, "wo.valid_from DESC, wo.id DESC", page, size);
        r.rows().forEach(CardWorkOrderController::decorate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        return out;
    }

    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Filter f, HttpServletResponse response) throws IOException {
        Map<String, Object> p = new HashMap<>();
        List<Map<String, Object>> rows = db.list(SELECT + where(user, f, p) + " ORDER BY wo.asn, wo.valid_from", p);
        rows.forEach(CardWorkOrderController::decorate);
        ExcelExport.write(response, "Cartes_et_ordres_de_travail", "Cartes et ordres", List.of(
                Col.of("N° de carte", "asn"), Col.of("Type de carte", "kind"), Col.of("Porteur", "holder"),
                Col.of("État de la carte", "cardStatus"), Col.of("Station", "stationName"),
                Col.of("Ordre de travail", "workOrderNo"), Col.of("Équipement", "equipmentName"),
                Col.of("N° de série de l'équipement", "equipmentSerial"), Col.of("Catégorie", "category"),
                Col.of("En vigueur depuis", "validFrom"), Col.of("Jusqu'au", "validTo"), Col.of("Observations", "memo"),
                Col.of("Saisi par", "createBy")), rows);
    }

    private static void decorate(Map<String, Object> r) {
        r.put("cardStatus", Labels.cardStatus(r.get("cardStatusTypeId")));
    }
}
