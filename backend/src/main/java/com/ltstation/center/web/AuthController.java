package com.ltstation.center.web;

import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.PublicApi;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.AuthService;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.LicenseService;
import com.ltstation.center.service.MenuCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connexion, déconnexion, session et activation de la plateforme. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final LicenseService license;
    private final CompanyScopeService scope;
    private final MenuCatalog menus;

    public AuthController(AuthService auth, LicenseService license, CompanyScopeService scope, MenuCatalog menus) {
        this.auth = auth;
        this.license = license;
        this.scope = scope;
        this.menus = menus;
    }

    public record LoginRequest(String user, String password) {
    }

    public record PasswordRequest(String oldPassword, String newPassword) {
    }

    public record ActivateRequest(String code) {
    }

    @PublicApi
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        SessionUser user = auth.login(req.user(), req.password());
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate(); // évite la fixation de session
        }
        http.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        return me(user);
    }

    @PublicApi
    @PostMapping("/logout")
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(@CurrentUser SessionUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", user.getUserId());
        m.put("roleId", user.getRoleId());
        m.put("loginName", user.getLoginName());
        m.put("loginType", user.getLoginType());
        m.put("companyGroup", user.getCompanyGroup());
        m.put("stationId", user.getStationId());
        m.put("stationName", user.getStationName());
        m.put("employeeName", user.getEmployeesName());
        m.put("auth", user.authSet());
        m.put("gzhAuth", user.getGzhAuth());
        m.put("isCenter", user.isCenter());
        m.put("gprs", user.getGprs());
        m.put("title", scope.title(user));
        m.put("pcMenus", menus.pc());
        m.put("phoneMenus", menus.phone());
        return m;
    }

    @PostMapping("/password")
    public void changePassword(@CurrentUser SessionUser user, @RequestBody PasswordRequest req) {
        auth.changePassword(user, req.oldPassword(), req.newPassword());
    }

    @PublicApi
    @GetMapping("/license")
    public Map<String, Object> licenseState() {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDateTime expiry = license.expiry().orElse(null);
        m.put("expiry", expiry);
        m.put("active", expiry != null && LocalDateTime.now().isBefore(expiry));
        return m;
    }

    @PublicApi
    @PostMapping("/activate")
    public Map<String, Object> activate(@RequestBody ActivateRequest req, HttpServletRequest http) {
        LocalDateTime expiry = license.activate(req.code() == null ? "" : req.code(), http.getServerName());
        return Map.of("expiry", expiry);
    }
}
