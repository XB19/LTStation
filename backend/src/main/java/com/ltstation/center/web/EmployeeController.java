package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.Employees;
import com.ltstation.center.domain.entity.GPRSAccount;
import com.ltstation.center.domain.repository.EmployeesRepository;
import com.ltstation.center.domain.repository.GPRSAccountRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.DataVersionService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * Configuration des employés et de leurs droits PC / mobile (Main/EmpConfigure d'origine).
 * L'identifiant de connexion est stocké dans Address et le mot de passe dans Memo,
 * comme dans la base d'origine (utilisée aussi par les logiciels de station).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final Db db;
    private final EmployeesRepository employees;
    private final GPRSAccountRepository accounts;
    private final CompanyScopeService scope;
    private final DataVersionService versions;

    public EmployeeController(Db db, EmployeesRepository employees, GPRSAccountRepository accounts,
                              CompanyScopeService scope, DataVersionService versions) {
        this.db = db;
        this.employees = employees;
        this.accounts = accounts;
        this.scope = scope;
        this.versions = versions;
    }

    /** Employés actifs du périmètre (GetEmployeeList). */
    @GetMapping
    public List<Map<String, Object>> list(@CurrentUser SessionUser user,
                                          @RequestParam(required = false) Integer regionId,
                                          @RequestParam(required = false) Integer cityId,
                                          @RequestParam(required = false) Integer stationId) {
        return db.list("SELECT e.employee_id, e.employee_no, e.code_inner, e.name, e.address login_name, e.telephone, "
                + "e.station_id, s.name station_name, a.name post_name FROM employees e "
                + "LEFT JOIN stations s ON s.station_id = e.station_id "
                + "LEFT JOIN authority_mess a ON CAST(a.user_id_mess AS text) = e.post_code "
                + "WHERE e.is_using = true AND e.station_id IN (:s) ORDER BY e.employee_id",
                Map.of("s", scope.scopedStations(user, regionId, cityId, stationId)));
    }

    /** Numéros proposés : n° d'employé global (GetEmployeeMax) et code interne par station (GetEmployeeCodeInnerMax). */
    @GetMapping("/next-codes")
    public Map<String, Object> nextCodes(@RequestParam(required = false) Integer stationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        String maxNo = db.jdbc().queryForObject("SELECT max(employee_no) FROM employees", Map.of(), String.class);
        long no;
        try {
            no = maxNo == null || maxNo.isBlank() ? 100001 : Long.parseLong(maxNo.trim()) + 1;
        } catch (NumberFormatException e) {
            no = 100001;
        }
        out.put("employeeNo", String.valueOf(no));
        if (stationId != null && stationId != 0) {
            Integer max = db.jdbc().queryForObject("SELECT max(code_inner) FROM employees WHERE station_id = :s",
                    Map.of("s", stationId), Integer.class);
            out.put("codeInner", max == null ? 1 : max + 1);
        }
        return out;
    }

    /** Détail d'un employé pour modification (GetEmployeeMessage). */
    @GetMapping("/{id}")
    public Map<String, Object> get(@CurrentUser SessionUser user, @PathVariable int id) {
        Employees e = load(user, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", e.getEmployeeId());
        m.put("stationId", e.getStationId());
        m.put("employeeNo", e.getEmployeeNo());
        m.put("codeInner", e.getCodeInner());
        m.put("name", e.getName());
        m.put("loginName", e.getAddress());
        m.put("phone", e.getTelephone());
        m.put("pcMenuAuth", split(e.getPcMenuAuth()));
        m.put("gzhAuth", split(e.getGzhAuth()));
        return m;
    }

    public record EmployeeForm(Integer stationId, String employeeNo, Integer codeInner, String name, String loginName,
                               String password, String phone, List<String> pcMenuAuth, List<String> gzhAuth) {
    }

    /** Création (ICEmployeeAdd) : crée aussi le compte de connexion GPRSAccount de type employé. */
    @PostMapping
    @Transactional
    public Map<String, Object> add(@CurrentUser SessionUser user, @RequestBody EmployeeForm f) {
        checkStation(user, f.stationId());
        String login = req(f.loginName(), "Identifiant de connexion");
        String password = req(f.password(), "Mot de passe");
        if (f.phone() != null && !f.phone().isBlank()
                && db.one("SELECT 1 FROM employees WHERE telephone = :p", Map.of("p", f.phone())) != null) {
            throw new ApiException("Ce numéro de téléphone existe déjà");
        }
        if (db.one("SELECT 1 FROM employees WHERE address = :l", Map.of("l", login)) != null
                || db.one("SELECT 1 FROM gprs_account WHERE user_name = :l", Map.of("l", login)) != null) {
            throw new ApiException("L'identifiant de connexion doit être unique");
        }
        Employees e = new Employees();
        e.setStationId(f.stationId());
        e.setName(req(f.name(), "Nom"));
        e.setEmployeeNo(req(f.employeeNo(), "N° d'employé"));
        e.setCode(f.name());
        e.setCertificateTypeId(1);
        e.setCertifyKey("");
        e.setGenderId(1);
        e.setBirthday(LocalDateTime.now());
        e.setAddress(login);
        e.setTelephone(f.phone());
        e.setMemo(password);
        e.setCodeInner(f.codeInner() == null ? 1 : f.codeInner());
        e.setIsUsing(true);
        e.setPcMenuAuth(allowed(user, f.pcMenuAuth(), user.getAuth()));
        e.setGzhAuth(allowed(user, f.gzhAuth(), user.getGzhAuth()));
        e = employees.save(e);
        versions.bump("employee");
        GPRSAccount a = new GPRSAccount();
        a.setCreateTime(LocalDateTime.now());
        a.setUserName(login);
        a.setPassword(password);
        a.setTypeId(1);
        a.setRoleId(0);
        accounts.save(a);
        return get(user, e.getEmployeeId());
    }

    /** Modification (ICEmployeeUpdate) : met aussi à jour le compte de connexion lié. */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody EmployeeForm f) {
        Employees e = load(user, id);
        checkStation(user, f.stationId());
        GPRSAccount account = accounts.findFirstByUserNameAndPassword(e.getAddress(), e.getMemo()).orElse(null);
        if (f.phone() != null && !f.phone().isBlank()
                && db.one("SELECT 1 FROM employees WHERE employee_id <> :id AND telephone = :p",
                Map.of("id", id, "p", f.phone())) != null) {
            throw new ApiException("Ce numéro de téléphone existe déjà");
        }
        e.setTelephone(f.phone());
        e.setStationId(f.stationId());
        e.setName(req(f.name(), "Nom"));
        if (f.password() != null && !f.password().isEmpty()) {
            e.setMemo(f.password());
        }
        e.setBirthday(LocalDateTime.now());
        e.setPcMenuAuth(allowed(user, f.pcMenuAuth(), user.getAuth()));
        e.setGzhAuth(allowed(user, f.gzhAuth(), user.getGzhAuth()));
        employees.save(e);
        if (account != null) {
            account.setUserName(e.getAddress());
            account.setPassword(e.getMemo());
            accounts.save(account);
        }
        versions.bump("employee");
        return get(user, id);
    }

    /** Suppression logique (ICEmployeeDelete) : is_using = false. */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        Employees e = load(user, id);
        e.setIsUsing(false);
        employees.save(e);
        versions.bump("employee");
    }

    private Employees load(SessionUser user, int id) {
        Employees e = employees.findById(id).orElseThrow(() -> new ApiException("Information inexistante"));
        checkStation(user, e.getStationId());
        return e;
    }

    /** Ne conserve que les droits que l'utilisateur connecté possède lui-même (comme l'écran d'origine). */
    private static String allowed(SessionUser user, List<String> requested, String own) {
        if (requested == null) {
            return "";
        }
        Set<String> mine = user.isSystemManager() ? null : Set.copyOf(split(own));
        return requested.stream().map(String::trim).filter(s -> !s.isEmpty())
                .filter(s -> mine == null || mine.contains(s)).distinct().collect(Collectors.joining(","));
    }

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private void checkStation(SessionUser user, Integer stationId) {
        if (stationId == null || !scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
    }

    private static String req(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new ApiException("Le champ « " + field + " » est obligatoire");
        }
        return v.trim();
    }
}
