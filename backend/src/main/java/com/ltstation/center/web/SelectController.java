package com.ltstation.center.web;

import com.ltstation.center.common.ApiException;
import com.ltstation.center.common.Db;
import com.ltstation.center.common.SelectItem;
import com.ltstation.center.security.CurrentUser;
import com.ltstation.center.security.SessionUser;
import com.ltstation.center.service.CompanyScopeService;
import com.ltstation.center.service.CompanyScopeService.EmpScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listes déroulantes de toute l'application (portage complet de Home/GetICSelect).
 * Appel : GET /api/select/{type}?param=valeur
 */
@RestController
@RequestMapping("/api/select")
public class SelectController {

    private final Db db;
    private final CompanyScopeService scope;

    public SelectController(Db db, CompanyScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    @GetMapping("/{type}")
    public List<SelectItem> select(@CurrentUser SessionUser user, @PathVariable String type,
                                   @RequestParam Map<String, String> p) {
        try {
            return build(user, type, p);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            // L'original renvoyait une liste vide en cas d'erreur
            return List.of();
        }
    }

    private List<SelectItem> build(SessionUser user, String type, Map<String, String> p) {
        List<Integer> nodes = scope.nodeList(user);
        Map<String, Object> q = new HashMap<>();
        q.put("nodes", nodes);
        return switch (type) {
            case "Region" -> items("SELECT region_id id, region_desc text FROM regions WHERE company_id IN (:nodes) ORDER BY region_id", q);
            case "CityChange" -> {
                int regionId = intParam(p, "regionId", 0);
                if (regionId == 0) {
                    Map<String, Object> first = db.one("SELECT region_id FROM regions ORDER BY region_id LIMIT 1", Map.of());
                    if (first == null) {
                        yield List.of();
                    }
                    regionId = ((Number) first.get("regionId")).intValue();
                }
                yield items("SELECT city_id id, city_desc text FROM cities WHERE region_id = :r", Map.of("r", regionId));
            }
            case "StationChange" -> items("SELECT station_id id, name text FROM stations WHERE city_id = :c",
                    Map.of("c", intParam(p, "cityId", 0)));
            case "GradeID" -> items("SELECT grade_specific_id id, name text FROM grade_specifics", Map.of());
            case "OilType" -> items("SELECT CAST(code AS integer) id, name text FROM grade_specifics WHERE company_id = :c "
                    + "AND code ~ '^[0-9]+$'", Map.of("c", user.companyGroupId()));
            case "CertificateType" -> fixed("Carte d'identité", "Permis de conduire", "Carte militaire", "Passeport",
                    "Carte professionnelle", "Autre");
            case "CarColor" -> fixed("Bleu", "Jaune", "Jaune-vert", "Vert", "Noir", "Inconnu", "Compresseur");
            case "CarType" -> fixed("Taxi", "Autocar", "Minibus", "Poids lourd", "Voiture particulière",
                    "Véhicule de société", "Autre");
            case "LimitType" -> fixed("Limite par région", "Limite par ville", "Limite par station");
            case "LostCardType" -> List.of(new SelectItem(1, "Carte utilisateur"), new SelectItem(2, "Carte de gestion de groupe"),
                    new SelectItem(4, "Carte employé"), new SelectItem(11, "Carte de crédit utilisateur"),
                    new SelectItem(12, "Carte de crédit de groupe"), new SelectItem(13, "Carte secondaire"),
                    new SelectItem(14, "Carte principale"));
            case "LimitTypeValue" -> switch (intParam(p, "limitType", 0)) {
                case 1 -> items("SELECT region_id id, region_desc text FROM regions WHERE company_id IN (:nodes)", q);
                case 2 -> items("SELECT city_id id, city_desc text FROM cities WHERE company_id IN (:nodes)", q);
                case 3 -> items("SELECT station_id id, name text FROM stations WHERE company_id IN (:nodes)", q);
                default -> List.of();
            };
            case "CardDisCount" -> {
                List<SelectItem> l = new ArrayList<>();
                l.add(new SelectItem(0, "100"));
                l.addAll(items("SELECT id, CAST(dis_count_value AS text) text FROM dis_count WHERE dis_count_value <> 100", Map.of()));
                yield l;
            }
            case "Station" -> {
                EmpScope emp = scope.empScope(user);
                if (emp.center()) {
                    yield items("SELECT station_id id, name text FROM stations WHERE is_active = 0 AND company_id IN (:nodes)", q);
                }
                yield items("SELECT station_id id, name text FROM stations WHERE station_id = :s", Map.of("s", emp.stationId()));
            }
            case "EmpSelect" -> items("SELECT employee_id id, name text FROM employees WHERE station_id = :s",
                    Map.of("s", intParam(p, "stationId", 0)));
            case "CompanyCardType" -> fixed("Carte de gestion de groupe", "Carte de crédit de groupe", "Carte principale de groupe");
            case "CardGroup" -> {
                String cardType = p.getOrDefault("cardType", "Person");
                String prefix = switch (cardType) {
                    case "Credit" -> "010012";
                    case "Child" -> "010014";
                    default -> "010002";
                };
                List<SelectItem> l = new ArrayList<>();
                if (prefix.equals("010002")) {
                    l.add(new SelectItem(100000001, "Utilisateur individuel"));
                }
                l.addAll(items("SELECT g.group_id id, g.name text FROM group_card_infos c JOIN groups g ON g.group_id = c.group_id "
                        + "WHERE substring(c.asn, 1, 6) = :p", Map.of("p", prefix)));
                yield l;
            }
            case "PayMentType" -> fixed("Espèces", "Paiement en ligne");
            case "CustomerName" -> numbered(db.jdbc().queryForList(
                    "SELECT DISTINCT cu.name FROM customers cu WHERE cu.customer_id IN (SELECT ci.customer_id FROM customer_card_infos ci "
                            + "WHERE ci.station_id IN (SELECT station_id FROM stations WHERE company_id IN (:nodes)))", q, String.class));
            case "CardNumSearch" -> items("SELECT cu.customer_id id, cu.vehicle_no text FROM customers cu WHERE trim(cu.vehicle_no) <> '' "
                    + "AND cu.customer_id IN (SELECT ci.customer_id FROM customer_card_infos ci WHERE ci.station_id IN "
                    + "(SELECT station_id FROM stations WHERE company_id IN (:nodes)))", q);
            case "StationTank" -> {
                int s = intParam(p, "stationId", 0);
                if (db.one("SELECT 1 FROM stations WHERE station_id = :s", Map.of("s", s)) == null) {
                    throw new ApiException("Station inexistante");
                }
                yield items("SELECT id, tank_name text FROM air_tank WHERE station_id = :s ORDER BY id", Map.of("s", s));
            }
            case "SingleBillReportClass" -> fixed("Utilisateur", "Employé");
            case "ScoreCus" -> {
                String col;
                String val;
                if (!p.getOrDefault("personName", "").isEmpty()) {
                    col = "name";
                    val = p.get("personName");
                } else if (!p.getOrDefault("phone", "").isEmpty()) {
                    col = "telephone";
                    val = p.get("phone");
                } else if (!p.getOrDefault("carNo", "").isEmpty()) {
                    col = "vehicle_no";
                    val = p.get("carNo");
                } else {
                    yield List.of();
                }
                yield numbered(db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers cu ON cu.customer_id = ci.customer_id "
                        + "WHERE cu." + col + " = :v AND ci.card_status_type_id = 0", Map.of("v", val), String.class));
            }
            case "PriceInfo" -> items("SELECT p.grade_specific_id id, g.name text FROM price_infos p JOIN grade_specifics g "
                    + "ON g.grade_specific_id = p.grade_specific_id WHERE p.station_id = :s", Map.of("s", intParam(p, "stationId", 0)));
            case "Digits" -> List.of(new SelectItem(0, "0"), new SelectItem(1, "1"), new SelectItem(2, "2"), new SelectItem(3, "3"));
            case "Auth" -> items("SELECT id, name text FROM authority_mess WHERE new_company_id = :c AND is_new = 2",
                    Map.of("c", parseInt(user.getCompanyId())));
            case "AuthCurr" -> items("SELECT id, name text FROM authority_mess WHERE new_company_id = :c ORDER BY user_id_mess",
                    Map.of("c", parseInt(user.getCompanyId())));
            case "RegionAuth" -> {
                EmpScope emp = scope.empScope(user);
                if (emp.center()) {
                    yield items("SELECT region_id id, region_desc text FROM regions WHERE company_id IN (:nodes) ORDER BY region_id", q);
                }
                yield items("SELECT r.region_id id, r.region_desc text FROM stations s JOIN cities c ON c.city_id = s.city_id "
                        + "JOIN regions r ON r.region_id = c.region_id WHERE s.station_id = :s", Map.of("s", emp.stationId()));
            }
            case "CityAuth" -> {
                EmpScope emp = scope.empScope(user);
                if (emp.center()) {
                    yield items("SELECT city_id id, city_desc text FROM cities WHERE region_id = :r",
                            Map.of("r", intParam(p, "regionId", 0)));
                }
                yield items("SELECT c.city_id id, c.city_desc text FROM stations s JOIN cities c ON c.city_id = s.city_id "
                        + "WHERE s.station_id = :s", Map.of("s", emp.stationId()));
            }
            case "StationAuth" -> {
                EmpScope emp = scope.empScope(user);
                if (emp.center()) {
                    yield items("SELECT station_id id, name text FROM stations WHERE is_active = 0 AND city_id = :c",
                            Map.of("c", intParam(p, "cityId", 0)));
                }
                yield items("SELECT station_id id, name text FROM stations WHERE station_id = :s", Map.of("s", emp.stationId()));
            }
            case "TankSelect" -> items("SELECT id, tank_name text FROM air_tank WHERE station_id = :s AND is_run = true",
                    Map.of("s", intParam(p, "stationId", 0)));
            case "ScoreType" -> List.of(new SelectItem(0, "Consommation"), new SelectItem(1, "Recharge"));
            case "CusType" -> List.of(new SelectItem(0, "Utilisateur"), new SelectItem(1, "Employé"));
            case "Company" -> items("SELECT id, group_name text FROM login_company_group WHERE group_id = :g",
                    Map.of("g", user.companyGroupId()));
            case "DeviceSelect" -> items("SELECT id, device_no text FROM air_device WHERE station_id = :s AND is_run = true",
                    Map.of("s", intParam(p, "stationId", 0)));
            case "OrderAsn" -> numbered(db.jdbc().queryForList("SELECT ci.asn FROM customer_card_infos ci JOIN customers cu "
                    + "ON cu.customer_id = ci.customer_id WHERE ci.card_status_type_id = 0 AND (cu.name = :u OR cu.certificate_no = :c)",
                    Map.of("u", p.getOrDefault("user", ""), "c", p.getOrDefault("certificateNo", "")), String.class));
            case "TankVolume" -> items("SELECT DISTINCT tank_volume_id id, tank_volume_name text FROM tank_volume ORDER BY tank_volume_id", Map.of());
            case "DeviceNo" -> {
                List<String> devices = db.jdbc().queryForList("SELECT device_no FROM box_set WHERE station_id = :s ORDER BY id",
                        Map.of("s", intParam(p, "stationId", 0)), String.class);
                List<SelectItem> l = new ArrayList<>();
                for (int i = 0; i < devices.size(); i++) {
                    l.add(new SelectItem(i, devices.get(i)));
                }
                yield l;
            }
            case "TCPStation" -> items("SELECT station_id id, name text FROM stations WHERE company_id = :c AND email3 <> ''",
                    Map.of("c", user.companyGroupId()));
            // --- Listes de Home/GetTCPSelect ---
            case "ICStation" -> intParam(p, "cityId", 0) != 0
                    ? items("SELECT station_id id, name text FROM stations WHERE city_id = :c ORDER BY station_id",
                    Map.of("c", intParam(p, "cityId", 0)))
                    : items("SELECT station_id id, name text FROM stations WHERE company_id IN (:nodes) ORDER BY station_id", q);
            case "TCPAllStation" -> items("SELECT station_id id, name text FROM stations WHERE company_id = :c",
                    Map.of("c", user.companyGroupId()));
            case "TCPGrade" -> items("SELECT grade_specific_id id, name text FROM grade_specifics WHERE company_id = :c "
                    + "ORDER BY grade_specific_id", Map.of("c", user.companyGroupId()));
            case "StationGrade" -> items("SELECT p.grade_specific_id id, g.name text FROM price_infos p LEFT JOIN grade_specifics g "
                    + "ON g.grade_specific_id = p.grade_specific_id WHERE p.station_id = :s", Map.of("s", intParam(p, "stationId", 0)));
            case "GetTCPTank" -> items("SELECT id, tank_name text FROM air_tank WHERE station_id = :s",
                    Map.of("s", intParam(p, "stationId", 0)));
            case "TransactionType" -> {
                List<SelectItem> l = new ArrayList<>();
                for (int i = 1; i < com.ltstation.center.service.Labels.TRANSACTION_TYPES.length - 1; i++) {
                    l.add(new SelectItem(i, com.ltstation.center.service.Labels.TRANSACTION_TYPES[i]));
                }
                yield l;
            }
            case "CardStatus" -> {
                List<SelectItem> l = new ArrayList<>();
                for (int i = 0; i < com.ltstation.center.service.Labels.CARD_STATUS.length; i++) {
                    l.add(new SelectItem(i, com.ltstation.center.service.Labels.CARD_STATUS[i]));
                }
                yield l;
            }
            default -> throw new ApiException("Type de liste inconnu : " + type);
        };
    }

    private List<SelectItem> items(String sql, Map<String, ?> params) {
        return db.list(sql, params).stream().map(r -> new SelectItem(r.get("id"), String.valueOf(r.get("text")))).toList();
    }

    /** Liste à identifiants 1..n (comme l'original) */
    private static List<SelectItem> fixed(String... labels) {
        List<SelectItem> l = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            l.add(new SelectItem(i + 1, labels[i]));
        }
        return l;
    }

    private static List<SelectItem> numbered(List<String> texts) {
        List<SelectItem> l = new ArrayList<>();
        int i = 1;
        for (String t : texts) {
            l.add(new SelectItem(i++, t));
        }
        return l;
    }

    private static int intParam(Map<String, String> p, String key, int def) {
        String v = p.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        return Integer.parseInt(v.trim());
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
