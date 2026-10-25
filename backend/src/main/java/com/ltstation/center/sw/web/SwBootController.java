package com.ltstation.center.sw.web;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.security.PublicApi;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.engine.StationEngine;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Démarrage du logiciel Station (Program.cs) : contrôle de la licence (RegisterInfo / adresse MAC),
 * assistant de création de la station (StationCreate), connexion (Login) et droits (TypeSwitch).
 */
@RestController
@RequestMapping("/api/sw")
@PublicApi
public class SwBootController {

    public static final String SESSION_KEY = "SW_LOGIN";
    private final StationEngine engine;

    public SwBootController(StationEngine engine) {
        this.engine = engine;
    }

    private static Sugar db() {
        return SW.db();
    }

    /** Licence valide : l'une des adresses MAC du poste correspond à RegisterInfo (double chiffrement). */
    public static boolean registered() {
        List<String> macs = BaseFunction.GetMacAddress();
        if (macs == null) {
            return false;
        }
        for (Row r : db().list("RegisterInfo")) {
            String plain = BaseFunction.DecryptByAES(BaseFunction.DecryptByAES(r.s("SecretInfo")));
            for (String m : macs) {
                if (m.trim().equals(plain.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    @GetMapping("/boot")
    public Map<String, Object> boot() {
        Map<String, Object> m = new LinkedHashMap<>();
        Row st = db().first("Stations");
        int valid = st == null ? -1 : BaseFunction.GetStationValid(st.s("Code"));
        m.put("outOfTime", valid == 2 || valid == 3);
        m.put("registered", registered());
        m.put("systemType", IniInfo.SystemType);
        m.put("normalType", IniInfo.NormalType);
        m.put("language", IniInfo.Language);
        m.put("auto", IniInfo.Auto);
        m.put("station", st == null ? null : Row.of("Code", st.get("Code"), "Name", st.get("Name"), "StationID", st.get("StationID")));
        m.put("center", IniInfo.Center);
        m.put("texts", MultiLanguage.all());
        return m;
    }

    @PostMapping("/language")
    public Map<String, Object> language(@RequestBody Map<String, String> body) {
        String lang = body.getOrDefault("lang", "fr-FR");
        IniInfo.Language = lang;
        IniInfo.setConfigValue("Language", lang);
        MultiLanguage.languageChange(lang);
        return Map.of("texts", MultiLanguage.all());
    }

    /** Login.LoginLoad : Memo1 = identifiant, Memo2 = mot de passe ; StationType "0" = sans droit. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String user = body.getOrDefault("user", "");
        String pass = body.getOrDefault("pass", "");
        Row emp = db().first("Employees", "{Memo1} = :u AND {Memo2} = :p", Sugar.p("u", user, "p", pass));
        if (emp == null) {
            throw new SwException(MultiLanguage.getText("LoginFailed"));
        }
        if ("0".equals(emp.str("StationType"))) {
            throw new SwException(MultiLanguage.getText("AuthorityProblem"));
        }
        IniInfo.LoginID = emp.i("EmployeeID");
        IniInfo.LoginName = emp.s("Name");
        IniInfo.LoginType = Conv.toInt(emp.get("StationType"));
        IniInfo.Auth = emp.s("Auth");
        IniInfo.LoginPassword = pass;
        session.setAttribute(SESSION_KEY, IniInfo.LoginID);
        engine.start();
        return Map.of("ok", true, "name", IniInfo.LoginName);
    }

    /** Program.cs, Auto ≠ 0 : connexion automatique du premier employé (mode Normal). */
    @PostMapping("/autologin")
    public Map<String, Object> autologin(HttpSession session) {
        if (IniInfo.Auto == 0) {
            throw new SwException(MultiLanguage.getText("AuthorityProblem"), 403);
        }
        Row emp = db().first("Employees");
        if (emp == null) {
            throw new SwException(MultiLanguage.getText("LoginFailed"));
        }
        IniInfo.LoginID = emp.i("EmployeeID");
        IniInfo.LoginName = emp.s("Name");
        IniInfo.LoginType = Conv.toInt(emp.get("StationType"));
        IniInfo.Auth = emp.s("Auth");
        session.setAttribute(SESSION_KEY, IniInfo.LoginID);
        return Map.of("ok", true, "name", IniInfo.LoginName);
    }

    /** EmpLoginItem : changement d'employé sans arrêter le moteur. */
    @PostMapping("/relogin")
    public Map<String, Object> relogin(@RequestBody Map<String, String> body, HttpSession session) {
        return login(body, session);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return Map.of("ok", true);
    }

    /** ExitItem : arrêt des fils d'exécution (IniInfo.ThreadStop = false). */
    @PostMapping("/exit")
    public Map<String, Object> exit(HttpSession session) {
        engine.stop();
        session.removeAttribute(SESSION_KEY);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        requireLogin(session);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loginId", IniInfo.LoginID);
        m.put("loginName", IniInfo.LoginName);
        m.put("loginType", IniInfo.LoginType);
        m.put("stationId", IniInfo.StationID);
        m.put("stationName", IniInfo.StationName);
        m.put("code", IniInfo.Code);
        m.put("visible", typeSwitch(IniInfo.LoginType));
        m.put("scoreDeal", IniInfo.ScoreDeal);
        m.put("normalType", IniInfo.NormalType);
        m.put("reportType", IniInfo.ReportType);
        m.put("moneyFormat", IniInfo.MoneyFormat);
        m.put("volumeFormat", IniInfo.VolumeFormat);
        m.put("priceFormat", IniInfo.PriceFormat);
        return m;
    }

    @GetMapping("/main")
    public Map<String, Object> main(HttpSession session) {
        requireLogin(session);
        return engine.view();
    }

    public static void requireLogin(HttpSession session) {
        if (session.getAttribute(SESSION_KEY) == null) {
            throw new SwException("Session expirée : veuillez vous reconnecter", 401);
        }
    }

    /**
     * TypeSwitch : éléments visibles de la barre d'outils et des menus selon AuthorityMess
     * (droits 1 à 24 du profil de l'employé).
     */
    static List<String> typeSwitch(int id) {
        List<String> v = new ArrayList<>();
        Row auth = db().first("AuthorityMess", "{User_IDMess} = :i", Sugar.p("i", id));
        if (auth == null) {
            throw new SwException(MultiLanguage.getText("AuthorityProblem"));
        }
        String content = IniInfo.SpecialInt == 1 ? "3,4,7,8,10,13,15,16,17,18" : auth.s("AuthorityContent");
        v.add("StationPrint");
        v.add("TankItem");
        v.add("TankVolumeItem");
        v.add("TankPortItem");
        v.add("BaseMessage");
        v.add("SuperviseItem");
        v.add("PortItem");
        v.add("NozItem");
        v.add("RFIDItem");
        v.add("PriceDisplay");
        for (String r : content.split(",")) {
            switch (r.trim()) {
                case "1" -> v.add("OilPrice");
                case "4" -> v.add("CardMake");
                case "7" -> v.add("CardUpDate");
                case "10" -> v.add("CardLost");
                case "13" -> v.add("CardChange");
                case "16" -> v.add("Deposit");
                case "2" -> {
                    v.add("TransactionSearch");
                    v.add("TransactionR");
                }
                case "5" -> v.add("EmpManager");
                case "8" -> {
                    v.add("Shift");
                    v.add("ShiftReport");
                }
                case "11" -> {
                    v.add("DailyReport");
                    v.add("DailyR");
                }
                case "14" -> {
                    v.add("MonthReport");
                    v.add("MonthR");
                }
                case "17" -> v.add("CardSearch");
                case "3" -> v.add("ScoreDeal");
                case "6" -> v.add("DisCount");
                case "9" -> v.add("KeyManagement");
                case "12" -> {
                    v.add("PSAMMake");
                    v.add("ShiftItem");
                }
                case "15" -> v.add("BusinessReport");
                case "18" -> v.add("PasswodLost");
                case "19" -> v.add("TopSystemSeting");
                case "20" -> v.add("TopStationManager");
                case "21" -> v.add("TopCommunication");
                case "22" -> v.add("ExtentTool");
                case "23" -> v.add("TopUserCheck");
                case "24" -> v.add("AuthorityManagement");
                default -> {
                }
            }
        }
        if (auth.i("User_IDMess") == 1) {
            v.add("AuthorityManagement");
        }
        v.add("ShiftCancel");
        Row reg = db().first("RegisterInfo", "{SourceInfo} = 'CarUser'", null);
        if (reg != null && BaseFunction.DecryptByAES(reg.s("SecretInfo")).contains("CarUserActive")) {
            v.add("CarNoManager");
            v.add("CarNoVerify");
            v.add("SurveillanceCamera");
        }
        if (IniInfo.NormalType == 2) {
            v.removeAll(List.of("AppItem", "RFIDItem", "PriceDisplay", "DisplayPay", "RegisiterValid", "VirtueScore", "TankPortItem"));
        }
        return v;
    }
}
