package com.ltstation.center.station;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.PublicApi;
import com.ltstation.center.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion au logiciel Station (Login d'origine) : identifiant et mot de passe de l'employé
 * (champs address / memo de la fiche employé, comme dans la base d'origine).
 */
@RestController
@RequestMapping("/api/station/auth")
public class StationAuthController {

    private final Db db;
    private final StationContext stations;

    public StationAuthController(Db db, StationContext stations) {
        this.db = db;
        this.stations = stations;
    }

    public record LoginRequest(String login, String password) {
    }

    @PublicApi
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        if (req.login() == null || req.login().isBlank() || req.password() == null || req.password().isEmpty()) {
            throw new ApiException("Saisissez l'identifiant et le mot de passe");
        }
        Map<String, Object> e = db.one("SELECT e.employee_id, e.name, e.station_id, e.telephone, e.pc_menu_auth, s.name station_name, "
                + "s.company_id, s.station_type_id FROM employees e JOIN stations s ON s.station_id = e.station_id "
                + "WHERE e.address = :l AND e.memo = :p AND e.is_using = true LIMIT 1", Map.of("l", req.login().trim(), "p", req.password()));
        if (e == null) {
            throw new ApiException("Identifiant ou mot de passe incorrect");
        }
        SessionUser s = new SessionUser();
        s.setLoginType("1");
        s.setUserId(((Number) e.get("employeeId")).intValue());
        s.setEmpId(((Number) e.get("employeeId")).intValue());
        s.setLoginName((String) e.get("name"));
        s.setEmployeesName((String) e.get("name"));
        s.setStationId(((Number) e.get("stationId")).intValue());
        s.setStationName((String) e.get("stationName"));
        s.setCompanyGroup(String.valueOf(e.get("companyId")));
        s.setCompanyId(String.valueOf(e.get("companyId")));
        s.setTelephone((String) e.get("telephone"));
        s.setAuth(Objects.requireNonNullElse((String) e.get("pcMenuAuth"), ""));
        s.setCenter(Objects.equals(e.get("stationTypeId"), 3));
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        http.getSession(true).setAttribute(SessionUser.SESSION_KEY, s);
        return me(s);
    }

    @PublicApi
    @PostMapping("/logout")
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** Employé connecté, station et poste ouvert. */
    @GetMapping("/me")
    public Map<String, Object> me(@CurrentUser SessionUser user) {
        StationContext.Ctx c = stations.of(user);
        Map<String, Object> st = db.one("SELECT station_id, code, name, address, phone FROM stations WHERE station_id = :s",
                Map.of("s", c.stationId()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", c.employeeId());
        m.put("employeeName", c.employeeName());
        m.put("station", st);
        m.put("duty", stations.openDuty(c.stationId()));
        // Profil de droits (1 à 24) ; sans profil, l'employé a tous les droits
        Map<String, Object> a = db.one("SELECT a.content FROM employees e JOIN station_authority a ON CAST(a.id AS text) = e.auth_id WHERE e.employee_id = :e",
                Map.of("e", c.employeeId()));
        m.put("rights", a == null ? null : java.util.Arrays.stream(((String) a.get("content")).split(","))
                .map(String::trim).filter(x -> x.matches("\\d+")).map(Integer::valueOf).toList());
        return m;
    }
}
