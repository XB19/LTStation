package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Dto;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Fonctions communes de la classe DealServer (ServerDeal.dll de Center d'origine) utilisées
 * par l'API des stations.
 */
@Component
public class CenterDeal {

    private final Sugar db;
    private final Dto dto = new Dto("center");

    public CenterDeal(@Qualifier("centerSugar") Sugar db) {
        this.db = db;
    }

    public Sugar db() {
        return db;
    }

    public Dto dto() {
        return dto;
    }

    /** Carte utilisateur / crédit / enfant avec son client et son compte. */
    public record CusCard(boolean ok, Row customers, Row cardInfos, Row accounts, String error) {
    }

    /** Carte employé avec son employé et son compte. */
    public record EmpCard(boolean ok, Row employees, Row cardInfos, Row accounts, String error) {
    }

    /** Carte de groupe avec son groupe et son compte. */
    public record GroupCard(boolean ok, Row groups, Row cardInfos, Row accounts, String error) {
    }

    public static long convertDateTimeToInt(LocalDateTime dt) {
        return Conv.toMillis(dt);
    }

    public static long now() {
        return Conv.toMillis(LocalDateTime.now());
    }

    public static LocalDateTime timeStampToDateTime(long ts) {
        return Conv.fromMillis(ts);
    }

    public CusCard getCusCardInfo(String asn) {
        try {
            Row info = db.first("CustomerCardInfos", "{ASN} = :a", Map.of("a", asn));
            if (info == null) {
                throw new IllegalStateException("Informations utilisateur introuvables");
            }
            Row cus = db.first("Customers", "{CustomerID} = :id", Map.of("id", info.i("CustomerID")));
            if (cus == null) {
                throw new IllegalStateException("Utilisateur introuvable");
            }
            Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", Map.of("id", info.i("CustomerAccountID")));
            if (acc == null) {
                throw new IllegalStateException("Compte utilisateur introuvable");
            }
            return new CusCard(true, cus, info, acc, "");
        } catch (RuntimeException ex) {
            return new CusCard(false, new Row(), new Row(), new Row(), ex.getMessage());
        }
    }

    public CusCard getCusCardInfoByNPValue(String np) {
        try {
            Row cus = db.first("Customers", "{NP_Value} = :v", Map.of("v", np));
            Row info = db.first("CustomerCardInfos", "{NP_Value} = :v", Map.of("v", np));
            Row acc = db.first("CustomerAccounts", "{NP_Value} = :v", Map.of("v", np));
            if (cus == null || info == null || acc == null) {
                throw new IllegalStateException("Compte erroné");
            }
            return new CusCard(true, cus, info, acc, "");
        } catch (RuntimeException ex) {
            return new CusCard(false, null, null, null, ex.getMessage());
        }
    }

    public EmpCard getEmpCardInfo(String asn) {
        try {
            Row info = db.first("EmployeeCardInfos", "{ASN} = :a", Map.of("a", asn));
            if (info == null) {
                throw new IllegalStateException("Informations utilisateur introuvables");
            }
            Row emp = db.first("Employees", "{EmployeeID} = :id", Map.of("id", info.i("EmployeeID")));
            if (emp == null) {
                throw new IllegalStateException("Utilisateur introuvable");
            }
            Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", Map.of("id", info.i("CustomerAccountID")));
            if (acc == null) {
                throw new IllegalStateException("Compte utilisateur introuvable");
            }
            return new EmpCard(true, emp, info, acc, "");
        } catch (RuntimeException ex) {
            return new EmpCard(false, new Row(), new Row(), new Row(), ex.getMessage());
        }
    }

    public GroupCard getGroupCardInfo(String asn) {
        try {
            Row info = db.first("GroupCardInfos", "{ASN} = :a", Map.of("a", asn));
            if (info == null) {
                throw new IllegalStateException("Informations du groupe introuvables");
            }
            Row g = db.first("Groups", "{GroupID} = :id", Map.of("id", info.i("GroupID")));
            if (g == null) {
                throw new IllegalStateException("Groupe introuvable");
            }
            Row acc = db.first("GroupAccounts", "{GroupAccountID} = :id", Map.of("id", info.i("GroupAccountID")));
            if (acc == null) {
                throw new IllegalStateException("Compte du groupe introuvable");
            }
            return new GroupCard(true, g, info, acc, "");
        } catch (RuntimeException ex) {
            return new GroupCard(false, new Row(), new Row(), new Row(), ex.getMessage());
        }
    }

    public String getICName(String asn) {
        switch (asn.substring(0, 6)) {
            case "010001", "010011", "010013": {
                CusCard c = getCusCardInfo(asn);
                return c.ok() ? c.customers().s("Name") : "";
            }
            case "010004": {
                EmpCard e = getEmpCardInfo(asn);
                return e.ok() ? e.employees().s("Name") : "";
            }
            case "010002", "010012", "010014": {
                GroupCard g = getGroupCardInfo(asn);
                return g.ok() ? g.groups().s("Name") : "";
            }
            default:
                return "";
        }
    }

    public String getCardUserName(String asn) {
        try {
            switch (asn.substring(0, 6)) {
                case "FFFFFF", "010001", "010011", "010013": {
                    CusCard c = getCusCardInfo(asn);
                    if (c.ok()) {
                        return c.customers().s("Name");
                    }
                    throw new IllegalStateException(c.error());
                }
                case "010004": {
                    EmpCard e = getEmpCardInfo(asn);
                    if (e.ok()) {
                        return e.employees().s("Name");
                    }
                    throw new IllegalStateException(e.error());
                }
                case "010002", "010012", "010014": {
                    GroupCard g = getGroupCardInfo(asn);
                    if (g.ok()) {
                        return g.groups().s("Name");
                    }
                    throw new IllegalStateException(g.error());
                }
                default:
                    return "";
            }
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public String getStationName(int stationId) {
        Row s = db.first("Stations", "{StationID} = :id", Map.of("id", stationId));
        return s == null ? "" : s.s("Name");
    }

    public String getGraName(int id) {
        Row g = db.first("GradeSpecifics", "{GradeSpecificID} = :id", Map.of("id", id));
        return g == null ? "" : g.s("Name");
    }

    public String getGroupName(int groupId) {
        try {
            Row g = db.first("Groups", "{GroupID} = :id", Map.of("id", groupId));
            return g == null ? "" : g.s("Name");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public String getEmployeeName(int id) {
        Row e = db.first("Employees", "{EmployeeID} = :id", Map.of("id", id));
        return e == null ? "" : e.s("Name");
    }

    public int getGradeID(String code) {
        try {
            Row g = db.first("GradeSpecifics", "{Code} = :c", Map.of("c", code));
            return g == null ? 0 : g.i("GradeSpecificID");
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Libellés des types d'opérations du journal des comptes (GetTransactionType). */
    public static String getTransactionType(int id) {
        return switch (id) {
            case 1 -> "Ouverture";
            case 2 -> "Recharge";
            case 3 -> "Perte";
            case 4 -> "Perte du code PIN";
            case 5 -> "Déblocage en ligne";
            case 6 -> "Modification des informations";
            case 7 -> "Ouverture (remplacement)";
            case 8 -> "Recharge (remplacement)";
            case 9 -> "Clôture";
            case 10 -> "Virement";
            case 11 -> "Consommation";
            case 12 -> "Annulation (contrepassation)";
            case 13 -> "Consommation hors carburant";
            case 14 -> "Remboursement";
            case 15 -> "Transfert depuis une carte chauffeur clôturée";
            case 16 -> "Virement de la carte de gestion";
            case 17 -> "Transfert (remplacement)";
            default -> "";
        };
    }

    public static String setStrLength(String str, int len) {
        StringBuilder s = new StringBuilder(str == null ? "" : str);
        while (s.length() < len) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0 ? b : a;
    }

    /** Type de carte (CardTypeID) déduit du préfixe de l'ASN, comme AddAccountLog. */
    public static int cardTypeOf(String asn) {
        if (asn == null || asn.length() <= 6) {
            return 16;
        }
        return switch (asn.substring(0, 6)) {
            case "010001" -> 1;
            case "010002" -> 2;
            case "010011" -> 11;
            case "010012" -> 12;
            case "010013" -> 13;
            case "010014" -> 14;
            case "110003" -> 15;
            case "110001" -> 17;
            default -> 4;
        };
    }

    /**
     * AddAccountLog : écrit une ligne du journal des comptes et renvoie son identifiant
     * (0 en cas d'erreur, comme l'original).
     */
    public long addAccountLog(String accountNo, double amount, String asn, double balance, int cardServiceLogId,
                              BigDecimal discountAmount, double feeAmount, Row emp, int paymentId, String transDatetime,
                              int stationId) {
        try {
            if (emp == null) {
                emp = db.first("Employees");
            }
            if (stationId == 0) {
                stationId = emp.i("StationID");
            }
            Row log = Row.of(
                "AccountDatetime", LocalDateTime.now().format(Conv.STD),
                "AccountNo", accountNo,
                "Amount", Conv.round(amount, 3),
                "ASN", asn,
                "Balance", Conv.round(balance, 3),
                "CardServiceLogID", cardServiceLogId,
                "CardTypeID", cardTypeOf(asn),
                "ChequeNo", "",
                "CreditCountID", "",
                "DiscountAmount", Conv.round(discountAmount, 3),
                "EmployeeID", emp.i("EmployeeID"),
                "FeeAmount", Conv.round(feeAmount, 3),
                "OperatorName", emp.s("Name"),
                "StationID", stationId,
                "TerminalNo", "",
                "TransactionTypeID", cardServiceLogId,
                "TransDatetime", transDatetime,
                "PaymentID", paymentId,
                "ProcessCode", "");
            Object id = db.insert("AccountServiceLogs", log);
            return id == null ? 0 : Conv.toLong(id);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** CreateAppCode : code court à 5 chiffres (1 à 8) non utilisé. */
    public String createAppCode() {
        Random random = new Random();
        for (int tries = 0; tries < 1000; tries++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                code.append(1 + random.nextInt(8));
            }
            Row used = db.first("AppCodeUse", "({AppCodeShort} = :c AND {IsUsing} = 0) OR {IsUsing} = 1", Map.of("c", code.toString()));
            if (used == null) {
                return code.toString();
            }
        }
        return "";
    }
}
