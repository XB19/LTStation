package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.AirDevice;
import com.ltstation.center.domain.entity.AirTank;
import com.ltstation.center.domain.entity.GradeSpecifics;
import com.ltstation.center.domain.repository.AirDeviceRepository;
import com.ltstation.center.domain.repository.AirTankRepository;
import com.ltstation.center.domain.repository.GradeSpecificsRepository;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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
 * Appareils cloud (table air_device) : appareils inconnus, ajout / modification,
 * activation et date de validité (Main/UnKnowSeting, Main/ActiveDevice, Home/AirDeviceChange).
 */
@RestController
@RequestMapping("/api/air-devices")
public class AirDeviceController {

    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Db db;
    private final AirDeviceRepository devices;
    private final AirTankRepository tanks;
    private final GradeSpecificsRepository grades;
    private final StationsRepository stations;
    private final CompanyScopeService scope;
    private final String wechatAppId;
    private final String publicUrl;

    public AirDeviceController(Db db, AirDeviceRepository devices, AirTankRepository tanks, GradeSpecificsRepository grades,
                               StationsRepository stations, CompanyScopeService scope,
                               @Value("${app.wechat.app-id:}") String wechatAppId,
                               @Value("${app.public-url:}") String publicUrl) {
        this.db = db;
        this.devices = devices;
        this.tanks = tanks;
        this.grades = grades;
        this.stations = stations;
        this.scope = scope;
        this.wechatAppId = wechatAppId;
        this.publicUrl = publicUrl;
    }

    // ------------------------------------------------------------ Appareils inconnus

    /** Appareils qui se sont connectés sans être déclarés (GetUnknowDevice). */
    @GetMapping("/unknown")
    public List<Map<String, Object>> unknown() {
        return db.list("SELECT id, un_know_device_no, date_time FROM unknow_device ORDER BY date_time", Map.of());
    }

    @DeleteMapping("/unknown/{id}")
    @Transactional
    public void deleteUnknown(@PathVariable int id) {
        db.update("DELETE FROM unknow_device WHERE id = :id", Map.of("id", id));
    }

    // ------------------------------------------------------------ Ajout / modification

    /**
     * digitsType : 1 = 664, 2 = 886, 3 = atomiseur (valeurs stockées "664", "886", "3").
     */
    public record DeviceForm(Integer stationId, Integer tankId, Integer nozId, Integer amountDigits, Integer volumeDigits,
                             Integer priceDigits, Integer deviceType, String deviceNo, Integer unknownId) {
    }

    /** Ajout d'un appareil (AirDeviceChange "Add") ; supprime l'appareil inconnu correspondant. */
    @PostMapping
    @Transactional
    public AirDevice add(@CurrentUser SessionUser user, @RequestBody DeviceForm f) {
        checkStation(user, f.stationId());
        GradeSpecifics grade = gradeOfTank(f.tankId());
        if (db.one("SELECT 1 FROM air_device WHERE station_id = :s AND noz_id = :n AND is_run = true",
                Map.of("s", f.stationId(), "n", nz(f.nozId()))) != null) {
            throw new ApiException("Ce numéro de pistolet existe déjà");
        }
        AirDevice d = new AirDevice();
        fill(d, f, grade);
        d.setIsRun(true);
        d.setCreateTime(LocalDateTime.now());
        d.setValidTime(LocalDateTime.now().format(TS));
        d = devices.save(d);
        if (f.unknownId() != null) {
            deleteUnknown(f.unknownId());
        } else if (f.deviceNo() != null) {
            db.update("DELETE FROM unknow_device WHERE un_know_device_no = :n", Map.of("n", f.deviceNo()));
        }
        return d;
    }

    /** Modification (AirDeviceChange "Update"). */
    @PutMapping("/{id}")
    @Transactional
    public AirDevice update(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody DeviceForm f) {
        checkStation(user, f.stationId());
        GradeSpecifics grade = gradeOfTank(f.tankId());
        if (db.one("SELECT 1 FROM air_device WHERE id <> :id AND station_id = :s AND noz_id = :n AND is_run = true",
                Map.of("id", id, "s", f.stationId(), "n", nz(f.nozId()))) != null) {
            throw new ApiException("Numéro de pistolet en double");
        }
        AirDevice d = devices.findById(id).filter(x -> Boolean.TRUE.equals(x.getIsRun()))
                .orElseThrow(() -> new ApiException("L'appareil n'existe pas"));
        fill(d, f, grade);
        return devices.save(d);
    }

    /** Suppression logique : is_run = false (AirDeviceChange "Delete"). */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CurrentUser SessionUser user, @PathVariable int id) {
        AirDevice d = devices.findById(id).orElseThrow(() -> new ApiException("L'appareil n'existe pas"));
        checkStation(user, d.getStationId());
        d.setIsRun(false);
        devices.save(d);
    }

    // ------------------------------------------------------------ Activation

    /** Appareils actifs filtrés par région / ville / station, avec leur date de validité (GetAirActive). */
    @GetMapping("/activation")
    public List<Map<String, Object>> activation(@CurrentUser SessionUser user,
                                                @RequestParam(required = false) Integer regionId,
                                                @RequestParam(required = false) Integer cityId,
                                                @RequestParam(required = false) Integer stationId) {
        return db.list("SELECT d.id, d.device_no, d.noz_id, s.name station_name, d.valid_time FROM air_device d "
                + "LEFT JOIN stations s ON s.station_id = d.station_id WHERE d.is_run = true AND d.station_id IN (:s) "
                + "ORDER BY d.station_id, d.noz_id", Map.of("s", scope.scopedStations(user, regionId, cityId, stationId)));
    }

    public record ValidTimeForm(String validTime) {
    }

    /** Changement manuel de la date de validité, réservé au super administrateur (JoyFillRenew/ModifyData). */
    @PutMapping("/{id}/valid-time")
    @Transactional
    public void setValidTime(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody ValidTimeForm f) {
        if (!user.isSystemManager()) {
            throw new ApiException("Droits insuffisants");
        }
        AirDevice d = devices.findById(id).orElseThrow(() -> new ApiException("Appareil introuvable"));
        d.setValidTime(f.validTime());
        devices.save(d);
    }

    /**
     * Lien d'activation par paiement WeChat à encoder dans le QR code (DeviceActive côté vue) :
     * autorisation OAuth WeChat redirigeant vers la page mobile d'activation.
     */
    @GetMapping("/{id}/activation-link")
    public Map<String, String> activationLink(@CurrentUser SessionUser user, @PathVariable int id) {
        if (wechatAppId.isBlank() || publicUrl.isBlank()) {
            throw new ApiException("Paiement WeChat non configuré (app.wechat.app-id / app.public-url)");
        }
        String target = publicUrl.replaceAll("/+$", "") + "/App/GetActiveDevice?id=" + id + "&LoginName="
                + java.net.URLEncoder.encode(user.getLoginName(), java.nio.charset.StandardCharsets.UTF_8);
        String link = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=" + wechatAppId + "&redirect_uri="
                + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code&scope=snsapi_userinfo&state=1#wechat_redirect";
        return Map.of("link", link);
    }

    // ------------------------------------------------------------ Utilitaires

    private void fill(AirDevice d, DeviceForm f, GradeSpecifics grade) {
        d.setStationId(f.stationId());
        d.setStationName(stations.findById(f.stationId()).map(s -> s.getName()).orElse(""));
        d.setOilId(grade.getGradeSpecificId());
        d.setOilName(grade.getName());
        d.setNozId(nz(f.nozId()));
        d.setAmountDigits(nz(f.amountDigits()));
        d.setVolumeDigits(nz(f.volumeDigits()));
        d.setPriceDigits(nz(f.priceDigits()));
        d.setDeviceNo(f.deviceNo());
        d.setTankNo(String.valueOf(f.tankId()));
        d.setDigitsType(switch (f.deviceType() == null ? 1 : f.deviceType()) {
            case 2 -> "886";
            case 3 -> "3";
            default -> "664";
        });
    }

    private GradeSpecifics gradeOfTank(Integer tankId) {
        AirTank tank = tankId == null ? null : tanks.findById(tankId).orElse(null);
        if (tank == null) {
            throw new ApiException("Les informations de la cuve n'existent pas");
        }
        return grades.findById(tank.getGradeId()).orElseThrow(() -> new ApiException("L'énergie actuelle n'existe pas"));
    }

    private void checkStation(SessionUser user, Integer stationId) {
        if (stationId == null || !scope.stationIds(user).contains(stationId)) {
            throw new ApiException("Cette station ne fait pas partie de votre périmètre");
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
