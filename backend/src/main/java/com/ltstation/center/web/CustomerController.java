package com.ltstation.center.web;

import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelExport;
import com.ltstation.center.common.ExcelExport.Col;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.Labels;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liste des porteurs de carte (Main/CustomerList d'origine).
 * searchType : 0 = utilisateurs (cartes 010001/010011/010013/FFFFFF), 1 = employés,
 * 2 = groupes (carte de groupe + cartes des membres), 3 = application (cartes 010008).
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final Db db;
    private final CompanyScopeService scope;

    public CustomerController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    public record Filter(Integer searchType, String userName, String phone, String certificateNo, String carNumber,
                         String cardNo) {
    }

    private static final String CUSTOMER_SELECT = "SELECT ci.asn, cu.name customer_name, cu.customer_id cus_id, g.name group_name, "
            + "cu.vehicle_no car_no, cu.telephone phone, cu.certificate_no cno, ci.issue_datetime, ci.valid_datetime, "
            + "ci.card_status_type_id, s.name station_name, ci.open_accoun_emp_name operate_name, a.balance "
            + "FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
            + "JOIN customer_accounts a ON a.customer_account_id = ci.customer_account_id "
            + "LEFT JOIN groups g ON g.group_id = ci.group_id LEFT JOIN stations s ON s.station_id = ci.station_id";

    @GetMapping
    public Map<String, Object> list(@CurrentUser SessionUser user, @ModelAttribute Filter f,
                                    @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        PageResult<Map<String, Object>> r = query(user, f, page, size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rows());
        out.put("total", r.total());
        return out;
    }

    @GetMapping("/export")
    public void export(@CurrentUser SessionUser user, @ModelAttribute Filter f, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = query(user, f, 1, 10000).rows();
        ExcelExport.write(response, "Utilisateurs", "Utilisateurs", List.of(
                Col.of("N° de carte", "asn"), Col.of("Nom", "customerName"), Col.of("ID utilisateur", "cusId"),
                Col.of("Groupe", "groupName"), Col.of("Immatriculation", "carNo"), Col.of("Téléphone", "phone"),
                Col.of("N° de pièce", "cno"), Col.of("Émission", "issueDatetime"), Col.of("Validité", "validDatetime"),
                Col.of("État", "cardStatue"), Col.of("Station d'ouverture", "stationName"),
                Col.of("Opérateur", "operateName"), Col.of("Solde", "balance")), rows);
    }

    private PageResult<Map<String, Object>> query(SessionUser user, Filter f, int page, int size) {
        int type = f.searchType() == null ? 0 : f.searchType();
        Map<String, Object> p = new HashMap<>();
        p.put("stations", scope.stationIds(user));
        PageResult<Map<String, Object>> r;
        switch (type) {
            case 1 -> {
                StringBuilder w = new StringBuilder(" WHERE ec.station_id IN (:stations)");
                like(w, p, "e.name", "name", f.userName());
                like(w, p, "e.telephone", "phone", f.phone());
                r = db.page("SELECT ec.asn, e.name customer_name, e.employee_id cus_id, '' group_name, '' car_no, e.telephone phone, "
                        + "'' cno, ec.issue_datetime, ec.valid_datetime, ec.card_status_type_id, s.name station_name, "
                        + "ec.open_accoun_emp_name operate_name, a.balance FROM employee_card_infos ec "
                        + "JOIN employees e ON e.employee_id = ec.employee_id "
                        + "JOIN customer_accounts a ON a.customer_account_id = ec.customer_account_id "
                        + "LEFT JOIN stations s ON s.station_id = ec.open_station_id" + w, p, "ec.issue_datetime DESC", page, size);
            }
            case 2 -> {
                StringBuilder w = new StringBuilder(" WHERE gc.station_id IN (:stations)");
                if (notBlank(f.userName())) {
                    w.append(" AND (g.name = :name OR g.short_name = :name)");
                    p.put("name", f.userName());
                }
                like(w, p, "g.telephone", "phone", f.phone());
                like(w, p, "g.certificate_no", "cno", f.certificateNo());
                PageResult<Map<String, Object>> groups = db.page("SELECT gc.asn, g.name customer_name, g.group_id cus_id, '' group_name, "
                        + "'' car_no, g.telephone phone, g.certificate_no cno, gc.issue_datetime, gc.valid_datetime, "
                        + "gc.card_status_type_id, s.name station_name, '' operate_name, round(ga.balance::numeric) balance "
                        + "FROM group_card_infos gc JOIN groups g ON g.group_id = gc.group_id "
                        + "JOIN group_accounts ga ON ga.group_account_id = gc.group_account_id "
                        + "LEFT JOIN stations s ON s.station_id = gc.station_id" + w, p, "gc.issue_datetime DESC", page, size);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> g : groups.rows()) {
                    rows.add(g);
                    rows.addAll(db.list(CUSTOMER_SELECT.replace("JOIN customer_accounts a", "LEFT JOIN customer_accounts a")
                            + " WHERE ci.group_id = :g", Map.of("g", g.get("cusId"))));
                }
                r = PageResult.of(rows, groups.total());
            }
            default -> {
                StringBuilder w = new StringBuilder(" WHERE ci.station_id IN (:stations)");
                w.append(type == 3 ? " AND substring(ci.asn, 1, 6) = '010008'"
                        : " AND substring(ci.asn, 1, 6) IN ('010001', '010011', '010013', 'FFFFFF')");
                like(w, p, "cu.name", "name", f.userName());
                like(w, p, "cu.telephone", "phone", f.phone());
                like(w, p, "cu.certificate_no", "cno", f.certificateNo());
                like(w, p, "cu.vehicle_no", "car", f.carNumber());
                if (notBlank(f.cardNo())) {
                    w.append(" AND ci.asn LIKE :card");
                    p.put("card", "%" + f.cardNo().trim() + "%");
                }
                r = db.page(CUSTOMER_SELECT + w, p, "ci.issue_datetime DESC", page, size);
            }
        }
        r.rows().forEach(row -> {
            row.put("cardStatue", Labels.cardStatus(row.get("cardStatusTypeId")));
            if (row.get("balance") != null) {
                row.put("balance", new BigDecimal(row.get("balance").toString()).setScale(3, RoundingMode.HALF_UP));
            }
        });
        return r;
    }

    /** Égalité exacte (comme l'original) sur un champ si la valeur est renseignée. */
    private static void like(StringBuilder w, Map<String, Object> p, String col, String key, String value) {
        if (notBlank(value)) {
            w.append(" AND ").append(col).append(" = :").append(key);
            p.put(key, value.trim());
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
