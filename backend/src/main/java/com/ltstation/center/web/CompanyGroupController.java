package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.domain.entity.LoginCompanyGroup;
import com.ltstation.center.domain.repository.LoginCompanyGroupRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion de la plateforme : arbre des sociétés (LoginCompanyGroup) et
 * statistiques de ventes par nœud (Main/AccountManager d'origine).
 * Le nœud 0 représente la racine virtuelle « Gestion de la plateforme ».
 */
@RestController
@RequestMapping("/api/company-groups")
public class CompanyGroupController {

    private final LoginCompanyGroupRepository groups;
    private final CompanyScopeService scope;
    private final Db db;

    public CompanyGroupController(LoginCompanyGroupRepository groups, CompanyScopeService scope, Db db) {
        this.groups = groups;
        this.scope = scope;
        this.db = db;
    }

    public record TreeNode(int id, String name, Integer parentId, List<TreeNode> children) {
    }

    /** Arbre à partir du nœud de l'utilisateur (GetAccountManagerList). */
    @GetMapping("/tree")
    public List<TreeNode> tree(@CurrentUser SessionUser user) {
        int root = user.companyGroupId();
        return groups.findById(root)
                .map(g -> List.of(new TreeNode(g.getId(), g.getGroupName(), g.getGroupId(), children(g.getId(), 0))))
                .orElseGet(() -> List.of(new TreeNode(0, "Gestion de la plateforme", null, children(0, 0))));
    }

    private List<TreeNode> children(int parentId, int depth) {
        List<TreeNode> out = new ArrayList<>();
        if (depth > 50) {
            return out; // protège contre une boucle dans les données
        }
        for (LoginCompanyGroup g : groups.findByGroupId(parentId)) {
            out.add(new TreeNode(g.getId(), g.getGroupName(), g.getGroupId(), children(g.getId(), depth + 1)));
        }
        return out;
    }

    public record NodeForm(String name) {
    }

    /** Ajout d'un sous-compte (AccountChilrenChange, type Add). */
    @PostMapping("/{parentId}/children")
    @Transactional
    public TreeNode addChild(@CurrentUser SessionUser user, @PathVariable int parentId, @RequestBody NodeForm form) {
        checkScope(user, parentId);
        String name = required(form);
        LoginCompanyGroup g = new LoginCompanyGroup();
        g.setCreatreTime(LocalDateTime.now());
        g.setGroupName(name);
        g.setGroupId(parentId);
        g.setValidSecretKey(LegacyAes.encrypt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))));
        g = groups.save(g);
        return new TreeNode(g.getId(), g.getGroupName(), parentId, List.of());
    }

    /** Renommage d'un nœud (AccountChilrenChange, type Update). */
    @PutMapping("/{id}")
    @Transactional
    public void rename(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody NodeForm form) {
        checkScope(user, id);
        LoginCompanyGroup g = groups.findById(id).orElseThrow(() -> new ApiException("Le nœud n'existe pas"));
        g.setGroupName(required(form));
        groups.save(g);
    }

    /** Totaux jour / mois / année du nœud et de ses descendants (GetNodeTotalMess). */
    @GetMapping("/{id}/totals")
    public Map<String, Object> totals(@CurrentUser SessionUser user, @PathVariable int id) {
        checkScope(user, id);
        LoginCompanyGroup g = id == 0 ? null : groups.findById(id).orElseThrow(() -> new ApiException("Le nœud n'existe pas"));
        List<Integer> stations = scope.stationIds(id);
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        putTotals(out, "day", stations, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        putTotals(out, "month", stations, today.withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).plusMonths(1).atStartOfDay());
        putTotals(out, "year", stations, today.withDayOfYear(1).atStartOfDay(), today.withDayOfYear(1).plusYears(1).atStartOfDay());
        out.put("validSecretKey", g == null ? null : g.getValidSecretKey());
        out.put("validCount", g == null ? null : g.getValidCount());
        return out;
    }

    /**
     * Totaux par sous-nœud (ou par station pour un nœud feuille) sur la période
     * 0 = jour, 1 = mois, 2 = année (GetAccountTotal).
     */
    @GetMapping("/{id}/children-totals")
    public List<Map<String, Object>> childrenTotals(@CurrentUser SessionUser user, @PathVariable int id,
                                                    @RequestParam(defaultValue = "0") int period) {
        checkScope(user, id);
        if (id != 0 && groups.findById(id).isEmpty()) {
            throw new ApiException("Le nœud n'existe pas");
        }
        LocalDate today = LocalDate.now();
        LocalDateTime from;
        LocalDateTime to;
        switch (period) {
            case 1 -> {
                from = today.withDayOfMonth(1).atStartOfDay();
                to = from.plusMonths(1);
            }
            case 2 -> {
                from = today.withDayOfYear(1).atStartOfDay();
                to = from.plusYears(1);
            }
            default -> {
                from = today.atStartOfDay();
                to = from.plusDays(1);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<LoginCompanyGroup> kids = groups.findByGroupId(id);
        if (!kids.isEmpty()) {
            for (LoginCompanyGroup k : kids) {
                Map<String, Object> sums = sums(scope.stationIds(k.getId()), from, to);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", "g" + k.getId());
                row.put("name", k.getGroupName());
                row.put("totalAmount", sums.get("amount"));
                row.put("totalVolume", sums.get("volume"));
                out.add(row);
            }
        } else {
            for (Map<String, Object> r : db.list("SELECT s.station_id, s.name, COALESCE(sum(o.truely_pay), 0) amount, "
                    + "COALESCE(sum(o.volume), 0) volume FROM stations s JOIN oil_transactions o ON o.station_id = s.station_id "
                    + "AND o.true_time >= :from AND o.true_time < :to WHERE s.company_id = :c GROUP BY s.station_id, s.name",
                    Map.of("from", from, "to", to, "c", id))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", "s" + r.get("stationId"));
                row.put("name", r.get("name"));
                row.put("totalAmount", round(r.get("amount")));
                row.put("totalVolume", round(r.get("volume")));
                out.add(row);
            }
        }
        return out;
    }

    private void putTotals(Map<String, Object> out, String prefix, List<Integer> stations, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> s = sums(stations, from, to);
        out.put(prefix + "Amount", s.get("amount"));
        out.put(prefix + "Volume", s.get("volume"));
    }

    private Map<String, Object> sums(List<Integer> stations, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> r = db.one("SELECT COALESCE(sum(truely_pay), 0) amount, COALESCE(sum(volume), 0) volume "
                + "FROM oil_transactions WHERE station_id IN (:s) AND true_time >= :from AND true_time < :to",
                Map.of("s", stations, "from", from, "to", to));
        return Map.of("amount", round(r.get("amount")), "volume", round(r.get("volume")));
    }

    private void checkScope(SessionUser user, int id) {
        if (!scope.nodeList(user).contains(id)) {
            throw new ApiException("Ce nœud ne fait pas partie de votre périmètre");
        }
    }

    private static String required(NodeForm form) {
        if (form == null || form.name() == null || form.name().isBlank()) {
            throw new ApiException("Le nom du compte est obligatoire");
        }
        return form.name().trim();
    }

    private static BigDecimal round(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_EVEN);
    }
}
