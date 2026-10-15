package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.domain.entity.Cities;
import com.ltstation.center.domain.entity.GradeSpecifics;
import com.ltstation.center.domain.entity.Regions;
import com.ltstation.center.domain.entity.Stations;
import com.ltstation.center.domain.repository.CitiesRepository;
import com.ltstation.center.domain.repository.GradeSpecificsRepository;
import com.ltstation.center.domain.repository.RegionsRepository;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Configuration des stations : régions, villes, stations et énergies
 * (Main/StationSeting d'origine).
 */
@RestController
@RequestMapping("/api/station-setting")
public class StationSettingController {

    private final RegionsRepository regions;
    private final CitiesRepository cities;
    private final StationsRepository stations;
    private final GradeSpecificsRepository grades;
    private final CompanyScopeService scope;
    private final Db db;
    private final boolean leaseMode;

    public StationSettingController(RegionsRepository regions, CitiesRepository cities, StationsRepository stations,
                                    GradeSpecificsRepository grades, CompanyScopeService scope, Db db,
                                    @Value("${app.lease-mode:true}") boolean leaseMode) {
        this.regions = regions;
        this.cities = cities;
        this.stations = stations;
        this.grades = grades;
        this.scope = scope;
        this.db = db;
        this.leaseMode = leaseMode;
    }

    // ------------------------------------------------------------------ Régions

    @GetMapping("/regions")
    public List<Regions> regionList(@CurrentUser SessionUser user) {
        return regions.findByCompanyIdInOrderByRegionId(scope.nodeList(user));
    }

    /** Prochain code interne de région (GetRegionMax) : dernier + 1 sur 2 chiffres. */
    @GetMapping("/regions/next-code")
    public Map<String, String> regionNextCode() {
        String next = regions.findFirstByOrderByRegionIdDesc()
                .map(r -> pad(parse(r.getCodeInner()) + 1, 2)).orElse("01");
        return Map.of("codeInner", next);
    }

    public record RegionForm(String code, String codeInner, String regionDesc, Integer companyId) {
    }

    @PostMapping("/regions")
    @Transactional
    public Regions addRegion(@CurrentUser SessionUser user, @RequestBody RegionForm f) {
        // Sans compte choisi : nœud de l'utilisateur (racine 0 pour le super administrateur)
        Integer companyId = f.companyId() != null ? f.companyId() : user.companyGroupId();
        requireInScope(user, companyId);
        Regions r = new Regions();
        r.setCode(req(f.code(), "Code"));
        r.setCodeInner(req(f.codeInner(), "Code interne"));
        r.setRegionDesc(req(f.regionDesc(), "Nom de la région"));
        r.setCompanyId(companyId);
        if (regions.findFirstByCodeOrRegionDesc(r.getCode(), r.getRegionDesc()).isPresent()) {
            throw new ApiException("Informations de région en double");
        }
        return regions.save(r);
    }

    @PutMapping("/regions/{id}")
    @Transactional
    public Regions updateRegion(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody RegionForm f) {
        Regions r = regions.findById(id).orElseThrow(() -> new ApiException("La région n'existe pas"));
        requireInScope(user, r.getCompanyId());
        r.setCode(req(f.code(), "Code"));
        r.setCodeInner(req(f.codeInner(), "Code interne"));
        r.setRegionDesc(req(f.regionDesc(), "Nom de la région"));
        if (f.companyId() != null) {
            requireInScope(user, f.companyId());
            r.setCompanyId(f.companyId());
        }
        boolean dup = db.one("SELECT 1 FROM regions WHERE region_id <> :id AND (code = :c OR code_inner = :ci OR region_desc = :d)",
                Map.of("id", id, "c", r.getCode(), "ci", r.getCodeInner(), "d", r.getRegionDesc())) != null;
        if (dup) {
            throw new ApiException("Informations de région en double");
        }
        return regions.save(r);
    }

    @DeleteMapping("/regions/{id}")
    @Transactional
    public void deleteRegion(@CurrentUser SessionUser user, @PathVariable int id) {
        Regions r = regions.findById(id).orElseThrow(() -> new ApiException("La région n'existe pas"));
        requireInScope(user, r.getCompanyId());
        if (cities.existsByRegionId(id)) {
            throw new ApiException("La région est utilisée, impossible de la supprimer");
        }
        regions.delete(r);
    }

    // ------------------------------------------------------------------ Villes

    @GetMapping("/cities")
    public List<Cities> cityList(@CurrentUser SessionUser user) {
        return cities.findByCompanyIdInOrderByCityId(scope.nodeList(user));
    }

    /**
     * Codes proposés pour une ville (GetCityMax) : si la ville appartient déjà à la région,
     * ses codes actuels ; sinon dernier code interne + 1 (4 chiffres), ou code de la région + "01".
     */
    @GetMapping("/cities/next-code")
    public Map<String, String> cityNextCode(@RequestParam int regionId, @RequestParam(required = false) Integer cityId) {
        Regions r = regions.findById(regionId).orElseThrow(() -> new ApiException("La région n'existe pas"));
        if (cityId != null) {
            Cities c = cities.findById(cityId).orElse(null);
            if (c != null && Objects.equals(c.getRegionId(), regionId)) {
                return Map.of("codeInner", c.getCodeInner(), "code", c.getCode());
            }
        }
        return cities.findFirstByRegionIdOrderByCodeInnerDesc(regionId)
                .map(c -> Map.of("codeInner", pad(parse(c.getCodeInner()) + 1, 4), "code", Objects.toString(c.getCode(), "")))
                .orElse(Map.of("codeInner", r.getCodeInner() + "01", "code", r.getCode() + "01"));
    }

    public record CityForm(Integer regionId, String code, String codeInner, String cityDesc) {
    }

    @PostMapping("/cities")
    @Transactional
    public Cities addCity(@CurrentUser SessionUser user, @RequestBody CityForm f) {
        Regions r = f.regionId() == null ? null : regions.findById(f.regionId()).orElse(null);
        if (r == null) {
            throw new ApiException("Région incorrecte");
        }
        requireInScope(user, r.getCompanyId());
        Cities c = new Cities();
        c.setRegionId(r.getRegionId());
        c.setCode(req(f.code(), "Code"));
        c.setCodeInner(req(f.codeInner(), "Code interne"));
        c.setCityDesc(req(f.cityDesc(), "Nom de la ville"));
        c.setCompanyId(r.getCompanyId());
        if (cities.findFirstByCityDescAndRegionId(c.getCityDesc(), c.getRegionId()).isPresent()) {
            throw new ApiException("Informations de ville en double");
        }
        return cities.save(c);
    }

    @PutMapping("/cities/{id}")
    @Transactional
    public Cities updateCity(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody CityForm f) {
        Cities c = cities.findById(id).orElseThrow(() -> new ApiException("La ville n'existe pas"));
        Regions r = f.regionId() == null ? null : regions.findById(f.regionId()).orElse(null);
        if (r == null) {
            throw new ApiException("La région n'existe pas");
        }
        requireInScope(user, c.getCompanyId());
        requireInScope(user, r.getCompanyId());
        c.setRegionId(r.getRegionId());
        c.setCode(req(f.code(), "Code"));
        c.setCodeInner(req(f.codeInner(), "Code interne"));
        c.setCityDesc(req(f.cityDesc(), "Nom de la ville"));
        c.setCompanyId(r.getCompanyId());
        if (cities.findFirstByCityDescAndCityIdNot(c.getCityDesc(), id).isPresent()) {
            throw new ApiException("Informations de ville en double");
        }
        return cities.save(c);
    }

    @DeleteMapping("/cities/{id}")
    @Transactional
    public void deleteCity(@CurrentUser SessionUser user, @PathVariable int id) {
        Cities c = cities.findById(id).orElseThrow(() -> new ApiException("La ville n'existe pas"));
        requireInScope(user, c.getCompanyId());
        if (stations.existsByCityId(id)) {
            throw new ApiException("La ville est utilisée, impossible de la supprimer");
        }
        cities.delete(c);
    }

    // ------------------------------------------------------------------ Stations

    /** Stations actives du périmètre (is_active = 0). */
    @GetMapping("/stations")
    public List<Map<String, Object>> stationList(@CurrentUser SessionUser user) {
        return db.list("SELECT s.*, c.region_id FROM stations s LEFT JOIN cities c ON c.city_id = s.city_id "
                + "WHERE s.company_id IN (:n) AND s.is_active = 0 ORDER BY s.station_id", Map.of("n", scope.nodeList(user)));
    }

    /** Prochain code de station (GetStationMax). */
    @GetMapping("/stations/next-code")
    public Map<String, String> stationNextCode() {
        return Map.of("code", nextStationCode());
    }

    public record StationForm(Integer cityId, Integer stationTypeId, String name, String code, String address,
                              String phone, String fax, String manager, String email, String postal,
                              BigDecimal longitude, BigDecimal latitude) {
    }

    @PostMapping("/stations")
    @Transactional
    public Stations addStation(@CurrentUser SessionUser user, @RequestBody StationForm f) {
        Cities c = f.cityId() == null ? null : cities.findById(f.cityId()).orElse(null);
        if (c == null) {
            throw new ApiException("La ville de rattachement n'existe pas");
        }
        requireInScope(user, c.getCompanyId());
        Stations s = new Stations();
        s.setCode(nextStationCode());
        fill(s, f);
        s.setCityId(c.getCityId());
        s.setCompanyId(c.getCompanyId());
        // En mode location, la station doit ensuite être activée (Activation de stations)
        s.setIsActive(leaseMode ? -1 : 0);
        boolean dup = db.one("SELECT 1 FROM stations WHERE name = :n AND is_active = 0 AND city_id = :c",
                Map.of("n", s.getName(), "c", s.getCityId())) != null;
        if (dup) {
            throw new ApiException("Informations de station en double");
        }
        return stations.save(s);
    }

    @GetMapping("/stations/{id}")
    public Stations getStation(@CurrentUser SessionUser user, @PathVariable int id) {
        Stations s = stations.findById(id).orElseThrow(() -> new ApiException("La station n'existe pas"));
        requireInScope(user, s.getCompanyId());
        return s;
    }

    @PutMapping("/stations/{id}")
    @Transactional
    public Stations updateStation(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody StationForm f) {
        Stations s = stations.findById(id).orElseThrow(() -> new ApiException("La station n'existe pas"));
        Cities c = f.cityId() == null ? null : cities.findById(f.cityId()).orElse(null);
        if (c == null) {
            throw new ApiException("La ville n'existe pas");
        }
        requireInScope(user, s.getCompanyId());
        requireInScope(user, c.getCompanyId());
        fill(s, f);
        if (f.code() != null && !f.code().isBlank()) {
            s.setCode(f.code());
        }
        s.setCityId(c.getCityId());
        s.setCompanyId(c.getCompanyId());
        boolean dup = db.one("SELECT 1 FROM stations WHERE station_id <> :id AND name = :n",
                Map.of("id", id, "n", s.getName())) != null;
        if (dup) {
            throw new ApiException("Informations de station en double");
        }
        return stations.save(s);
    }

    /** Suppression logique : is_active = 1 (comme l'original). */
    @DeleteMapping("/stations/{id}")
    @Transactional
    public void deleteStation(@CurrentUser SessionUser user, @PathVariable int id) {
        Stations s = stations.findById(id).orElseThrow(() -> new ApiException("La station n'existe pas"));
        requireInScope(user, s.getCompanyId());
        s.setIsActive(1);
        stations.save(s);
    }

    // ------------------------------------------------------------------ Énergies

    @GetMapping("/oil-types")
    public List<GradeSpecifics> oilTypeList(@CurrentUser SessionUser user) {
        return grades.findByCompanyIdOrderByGradeSpecificId(user.companyGroupId());
    }

    /** Prochain code d'énergie (GetOilTypeMax) : plus grand code + 1, ou 1001. */
    @GetMapping("/oil-types/next-code")
    public Map<String, String> oilTypeNextCode() {
        return Map.of("code", grades.findFirstByOrderByCodeDesc()
                .map(g -> String.valueOf(parse(g.getCode()) + 1)).orElse("1001"));
    }

    public record OilTypeForm(String code, String name) {
    }

    @PostMapping("/oil-types")
    @Transactional
    public GradeSpecifics addOilType(@CurrentUser SessionUser user, @RequestBody OilTypeForm f) {
        int companyId = user.companyGroupId();
        GradeSpecifics g = new GradeSpecifics();
        g.setCode(req(f.code(), "Code"));
        g.setName(req(f.name(), "Nom de l'énergie"));
        g.setCompanyId(companyId);
        if (grades.findFirstByNameAndCompanyId(g.getName(), companyId).isPresent()) {
            throw new ApiException("Informations d'énergie en double");
        }
        return grades.save(g);
    }

    @PutMapping("/oil-types/{id}")
    @Transactional
    public GradeSpecifics updateOilType(@CurrentUser SessionUser user, @PathVariable int id, @RequestBody OilTypeForm f) {
        GradeSpecifics g = grades.findById(id).orElseThrow(() -> new ApiException("L'énergie n'existe pas"));
        requireInScope(user, g.getCompanyId());
        String name = req(f.name(), "Nom de l'énergie");
        boolean dup = db.one("SELECT 1 FROM grade_specifics WHERE grade_specific_id <> :id AND name = :n AND company_id = :c",
                Map.of("id", id, "n", name, "c", user.companyGroupId())) != null;
        if (dup) {
            throw new ApiException("Informations d'énergie en double");
        }
        g.setCode(req(f.code(), "Code"));
        g.setName(name);
        return grades.save(g);
    }

    @DeleteMapping("/oil-types/{id}")
    @Transactional
    public void deleteOilType(@CurrentUser SessionUser user, @PathVariable int id) {
        GradeSpecifics g = grades.findById(id).orElseThrow(() -> new ApiException("L'énergie n'existe pas"));
        requireInScope(user, g.getCompanyId());
        if (db.one("SELECT 1 FROM price_infos WHERE grade_specific_id = :id LIMIT 1", Map.of("id", id)) != null) {
            throw new ApiException("L'énergie est utilisée, impossible de la supprimer");
        }
        grades.delete(g);
    }

    // ------------------------------------------------------------------ Utilitaires

    private String nextStationCode() {
        String max = stations.findMaxCode();
        return max == null || max.isBlank() ? "1000001" : String.valueOf(Long.parseLong(max.trim()) + 1);
    }

    private static void fill(Stations s, StationForm f) {
        s.setName(req(f.name(), "Nom de la station"));
        s.setStationTypeId(f.stationTypeId() == null ? 1 : f.stationTypeId());
        s.setAddress(f.address());
        s.setPhone(f.phone());
        s.setFax(f.fax());
        s.setManager(f.manager());
        s.setEmail1(f.email());
        s.setPostal(f.postal());
        s.setLongitude(f.longitude() == null ? BigDecimal.ZERO : f.longitude().setScale(3, RoundingMode.HALF_UP));
        s.setLatitude(f.latitude() == null ? BigDecimal.ZERO : f.latitude().setScale(3, RoundingMode.HALF_UP));
    }

    private void requireInScope(SessionUser user, Integer companyId) {
        if (companyId == null || !scope.nodeList(user).contains(companyId)) {
            throw new ApiException("Cet élément ne fait pas partie de votre périmètre");
        }
    }

    private static String req(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new ApiException("Le champ « " + field + " » est obligatoire");
        }
        return v.trim();
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String pad(int v, int len) {
        String s = String.valueOf(v);
        return s.length() >= len ? s : "0".repeat(len - s.length()) + s;
    }
}
