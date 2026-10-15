package com.ltstation.center.service;

import com.ltstation.center.domain.entity.LoginCompanyGroup;
import com.ltstation.center.domain.repository.LoginCompanyGroupRepository;
import com.ltstation.center.domain.repository.StationsRepository;
import com.ltstation.center.security.SessionUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Périmètre de données de l'utilisateur : son nœud de société et tous les
 * nœuds descendants (GetNodeList / GetChilrenID d'origine).
 */
@Service
public class CompanyScopeService {

    private final LoginCompanyGroupRepository groups;
    private final StationsRepository stations;

    public CompanyScopeService(LoginCompanyGroupRepository groups, StationsRepository stations) {
        this.groups = groups;
        this.stations = stations;
    }

    /** Nœud de l'utilisateur + tous ses descendants. */
    public List<Integer> nodeList(SessionUser user) {
        return nodeList(user.companyGroupId());
    }

    public List<Integer> nodeList(int rootId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(rootId);
        collectChildren(rootId, ids);
        return ids;
    }

    /** Ajoute récursivement les identifiants des sous-nœuds de {@code parentId}. */
    public void collectChildren(int parentId, List<Integer> into) {
        for (LoginCompanyGroup child : groups.findByGroupId(parentId)) {
            if (into.contains(child.getId())) {
                continue; // protège contre une boucle dans les données
            }
            into.add(child.getId());
            collectChildren(child.getId(), into);
        }
    }

    /** Stations appartenant au périmètre de l'utilisateur. */
    public List<Integer> stationIds(SessionUser user) {
        return stations.findStationIdsByCompanyIdIn(nodeList(user));
    }

    public List<Integer> stationIds(int rootId) {
        return stations.findStationIdsByCompanyIdIn(nodeList(rootId));
    }

    /**
     * Portée « employé » (GetCookieEmp d'origine) : un employé d'une station de
     * type 1 ne voit que sa station ; les autres (centre, super administrateur)
     * voient tout leur périmètre de société.
     * Dans l'original, les comptes sans employé (groupe, GPRS) provoquaient une
     * erreur silencieuse et des listes vides : ils sont ici traités comme « centre ».
     */
    public record EmpScope(boolean center, Integer stationId, Integer cityId) {
    }

    public EmpScope empScope(SessionUser user) {
        if (user.getEmpId() <= 0 || user.getStationId() == null) {
            return new EmpScope(true, null, null);
        }
        return stations.findById(user.getStationId())
                .map(s -> new EmpScope(!java.util.Objects.equals(s.getStationTypeId(), 1), s.getStationId(), s.getCityId()))
                .orElse(new EmpScope(false, user.getStationId(), null));
    }

    /**
     * Stations filtrées par région / ville / station sélectionnées (DealStationSQL d'origine),
     * toujours restreintes au périmètre de l'utilisateur. Renvoie null si aucun filtre n'est choisi.
     */
    public List<Integer> filterStations(SessionUser user, Integer regionId, Integer cityId, Integer stationId) {
        EmpScope emp = empScope(user);
        if (!emp.center()) {
            return List.of(emp.stationId());
        }
        List<Integer> allowed = stationIds(user);
        if (stationId != null) {
            return allowed.contains(stationId) ? List.of(stationId) : List.of();
        }
        if (cityId != null) {
            return stations.findByCityId(cityId).stream().map(s -> s.getStationId()).filter(allowed::contains).toList();
        }
        if (regionId != null) {
            return stations.findStationIdsByRegionId(regionId).stream().filter(allowed::contains).toList();
        }
        return null;
    }

    /** Comme {@link #filterStations} mais sans filtre = tout le périmètre. */
    public List<Integer> scopedStations(SessionUser user, Integer regionId, Integer cityId, Integer stationId) {
        List<Integer> f = filterStations(user, regionId, cityId, stationId);
        return f != null ? f : stationIds(user);
    }

    /** Nom affiché dans l'en-tête (TitleShow) : nom du groupe, ou "BL" par défaut. */
    public String title(SessionUser user) {
        return groups.findById(user.companyGroupId()).map(LoginCompanyGroup::getGroupName).orElse("BL");
    }
}
