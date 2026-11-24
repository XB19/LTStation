package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.ExcelImport;
import com.ltstation.center.common.ExcelImport.Column;
import com.ltstation.center.common.ExcelImport.Line;
import com.ltstation.center.domain.entity.Employees;
import com.ltstation.center.domain.entity.GPRSAccount;
import com.ltstation.center.domain.repository.EmployeesRepository;
import com.ltstation.center.domain.repository.GPRSAccountRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.DataVersionService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
        Long maxNo = db.jdbc().queryForObject("SELECT max(employee_no::bigint) FROM employees WHERE employee_no ~ '^[0-9]+$'", Map.of(), Long.class);
        out.put("employeeNo", String.valueOf(maxNo == null ? 100001 : maxNo + 1));
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

    // ------------------------------------------------------------------ Import Excel

    static final List<Column> IMPORT_COLUMNS = List.of(
            new Column("Station", "station", true, false),
            new Column("Nom", "name", true, false),
            new Column("Identifiant de connexion", "loginName", true, true),
            new Column("Mot de passe", "password", true, true),
            new Column("Téléphone", "phone", false, true));

    @GetMapping("/import-template")
    public void template(HttpServletResponse response) throws IOException {
        ExcelImport.template(response, "Modele_import_employes", IMPORT_COLUMNS,
                List.of(List.of("Station Démo Lomé", "Kodjo Mensah", "kmensah", "Lome2026!", "22890112233")),
                List.of("Import des employés : une ligne par employé, toutes les colonnes marquées * sont obligatoires.",
                        "Station : nom exact d'une station de votre périmètre.",
                        "Identifiant de connexion : unique (ni un autre employé ni un autre compte ne doit l'utiliser).",
                        "Mot de passe : mot de passe de connexion initial. Ce fichier contient des mots de passe : "
                                + "supprimez-le après l'import.",
                        "Téléphone : facultatif, unique s'il est saisi. Gardez les colonnes au format Texte.",
                        "Le n° d'employé et le code interne sont attribués automatiquement ; les droits sont choisis dans l'écran d'import.",
                        "Supprimez la ligne d'exemple avant d'importer."));
    }

    /**
     * Import : dryRun=true contrôle sans rien enregistrer ; sinon tous les employés sont créés, ou aucun s'il reste une erreur.
     * Les droits PC / mobile choisis s'appliquent à tous les employés du fichier.
     */
    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importFile(@CurrentUser SessionUser user, @RequestPart("file") MultipartFile file,
                                          @RequestParam(defaultValue = "true") boolean dryRun,
                                          @RequestParam(required = false) List<String> pcMenuAuth,
                                          @RequestParam(required = false) List<String> gzhAuth) {
        List<Line> lines = ExcelImport.read(file, IMPORT_COLUMNS);
        Map<String, Integer> stations = new HashMap<>();
        for (Map<String, Object> st : db.list("SELECT station_id, name FROM stations WHERE station_id IN (:s)",
                Map.of("s", scope.stationIds(user)))) {
            stations.put(ExcelImport.norm((String) st.get("name")), (Integer) st.get("stationId"));
        }
        Set<String> logins = new HashSet<>();
        Set<String> phones = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        List<EmployeeForm> forms = new ArrayList<>();
        for (Line l : lines) {
            List<String> errors = new ArrayList<>(l.errors());
            Integer station = l.get("station") == null ? null : stations.get(ExcelImport.norm(l.get("station")));
            if (l.get("station") != null && station == null) {
                errors.add("Station inconnue ou hors de votre périmètre : " + l.get("station"));
            }
            String login = l.get("loginName");
            if (login != null) {
                if (!logins.add(login.toLowerCase())) {
                    errors.add("Identifiant en double dans le fichier : " + login);
                } else if (db.one("SELECT 1 FROM employees WHERE address = :l", Map.of("l", login)) != null
                        || db.one("SELECT 1 FROM gprs_account WHERE user_name = :l", Map.of("l", login)) != null) {
                    errors.add("Identifiant déjà utilisé : " + login);
                }
            }
            String phone = l.get("phone");
            if (phone != null) {
                if (!phones.add(phone)) {
                    errors.add("Téléphone en double dans le fichier : " + phone);
                } else if (db.one("SELECT 1 FROM employees WHERE telephone = :p", Map.of("p", phone)) != null) {
                    errors.add("Ce numéro de téléphone existe déjà : " + phone);
                }
            }
            Map<String, Object> row = new LinkedHashMap<>(l.values());
            row.remove("password");
            row.put("line", l.number());
            row.put("action", "Création");
            row.put("errors", errors);
            out.add(row);
            if (errors.isEmpty()) {
                forms.add(new EmployeeForm(station, null, null, l.get("name"), login, l.get("password"), phone, pcMenuAuth, gzhAuth));
            }
        }
        boolean hasErrors = out.stream().anyMatch(r -> !((List<?>) r.get("errors")).isEmpty());
        if (dryRun || hasErrors) {
            return ExcelImport.result(out, dryRun, 0);
        }
        // N° d'employé et code interne calculés pour chaque ligne, après les créations précédentes (même transaction)
        for (EmployeeForm f : forms) {
            Map<String, Object> codes = nextCodes(f.stationId());
            add(user, new EmployeeForm(f.stationId(), (String) codes.get("employeeNo"), (Integer) codes.get("codeInner"), f.name(),
                    f.loginName(), f.password(), f.phone(), f.pcMenuAuth(), f.gzhAuth()));
        }
        return ExcelImport.result(out, false, forms.size());
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
