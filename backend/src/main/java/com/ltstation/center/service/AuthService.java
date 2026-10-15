package com.ltstation.center.service;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.domain.entity.Employees;
import com.ltstation.center.domain.entity.GPRSAccount;
import com.ltstation.center.domain.entity.Stations;
import com.ltstation.center.domain.repository.EmployeesRepository;
import com.ltstation.center.domain.repository.GPRSAccountRepository;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.SessionUser;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connexion à la plateforme (portage de GPRSController.LoginSubmit).
 *
 * Les identifiants "maîtres" codés en dur dans l'original (OITY/YITO+date,
 * BlueSky/…) ne sont pas repris : ils sont remplacés par un super
 * administrateur optionnel défini par variables d'environnement.
 */
@Service
public class AuthService {

    private final GPRSAccountRepository accounts;
    private final EmployeesRepository employees;
    private final StationsRepository stations;
    private final LicenseService license;
    private final MenuCatalog menus;
    private final String superUser;
    private final String superPassword;

    public AuthService(GPRSAccountRepository accounts, EmployeesRepository employees, StationsRepository stations,
                       LicenseService license, MenuCatalog menus,
                       @Value("${app.superadmin.username:}") String superUser,
                       @Value("${app.superadmin.password:}") String superPassword) {
        this.accounts = accounts;
        this.employees = employees;
        this.stations = stations;
        this.license = license;
        this.menus = menus;
        this.superUser = superUser;
        this.superPassword = superPassword;
    }

    @Transactional(readOnly = true)
    public SessionUser login(String user, String password) {
        if (user == null || user.isBlank() || password == null || password.isEmpty()) {
            throw new ApiException("Nom d'utilisateur ou mot de passe incorrect");
        }
        license.checkActive();

        if (!superUser.isBlank() && !superPassword.isBlank()
                && superUser.equals(user) && superPassword.equals(password)) {
            return systemManager();
        }

        GPRSAccount account = accounts.findFirstByUserNameAndPassword(user, password)
                .orElseThrow(() -> new ApiException("Nom d'utilisateur ou mot de passe incorrect"));

        SessionUser s = new SessionUser();
        s.setUserId(account.getId());
        s.setRoleId(Objects.requireNonNullElse(account.getRoleId(), 0));
        s.setLoginName(account.getUserName());
        s.setCompanyGroup("");
        s.setGprs("0");
        int type = Objects.requireNonNullElse(account.getTypeId(), -1);
        switch (type) {
            case 0 -> {
                // Client GPRS
                s.setAuth("");
                s.setLoginType("99");
                s.setGprs("1");
            }
            case 1 -> {
                // Employé : identifiant dans Address, mot de passe dans Memo (structure d'origine)
                Employees emp = employees.findFirstByAddressAndMemoAndIsUsingTrue(user, password)
                        .orElseThrow(() -> new ApiException("Compte utilisateur ou mot de passe incorrect"));
                s.setEmpId(emp.getEmployeeId());
                s.setAuth(Objects.requireNonNullElse(emp.getPcMenuAuth(), ""));
                s.setGzhAuth(Objects.requireNonNullElse(emp.getGzhAuth(), ""));
                Stations st = stations.findById(emp.getStationId())
                        .orElseThrow(() -> new ApiException("La station de l'employé est introuvable"));
                s.setCenter(Objects.equals(st.getStationTypeId(), 3));
                s.setLoginType("1");
                s.setStationId(emp.getStationId());
                s.setCompanyGroup(String.valueOf(st.getCompanyId()));
                s.setCompanyId(String.valueOf(st.getCompanyId()));
                s.setStationName(st.getName());
                s.setEmployeesName(emp.getName());
                s.setTelephone(emp.getTelephone());
            }
            case 2 -> {
                // Compte groupe de société
                s.setLoginType("2");
                s.setCompanyGroup(String.valueOf(account.getCompanyId()));
                s.setAuth(Objects.requireNonNullElse(account.getAuth(), ""));
            }
            default -> throw new ApiException("Type de compte inconnu");
        }
        return s;
    }

    /** Équivalent du compte "System Manage" d'origine (tous les droits, racine). */
    private SessionUser systemManager() {
        SessionUser s = new SessionUser();
        s.setAuth(menus.allPcAuth());
        s.setGzhAuth("0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,54");
        s.setLoginType("1");
        s.setEmpId(-1);
        s.setCompanyGroup("");
        s.setCompanyId("0");
        s.setUserId(-1);
        s.setLoginName("System Manage");
        s.setGprs("0");
        s.setCenter(true);
        return s;
    }

    /** Changement de mot de passe d'un compte GPRSAccount (et de l'employé lié). */
    @Transactional
    public void changePassword(SessionUser user, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new ApiException("Le nouveau mot de passe est obligatoire");
        }
        if (user.isSystemManager()) {
            throw new ApiException("Le mot de passe du super administrateur se change dans la configuration du serveur");
        }
        GPRSAccount account = accounts.findById(user.getUserId())
                .orElseThrow(() -> new ApiException("Compte introuvable"));
        if (!Objects.equals(account.getPassword(), oldPassword)) {
            throw new ApiException("Ancien mot de passe incorrect");
        }
        account.setPassword(newPassword);
        accounts.save(account);
        if (user.getEmpId() > 0) {
            employees.findById(user.getEmpId()).ifPresent(e -> {
                e.setMemo(newPassword);
                employees.save(e);
            });
        }
    }
}
