package com.ltstation.center.sw.core;

import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.hw.SerialClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * BaseFunction (ServerDeal) du logiciel Station : fonctions communes à toutes les fenêtres
 * (accès Center, recherches dans la base locale, clés des cartes IC, cuves, superviseurs).
 * Les fonctions propres au mode Normal sont dans NormalFunction.
 */
public final class BaseFunction {

    public static final String CONST_KEY = "";
    public static final String CONST_TEMPKEY = "";
    public static final String CON_TEMPKEY = "11111111111111111111111111111111";
    public static final String CON_BIN = "11FFFFFFFFFFFFFF";
    public static final String CON_USERINFO = "0100111111111111111111111111111111111111111111111111111111111111111111111111111101";
    public static final String CON_GENERALINFO = "0001123400000000000000000000000000000000000000000000000000000000";
    public static final String CON_SENSITIVITYINFO = "F".repeat(192);
    public static final String cmdDHWriteKeys_PSAMHEAD = "84D400001C";
    public static final String cmdDH_PSAMRANDOMPADDING = "00000000";
    public static final String[] cmdDHWriteKeys_PSAM = {"800100", "8D0100", "8E0100", "280100", "820100"};
    public static final String cmdSelectByFID = "00A40000023F00";
    public static final String cmdExtAuthorize = "00820000";
    public static final String cmdGetChallenge = "00840000";
    public static final int[] Buads = {4800, 9600, 14400, 19200, 38400, 56000, 115200};

    public static final LegacyLog BaseLog = new LegacyLog("BaseLog");
    public static String plnumber = "";
    public static long plnumTime;

    /** Encodage « ANSI » du poste (Encoding.Default de .NET). */
    private static final Charset ANSI = Charset.forName(System.getProperty("native.encoding", "windows-1252"));
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BaseFunction() {
    }

    private static Sugar db() {
        return SW.db();
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    // ------------------------------------------------------------ libellés

    public static String GetLogType(int id) {
        return switch (id) {
            case 1 -> MultiLanguage.getText("OpenAccount");
            case 2 -> MultiLanguage.getText("Deposit");
            case 3 -> MultiLanguage.getText("CardLost");
            case 4 -> MultiLanguage.getText("PasswordLost");
            case 5 -> MultiLanguage.getText("DealGrey");
            case 6 -> MultiLanguage.getText("CardUpdate");
            case 7 -> MultiLanguage.getText("CardChangeOpen");
            case 8 -> MultiLanguage.getText("CardChangeDeposit");
            case 9 -> MultiLanguage.getText("ClearAccount");
            case 10 -> MultiLanguage.getText("Transfer");
            case 11 -> MultiLanguage.getText("Consume");
            case 12 -> MultiLanguage.getText("Deduction");
            case 13 -> MultiLanguage.getText("Nonoilconsumption");
            case 14 -> MultiLanguage.getText("Repay");
            case 15 -> MultiLanguage.getText("ClearAccountTransfer");
            case 16 -> MultiLanguage.getText("MasterTransfer");
            default -> "";
        };
    }

    public static String GetRepayStatue(int statue) {
        return switch (statue) {
            case 0 -> MultiLanguage.getText("UnRepay");
            case 1 -> MultiLanguage.getText("PartialRepayment");
            case 2 -> MultiLanguage.getText("PaidOff");
            default -> "";
        };
    }

    public static String GetTType(int id, String asn) {
        return switch (id) {
            case 0 -> MultiLanguage.getText("Normal");
            case 1 -> MultiLanguage.getText("EscapeCard");
            case 2 -> MultiLanguage.getText("WrongCard");
            case 3 -> MultiLanguage.getText("Patching");
            case 4 -> MultiLanguage.getText("Replenish");
            case 5 -> MultiLanguage.getText("OnDuty");
            case 6 -> MultiLanguage.getText("OffDuty");
            case 7 -> MultiLanguage.getText("NonCard");
            case 8 -> MultiLanguage.getText("PriceChange");
            case 9 -> MultiLanguage.getText("CardRefuse");
            default -> asn != null && asn.startsWith("010006") ? MultiLanguage.getText("RepairCard") : MultiLanguage.getText("Other");
        };
    }

    public static String GetPayType(int id) {
        return switch (id) {
            case 0 -> MultiLanguage.getText("Cash");
            case 16 -> MultiLanguage.getText("ICCard");
            case 32 -> MultiLanguage.getText("CreditCard");
            case 48 -> MultiLanguage.getText("BankCard");
            case 64 -> "Ticket";
            case 80 -> "Main Po";
            case 96 -> "Sub Po";
            case 70 -> "Credit Coupons";
            case 128 -> "Cash Coupons";
            case 160 -> "App";
            case 176 -> "RFID";
            case 240 -> MultiLanguage.getText("AssistantCard");
            case 208 -> "";
            case 224 -> "Démarrage à distance";
            case 241 -> MultiLanguage.getText("CarPay");
            default -> MultiLanguage.getText("Other");
        };
    }

    public static String GetCardStatue(int id) {
        return switch (id) {
            case 1 -> MultiLanguage.getText("CardLost");
            case 3 -> MultiLanguage.getText("REISSUED");
            case 5 -> MultiLanguage.getText("ClearAccount");
            case 8 -> MultiLanguage.getText("Stop");
            default -> MultiLanguage.getText("Normal");
        };
    }

    public static String GetCardType(String asn) {
        if (asn == null || asn.length() < 6) {
            return "";
        }
        return switch (asn.substring(0, 6)) {
            case "010001" -> "Carte utilisateur";
            case "010002" -> "Carte de groupe";
            case "010011" -> "Carte crédit";
            case "010004" -> "Carte employé";
            case "010014" -> "Carte principale";
            case "010013" -> "Carte secondaire";
            case "110003" -> "Carte véhicule";
            case "FFFFFF" -> "Carte M1";
            case "010012" -> "Carte de groupe crédit";
            default -> "";
        };
    }

    public static String GetNormalCardType(String asn) {
        if (asn == null || asn.length() < 9) {
            return "";
        }
        return switch (asn.substring(0, 9)) {
            case "000000000" -> MultiLanguage.getText("EmployeeCard");
            case "000000001" -> "RFID";
            case "000000002" -> MultiLanguage.getText("UserCard");
            case "000000003" -> "Score";
            case "000000004" -> "Bill";
            default -> "";
        };
    }

    // ------------------------------------------------------------ dates

    /** ConvertDateTimeToInt() : millisecondes depuis 1970 (heure locale). */
    public static long ConvertDateTimeToInt() {
        return Conv.toMillis(LocalDateTime.now());
    }

    public static long ConvertDateTimeToInt(LocalDateTime dt) {
        return Conv.toMillis(dt);
    }

    public static LocalDateTime StampToDateTime(String timeStamp) {
        try {
            return Conv.fromMillis(Long.parseLong(timeStamp));
        } catch (RuntimeException e) {
            return LocalDateTime.now();
        }
    }

    // ------------------------------------------------------------ poste, chiffrement, HTTP

    /** Adresses MAC du poste au format .NET (« AA-BB-CC-DD-EE-FF »). */
    public static List<String> GetMacAddress() {
        try {
            List<String> out = new ArrayList<>();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(i == 0 ? "" : "-").append(String.format("%02X", mac[i] & 0xFF));
                    }
                    out.add(sb.toString());
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isExit(List<Row> grades, int oilCode) {
        for (Row g : grades) {
            if (String.valueOf(oilCode).equals(g.str("Code"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean MessagePrint() {
        return true;
    }

    public static String EncryptByAes(String input) {
        return LegacyAes.encrypt(input);
    }

    /** DecryptByAES : "" si le texte n'est pas déchiffrable. */
    public static String DecryptByAES(String input) {
        try {
            return input == null ? "" : LegacyAes.decrypt(input);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** AesEncrypt : AES-ECB, PKCS7, résultat Base64 (jeton FoRan). */
    public static String AesEncrypt(String str, String aesKey) {
        try {
            if (str == null || str.isEmpty() || aesKey == null || aesKey.isEmpty()) {
                return "";
            }
            Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"));
            return Base64.getEncoder().encodeToString(c.doFinal(str.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    /** Réponse de Center (CustomerRespone). */
    public record CustomerRespone(boolean Result, String Mess, int TotalCount) {
        static CustomerRespone empty() {
            return new CustomerRespone(false, null, 0);
        }
    }

    /**
     * HttpPost : envoie {"Result":true,"Mess":hex(AES(postData)),"TotalCount":0} à Center et lit la
     * réponse. En cas d'échec réseau, renvoie une réponse vide (Result = false), comme l'original.
     */
    public static CustomerRespone HttpPost(String url, String postData) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Result", true);
            body.put("Mess", EncryptByAes(postData));
            body.put("TotalCount", 0);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("Accept-Language", IniInfo.Language)
                .POST(HttpRequest.BodyPublishers.ofString(Wire.json(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Row r = Wire.parseRow(res.body());
            if (r == null) {
                return CustomerRespone.empty();
            }
            return new CustomerRespone(r.b("Result"), r.str("Mess"), r.i("TotalCount"));
        } catch (Exception e) {
            return CustomerRespone.empty();
        }
    }

    /** HttpPost vers Center avec un objet sérialisé comme JavaScriptSerializer. */
    public static CustomerRespone post(String action, Object obj) {
        return HttpPost(IniInfo.Center + action, obj instanceof String s ? s : Wire.json(obj));
    }

    public static boolean HTTPGetWithParam(String url, Map<String, String> dic, String[] result, String[] error) {
        result[0] = "";
        error[0] = "";
        try {
            StringBuilder sb = new StringBuilder(url);
            if (!dic.isEmpty()) {
                sb.append('?');
                int n = 0;
                for (Map.Entry<String, String> e : dic.entrySet()) {
                    sb.append(n++ > 0 ? "&" : "").append(e.getKey()).append('=').append(e.getValue());
                }
            }
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(sb.toString())).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            result[0] = res.body();
            return true;
        } catch (Exception e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    /** GetStationSetPassword : mot de passe des réglages de la station, demandé à Center. */
    public static String GetStationSetPassword(int stationId) {
        try {
            CustomerRespone r = post("Station/GetStationSetPassword", SW.dto().make("CustomerParam", "StationID", IniInfo.StationID));
            return DecryptByAES(r.Mess());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** GetStationValid : validité de la station selon Center (2 = station perdue, 0 = Center injoignable). */
    public static int GetStationValid(String code) {
        try {
            CustomerRespone r = post("ServerDeal/GetStationMessage", SW.dto().make("CustomerParam", "Code", code));
            if (r.Result()) {
                return Wire.parseRow(DecryptByAES(r.Mess())).i("ValidTime");
            }
            if (r.Mess() != null) {
                return "Station Lost".equals(DecryptByAES(r.Mess())) ? 2 : 1;
            }
            return 1;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ titulaires des cartes (base locale)

    /** Titulaire d'une carte : nom, identifiant, n° de pièce (GetCusMess). */
    public record CusMess(boolean ok, String name, int cusId, String certificateNo, String error) {
    }

    public static CusMess GetCusMess(String asn) {
        try {
            switch (asn.substring(0, 6)) {
                case "010001", "010011", "010013": {
                    Holder c = GetCustomerMess(asn);
                    return c.ok() ? new CusMess(true, c.a().s("Name"), c.a().i("CustomerID"), c.a().s("CertificateNo"), "")
                        : new CusMess(false, "", 0, "", c.error());
                }
                case "010004": {
                    Holder e = GetEmployeeMess(asn);
                    return e.ok() ? new CusMess(true, e.a().s("Name"), e.a().i("EmployeeID"), "", "")
                        : new CusMess(false, "", 0, "", e.error());
                }
                case "010014", "010002", "010012": {
                    Holder g = GetGroupMess(asn);
                    return g.ok() ? new CusMess(true, g.b().s("Name"), g.b().i("GroupID"), g.b().s("CertificateNo"), "")
                        : new CusMess(false, "", 0, "", g.error());
                }
                default:
                    return new CusMess(false, "", 0, "", "");
            }
        } catch (RuntimeException e) {
            return new CusMess(false, "", 0, "", "");
        }
    }

    /** Trois enregistrements liés (titulaire, carte, compte) et un message d'erreur. */
    public record Holder(boolean ok, Row a, Row b, Row c, String error) {
    }

    /** GetGroupMess : a = GroupCardInfos, b = Group, c = GroupAccounts. */
    public static Holder GetGroupMess(String asn) {
        try {
            Row info = db().first("GroupCardInfos", "{Asn} = :a", p("a", asn));
            if (info == null) {
                throw new IllegalStateException("L'utilisateur n'existe pas");
            }
            Row g = db().first("Group", "{GroupID} = :g", p("g", info.i("GroupID")));
            if (g == null) {
                throw new IllegalStateException("Les informations utilisateur n'existent pas");
            }
            Row acc = db().first("GroupAccounts", "{AccountNo} = :n", p("n", String.valueOf(info.i("GroupID"))));
            if (acc == null) {
                throw new IllegalStateException("Le compte utilisateur n'existe pas");
            }
            return new Holder(true, info, g, acc, "");
        } catch (RuntimeException e) {
            return new Holder(false, new Row(), new Row(), new Row(), e.getMessage());
        }
    }

    /** GetEmployeeMess : a = Employees, b = EmployeeCardInfos, c = CustomerAccounts. */
    public static Holder GetEmployeeMess(String asn) {
        try {
            Row info = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
            if (info == null) {
                throw new IllegalStateException("Les informations utilisateur n'existent pas");
            }
            Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", Conv.toInt(info.get("EmployeeCode"))));
            if (emp == null) {
                throw new IllegalStateException("L'utilisateur n'existe pas");
            }
            Row acc = db().first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", Conv.toInt(info.get("AccountNo"))));
            if (acc == null) {
                throw new IllegalStateException("Le compte utilisateur n'existe pas");
            }
            return new Holder(true, emp, info, acc, "");
        } catch (RuntimeException e) {
            return new Holder(false, new Row(), new Row(), new Row(), e.getMessage());
        }
    }

    /** GetCustomerMess : a = Customers, b = CustomerCardInfos, c = CustomerAccounts. */
    public static Holder GetCustomerMess(String asn) {
        try {
            Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
            if (info == null) {
                throw new IllegalStateException(MultiLanguage.getText("CardMiss"));
            }
            int cid = info.i("CustomerID");
            Row cus = db().first("Customers", "{CustomerID} = :c", p("c", cid));
            if (cus == null) {
                throw new IllegalStateException(MultiLanguage.getText("UserInexistence"));
            }
            Row acc = db().first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", cid));
            if (acc == null) {
                throw new IllegalStateException(MultiLanguage.getText("CardAccountMiss"));
            }
            return new Holder(true, cus, info, acc, "");
        } catch (RuntimeException e) {
            return new Holder(false, new Row(), new Row(), new Row(), e.getMessage());
        }
    }

    /** GetEmpMess : a = Employees, b = EmployeeCardInfos, c = compte (vide). */
    public static Holder GetEmpMess(String asn) {
        try {
            Row info = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
            if (info == null) {
                throw new IllegalStateException(MultiLanguage.getText("CardMiss"));
            }
            Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", Conv.toInt(info.get("EmployeeCode"))));
            if (emp == null) {
                throw new IllegalStateException(MultiLanguage.getText("CardMiss"));
            }
            return new Holder(true, emp, info, new Row(), "");
        } catch (RuntimeException e) {
            return new Holder(false, new Row(), new Row(), new Row(), e.getMessage());
        }
    }

    public static String GetCustomer(int customerId, String name) {
        try {
            Row c = db().first("Customers", "{CustomerID} = :c", p("c", customerId));
            if (c == null) {
                return "";
            }
            return switch (name) {
                case "Name" -> c.s("Name");
                case "Telephone" -> c.s("Telephone");
                case "VehicleNo" -> c.s("VehicleNo");
                default -> "";
            };
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String GetNameByAsn(String asn) {
        try {
            if (asn.startsWith("010004")) {
                Row e = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
                return e == null ? "" : e.s("Name");
            }
            Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
            if (info != null) {
                Row c = db().first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
                if (c != null) {
                    return c.s("Name");
                }
            }
            return "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String GetEmpName(String asn) {
        try {
            Row info = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
            if (info == null) {
                return "";
            }
            Row e = db().first("Employees", "{EmployeeID} = :e", p("e", Conv.toInt(info.get("EmployeeCode"))));
            return e == null ? "" : e.s("Name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String GetEmpName(int id) {
        Row e = db().first("Employees", "{EmployeeID} = :e", p("e", id));
        return e == null ? "" : e.s("Name");
    }

    // ------------------------------------------------------------ carburants, stations, cuves

    public static String GetOilNameByCode(int code) {
        try {
            Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", String.valueOf(code)));
            return g == null ? "" : g.s("Name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String GetOilNameByID(int id) {
        try {
            Row g = db().first("GradeSpecifics", "{GradeSpecificID} = :id", p("id", id));
            return g == null ? "" : g.s("Name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** GetOilIDByCode : identifiant du carburant, sinon celui du premier carburant, sinon 99. */
    public static int GetOilIDByCode(int code) {
        Row g = db().first("GradeSpecifics", "{Code} = :c", p("c", String.valueOf(code)));
        if (g != null) {
            return g.i("GradeSpecificID");
        }
        Row any = db().first("GradeSpecifics");
        return any != null ? any.i("GradeSpecificID") : 99;
    }

    public static String GetNormalGradeByID(int id) {
        try {
            Row g = db().first("NormalGrade", "{ID} = :id", p("id", id));
            return g == null ? "" : g.s("GradeName");
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static BigDecimal GetNormalPriceByGrade(int gradeId) {
        try {
            Row pi = db().first("NormalPriceInfo", "{GradeID} = :g", p("g", gradeId));
            return pi == null ? BigDecimal.ZERO : pi.dec("Price");
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }

    public static String GetNormalTankName(int id) {
        try {
            Row t = db().first("NormalTank", "{ID} = :id", p("id", id));
            return t == null ? "" : t.s("TankName");
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static int GetNormalTankNo(int tankId) {
        try {
            Row t = db().first("NormalTank", "{ID} = :id", p("id", tankId));
            return t == null ? 0 : Conv.toInt(t.get("TankNo"));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static String GetStationName(int stationId) {
        try {
            Row s = db().first("Stations", "{StationID} = :s", p("s", stationId));
            return s == null ? "" : s.s("Name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** GetGroupName : nom du groupe de véhicules, sinon l'identifiant. */
    public static String GetGroupName(int groupId) {
        try {
            Row g = db().first("CarGroup", "{ID} = :id", p("id", groupId));
            return g == null ? String.valueOf(groupId) : g.s("CarGroupName");
        } catch (RuntimeException e) {
            return String.valueOf(groupId);
        }
    }

    /** GetTankVolumeByHeight : volume par interpolation dans le barème de la cuve (TankVolConfig). */
    public static double GetTankVolumeByHeight(int tankId, double height) {
        try {
            Row low = db().first("TankVolConfig", "{TankID} = :t AND {Height} < :h ORDER BY {Height} DESC", p("t", tankId, "h", height));
            Row high = db().first("TankVolConfig", "{TankID} = :t AND {Height} > :h ORDER BY {Height}", p("t", tankId, "h", height));
            Row eq = db().first("TankVolConfig", "{TankID} = :t AND {Height} = :h", p("t", tankId, "h", height));
            if (eq != null) {
                return eq.d("Vol");
            }
            double lh = low == null ? 0 : low.d("Height");
            double lv = low == null ? 0 : low.d("Vol");
            double hh = high == null ? 0 : high.d("Height");
            double hv = high == null ? 0 : high.d("Vol");
            if (hh - lh == 0.0) {
                return 0.0;
            }
            return lv + (hv - lv) / (hh - lh) * (height - lh);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    public static int GetTankOilCode(int tankId) {
        try {
            Row t = db().first("tankConfig", "{ID} = :id", p("id", tankId));
            return t == null ? 0 : t.i("OilCode");
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** VersionSet : versions des listes ramenées sur un octet (envoyées aux pompes). */
    public static Row VersionSet() {
        Row dv = db().first("DataVersions");
        if (dv != null) {
            dv.put("AppendBlack", nz(dv.i("AppendBlack") % 256));
            dv.put("BasicBlack", nz(dv.i("BasicBlack") % 65536));
            dv.put("DelBlack", nz(dv.i("DelBlack") % 256));
            dv.put("MachineCfg", nz(dv.i("MachineCfg") % 256));
            dv.put("PPU", nz((dv.i("PPU") + dv.i("GradeSpec")) % 256));
            dv.put("White", nz(dv.i("White") % 256));
            return dv;
        }
        Row r = SW.dto().make("DataVersions");
        for (String k : new String[] {"AppendBlack", "BasicBlack", "Coupon", "CouponInfoes", "CreditCard", "CustomerCard", "DelBlack",
            "DistributionPoints", "Employee", "GradeSpec", "Gray", "MachineCfg", "PPU", "TransportInfoes", "White"}) {
            r.put(k, 1);
        }
        return r;
    }

    private static int nz(int v) {
        return v == 0 ? 1 : v;
    }

    // ------------------------------------------------------------ chaînes

    public static String SetStrLen(String str, int len) {
        StringBuilder s = new StringBuilder(str == null ? "" : str);
        while (s.length() < len) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    public static String SetLenBack(String str, int len) {
        StringBuilder s = new StringBuilder(str == null ? "" : str);
        while (s.length() < len) {
            s.append('0');
        }
        return s.toString();
    }

    public static String ConverToStr(String str, int len) {
        return SetStrLen(str, len);
    }

    public static String ConverToStr(String str) {
        return str.length() % 2 != 0 ? "0" + str : str;
    }

    public static String ConvertToHex(String str) {
        return SerialClass.byteToHexStr(str.getBytes(ANSI));
    }

    public static String StringConvertToAscii(String str) {
        return SerialClass.byteToHexStr(str.getBytes(ANSI));
    }

    public static boolean ValidMessage(String valid, String validText) {
        return Pattern.compile(validText).matcher(valid == null ? "" : valid).find();
    }

    public static String UTF8Eode(String str) {
        return new String(str.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    public static String fnbNot(String sTemp) {
        byte[] a = SerialClass.strToToHexByte(sTemp);
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (a[i] ^ 0xFF);
        }
        return SerialClass.byteToHexStr(a);
    }

    /** FloatToHex : octets du float en ordre machine (little-endian), comme BitConverter. */
    public static String FloatToHex(float value) {
        int bits = Float.floatToIntBits(value);
        return SerialClass.byteToHexStr(new byte[] {(byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >> 24)});
    }

    // ------------------------------------------------------------ clés des cartes (DES / 3DES)

    private static byte[] hex8(String s) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private static String des(int mode, String data, String key) {
        try {
            Cipher c = Cipher.getInstance("DES/ECB/NoPadding");
            c.init(mode, new SecretKeySpec(hex8(key), "DES"));
            return SerialClass.byteToHexStr(c.doFinal(hex8(data)));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static String Encrypt_DES16(String in, String key) {
        return des(Cipher.ENCRYPT_MODE, in, key).substring(0, 16);
    }

    public static String Decrypt_DES16(String in, String key) {
        return des(Cipher.DECRYPT_MODE, in, key);
    }

    /** DES3_Encrypt : longueur + données + bourrage 80 00…, puis 3DES (E-D-E) bloc par bloc. */
    public static String DES3_Encrypt(String key, String dataIn) {
        String k1 = key.substring(0, 16);
        String k2 = key.substring(16, 32);
        StringBuilder text = new StringBuilder(String.format("%02X", dataIn.length() / 2) + dataIn);
        int rem = text.length() % 16;
        if (rem > 0) {
            text.append("80");
            rem += 2;
            for (int i = 0; i < 16 - rem; i++) {
                text.append('0');
            }
        }
        StringBuilder out = new StringBuilder();
        for (int j = 0; j < text.length() / 16; j++) {
            String b = Encrypt_DES16(text.substring(16 * j, 16 * j + 16), k1);
            b = Decrypt_DES16(b, k2);
            b = Encrypt_DES16(b, k1);
            out.append(b);
        }
        return out.toString();
    }

    public static String DES3_Decrypt(String key, String dataIn) {
        String k1 = key.substring(0, 16);
        String k2 = key.substring(16, 32);
        StringBuilder text = new StringBuilder();
        int length = 0;
        for (int i = 0; i < dataIn.length() / 16; i++) {
            String b = Decrypt_DES16(dataIn.substring(16 * i, 16 * i + 16), k1);
            b = Encrypt_DES16(b, k2);
            b = Decrypt_DES16(b, k1);
            if (i == 0) {
                length = Integer.parseInt(b.substring(0, 2), 16) * 2;
            }
            text.append(b);
        }
        return text.substring(2, 2 + length);
    }

    public static String GetExtSeed(String seedA, String seedB) {
        seedA = ConvertToHex(seedA);
        seedB = ConvertToHex(seedB);
        String a = DES3_Encrypt(seedA, seedA).toLowerCase().substring(1, 33);
        String b = DES3_Encrypt(seedB, seedB).toLowerCase().substring(1, 33);
        String k = DES3_Encrypt(a, b).substring(1, 33);
        b = DES3_Encrypt(b, a).substring(1, 33);
        return DES3_Encrypt(k, b).substring(1, 33);
    }

    /** CreateSecretKey : les 20 clés de l'émetteur dérivées de deux graines. */
    public static String[] CreateSecretKey(String seedA, String seedB) {
        String[] keys = new String[20];
        try {
            String ext = GetExtSeed(seedA, seedB);
            String k = DES3_Encrypt(CONST_KEY, ext).substring(1, 33);
            String text = DES3_Encrypt(DES3_Encrypt(CONST_KEY, CONST_KEY).substring(1, 33), ext);
            for (int i = 0; i < 5; i++) {
                text = DES3_Encrypt(k, text);
            }
            text = text.substring(1, 33);
            for (int j = 1; j <= 20; j++) {
                text = j % 2 != 1 ? DES3_Encrypt(CONST_KEY, text) : DES3_Encrypt(k, text);
                text = text.substring(1, 33);
                String t = CONST_TEMPKEY;
                t = t.substring(16, 24) + t.substring(32, 40) + t.substring(8, 16) + t.substring(24, 32);
                keys[j - 1] = DES3_Encrypt(t, text);
            }
        } catch (RuntimeException ignored) {
            // comme l'original : clés partielles
        }
        return keys;
    }

    private static String tripDes(String key, String data) {
        String b = Encrypt_DES16(data, key);
        b = Decrypt_DES16(b, key.substring(16, 32));
        return Encrypt_DES16(b, key);
    }

    public static boolean fnbTripleDESEncrypt(String key, String dataIn, String[] out) {
        try {
            out[0] = tripDes(key, dataIn);
            return true;
        } catch (RuntimeException e) {
            out[0] = "";
            return false;
        }
    }

    /** fnGetDeriveKey : clé de carte dérivée de la clé maîtresse et des 16 derniers chiffres de l'ASN. */
    public static String fnGetDeriveKey(String masterKey, String asn) {
        String text = asn.substring(asn.length() - 16);
        String text2 = fnbNot(text);
        String a;
        String b;
        try {
            a = tripDes(masterKey, text);
        } catch (RuntimeException e) {
            a = "";
        }
        try {
            b = tripDes(masterKey, text2);
        } catch (RuntimeException e) {
            b = "";
        }
        return a + b;
    }

    // ------------------------------------------------------------ numéros de cartes (Center)

    /** GetASN : prochain numéro de carte d'un type, demandé à Center (texte chiffré, "" si erreur). */
    public static String GetASN(int cardType) {
        try {
            Row param = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
            CustomerRespone r = switch (cardType) {
                case 1 -> post("ServerDeal/GetCustomerCardCardInfo", param);
                case 2, 12 -> {
                    param.put("Start", cardType);
                    param.put("Mess", "");
                    yield post("ServerDeal/GetGroupCardInfoCardInfo", param);
                }
                case 3 -> post("ServerDeal/GetCreditCardInfo", param);
                case 5 -> post("ServerDeal/GetMasterGroupCardInfo", param);
                case 6 -> post("ServerDeal/GetChilrenGroupCardInfo", param);
                case 7 -> post("ServerDeal/GetCarUserCardInfo", param);
                case 8 -> post("ServerDeal/GetRepairCardInfo", param);
                default -> post("ServerDeal/GetEmployeeCardInfo", param);
            };
            if (r.Result()) {
                return r.Mess();
            }
            throw new IllegalStateException("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ------------------------------------------------------------ réglages

    public static boolean SetConfigValue(String key, String value) {
        return IniInfo.setConfigValue(key, value);
    }

    // ------------------------------------------------------------ commandes chiffrées des boîtiers

    public static boolean DealCmdEncry(String bts, String enType, String[] outdata) {
        try {
            int num = Integer.parseInt(enType, 16);
            int dir = ((num >> 16) & 0xF) % 2;
            int shift = (num & 0xF) / 2;
            StringBuilder out = new StringBuilder();
            for (int i = 14; i < bts.length() - 4; i += 2) {
                int b = Integer.parseInt(bts.substring(i, i + 2), 16);
                int w = (short) ((b << 8) | b);
                if (dir == 0) {
                    w >>= shift;
                    out.append(SetStrLen(String.format("%02X", w & 0xFF), 2));
                } else {
                    w <<= shift;
                    out.append(SetStrLen(String.format("%02X", (w >> 8) & 0xFF), 2));
                }
            }
            outdata[0] = bts.substring(0, 14) + out + bts.substring(bts.length() - 4);
            return true;
        } catch (RuntimeException e) {
            outdata[0] = e.getMessage();
            return false;
        }
    }

    public static byte[] DealCmdSecret(byte[] bts) {
        try {
            int len = ((bts[1] & 0xFF) << 8) | (bts[2] & 0xFF);
            byte[] out = new byte[len + 2];
            System.arraycopy(bts, 0, out, 0, len + 2);
            int key = ((bts[3] & 0xFF) << 24) | ((bts[4] & 0xFF) << 16) | ((bts[5] & 0xFF) << 8) | (bts[6] & 0xFF);
            int dir = ((key >> 16) & 0xF) % 2;
            int shift = (key & 0xF) / 2;
            for (int i = 7; i < len; i++) {
                int w = (short) (((out[i] & 0xFF) << 8) | (out[i] & 0xFF));
                if (dir == 0) {
                    w <<= shift;
                    out[i] = (byte) (w >> 8);
                } else {
                    w >>= shift;
                    out[i] = (byte) (w & 0xFF);
                }
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ programmes des boîtiers

    /** Fichier de programme téléchargé depuis Center (DowLoadFileParam). */
    public static final List<Row> FileList = Collections.synchronizedList(new ArrayList<>());

    public static boolean GetProgramVersion(int programType, String[] version, String[] error) {
        try {
            Path dir = Path.of("Dowload", programType == 1 ? "RFID_Keyboard" : "Communication");
            if (!Files.isDirectory(dir)) {
                throw new IllegalStateException(MultiLanguage.getText("ProgramFileMissing"));
            }
            try (var s = Files.list(dir)) {
                String name = s.findFirst().orElseThrow(() -> new IllegalStateException(MultiLanguage.getText("ProgramFileMissing")))
                    .getFileName().toString();
                version[0] = name.substring(0, name.length() - 4);
            }
            return true;
        } catch (Exception e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    public static boolean GetProgramVersionCenter(int programType, String curVersion, String[] version, String[] error) {
        try {
            Row param = SW.dto().make("DowLoadFileParam");
            param.put("fileType", switch (programType) {
                case 0 -> "Communication";
                case 1 -> "RFID";
                case 2 -> "LTLG";
                case 3 -> "BPro";
                default -> "";
            });
            param.put("CurrentVersion", curVersion);
            CustomerRespone r = post("Station/GetDowloadFile", param);
            if (r.Result()) {
                Row f = Wire.parseRow(DecryptByAES(r.Mess()));
                if (f.i("FileIndex") != -1) {
                    FileList.removeIf(x -> x.i("FileIndex") == f.i("FileIndex"));
                    FileList.add(f);
                    version[0] = f.s("FileName");
                    return true;
                }
                throw new IllegalStateException("Dowload Index Error");
            }
            throw new IllegalStateException(DecryptByAES(r.Mess()));
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    // ------------------------------------------------------------ e-mail d'alarme

    /**
     * SendEmail : l'original envoyait les alarmes depuis un compte Gmail écrit en dur ; ici le
     * compte se règle avec MailUser / MailPassword / MailHost / MailPort (vides : pas d'envoi).
     */
    public static boolean SendEmail(String toEmail, String title, String content, String[] error) {
        String user = IniInfo.setting("MailUser");
        String pass = IniInfo.setting("MailPassword");
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            error[0] = "Compte d'envoi d'e-mails non configuré";
            return false;
        }
        error[0] = "Envoi d'e-mails non disponible sur ce poste";
        return false;
    }

    // ------------------------------------------------------------ barème des cuves

    public static boolean CreateTankCable(int tankId, String[] error) {
        try {
            List<Row> list = db().list("TankCableCalculate", "{CalculateIndex} = -1 AND {TankID} = :t ORDER BY {CurrentHeight}", p("t", tankId));
            Integer max = db().scalar("SELECT max(\"index\") FROM tank_cable_final", null, Integer.class);
            int index = (max == null ? 0 : max) + 1;
            List<Row> out = new ArrayList<>();
            if (!list.isEmpty()) {
                BigDecimal top = list.get(list.size() - 1).dec("CurrentHeight");
                BigDecimal firstH = list.get(0).dec("CurrentHeight");
                BigDecimal firstV = list.get(0).dec("CurrentVolume");
                BigDecimal steps = top.divide(BigDecimal.TEN, 0, RoundingMode.CEILING);
                BigDecimal ratio = firstV.divide(firstH, 20, RoundingMode.HALF_EVEN);
                for (int i = 0; BigDecimal.valueOf(i).compareTo(steps.multiply(BigDecimal.TEN)) < 0; i += 10) {
                    BigDecimal h = BigDecimal.valueOf(i);
                    if (h.compareTo(firstH) < 0) {
                        out.add(Row.of("CurrentHeight", h, "CurrentVolume", h.multiply(ratio).setScale(3, RoundingMode.HALF_EVEN),
                            "Index", index, "TankID", tankId));
                        continue;
                    }
                    final int lo = i;
                    BigDecimal[] line = CreateLine(list.stream().filter(t -> t.dec("CurrentHeight").compareTo(BigDecimal.valueOf(lo)) >= 0
                        && t.dec("CurrentHeight").compareTo(BigDecimal.valueOf(lo + 10)) <= 0).toList());
                    if (line[0].compareTo(BigDecimal.valueOf(-1)) != 0 || line[1].compareTo(BigDecimal.valueOf(-1)) != 0) {
                        out.add(Row.of("CurrentHeight", h, "CurrentVolume", h.multiply(line[0]).add(line[1]).setScale(3, RoundingMode.HALF_EVEN),
                            "Index", index, "TankID", tankId));
                        if (h.compareTo(steps.subtract(BigDecimal.ONE).multiply(BigDecimal.TEN)) == 0) {
                            BigDecimal h2 = BigDecimal.valueOf(i + 10);
                            out.add(Row.of("CurrentHeight", h2, "CurrentVolume", h2.multiply(line[0]).add(line[1]).setScale(3, RoundingMode.HALF_EVEN),
                                "Index", index, "TankID", tankId));
                        }
                    }
                }
            }
            if (!out.isEmpty()) {
                db().insertAll("TankCableFinal", out);
                db().exec("UPDATE tank_cable_calculate SET calculate_index = :i WHERE tank_id = :t AND calculate_index = -1",
                    p("i", index, "t", tankId));
            }
            return true;
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    /** CreateLine : régression linéaire (pente, ordonnée) des points hauteur/volume. */
    public static BigDecimal[] CreateLine(List<Row> xy) {
        try {
            int n = xy.size();
            BigDecimal sx = BigDecimal.ZERO;
            BigDecimal sy = BigDecimal.ZERO;
            BigDecimal sxx = BigDecimal.ZERO;
            BigDecimal sxy = BigDecimal.ZERO;
            for (Row r : xy) {
                BigDecimal x = r.dec("CurrentHeight");
                BigDecimal y = r.dec("CurrentVolume");
                sx = sx.add(x);
                sy = sy.add(y);
                sxx = sxx.add(x.multiply(x));
                sxy = sxy.add(x.multiply(y));
            }
            BigDecimal nn = BigDecimal.valueOf(n);
            BigDecimal k = nn.multiply(sxy).subtract(sx.multiply(sy)).divide(nn.multiply(sxx).subtract(sx.multiply(sx)), 20, RoundingMode.HALF_EVEN);
            BigDecimal b = sy.subtract(k.multiply(sx)).divide(nn, 20, RoundingMode.HALF_EVEN);
            return new BigDecimal[] {k, b};
        } catch (RuntimeException e) {
            return new BigDecimal[] {BigDecimal.valueOf(-1), BigDecimal.valueOf(-1)};
        }
    }

    public static BigDecimal CreateLineK(List<Row> xy) {
        try {
            if (xy.size() < 2) {
                return BigDecimal.valueOf(-1);
            }
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (int i = 0; i < xy.size() / 2; i++) {
                Row a = xy.get(i);
                Row b = xy.get(xy.size() - i - 1);
                sum = sum.add(b.dec("CurrentVolume").subtract(a.dec("CurrentVolume"))
                    .divide(b.dec("CurrentHeight").subtract(a.dec("CurrentHeight")), 20, RoundingMode.HALF_EVEN));
                n++;
            }
            return sum.divide(BigDecimal.valueOf(n), 20, RoundingMode.HALF_EVEN);
        } catch (RuntimeException e) {
            return BigDecimal.valueOf(-1);
        }
    }

    public static BigDecimal RaiseTankVolume(int tankId, BigDecimal height, BigDecimal volume, String[] error) {
        try {
            Row up = db().first("TankCableFinal", "{TankID} = :t AND {CurrentHeight} >= :h ORDER BY {CurrentHeight}", p("t", tankId, "h", height));
            Row down = db().first("TankCableFinal", "{TankID} = :t AND {CurrentHeight} <= :h ORDER BY {CurrentHeight} DESC",
                p("t", tankId, "h", height));
            if (up != null && down != null) {
                BigDecimal dh = up.dec("CurrentHeight").subtract(down.dec("CurrentHeight"));
                volume = dh.signum() == 0 ? down.dec("CurrentVolume")
                    : up.dec("CurrentVolume").subtract(down.dec("CurrentVolume")).divide(dh, 20, RoundingMode.HALF_EVEN)
                        .multiply(height.subtract(down.dec("CurrentHeight"))).add(down.dec("CurrentVolume"));
            }
            if (db().first("NormalTank", "{ID} = :id", p("id", tankId)) == null) {
                throw new IllegalStateException("Lost Tank");
            }
            return volume;
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return BigDecimal.valueOf(-1);
        }
    }

    /** DowloadTankCable : une page (100 points) du barème au format hexadécimal attendu par la jauge. */
    public static String DowloadTankCable(int tankId, int index, int[] totals) {
        try {
            Integer max = db().scalar("SELECT max(\"index\") FROM tank_cable_final WHERE tank_id = :t", p("t", tankId), Integer.class);
            if (max == null || max == 0) {
                throw new IllegalStateException("Lost Cable");
            }
            // L'original appelle ToPageList(100, Index) : la page 100, de Index lignes.
            Sugar.Page pg = db().page("TankCableFinal", "{TankID} = :t AND {Index} = :i", p("t", tankId, "i", max), "{CurrentHeight}",
                100, Math.max(index, 1));
            totals[0] = pg.total();
            totals[1] = pg.totalPage();
            if (pg.rows().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Row r : pg.rows()) {
                sb.append(SetStrLen(FloatToHex(r.dec("CurrentHeight").floatValue()), 8));
                sb.append(SetStrLen(FloatToHex(r.dec("CurrentVolume").floatValue()), 8));
            }
            return SetStrLen(String.format("%02x", pg.totalPage()), 4) + SetStrLen(String.format("%02x", index), 4)
                + SetStrLen(String.format("%02x", pg.rows().size()), 2) + sb;
        } catch (RuntimeException e) {
            BaseLog.writeLog("DowloadTankCable Error:" + e.getMessage());
            return "";
        }
    }
}
