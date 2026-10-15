package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.PageResult;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CardAccountService;
import com.ltstation.center.service.CardAccountService.Operator;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Flottes (Main/JoyFillFleetList, JoyFillFleetListController d'origine) : compte principal de la flotte,
 * cartes M1 attribuées à la flotte, recharge / remboursement du compte principal.
 * accountType : 1 = montant, 2 = litres ; accountState : 0 = suspendu, 1 = normal.
 */
@RestController
@RequestMapping("/api/fleets")
public class FleetController {

    private final Db db;
    private final CompanyScopeService scope;
    private final CardAccountService cards;

    public FleetController(Db db, CompanyScopeService scope, CardAccountService cards) {
        this.db = db;
        this.scope = scope;
        this.cards = cards;
    }

    /** Informations de flotte de l'utilisateur connecté (JoyFillFleetDriver/GetFleetInfo). */
    @GetMapping("/me")
    public Map<String, Object> me(@CurrentUser SessionUser user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("loginName", user.getLoginName());
        out.put("employeeName", user.getEmployeesName());
        out.put("stationId", user.getStationId());
        out.put("stationName", user.getStationName());
        out.put("employeeId", user.getEmpId());
        Map<String, Object> f = user.getStationId() == null ? null : db.one("SELECT id, team_name, master_balance, account_type "
                + "FROM joy_fill_fleet_list WHERE station_id = :s AND contact_person_id = :e LIMIT 1",
                Map.of("s", user.getStationId(), "e", user.getEmpId()));
        if (f != null) {
            out.put("teamId", f.get("id"));
            out.put("teamName", f.get("teamName"));
            out.put("masterBalance", f.get("masterBalance"));
            out.put("accountType", f.get("accountType"));
        }
        return out;
    }

    @GetMapping
    public PageResult<Map<String, Object>> list(@CurrentUser SessionUser user,
                                                @RequestParam(defaultValue = "-1") int accountState,
                                                @RequestParam(defaultValue = "-1") int accountType,
                                                @RequestParam(defaultValue = "") String teamName,
                                                @RequestParam(defaultValue = "") String contactPerson,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        StringBuilder w = new StringBuilder(" WHERE station_id IN (:s)");
        Map<String, Object> p = new HashMap<>();
        p.put("s", scope.scopedStations(user, null, null, null));
        if (accountState > -1) {
            w.append(" AND account_state = :as");
            p.put("as", accountState);
        }
        if (accountType > -1) {
            w.append(" AND account_type = :at");
            p.put("at", accountType);
        }
        if (!teamName.isBlank()) {
            w.append(" AND team_name LIKE :tn");
            p.put("tn", "%" + teamName.trim() + "%");
        }
        if (!contactPerson.isBlank()) {
            w.append(" AND contact_person LIKE :cp");
            p.put("cp", "%" + contactPerson.trim() + "%");
        }
        if (start != null) {
            w.append(" AND create_time >= :start");
            p.put("start", start);
        }
        if (end != null) {
            w.append(" AND create_time <= :end");
            p.put("end", end);
        }
        return db.page("SELECT * FROM joy_fill_fleet_list" + w, p, "id DESC", page, size);
    }

    /** Cartes M1 libres de la société, ou déjà attribuées à cette flotte (JoyFillCard/GetData). */
    @GetMapping("/cards")
    public List<Map<String, Object>> cards(@CurrentUser SessionUser user, @RequestParam(required = false) Integer teamId,
                                           @RequestParam(defaultValue = "false") boolean assignedOnly) {
        String where = assignedOnly ? "team_id = :t" : "(team_id IS NULL OR team_id = :t)";
        return db.list("SELECT id, card_no, team_id FROM pre_card_m WHERE \"using\" = false AND company_id = :c AND " + where
                + " ORDER BY card_no", Db.p("c", companyOf(user), "t", teamId == null ? -1 : teamId));
    }

    public record FleetForm(String teamName, Integer contactPersonId, String contactPerson, String phoneNumber, String address,
                            String remark, Integer accountState, Integer accountType, List<String> mCardIds) {
    }

    @PostMapping
    @Transactional
    public Map<String, Object> add(@CurrentUser SessionUser user, @RequestBody FleetForm f) {
        Operator op = cards.operator(user);
        Map<String, Object> st = station(op.stationId());
        if (db.one("SELECT 1 FROM joy_fill_fleet_list WHERE team_name = :n AND company_id = :c AND station_id = :s",
                Db.p("n", f.teamName(), "c", st.get("companyId"), "s", op.stationId())) != null) {
            throw new ApiException("Données en double");
        }
        if (db.one("SELECT 1 FROM joy_fill_fleet_list WHERE contact_person = :cp", Db.p("cp", f.contactPerson())) != null) {
            throw new ApiException("Le contact de la flotte doit être unique");
        }
        Number id = db.jdbc().queryForObject("INSERT INTO joy_fill_fleet_list (company_id, company_name, station_id, station_name, "
                + "team_name, account_state, contact_person_id, contact_person, phone_number, address, remark, master_balance, "
                + "create_time, m_card_i_ds, account_type) VALUES (:c, :cn, :s, :sn, :n, :as, :cpi, :cp, :ph, :ad, :r, 0, now(), "
                + ":cards, :at) RETURNING id", new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                Db.p("c", st.get("companyId"), "cn", st.get("companyName"), "s", op.stationId(), "sn", st.get("name"),
                        "n", req(f.teamName()), "as", f.accountState() == null ? 1 : f.accountState(),
                        "cpi", f.contactPersonId() == null ? 0 : f.contactPersonId(), "cp", f.contactPerson(),
                        "ph", f.phoneNumber(), "ad", f.address(), "r", f.remark(), "cards", joinCards(f.mCardIds()),
                        "at", f.accountType() == null ? 1 : f.accountType())), Number.class);
        assignCards(id.intValue(), f.mCardIds());
        return Map.of("id", id);
    }

    /** Modification ; un changement de nom est répercuté sur les chauffeurs et l'historique (comme l'original). */
    @PutMapping("/{id}")
    @Transactional
    public void update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody FleetForm f) {
        Map<String, Object> old = fleet(user, id);
        String name = req(f.teamName());
        if (!name.equals(old.get("teamName"))) {
            for (String t : List.of("joy_fill_fleet_recharge_record", "joy_fill_fleet_driver", "joy_fill_fleet_distribute_record",
                    "joy_fill_fleet_card_record")) {
                db.update("UPDATE " + t + " SET team_name = :n WHERE team_id = :id", Map.of("n", name, "id", id));
            }
        }
        db.update("UPDATE joy_fill_fleet_list SET team_name = :n, contact_person_id = :cpi, account_state = :as, account_type = :at, "
                + "contact_person = :cp, phone_number = :ph, remark = :r, address = :ad, m_card_i_ds = :cards WHERE id = :id",
                Db.p("n", name, "cpi", f.contactPersonId() == null ? 0 : f.contactPersonId(), "as", f.accountState(),
                        "at", f.accountType(), "cp", f.contactPerson(), "ph", f.phoneNumber(), "r", f.remark(), "ad", f.address(),
                        "cards", joinCards(f.mCardIds()), "id", id));
        assignCards(id, f.mCardIds());
    }

    /** Suppression impossible tant que la flotte a des chauffeurs ; libère ses cartes M1. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        fleet(user, id);
        if (db.one("SELECT 1 FROM joy_fill_fleet_driver WHERE team_id = :id LIMIT 1", Map.of("id", id)) != null) {
            throw new ApiException("La flotte a encore des chauffeurs, suppression impossible");
        }
        db.update("UPDATE pre_card_m SET team_id = NULL WHERE team_id = :id", Map.of("id", id));
        db.update("DELETE FROM joy_fill_fleet_list WHERE id = :id", Map.of("id", id));
    }

    /** type 1 = recharge, 2 = remboursement ; expectedBalance protège contre une modification concurrente. */
    public record RechargeForm(int type, BigDecimal amount, BigDecimal expectedBalance) {
    }

    @PostMapping("/{id}/recharge")
    @Transactional
    public Map<String, Object> recharge(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody RechargeForm f) {
        Map<String, Object> fl = fleet(user, id);
        if (f.amount() == null || f.amount().signum() <= 0) {
            throw new ApiException("Montant incorrect");
        }
        BigDecimal before = new BigDecimal(fl.get("masterBalance").toString());
        if (f.expectedBalance() != null && before.compareTo(f.expectedBalance()) != 0) {
            throw new ApiException("Le solde a changé entre-temps, veuillez actualiser");
        }
        BigDecimal after = f.type() == 2 ? before.subtract(f.amount()) : before.add(f.amount());
        if (after.signum() < 0) {
            throw new ApiException("Solde insuffisant, remboursement impossible");
        }
        db.update("UPDATE joy_fill_fleet_list SET master_balance = :b WHERE id = :id", Map.of("b", after, "id", id));
        db.update("INSERT INTO joy_fill_fleet_recharge_record (company_id, company_name, station_id, station_name, team_id, team_name, "
                + "recharge_time, before_balance, recharge_amount, after_balance, login_name, recharge_type, account_type) "
                + "VALUES (:c, :cn, :s, :sn, :t, :tn, now(), :b, :a, :af, :op, :rt, :at)",
                Db.p("c", fl.get("companyId"), "cn", fl.get("companyName"), "s", fl.get("stationId"), "sn", fl.get("stationName"),
                        "t", id, "tn", fl.get("teamName"), "b", before, "a", f.amount(), "af", after,
                        "op", user.getEmployeesName() == null ? user.getLoginName() : user.getEmployeesName(),
                        "rt", f.type() == 2 ? 2 : 1, "at", fl.get("accountType")));
        return Map.of("masterBalance", after);
    }

    // ------------------------------------------------------------ Utilitaires

    public Map<String, Object> fleet(SessionUser user, int id) {
        Map<String, Object> f = db.one("SELECT * FROM joy_fill_fleet_list WHERE id = :id", Map.of("id", id));
        if (f == null) {
            throw new ApiException("La flotte n'existe pas");
        }
        if (!scope.stationIds(user).contains(((Number) f.get("stationId")).intValue())) {
            throw new ApiException("Cette flotte ne fait pas partie de votre périmètre");
        }
        return f;
    }

    /** Réattribue les cartes M1 de la flotte (cradUpdata_upData). */
    private void assignCards(int id, List<String> cardNos) {
        db.update("UPDATE pre_card_m SET team_id = NULL WHERE team_id = :id AND \"using\" = false", Map.of("id", id));
        if (cardNos != null && !cardNos.isEmpty()) {
            db.update("UPDATE pre_card_m SET team_id = :id WHERE card_no IN (:cards)", Map.of("id", id, "cards", cardNos));
        }
    }

    private Map<String, Object> station(int stationId) {
        Map<String, Object> st = db.one("SELECT s.station_id, s.name, s.company_id, g.group_name company_name FROM stations s "
                + "LEFT JOIN login_company_group g ON g.id = s.company_id WHERE s.station_id = :s", Map.of("s", stationId));
        if (st == null) {
            throw new ApiException("Station de l'opérateur introuvable");
        }
        return st;
    }

    private int companyOf(SessionUser user) {
        Map<String, Object> st = db.one("SELECT company_id FROM stations WHERE station_id = :s",
                Map.of("s", cards.operator(user).stationId()));
        return st == null || st.get("companyId") == null ? user.companyGroupId() : ((Number) st.get("companyId")).intValue();
    }

    private static String joinCards(List<String> cards) {
        return cards == null ? "" : String.join(",", cards);
    }

    private static String req(String v) {
        if (v == null || v.isBlank()) {
            throw new ApiException("Le nom de la flotte est obligatoire");
        }
        return v.trim();
    }
}
