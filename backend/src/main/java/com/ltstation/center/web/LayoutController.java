package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.AirTank;
import com.ltstation.center.domain.entity.GPRSAccount;
import com.ltstation.center.domain.repository.AirTankRepository;
import com.ltstation.center.domain.repository.EmployeesRepository;
import com.ltstation.center.domain.repository.GPRSAccountRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Données communes à toutes les pages : alarmes, appareils expirés, compte. */
@RestController
@RequestMapping("/api/layout")
public class LayoutController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CompanyScopeService scope;
    private final AirTankRepository tanks;
    private final GPRSAccountRepository accounts;
    private final EmployeesRepository employees;
    private final Db db;

    public LayoutController(CompanyScopeService scope, AirTankRepository tanks, GPRSAccountRepository accounts,
                            EmployeesRepository employees, Db db) {
        this.scope = scope;
        this.tanks = tanks;
        this.accounts = accounts;
        this.employees = employees;
        this.db = db;
    }

    public record TankAlarm(Integer id, String tankName, String errorMessage) {
    }

    /** Alarmes des cuves en cours (Home/GetError), interrogé toutes les 10 s. */
    @GetMapping("/alarms")
    public List<TankAlarm> alarms(@CurrentUser SessionUser user) {
        List<Integer> stationIds = scope.stationIds(user);
        List<TankAlarm> out = new ArrayList<>();
        if (stationIds.isEmpty()) {
            return out;
        }
        for (AirTank t : tanks.findByIsRunTrueAndStationIdIn(stationIds)) {
            List<String> errors = new ArrayList<>();
            if (nz(t.getElectrostaticGrounding()) != 0) {
                errors.add("Mise à la terre électrostatique anormale");
            }
            if (nz(t.getTankleakage()) != 0) {
                errors.add("Fuite de cuve");
            }
            if (nz(t.getCombustibleGas()) != 0) {
                errors.add("Fuite de gaz");
            }
            if (nz(t.getTankhightemperature()) == 1) {
                errors.add("Température élevée");
            }
            if (nz(t.getTankhightemperature()) == 2) {
                errors.add("Température basse");
            }
            if (nz(t.getTankLevel()) == 1) {
                errors.add("Niveau haut");
            }
            if (nz(t.getTankLevel()) == 2) {
                errors.add("Niveau bas");
            }
            if (!errors.isEmpty()) {
                out.add(new TankAlarm(t.getId(), t.getTankName(), String.join(", ", errors)));
            }
        }
        return out;
    }

    public record ExpiredDevices(long expiringSoon, long expired) {
    }

    /** Appareils dont l'activation expire dans le mois / a expiré (JoyFillRenew/GetExpiredNumber). */
    @GetMapping("/expired-devices")
    public ExpiredDevices expiredDevices(@CurrentUser SessionUser user) {
        if (!user.hasMenu("34")) {
            return new ExpiredDevices(0, 0);
        }
        List<Integer> stationIds;
        if (user.isCenter()) {
            stationIds = db.jdbc().queryForList("SELECT station_id FROM stations WHERE company_id = :c",
                    Map.of("c", parseInt(user.getCompanyId())), Integer.class);
        } else {
            stationIds = user.getStationId() == null ? List.of() : List.of(user.getStationId());
        }
        String now = LocalDateTime.now().format(TS);
        String inMonth = LocalDateTime.now().plusMonths(1).format(TS);
        Map<String, Object> p = Map.of("ids", stationIds, "now", now, "month", inMonth);
        long soon = db.count("SELECT 1 FROM air_device WHERE is_run = true AND valid_time < :month "
                + "AND valid_time > :now AND valid_time <> '' AND station_id IN (:ids)", p);
        long expired = db.count("SELECT 1 FROM air_device WHERE is_run = true AND valid_time < :now "
                + "AND valid_time <> '' AND station_id IN (:ids)", p);
        return new ExpiredDevices(soon, expired);
    }

    public record AccountForm(String userName, String password) {
    }

    /** Paramètres du compte connecté (GPRS/GetCustomeMessage). */
    @GetMapping("/account")
    public AccountForm account(@CurrentUser SessionUser user) {
        GPRSAccount a = accounts.findById(user.getUserId())
                .orElseThrow(() -> new ApiException("Ce compte ne peut pas être modifié ici"));
        return new AccountForm(a.getUserName(), "");
    }

    /** Modification du compte connecté (GPRS/CustomerUpdate) : met aussi à jour l'employé lié. */
    @PutMapping("/account")
    @Transactional
    public void updateAccount(@CurrentUser SessionUser user, @RequestBody AccountForm form) {
        GPRSAccount cus = accounts.findById(user.getUserId())
                .orElseThrow(() -> new ApiException("Objet inexistant"));
        if (form.userName() == null || form.userName().isBlank()) {
            throw new ApiException("Le nom d'utilisateur est obligatoire");
        }
        String password = form.password() == null || form.password().isEmpty() ? cus.getPassword() : form.password();
        if (Objects.equals(cus.getTypeId(), 1)) {
            employees.findFirstByAddressAndMemoAndIsUsingTrue(cus.getUserName(), cus.getPassword()).ifPresent(e -> {
                e.setAddress(form.userName());
                e.setMemo(password);
                employees.save(e);
            });
        }
        cus.setUserName(form.userName());
        cus.setPassword(password);
        accounts.save(cus);
        user.setLoginName(form.userName());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
