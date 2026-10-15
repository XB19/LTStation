package com.ltstation.center.security;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Utilisateur connecté, stocké en session HTTP.
 * Reprend les champs du cookie "UserInfo" de la plateforme d'origine.
 */
@Getter
@Setter
public class SessionUser implements Serializable {

    public static final String SESSION_KEY = "LT_USER";

    /** "1" = employé/administrateur, "2" = compte groupe, "99" = client GPRS. */
    private String loginType;
    private int userId;
    private int roleId;
    private String loginName;
    /** Identifiant du nœud LoginCompanyGroup (vide = racine "Gestion de la plateforme"). */
    private String companyGroup = "";
    private String companyId;
    private int empId;
    private Integer stationId;
    private String stationName;
    private String employeesName;
    private String telephone;
    /** Droits du menu PC (identifiants séparés par des virgules). */
    private String auth = "";
    /** Droits du menu mobile / compte public WeChat. */
    private String gzhAuth = "";
    private boolean center;
    /** "1" pour un compte client GPRS. */
    private String gprs = "0";

    public Set<String> authSet() {
        Set<String> set = new LinkedHashSet<>();
        if (auth != null) {
            Arrays.stream(auth.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        }
        return set;
    }

    public boolean hasMenu(String id) {
        return authSet().contains(id);
    }

    /** Identifiant numérique du groupe de société (0 si vide), comme GetNodeList. */
    public int companyGroupId() {
        if (companyGroup == null || companyGroup.isBlank()) {
            return 0;
        }
        return Integer.parseInt(companyGroup.trim());
    }

    public boolean isSystemManager() {
        return userId == -1;
    }
}
