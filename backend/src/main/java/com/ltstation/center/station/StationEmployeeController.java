package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.DataVersionService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employés de la station (EmpForm, CreateEmployee, EmpUpdate d'origine) et profils de droits (Authority, AddAuthority).
 * L'identifiant et le mot de passe de connexion sont ceux de la fiche employé (address / memo), comme dans la base d'origine ;
 * le profil de droits est conservé dans employees.auth_id.
 */
@RestController
@RequestMapping("/api/station")
public class StationEmployeeController {

    private final Db db;
    private final StationContext stations;
    private final DataVersionService versions;

    public StationEmployeeController(Db db, StationContext stations, DataVersionService versions) {
        this.db = db;
        this.stations = stations;
        this.versions = versions;
    }

    // ------------------------------------------------------------------ Employés

    @GetMapping("/employees")
    public List<Map<String, Object>> employees(@CurrentUser SessionUser user) {
        int s = stations.of(user).stationId();
        return db.list("SELECT e.employee_id, e.employee_no, e.name, e.address login_name, e.center_login, e.auth_id, a.name authority_name "
                + "FROM employees e LEFT JOIN station_authority a ON CAST(a.id AS text) = e.auth_id WHERE e.station_id = :s AND e.is_using = true "
                + "ORDER BY e.employee_id", Map.of("s", s));
    }

    public record EmployeeForm(String name, String loginName, String password, Integer authorityId, Boolean centerLogin) {
    }

    @PostMapping("/employees")
    @Transactional
    public void addEmployee(@CurrentUser SessionUser user, @RequestBody EmployeeForm f) {
        StationContext.Ctx c = stations.of(user);
        String name = req(f.name(), "Nom");
        String login = req(f.loginName(), "Identifiant");
        String password = req(f.password(), "Mot de passe");
        if (db.one("SELECT 1 FROM employees WHERE address = :l UNION ALL SELECT 1 FROM gprs_account WHERE user_name = :l", Map.of("l", login)) != null) {
            throw new ApiException("Cet identifiant existe déjà");
        }
        String maxNo = db.jdbc().queryForObject("SELECT max(employee_no) FROM employees WHERE employee_no ~ '^[0-9]+$'", Map.of(), String.class);
        long no = maxNo == null ? 100001 : Long.parseLong(maxNo) + 1;
        Integer inner = db.jdbc().queryForObject("SELECT COALESCE(max(code_inner), 0) + 1 FROM employees WHERE station_id = :s", Map.of("s", c.stationId()),
                Integer.class);
        db.update("INSERT INTO employees (employee_no, name, code, station_id, certificate_type_id, certify_key, gender_id, birthday, address, memo, code_inner, "
                + "is_using, center_login, auth_id) VALUES (:no, :n, :n, :s, 1, '', 1, now(), :l, :p, :ci, true, :cl, :a)",
                Db.p("no", String.valueOf(no), "n", name, "s", c.stationId(), "l", login, "p", password, "ci", inner, "cl", Boolean.TRUE.equals(f.centerLogin()),
                        "a", f.authorityId() == null ? null : String.valueOf(f.authorityId())));
        db.update("INSERT INTO gprs_account (user_name, password, type_id, role_id, create_time) VALUES (:l, :p, 1, 0, now())", Map.of("l", login, "p", password));
        versions.bump("employee");
    }

    @PutMapping("/employees/{id}")
    @Transactional
    public void updateEmployee(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody EmployeeForm f) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> e = db.one("SELECT address, memo FROM employees WHERE employee_id = :id AND station_id = :s", Map.of("id", id, "s", c.stationId()));
        if (e == null) {
            throw new ApiException("Employé introuvable");
        }
        String login = req(f.loginName(), "Identifiant");
        if (!login.equals(e.get("address")) && db.one("SELECT 1 FROM employees WHERE address = :l UNION ALL SELECT 1 FROM gprs_account WHERE user_name = :l",
                Map.of("l", login)) != null) {
            throw new ApiException("Cet identifiant existe déjà");
        }
        String password = f.password() == null || f.password().isEmpty() ? (String) e.get("memo") : f.password();
        db.update("UPDATE employees SET name = :n, address = :l, memo = :p, center_login = :cl, auth_id = :a WHERE employee_id = :id",
                Db.p("n", req(f.name(), "Nom"), "l", login, "p", password, "cl", Boolean.TRUE.equals(f.centerLogin()),
                        "a", f.authorityId() == null ? null : String.valueOf(f.authorityId()), "id", id));
        db.update("UPDATE gprs_account SET user_name = :l, password = :p WHERE user_name = :ol AND password = :op AND type_id = 1",
                Db.p("l", login, "p", password, "ol", e.get("address"), "op", e.get("memo")));
        versions.bump("employee");
    }

    @DeleteMapping("/employees/{id}")
    @Transactional
    public void deleteEmployee(@CurrentUser SessionUser user, @PathVariable int id) {
        StationContext.Ctx c = stations.of(user);
        if (id == c.employeeId()) {
            throw new ApiException("Vous ne pouvez pas supprimer votre propre compte");
        }
        db.update("UPDATE employees SET is_using = false WHERE employee_id = :id AND station_id = :s", Map.of("id", id, "s", c.stationId()));
        versions.bump("employee");
    }

    // ------------------------------------------------------------------ Profils de droits

    @GetMapping("/authorities")
    public List<Map<String, Object>> authorities(@CurrentUser SessionUser user) {
        return db.list("SELECT id, name, content FROM station_authority WHERE station_id = :s ORDER BY id", Map.of("s", stations.of(user).stationId()));
    }

    public record AuthorityForm(String name, List<Integer> rights) {
    }

    @PostMapping("/authorities")
    @Transactional
    public void addAuthority(@CurrentUser SessionUser user, @RequestBody AuthorityForm f) {
        int s = stations.of(user).stationId();
        String name = req(f.name(), "Nom du droit");
        if (db.one("SELECT 1 FROM station_authority WHERE station_id = :s AND name = :n", Map.of("s", s, "n", name)) != null) {
            throw new ApiException("Ce profil existe déjà");
        }
        db.update("INSERT INTO station_authority (station_id, name, content) VALUES (:s, :n, :c)", Map.of("s", s, "n", name, "c", content(f.rights())));
    }

    @PutMapping("/authorities/{id}")
    @Transactional
    public void updateAuthority(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody AuthorityForm f) {
        db.update("UPDATE station_authority SET content = :c WHERE id = :id AND station_id = :s",
                Map.of("c", content(f.rights()), "id", id, "s", stations.of(user).stationId()));
    }

    /** Droits de l'employé connecté (null = tous les droits, employé sans profil). */
    public List<Integer> rights(int employeeId) {
        Map<String, Object> r = db.one("SELECT a.content FROM employees e JOIN station_authority a ON CAST(a.id AS text) = e.auth_id "
                + "WHERE e.employee_id = :e", Map.of("e", employeeId));
        if (r == null) {
            return null;
        }
        return Arrays.stream(((String) r.get("content")).split(",")).map(String::trim).filter(x -> x.matches("\\d+")).map(Integer::valueOf).toList();
    }

    private static String content(List<Integer> rights) {
        return rights == null ? "" : rights.stream().filter(r -> r >= 1 && r <= 24).sorted().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String req(String v, String label) {
        if (v == null || v.isBlank()) {
            throw new ApiException(label + " obligatoire");
        }
        return v.trim();
    }
}
