package com.ltstation.center.serverdeal;

import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (7/7) : stations en mode Normal (enregistrement, transactions,
 * carburants, prix, cuves, pistolets, alarmes, employés), connexion, numéros de cartes, cartes
 * grises, activation, appareils à gaz, comptes RFID, impulsions, recharges en ligne, groupes de
 * véhicules / véhicules / bouteilles, codes App et comptes NP.
 */
@RestController
@RequestMapping("/ServerDeal")
public class ServerDealNormalController extends ServerDealBase {

    private static final String STATION_ERROR = "Erreur de station";
    private static final String DATA_REPEAT = "Données en double";
    private static final String EMPTY = "Aucune donnée";

    public ServerDealNormalController(@Qualifier("centerSugar") Sugar db, CenterDeal deal) {
        super(db, deal);
    }

    private static String nowStr() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private Row stationById(int id) {
        return db.first("Stations", "{StationID} = :s", p("s", id));
    }

    // ------------------------------------------------------------ stations Normal

    @RequestMapping("/CreateNormalStation")
    public String CreateNormalStation(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row company = db.first("LoginCompanyGroup", "{ValidSecretKey} = :k", p("k", modal.str("SendMailServer")));
            if (company == null) {
                throw fail("Numéro d'application erroné");
            }
            Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
            if (st == null) {
                if (db.any("Stations", "{CompanyID} = :c AND {Name} = :n", p("c", company.i("ID"), "n", modal.str("Name")))) {
                    throw fail("Nom de station en double");
                }
                if (company.i("ValidCount") != 0) {
                    long n = db.count("Stations", "{CompanyID} = :c", p("c", company.i("ID")));
                    if (n + 1 >= company.i("ValidCount")) {
                        throw fail("Le nombre de stations autorisées est dépassé");
                    }
                }
            }
            Row st3 = Row.of("Address", modal.get("Address"), "Name", modal.get("Name"), "Code", modal.get("Code"),
                "CityID", modal.i("CityID"), "Latitude", modal.get("Latitude"), "Longitude", modal.get("Longitude"),
                "Manager", modal.get("Manager"), "Phone", modal.get("Tel"), "Email1", modal.get("email"), "Is_Active", 1,
                "StationTypeID", 4, "TransportInfoID", 11, "CompanyID", company.i("ID"),
                "Email3", modal.s("Code") + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            int id;
            if (st != null) {
                st3.put("StationID", st.i("StationID"));
                db.update("Stations", st3);
                id = st.i("StationID");
            } else {
                id = Conv.toInt(db.insert("Stations", st3));
            }
            return okText(company.i("ID") + "|" + id);
        } catch (Exception ex) {
            return ret(false, enc(ex.getMessage()), 0);
        }
    }

    @RequestMapping("/UploadNormalTransaction")
    public String UploadNormalTransaction(@RequestBody(required = false) String body) {
        try {
            Row t = req(body);
            Row st = stationById(t.i("StationID"));
            if (st == null) {
                throw fail(STATION_ERROR);
            }
            LocalDateTime trueTime = Conv.toDateTime(Conv.fromMillis(t.l("CreateTime")).format(Conv.STD));
            Row oil = Row.of("AMN", t.d("Amount"), "Asn", t.str("CardNo") == null ? "" : t.str("CardNo"), "Volume", t.d("Volume"),
                "TruelyPay", t.d("Amount"), "TrueTime", trueTime, "StationID", t.i("StationID"), "GunId", t.i("NozID"),
                "VolumeTotal", t.d("VolumeTotal"), "PPU", t.d("Price"), "GradeSpecificID", t.i("GradeID"), "PaymentID", t.i("PayMent"),
                "TType", 999, "EmployeeID", t.i("EmployeeID"), "POSTTC", "9999", "CTC", "9999", "TAC", "9999", "GMAC", "9999",
                "GTAC", "9999", "PSAMTID", "9999", "PSAMTTC", "9999", "Flg", "1", "Discount", 100.0, "PreferentialPrice", 0.0,
                "Upload", "1", "CompanyID", st.i("CompanyID"));
            if (db.any("OilTransactions", "{TType} = 999 AND {StationID} = :s AND {GunId} = :g AND {TrueTime} = :t",
                p("s", oil.i("StationID"), "g", oil.i("GunId"), "t", trueTime))) {
                throw fail("transaction Exit");
            }
            db.insert("OilTransactions", oil);
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/UpdateNormalGrade")
    public String UpdateNormalGrade(@RequestBody(required = false) String body) {
        try {
            Row grade = deal.dto().shape("Modal.GradeSpecifics", req(body));
            Row raw = req(body);
            int app = raw.i("APPNumber");
            if (grade.i("GradeSpecificID") == 0 && db.any("GradeSpecifics", "{Name} = :n AND {CompanyID} = :c",
                p("n", grade.str("Name"), "c", app))) {
                throw fail("Nom en double");
            }
            String max = db.scalar("SELECT max(code) FROM grade_specifics", null, String.class);
            String code = max == null || max.isEmpty() ? "1001" : String.valueOf(Conv.toInt(max) + 1);
            grade.put("CompanyID", app);
            if (grade.i("GradeSpecificID") == 0) {
                grade.put("Code", code);
                grade.remove("GradeSpecificID");
                Object id = db.insert("GradeSpecifics", grade);
                grade = deal.dto().shape("Modal.GradeSpecifics", grade);
                grade.put("GradeSpecificID", id);
            } else {
                Row g2 = db.first("GradeSpecifics", "{Code} = :c", p("c", grade.str("Code")));
                if (g2 == null) {
                    throw fail("Énergie introuvable");
                }
                g2.put("Name", grade.get("Name"));
                db.update("GradeSpecifics", g2);
            }
            Row out = grade.copy();
            out.put("APPNumber", app);
            return okJson(out);
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalGradeDowload")
    public String NormalGradeDowload(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            return ret(true, Wire.json(ent("GradeSpecifics", db.list("GradeSpecifics", "{CompanyID} = :c", p("c", param.i("CompanyID"))))), 0);
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalDeleteGrade")
    public String NormalDeleteGrade(@RequestBody(required = false) String body) {
        try {
            Row grade = req(body);
            db.delete("GradeSpecifics", "{Code} = :c", p("c", grade.str("Code")));
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/UpdateNormalPrice")
    public String UpdateNormalPrice(@RequestBody(required = false) String body) {
        try {
            Row pi = req(body);
            Row grade = db.first("GradeSpecifics", "{Code} = :c", p("c", pi.str("Code")));
            if (grade == null) {
                throw fail("Erreur d'énergie");
            }
            Row price = db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g AND {CompanyID} = :c",
                p("s", pi.i("StationID"), "g", grade.i("GradeSpecificID"), "c", pi.i("CompanyID")));
            if (price != null) {
                price.put("NewPPU", pi.d("NewPPU"));
                price.put("CompanyID", pi.i("CompanyID"));
                db.update("PriceInfos", price);
            } else {
                db.insert("PriceInfos", Row.of("StationID", pi.i("StationID"), "GradeSpecificID", grade.i("GradeSpecificID"),
                    "NewPPU", pi.d("NewPPU"), "CurrentPPU", pi.d("CurrentPPU"), "CurrentDensity", 1.0, "Newdensity", 1.0,
                    "ValidateTime", LocalDateTime.now(), "CompanyID", pi.i("CompanyID")));
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalDeletePrice")
    public String NormalDeletePrice(@RequestBody(required = false) String body) {
        try {
            Row pi = req(body);
            Row price = db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g", p("s", pi.i("StationID"), "g", pi.i("GradeID")));
            if (price == null) {
                throw fail("Empty");
            }
            db.deleteEntity("PriceInfos", price);
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalTankUpdate")
    public String NormalTankUpdate(@RequestBody(required = false) String body) {
        try {
            Row tank = req(body);
            int sid = tank.i("StationID");
            switch (tank.s("UpdateType")) {
                case "Add" -> {
                    Row st = stationById(sid);
                    if (st == null) {
                        throw fail(STATION_ERROR);
                    }
                    if (db.any("StationTank", "{StationID} = :s AND {TankNo} = :t", p("s", sid, "t", tank.get("NTankNo")))) {
                        throw fail(DATA_REPEAT);
                    }
                    db.insert("StationTank", Row.of("StationID", sid, "CompanyID", st.i("CompanyID"), "TankNo", tank.get("NTankNo")));
                }
                case "Update" -> {
                    Row st = stationById(sid);
                    if (st == null) {
                        throw fail(STATION_ERROR);
                    }
                    Row exit = db.first("StationTank", "{StationID} = :s AND {TankNo} = :t", p("s", sid, "t", tank.get("OTankNo")));
                    if (exit == null) {
                        throw fail(EMPTY);
                    }
                    if (db.any("StationTank", "{ID} <> :id AND {StationID} = :s AND {TankNo} = :t",
                        p("id", exit.i("ID"), "s", sid, "t", tank.get("NTankNo")))) {
                        throw fail(DATA_REPEAT);
                    }
                    db.deleteEntity("StationTank", exit);
                    db.insert("StationTank", Row.of("StationID", sid, "CompanyID", st.i("CompanyID"), "TankNo", tank.get("NTankNo")));
                }
                case "Delete" -> {
                    Row t = db.first("StationTank", "{StationID} = :s AND {TankNo} = :t", p("s", sid, "t", tank.get("OTankNo")));
                    if (t == null) {
                        throw fail(EMPTY);
                    }
                    db.deleteEntity("StationTank", t);
                }
                default -> {
                }
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalTankUpload")
    public String NormalTankUpload(@RequestBody(required = false) String body) {
        try {
            int company = -1;
            for (Row item : reqList(body)) {
                if (company == -1) {
                    Row st = stationById(item.i("StationID"));
                    if (st == null) {
                        throw fail(STATION_ERROR);
                    }
                    company = st.i("CompanyID");
                }
                int id = Conv.toInt(item.get("TankNo"));
                Row s = db.first("StationTankStatue", "{TankID} = :t AND {StationID} = :s", p("t", id, "s", item.i("StationID")));
                boolean add = s == null;
                if (add) {
                    s = Row.of("StationID", item.i("StationID"), "TankID", id);
                }
                s.put("TankHeight", item.get("TankHeight"));
                s.put("TankVolume", item.get("TankVolume"));
                s.put("CurrentHeight", item.get("CurrentHeight"));
                s.put("CurrentVolume", item.get("CurrentVolume"));
                s.put("CurrentWaterHeight", item.get("CurrentWater"));
                s.put("CurrentTemp", item.get("CurrentTemp"));
                s.put("CompanyID", company);
                s.put("UpdateTime", Conv.toLong(item.get("UpdateTime")));
                s.put("GradeID", item.i("GradeID"));
                s.put("TankName", item.get("TankName"));
                s.put("GradeName", deal.getGraName(item.i("GradeID")));
                if (add) {
                    db.insert("StationTankStatue", s);
                } else {
                    db.update("StationTankStatue", s);
                }
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalNozUpload")
    public String NormalNozUpload(@RequestBody(required = false) String body) {
        try {
            Row noz = req(body);
            int sid = noz.i("StationID");
            switch (noz.s("UpdateType")) {
                case "Add" -> {
                    Row st = stationById(sid);
                    if (st == null) {
                        throw fail(STATION_ERROR);
                    }
                    if (db.any("StationNoz", "{StationID} = :s AND {NozID} = :n", p("s", sid, "n", noz.i("NNozID")))) {
                        throw fail(DATA_REPEAT);
                    }
                    db.insert("StationNoz", Row.of("StationID", sid, "NozID", noz.i("NNozID"), "CompanyID", st.i("CompanyID")));
                }
                case "Update" -> {
                    Row st = stationById(sid);
                    if (st == null) {
                        throw fail(STATION_ERROR);
                    }
                    db.delete("StationNoz", "{StationID} = :s AND {NozID} = :n", p("s", sid, "n", noz.i("ONozID")));
                    db.insert("StationNoz", Row.of("StationID", sid, "NozID", noz.i("NNozID"), "CompanyID", st.i("CompanyID")));
                }
                case "Delete" -> {
                    Row n = db.first("StationNoz", "{StationID} = :s AND {NozID} = :n", p("s", sid, "n", noz.i("ONozID")));
                    if (n == null) {
                        throw fail(EMPTY);
                    }
                    db.deleteEntity("StationNoz", n);
                }
                default -> {
                }
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalNozStatueUpload")
    public String NormalNozStatueUpload(@RequestBody(required = false) String body) {
        try {
            int company = -1;
            for (Row item : reqList(body)) {
                if (company == -1) {
                    Row st = stationById(item.i("StationID"));
                    if (st == null) {
                        throw fail("Object reference not set to an instance of an object.");
                    }
                    company = st.i("CompanyID");
                }
                int noz = Conv.toInt(item.get("NozID"));
                Row s = db.first("StationNozStatue", "{StationID} = :s AND {NozID} = :n", p("s", item.i("StationID"), "n", noz));
                boolean add = s == null;
                if (add) {
                    s = new Row();
                }
                s.put("StationID", item.i("StationID"));
                s.put("NozID", noz);
                s.put("CurrentAmount", item.get("Amont"));
                s.put("CurrentVolume", item.get("Volume"));
                s.put("AmountTotal", item.get("AmountTotal"));
                s.put("VolumeTotal", item.get("VolumeTotal"));
                s.put("Statue", item.i("View_Statue") == 4 ? -1 : item.i("View_Statue"));
                s.put("Price", item.get("SetPrice"));
                s.put("UpdateTime", CenterDeal.now());
                s.put("CompanyID", company);
                s.put("GradeID", item.i("GradeID"));
                s.put("GradeName", item.get("GradeName"));
                if (add) {
                    db.insert("StationNozStatue", s);
                } else {
                    db.update("StationNozStatue", s);
                }
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalCommandDowload")
    public String NormalCommandDowload(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            Row st = db.first("Stations", "{Code} = :c", p("c", param.str("Code")));
            if (st == null) {
                throw fail("Empty");
            }
            List<Row> list = db.list("CmdList", "{Dowload} = false AND {StationID} = :s", p("s", st.i("StationID")));
            for (Row item : list) {
                item.put("Dowload", true);
            }
            db.updateAll("CmdList", list);
            return okJson(ent("CmdList", list));
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalAlarmUpload")
    public String NormalAlarmUpload(@RequestBody(required = false) String body) {
        try {
            for (Row item : reqList(body)) {
                Row st = stationById(item.i("StationID"));
                if (st == null) {
                    continue;
                }
                Row h = db.first("TankAlarmHistory", "{StationID} = :s AND {TankID} = :t AND {StartTime} = :st",
                    p("s", item.i("StationID"), "t", item.i("TankID"), "st", item.get("StartTime")));
                if (h != null) {
                    h.put("EndTime", item.get("EndTime"));
                    h.put("Deal", true);
                    h.put("UpdateTime", CenterDeal.now());
                    db.update("TankAlarmHistory", h);
                    continue;
                }
                db.insert("TankAlarmHistory", Row.of("StationID", item.i("StationID"), "TankID", item.i("TankID"),
                    "TankName", item.get("TankName"), "AlarmTypeID", item.get("AlarmTypeID"), "Deal", item.b("Deal"),
                    "CompanyID", st.i("CompanyID"), "StartTime", item.get("StartTime"), "EndTime", item.get("EndTime"),
                    "UpdateTime", CenterDeal.now()));
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/NormalEmpUpload")
    public String NormalEmpUpload(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row se = req(body);
                int sid = se.i("StationID");
                switch (se.s("StationType")) {
                    case "Add": {
                        if (db.any("Employees", "{StationID} = :s AND {Name} = :n", p("s", sid, "n", se.str("Name")))) {
                            throw fail("Employee Repeat");
                        }
                        Row st = stationById(sid);
                        if (st == null) {
                            throw fail("Station Error");
                        }
                        if (db.any("GPRSAccount", "{UserName} = :u AND {TypeID} = 2", p("u", se.str("Name")))) {
                            throw fail("Login Account Repeat");
                        }
                        Integer max = db.scalar("SELECT max(CASE WHEN employee_no ~ '^\\s*-?\\d+\\s*$' THEN trim(employee_no)::int END) "
                            + "FROM employees", null, Integer.class);
                        String no = String.valueOf((max == null ? 100000 : max) + 1);
                        Row emp = Row.of("StationID", sid, "IsUsing", true, "Telephone", se.get("Telephone"), "Score", BigDecimal.ZERO,
                            "AuthID", se.get("Auth"), "Code", se.get("Code"), "CodeInner", Conv.toInt(se.get("Code")), "CertificateTypeID", 1,
                            "Address", se.get("Memo1"), "Name", se.get("Name"), "EmployeeNo", no, "Password", se.get("Memo2"),
                            "Memo", se.get("Memo2"), "PostCode", se.get("Auth"), "GenderID", 1);
                        Object id = db.insert("Employees", emp);
                        se.put("EmployeeID", id);
                        db.insert("GPRSAccount", Row.of("Auth", "7,8", "EmpID", id, "UserName", se.get("Name"), "Password", se.get("Memo2"),
                            "CompanyID", st.i("CompanyID"), "Phone", se.get("Telephone"), "CreateTime", LocalDateTime.now(), "TypeID", 2));
                        tx.commit();
                        return ret(true, Wire.json(se), 0);
                    }
                    case "Update": {
                        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", se.i("EmployeeID")));
                        if (emp == null) {
                            throw fail("Employee Error");
                        }
                        if (db.any("Employees", "{Name} = :n AND {StationID} = :s AND {EmployeeID} <> :e",
                            p("n", se.str("Name"), "s", sid, "e", se.i("EmployeeID")))) {
                            throw fail("Employee Repeat");
                        }
                        emp.put("StationID", sid);
                        emp.put("Telephone", se.get("Telephone"));
                        emp.put("AuthID", se.get("Auth"));
                        emp.put("Code", se.get("Code"));
                        emp.put("CodeInner", Conv.toInt(se.get("Code")));
                        emp.put("Address", se.get("Memo1"));
                        emp.put("Name", se.get("Name"));
                        emp.put("EmployeeNo", se.get("Code"));
                        emp.put("Password", se.get("Memo2"));
                        emp.put("Memo", se.get("Memo2"));
                        emp.put("PostCode", se.get("Auth"));
                        db.update("Employees", emp);
                        Row acc = db.first("GPRSAccount", "{EmpID} = :e AND {TypeID} = 2", p("e", emp.i("EmployeeID")));
                        if (acc != null) {
                            acc.put("UserName", se.get("Name"));
                            acc.put("Password", se.get("Memo2"));
                            acc.put("Phone", se.get("Telephone"));
                            db.update("GPRSAccount", acc);
                        }
                        tx.commit();
                        return okEmpty();
                    }
                    case "Delete": {
                        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", se.i("EmployeeID")));
                        if (emp == null) {
                            throw fail("Employee Error");
                        }
                        db.deleteEntity("Employees", emp);
                        db.delete("GPRSAccount", "{EmpID} = :e AND {TypeID} = 2", p("e", emp.i("EmployeeID")));
                        tx.commit();
                        return okEmpty();
                    }
                    default:
                        tx.commit();
                        return ret(false, "Type Error", 0);
                }
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, enc(ex.getMessage()), 0);
            }
        }
    }

    @RequestMapping("/GetNormalEmp")
    public String GetNormalEmp(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
            if (st == null) {
                throw fail("Empty");
            }
            return okJson(ent("Employees", db.list("Employees", "{StationID} = :s", p("s", st.i("StationID")))));
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/GetLogin")
    public String GetLogin(@RequestBody(required = false) String body) {
        try {
            Row user = req(body);
            Row emp = db.first("Employees", "{Address} = :u AND {Memo} = :pw", p("u", user.str("User"), "pw", user.str("Password")));
            if (emp == null) {
                throw fail("L'utilisateur n'existe pas");
            }
            Row st = stationById(emp.i("StationID"));
            if (st == null) {
                throw fail("Les informations de la station n'existent pas");
            }
            return okJson(deal.dto().make("FLoginUserReturn", "Result", true, "EmpID", emp.i("EmployeeID"), "Error", "",
                "StationCode", st.get("Code"), "CompanyID", st.i("CompanyID")));
        } catch (Exception ex) {
            return ret(false, enc(ex.getMessage()), 0);
        }
    }

    // ------------------------------------------------------------ numéros de cartes

    @RequestMapping("/GetCardNoMax")
    public String GetCardNoMax(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            String text = "";
            long num = 0;
            switch (modal.s("Mess")) {
                case "0" -> {
                    text = maxAsn("employee_card_infos", "010004");
                    num = maxLong("SELECT max(customer_account_id) FROM employee_card_infos WHERE left(asn, 6) = '010004'");
                    text = blank(text) ? "01000411000000000001" : text;
                    num = num == 0 ? 2010200001L : num;
                }
                case "1" -> {
                    text = maxAsn("customer_card_infos", "010001");
                    num = maxLong("SELECT max(customer_account_id) FROM employee_card_infos WHERE left(asn, 6) = '010001'");
                    text = blank(text) ? "01000111000000000001" : text;
                    num = num == 0 ? 2100000001L : num;
                }
                case "2" -> {
                    text = maxAsn("customer_card_infos", "010011");
                    num = maxLong("SELECT max(customer_account_id) FROM employee_card_infos WHERE left(asn, 6) = '010011'");
                    text = blank(text) ? "01001111000000000001" : text;
                    num = num == 0 ? 1700000001L : num;
                }
                case "3" -> {
                    text = maxAsn("group_card_infos", "010002");
                    num = maxLong("SELECT max(group_id) FROM group_card_infos WHERE left(asn, 6) = '010002'");
                    text = blank(text) ? "01000211000000000001" : text;
                    num = num == 0 ? 1900000001L : num;
                }
                case "4" -> {
                    text = maxAsn("group_card_infos", "010012");
                    num = maxLong("SELECT max(group_id) FROM group_card_infos WHERE left(asn, 6) = '010012'");
                    text = blank(text) ? "01001211000000000001" : text;
                    num = num == 0 ? 1800000001L : num;
                }
                case "5" -> {
                    text = maxAsn("group_card_infos", "010014");
                    num = maxLong("SELECT max(group_id) FROM group_card_infos WHERE left(asn, 6) = '010014'");
                    text = blank(text) ? "01001411000000000001" : text;
                    num = num == 0 ? 1400000001L : num;
                }
                case "6" -> {
                    text = maxAsn("customer_card_infos", "010013");
                    num = maxLong("SELECT max(customer_id) FROM customer_card_infos WHERE left(asn, 6) = '010013'");
                    text = blank(text) ? "01001311000000000001" : text;
                    num = num == 0 ? 1300000001L : num;
                }
                default -> {
                }
            }
            text = CenterDeal.setStrLength(String.valueOf(Long.parseLong(text) + 1), 20);
            num++;
            return okJson(deal.dto().make("FCardNoReturn", "CardNo", text, "CustomerID", num));
        } catch (Exception ex) {
            return okEmpty();
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }

    private String maxAsn(String table, String prefix) {
        return db.scalar("SELECT max(asn) FROM " + table + " WHERE left(asn, 6) = :p", p("p", prefix), String.class);
    }

    private long maxLong(String sql) {
        Long v = db.scalar(sql, null, Long.class);
        return v == null ? 0 : v;
    }

    @RequestMapping("/GetPreCardNoMax")
    public String GetPreCardNoMax(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row r = deal.dto().make("FCardNoReturn");
            Row pre = db.first("PreCardNo", "{UserID} = :u", p("u", modal.str("Mess")));
            r.put("CardNo", pre == null ? modal.s("Mess") + "00000000000001" : pre.get("Asn"));
            return okJson(r);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/UpdatePreCardNoMax")
    public String UpdatePreCardNoMax(@RequestBody(required = false) String body) {
        try {
            Row card = req(body);
            String head = card.s("ASN").substring(0, 6);
            String asn = CenterDeal.setStrLength(String.valueOf(Long.parseLong(card.s("ASN")) + 1), 20);
            while (deal.getCusCardInfo(asn).ok()) {
                asn = CenterDeal.setStrLength(String.valueOf(Long.parseLong(asn) + 1), 20);
            }
            Row pre = db.first("PreCardNo", "{UserID} = :u", p("u", head));
            if (pre == null) {
                db.insert("PreCardNo", Row.of("UserID", head, "Asn", asn));
            } else {
                pre.put("Asn", asn);
                db.update("PreCardNo", pre);
            }
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGreyCard")
    public String GetGreyCard(@RequestBody(required = false) String body) {
        try {
            Row cp = req(body);
            String[] a = cp.s("Mess").split("\\|", -1);
            String qAsn = a[0];
            String d = a[1];
            String dt = d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + " " + d.substring(8, 10) + ":"
                + d.substring(10, 12) + ":" + d.substring(12, 14);
            Row oil = db.first("OilTransactions", "{Asn} = :a AND {TrueTime} = :t AND {TType} <> 0 ORDER BY {TrueTime} DESC",
                p("a", qAsn, "t", Conv.toDateTime(dt)));
            if (oil == null) {
                throw fail("L'enregistrement n'existe pas");
            }
            return okJson(ent("OilTransactions", oil));
        } catch (Exception ex) {
            return ret(false, enc(ex.getMessage()), 0);
        }
    }

    @RequestMapping("/Active")
    public String Active(@RequestBody(required = false) String body, HttpServletRequest request) {
        try {
            Row m = req(body);
            if (!request.getServerName().equals(m.str("Path"))) {
                throw fail("Adresse erronée");
            }
            String text = LegacyAes.decrypt(m.s("ActiveMess"));
            try {
                Conv.toDateTime(text);
            } catch (RuntimeException e) {
                throw fail("Informations erronées");
            }
            Row a = db.first("ActiveWeb");
            if (a == null) {
                db.insert("ActiveWeb", Row.of("SecretInfo", text));
            } else {
                a.put("SecretInfo", text);
                db.update("ActiveWeb", a);
            }
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetAirDevice")
    public String GetAirDevice(@RequestBody(required = false) String body) {
        try {
            Row cp = req(body);
            return Wire.json(ent("AirDevice", db.page("AirDevice", null, null, "{CreateTime}", cp.i("Start"), 10).rows()));
        } catch (Exception ex) {
            return "";
        }
    }

    @RequestMapping("/AirDeviceActive")
    public String AirDeviceActive(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            Row dev = db.first("AirDevice", "{IsRun} = true AND {DeviceNo} = :d", p("d", m.str("mac")));
            if (dev == null) {
                throw fail("L'appareil n'existe pas");
            }
            dev.put("ValidFlag", 0);
            dev.put("ValidTime", m.get("DT"));
            db.update("AirDevice", dev);
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    // ------------------------------------------------------------ comptes RFID

    @RequestMapping("/GetRFIDAccount")
    public String GetRFIDAccount(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", param.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row pc = deal.dto().make("PersonCard");
            CenterDeal.CusCard c = deal.getCusCardInfo(param.s("Mess"));
            if (!c.ok()) {
                Integer max = db.scalar("SELECT max(customer_id) FROM customers WHERE customer_id > 900000000 AND customer_id < 1000000000",
                    null, Integer.class);
                pc.put("AccountNo", String.valueOf(max == null || max == 0 ? 900000000 : max));
            } else {
                Row cus = c.customers();
                Row info = c.cardInfos();
                pc.put("AccountNo", c.accounts().get("AccountNo"));
                pc.put("Name", cus.get("Name"));
                pc.put("Telephone", cus.get("Telephone"));
                pc.put("CertificateNo", cus.get("CertificateNo"));
                pc.put("VehicleNo", cus.get("VehicleNo"));
                pc.put("PostCode", cus.get("PostCode"));
                pc.put("Address", cus.get("Address"));
                pc.put("Email", cus.get("Email"));
                pc.put("Discount", Conv.toStr(info.get("Discount")));
                pc.put("LimitOil", String.valueOf(deal.getGradeID(info.str("LimitOil"))));
                pc.put("LimitTimeDay", Conv.toStr(info.get("LimitTime")));
                pc.put("LimitVolume", Conv.toStr(info.get("LimitVolume")));
                pc.put("LimitAmountDay", Conv.toStr(info.get("LimitAmount")));
                pc.put("LimitAmountMonth", Conv.toStr(info.get("LimitAmountMonthly")));
                pc.put("LimitVolumeMonth", Conv.toStr(info.get("LimitVolumeMonthly")));
                pc.put("isLimitVehicleNo", info.b("isLimitVehicleNo"));
            }
            return okJson(pc);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private static String zero(Row m, String k) {
        String s = m.str(k);
        return "".equals(s) ? "0" : s;
    }

    @RequestMapping("/CreateRFIDAccount")
    public String CreateRFIDAccount(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row pc = req(body);
                int id = Conv.toInt(pc.get("AccountNo"));
                Row cus = Row.of("CustomerID", id, "Name", pc.get("Name"), "Telephone", pc.get("Telephone"), "PostCode", pc.get("PostCode"),
                    "Email", pc.get("Email"), "VehicleNo", pc.get("VehicleNo"), "CertificateNo", pc.get("CertificateNo"),
                    "CertificateTypeID", 1, "Code", "900001", "GroupID", 100000001, "Address", pc.get("Address"));
                LocalDateTime now = LocalDateTime.now();
                Row info = Row.of("AccountNo", pc.get("AccountNo"), "ASN", pc.get("Asn"), "MediaTypeID", 1,
                    "LimitAmount", Conv.toDouble(zero(pc, "LimitAmountDay")), "LimitVolume", Conv.toDouble(zero(pc, "LimitVolume")),
                    "LimitTime", Conv.toInt(zero(pc, "LimitTimeDay")), "LimitAmountMonthly", Conv.toDouble(zero(pc, "LimitAmountMonth")),
                    "LimitVolumeMonthly", Conv.toDouble(zero(pc, "LimitVolumeMonth")), "isLimitVehicleNo", pc.b("isLimitVehicleNo"),
                    "LimitVehicleNo", pc.get("VehicleNo"), "UpdateDatetime", now, "UsedAmountMonthly", 0.0, "CardStatusTypeID", 0,
                    "CustomerAccountID", id, "CustomerID", id, "Discount", pc.d("Discount"), "GroupID", 100000001);
                Row acc = Row.of("AccountNo", pc.get("AccountNo"), "Balance", 0.0, "CustomerAccountID", id, "UpdateDatetime", now,
                    "DepositDatetime", now, "ConsumeDatetime", now, "TransTime", now);
                if (db.any("CustomerCardInfos", "{ASN} = :a", p("a", pc.str("Asn")))) {
                    db.update("Customers", cus);
                    db.update("CustomerCardInfos", info);
                } else {
                    db.insert("Customers", cus);
                    db.insert("CustomerCardInfos", info);
                    db.insert("CustomerAccounts", acc);
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ impulsions et recharges en ligne

    @RequestMapping("/GetPluse")
    public String GetPluse(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            Row st = db.first("Stations", "{Code} = :c", p("c", param.str("Code")));
            if (st == null) {
                throw fail("Requête illégale");
            }
            Row pl = db.first("StationPluse", "{StationID} = :s AND {Dowload} = false", p("s", st.i("StationID")));
            if (pl != null) {
                pl.put("Dowload", true);
                db.update("StationPluse", pl);
            }
            return okJson(ent("StationPluse", pl));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private BigDecimal pendingOnline(String asn) {
        BigDecimal v = db.scalar("SELECT coalesce(sum(amount), 0) FROM online_deposit WHERE asn = :a AND is_using = 0", p("a", asn),
            BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    @RequestMapping("/GetCardDeposit")
    public String GetCardDeposit(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", param.str("Code")))) {
                throw fail("Requête illégale");
            }
            return okJson(pendingOnline(param.s("Mess")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/OnlineDepositChange")
    public String OnlineDepositChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row user = req(body);
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", user.i("EmpID")));
                if (emp == null) {
                    throw fail("L'employé n'existe pas : requête illégale");
                }
                if (!db.any("Stations", "{Code} = :c", p("c", user.str("Code")))) {
                    throw fail("Requête illégale");
                }
                BigDecimal num = pendingOnline(user.s("Asn"));
                BigDecimal amount = num.compareTo(user.dec("Amount")) != 0 ? num : user.dec("Amount");
                CenterDeal.CusCard c = deal.getCusCardInfo(user.s("Asn"));
                if (!c.ok()) {
                    throw fail(c.error());
                }
                c.accounts().put("Balance", c.accounts().d("Balance") + Conv.round(amount.doubleValue(), 3));
                c.accounts().put("UpdateDatetime", LocalDateTime.now());
                db.update("CustomerAccounts", c.accounts());
                db.exec("UPDATE online_deposit SET is_using = 20 WHERE asn = :a AND is_using = 0", p("a", user.s("Asn")));
                deal.addAccountLog(c.cardInfos().s("AccountNo"), Conv.round(amount.doubleValue(), 3), user.s("Asn"),
                    Conv.round(c.accounts().d("Balance"), 3), 2, BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                tx.commit();
                return okJson(num);
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ groupes de véhicules, véhicules, bouteilles

    @RequestMapping("/GetCarGroupList")
    public String GetCarGroupList(@RequestBody(required = false) String body) {
        return okEmpty();
    }

    @RequestMapping("/CarGroupUpdate")
    public String CarGroupUpdate(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String mess = "";
                Row request = deal.dto().shape("CarGroupRequest", req(body));
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", request.i("EmpID")));
                if (emp == null) {
                    throw fail("Erreur de connexion de l'employé");
                }
                switch (request.s("UpdateType")) {
                    case "AddQ", "Add" -> {
                        Row g = db.first("CarGroup", "{CarGroupName} = :n", p("n", request.str("CarGroupName")));
                        if (g != null) {
                            if (request.s("UpdateType").equals("AddQ")) {
                                String m = enc(Wire.json(ent("CarGroup", g)));
                                tx.commit();
                                return ret(true, m, 0);
                            }
                            throw fail("Nom de groupe en double");
                        }
                        g = Row.of("Balance", BigDecimal.ZERO, "CarGroupAddress", request.get("CarGroupAddress"),
                            "CarGroupCreditCode", request.get("CarGroupCreditCode"), "CarGroupName", request.get("CarGroupName"),
                            "CompanyID", 0, "Createtime", CenterDeal.now(), "UpdateTime", CenterDeal.now(), "Phone", request.get("Phone"));
                        Object id = db.insert("CarGroup", g);
                        request.put("ID", id);
                        mess = enc(Wire.json(request));
                        deal.addAccountLog(Conv.toStr(id), 0.0, g.s("CarGroupName"), 0.0, 1, BigDecimal.ZERO, 0.0, emp, 1, nowStr(),
                            emp.i("StationID"));
                    }
                    case "Delete" -> {
                        Row g = db.first("CarGroup", "{ID} = :id", p("id", request.i("ID")));
                        if (g != null) {
                            db.deleteEntity("CarGroup", g);
                        }
                    }
                    case "Get" -> {
                        Row g = db.first("CarGroup", "{ID} = :id", p("id", request.i("ID")));
                        if (g == null) {
                            throw fail("Le groupe n'existe pas");
                        }
                        mess = enc(Wire.json(ent("CarGroup", g)));
                    }
                    case "DeleteAll" -> {
                        Row g = db.first("CarGroup", "{ID} = :id", p("id", request.i("ID")));
                        if (g == null) {
                            throw fail("Le groupe n'existe pas");
                        }
                        List<Row> users = db.list("CarUser", "{CarGroupID} = :g", p("g", request.i("ID")));
                        for (Row u : users) {
                            db.delete("CustomerAccounts", "{CustomerAccountID} = :a", p("a", u.i("CustomerAccountID")));
                            db.delete("Cylinder", "{CarUserID} = :u", p("u", u.i("ID")));
                            db.deleteEntity("CarUser", u);
                        }
                        db.deleteEntity("CarGroup", g);
                    }
                    default -> {
                    }
                }
                tx.commit();
                return ret(true, mess, 0);
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CarUpdate")
    public String CarUpdate(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String mess = "";
                Row request = deal.dto().shape("CarUserRequest", req(body));
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", request.i("EmpID")));
                if (emp == null) {
                    throw fail("Informations de l'employé erronées");
                }
                switch (request.s("UpdateType")) {
                    case "Add" -> {
                        if (db.any("CarUser", "{PlateNumber} = :p", p("p", request.str("PlateNumber")))) {
                            throw fail("Immatriculation en double");
                        }
                        Row u = Row.of("UserName", request.get("UserName"), "PlateNumber", request.get("PlateNumber"),
                            "SecurityAdmin", request.get("SecurityAdmin"), "StartDate", request.get("StartDate"),
                            "ApplicantUser", request.get("ApplicantUser"), "ApplicantUserId", request.get("ApplicantUserId"),
                            "Balance", BigDecimal.ZERO, "CarGroupID", request.i("CarGroupID"), "MobilePhone", request.get("MobilePhone"),
                            "ReviewPassed", request.i("ReviewPassed"), "UpdateTime", CenterDeal.now(), "VehicleType", request.get("VehicleType"),
                            "Asn", request.get("Asn"), "CustomerAccountID", Conv.toInt(request.get("Account")));
                        request.put("ID", db.insert("CarUser", u));
                        mess = enc(Wire.json(request));
                        LocalDateTime now = LocalDateTime.now();
                        Row acc = Row.of("CustomerAccountID", u.i("CustomerAccountID"), "AccountNo", request.get("Account"),
                            "TotalAmountDaily", 0.0, "ServiceFee", 0.0, "TotalAmountDailyStore", 0.0, "TotalAmountMonthly", 0.0,
                            "TotalAmountMonthlyStore", 0.0, "Balance", 0.0, "Status", "0", "BookBalance", 0.0, "CompanyID", 0,
                            "ConsumeDatetime", now, "DepositDatetime", now, "LoadUpLimit", 0.0, "MileageLastest", 0.0, "TotalTimesDaily", 0,
                            "TotalTimesDailyStore", 0, "TotalVolumeMonthly", 0.0, "TransTime", now, "UpdateDatetime", now, "VolumeLastest", 0.0);
                        db.insert("CustomerAccounts", acc);
                        deal.addAccountLog(acc.s("AccountNo"), 0.0, request.s("Asn"), 0.0, 1, BigDecimal.ZERO, 0.0, emp, 1, nowStr(),
                            emp.i("StationID"));
                    }
                    case "Delete" -> {
                        Row u = db.first("CarUser", "{ID} = :id", p("id", request.i("ID")));
                        if (u == null) {
                            throw fail("L'utilisateur n'existe pas");
                        }
                        db.delete("CustomerAccounts", "{CustomerAccountID} = :a", p("a", u.i("CustomerAccountID")));
                        db.deleteEntity("CarUser", u);
                    }
                    default -> {
                    }
                }
                tx.commit();
                return ret(true, mess, 0);
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    private static final String[] CYL = {"VehicleID", "CylinderType", "CylinderTypeId", "ProductName", "MediumId", "Medium",
        "WorkPressure", "Cubage", "WallThickness", "ManufactureId", "ManufactureName", "ProductionDate", "ProductNo", "OwnedNo",
        "MjDate", "XjDate", "BfDate", "CarUserID"};

    @RequestMapping("/CylinderUpdate")
    public String CylinderUpdate(@RequestBody(required = false) String body) {
        try {
            String mess = "";
            Row request = deal.dto().shape("CylinderRequest", req(body));
            Row cyl = db.first("Cylinder", "{CylinderId} = :c", p("c", request.str("cylinderId")));
            request.put("UpdateType", cyl == null ? "Add" : "Update");
            if (cyl == null) {
                Row c = new Row();
                for (String k : CYL) {
                    c.put(k, request.get(k));
                }
                c.put("CreateTime", CenterDeal.now());
                c.put("UpdateTime", CenterDeal.now());
                c.put("CylinderId", request.get("cylinderId"));
                request.put("ID", db.insert("Cylinder", c));
                mess = enc(Wire.json(request));
            } else {
                for (String k : CYL) {
                    cyl.put(k, request.get(k));
                }
                cyl.put("UpdateTime", CenterDeal.now());
                cyl.put("CylinderId", request.get("cylinderId"));
                db.update("Cylinder", cyl);
                request.put("ID", cyl.i("ID"));
                mess = enc(Wire.json(request));
            }
            return ret(true, mess, 0);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** CarGroupToCar : transfert du solde d'un groupe vers un véhicule. */
    private String carGroupToCar(Row group, Row user, BigDecimal amount, Row emp) {
        try {
            if (group.dec("Balance").compareTo(amount) >= 0) {
                group.put("Balance", group.dec("Balance").subtract(amount));
                Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :a", p("a", user.i("CustomerAccountID")));
                if (acc != null) {
                    acc.put("Balance", acc.d("Balance") + Conv.round(amount.doubleValue(), 3));
                    acc.put("UpdateDatetime", LocalDateTime.now());
                    db.update("CustomerAccounts", acc);
                }
                user.put("Balance", user.dec("Balance").add(amount));
                user.put("UpdateTime", CenterDeal.now());
                db.update("CarUser", user);
                deal.addAccountLog(String.valueOf(user.i("CustomerAccountID")), Conv.round(amount.doubleValue(), 3), user.s("Asn"),
                    Conv.round(user.d("Balance"), 3), 10, BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                group.put("UpdateTime", CenterDeal.now());
                db.update("CarGroup", group);
                deal.addAccountLog(String.valueOf(group.i("ID")), Conv.round(amount.doubleValue(), 3), group.s("CarGroupName"),
                    Conv.round(group.d("Balance"), 3), 16, BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                return null;
            }
            return "Solde insuffisant";
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
    }

    @RequestMapping("/GroupBalanceChange")
    public String GroupBalanceChange(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row request = req(body);
                Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", request.i("EmpID")));
                if (emp == null) {
                    throw fail("Erreur d'employé");
                }
                Row g = db.first("CarGroup", "{ID} = :id", p("id", request.i("GroupID")));
                if (g == null) {
                    throw fail("Le groupe n'existe pas");
                }
                BigDecimal amount = request.dec("Amount").setScale(3, RoundingMode.HALF_EVEN);
                switch (request.s("UpdateType")) {
                    case "Deposit", "Red" -> {
                        boolean deposit = request.s("UpdateType").equals("Deposit");
                        g.put("Balance", deposit ? g.dec("Balance").add(amount) : g.dec("Balance").subtract(amount));
                        g.put("UpdateTime", CenterDeal.now());
                        db.update("CarGroup", g);
                        deal.addAccountLog(String.valueOf(g.i("ID")), Conv.round(request.d("Amount"), 3), g.s("CarGroupName"),
                            Conv.round(g.d("Balance"), 3), deposit ? 2 : 12, BigDecimal.ZERO, 0.0, emp, 1, nowStr(), emp.i("StationID"));
                    }
                    case "AVG" -> {
                        List<Row> users = db.list("CarUser", "{CarGroupID} = :g", p("g", g.i("ID")));
                        if (users.isEmpty()) {
                            throw fail("Aucune carte rattachée");
                        }
                        for (Row u : users) {
                            BigDecimal share = g.dec("Balance").divide(BigDecimal.valueOf(users.size()), 20, RoundingMode.HALF_EVEN);
                            String error = carGroupToCar(g, u, share, emp);
                            if (error != null) {
                                throw fail(error);
                            }
                        }
                    }
                    default -> {
                    }
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CarBalanceUpdate")
    public String CarBalanceUpdate(@RequestBody(required = false) String body) {
        try {
            Row request = req(body);
            Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", request.i("EmpID")));
            if (emp == null) {
                throw fail("Erreur d'employé");
            }
            Row g = db.first("CarGroup", "{ID} = :id", p("id", request.i("GroupID")));
            if (g == null) {
                throw fail("Le groupe émetteur n'existe pas");
            }
            Row u = db.first("CarUser", "{ID} = :id", p("id", request.i("UserID")));
            if (u == null) {
                throw fail("Le destinataire n'existe pas");
            }
            if (u.i("CarGroupID") != g.i("ID")) {
                throw fail("Le destinataire n'appartient pas à ce groupe");
            }
            if (g.dec("Balance").compareTo(request.dec("Amount")) < 0) {
                throw fail("Solde du groupe insuffisant, solde actuel : " + g.dec("Balance").toPlainString());
            }
            String error = carGroupToCar(g, u, request.dec("Amount"), emp);
            if (error != null) {
                throw fail(error);
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    private String downloadCars(String body, String table) {
        try {
            Row cp = req(body);
            String where = cp.b("DowloadAll") ? null : "{UpdateTime} > :t";
            Sugar.Page pg = db.page(table, where, p("t", CenterDeal.now() - 43200000L), "{ID}", cp.i("Start"), 100);
            return okJson(ent(table, pg.rows()), pg.totalPage());
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/DowLoadCarGroup")
    public String DowLoadCarGroup(@RequestBody(required = false) String body) {
        return downloadCars(body, "CarGroup");
    }

    @RequestMapping("/DowLoadCarUser")
    public String DowLoadCarUser(@RequestBody(required = false) String body) {
        return downloadCars(body, "CarUser");
    }

    @RequestMapping("/DowLoadCarCY")
    public String DowLoadCarCY(@RequestBody(required = false) String body) {
        return downloadCars(body, "Cylinder");
    }

    // ------------------------------------------------------------ codes App, préréglages, NP (corps JSON non chiffré)

    @RequestMapping("/DealAppInput")
    public String DealAppInput(@RequestBody(required = false) String body) {
        try {
            Row app = Wire.parseRow(body);
            CenterDeal.CusCard c = deal.getCusCardInfo(app.s("CardNo"));
            if (!c.ok()) {
                throw fail(c.error());
            }
            Row use = db.first("AppCodeUse", "({Asn} = :a AND {IsUsing} = 0) OR {IsUsing} = 1", p("a", app.s("CardNo")));
            if (use != null) {
                return ret(true, Wire.json(deal.dto().make("AppCodeReturn", "AppCode", use.get("AppCode"), "AppCodeShort",
                    use.get("AppCodeShort"))), 0);
            }
            Row info = c.cardInfos();
            Row acc = c.accounts();
            BigDecimal bal = acc.dec("Balance").setScale(3, RoundingMode.HALF_EVEN);
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal volume = BigDecimal.ZERO;
            if (info.i("LimitTime") != 0 && acc.i("TotalTimesDaily") >= info.i("LimitTime")) {
                throw fail("Daily Count Limited");
            }
            if (info.d("LimitAmount") != 0.0 && acc.d("TotalAmountDaily") >= info.d("LimitAmount")) {
                throw fail("Daily Amount Limited");
            }
            if (info.d("LimitVolumeMonthly") != 0.0 && acc.d("TotalVolumeMonthly") >= info.d("LimitVolumeMonthly")) {
                throw fail("Monthly Volume Limited");
            }
            if (info.d("LimitAmountMonthly") != 0.0 && acc.d("TotalAmountMonthly") >= info.d("LimitAmountMonthly")) {
                throw fail("Monthly Amount Limited");
            }
            if (info.get("LimitVolume") != null && info.d("LimitVolume") != 0.0) {
                volume = info.get("LimitVolumeMonthly") != null && info.d("LimitVolumeMonthly") != 0.0
                    ? Conv.toDecimal(Math.min(info.d("LimitVolume"), info.d("LimitVolumeMonthly") - acc.d("TotalVolumeMonthly")))
                    : Conv.toDecimal(info.d("LimitVolume"));
            }
            if (info.get("LimitAmount") != null && info.d("LimitAmount") != 0.0) {
                amount = info.get("LimitAmountMonthly") != null && info.d("LimitAmountMonthly") != 0.0
                    ? Conv.toDecimal(Math.min(info.d("LimitAmountMonthly") - acc.d("TotalAmountMonthly"),
                        info.d("LimitAmount") - acc.d("TotalAmountDaily")))
                    : Conv.toDecimal(info.d("LimitAmount") - acc.d("TotalAmountDaily"));
            }
            amount = amount.signum() != 0 ? CenterDeal.min(amount, bal) : bal;
            volume = volume.signum() > 0 ? volume.min(app.dec("Volume")) : app.dec("Volume");
            String code = deal.createAppCode();
            if (bal.signum() <= 0 || amount.signum() <= 0) {
                throw fail("Balance is zero");
            }
            Row use2 = Row.of("AppCode", CenterDeal.now() + code, "AppCodeShort", code, "Asn", app.get("CardNo"), "Amount", amount,
                "Volume", volume, "Balance", bal, "CreateTime", CenterDeal.now(), "LimitOil", info.get("LimitOil"), "IsUsing", 0);
            db.insert("AppCodeUse", use2);
            return ret(true, Wire.json(deal.dto().make("AppCodeReturn", "AppCode", use2.get("AppCode"), "AppCodeShort", code)), 0);
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/DealStart")
    public String DealStart(@RequestBody(required = false) String body) {
        try {
            Row modal = Wire.parseRow(body);
            if (db.any("CmdList", "{CmdPlatType} = 3 AND {Deal} <> 99 AND {StationID} = :s AND {NozID} = :n",
                p("s", modal.i("StationID"), "n", modal.i("NozID")))) {
                throw fail("Previous processing is not completed");
            }
            Row cmd = new Row();
            Row dev = db.first("AirDevice", "{StationID} = :s AND {NozID} = :n", p("s", modal.i("StationID"), "n", modal.i("NozID")));
            if (dev != null) {
                cmd.put("DeviceNo", dev.get("DeviceNo"));
            }
            cmd.put("StationID", modal.i("StationID"));
            cmd.put("NozID", modal.i("NozID"));
            cmd.put("CmdPlatType", 3);
            cmd.put("CmdType", "62");
            cmd.put("CreateTime", CenterDeal.now());
            cmd.put("Deal", 0);
            cmd.put("Dowload", false);
            cmd.put("CmdData", Wire.json(modal));
            Object id = db.insert("CmdList", cmd);
            modal.put("MarkPoint", Conv.toStr(id));
            cmd.put("ID", id);
            cmd.put("CmdData", Wire.json(modal));
            db.update("CmdList", cmd);
            return okEmpty();
        } catch (Exception ex) {
            return ret(true, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/GetAllStation")
    public String GetAllStation(@RequestBody(required = false) String body) {
        try {
            List<Row> out = new ArrayList<>();
            for (Row s : db.list("Stations")) {
                out.add(Row.of("StationID", s.i("StationID"), "Name", s.get("Name"), "Longitude", s.get("Longitude"),
                    "Latitude", s.get("Latitude")));
            }
            return ret(true, Wire.json(out), 0);
        } catch (Exception ex) {
            return ret(true, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/GetCardMessageApi")
    public String GetCardMessageApi(@RequestBody(required = false) String body) {
        try {
            Wire.parseRow(body);
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/CreateNpCustomer")
    public String CreateNpCustomer(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row r = Wire.parseRow(body);
                String np = r.s("NP_ValueID");
                Row cus = db.first("Customers", "{NP_Value} = :v", p("v", np));
                Row info = db.first("CustomerCardInfos", "{NP_Value} = :v", p("v", np));
                Row acc = db.first("CustomerAccounts", "{NP_Value} = :v", p("v", np));
                if (cus != null && info != null && acc != null) {
                    throw fail("Account already exists");
                }
                LocalDateTime now = LocalDateTime.now();
                if (cus == null) {
                    Integer max = db.scalar("SELECT max(customer_id) FROM customers WHERE customer_id > 100000000 AND customer_id <= 199999999",
                        null, Integer.class);
                    int id = max == null || max == 0 ? 100000001 : max + 1;
                    cus = Row.of("CustomerID", id, "Address", r.get("NP_Address"), "Birthday", now, "CertificateTypeID", 1,
                        "CertificateNo", r.get("NP_CertificateNo"), "Name", r.get("NP_Name"), "PostCode", "", "Telephone", r.get("NP_Phone"),
                        "VehicleNo", "", "VehicleColorID", 1, "VehicleTypeID", 1, "Code", String.valueOf(id), "GroupID", 100000001,
                        "Email", r.get("NP_Email"), "NP_Value", np, "CompanyID", 0);
                    db.insert("Customers", cus);
                }
                if (info == null) {
                    String max = db.scalar("SELECT max(asn) FROM customer_card_infos WHERE customer_id > 100000000 AND customer_id <= 199999999",
                        null, String.class);
                    String asn = max == null || max.isEmpty() ? "11000100000000000001" : new BigDecimal(max).add(BigDecimal.ONE).toPlainString();
                    int id = cus.i("CustomerID");
                    info = Row.of("AccountNo", String.valueOf(id), "ASN", asn, "CardStatusTypeID", 0, "CipherFlg", "", "CustomerAccountID", id,
                        "CreditLines", 0.0, "CustomerID", id, "Discount", 100.0, "GroupID", 100000001, "isCreditCard", false,
                        "isLimitVehicleNo", false, "IssueDatetime", now, "LimitAmount", 0.0, "LimitAmountMonthly", 0.0, "LimitMileage", 0.0,
                        "LimitOil", "", "LimitTime", 0, "LimitTypeID", 0, "LimitVehicleNo", "", "LimitVolume", 0.0, "LimitVolumeMonthly", 0.0,
                        "MileageTolerance", 0.0, "PreferentialPrice", 0.0, "Score", BigDecimal.ZERO, "StationID", 0, "UpdateDatetime", now,
                        "UseConfine", "", "UsedAmountMonthly", 0.0, "ValidDatetime", now.plusYears(5), "MediaTypeID", 1, "SpecialPrice", "",
                        "NP_Value", np);
                    db.insert("CustomerCardInfos", info);
                }
                if (acc == null) {
                    int id = cus.i("CustomerID");
                    acc = Row.of("CustomerAccountID", id, "AccountNo", String.valueOf(id), "Balance", 0.0, "BookBalance", 0.0,
                        "ConsumeDatetime", now, "DepositDatetime", now, "LoadUpLimit", 0.0, "MileageLastest", 0.0, "ServiceFee", 0.0,
                        "Status", "0", "TotalAmountDaily", 0.0, "TotalAmountDailyStore", 0.0, "TotalAmountMonthly", 0.0,
                        "TotalAmountMonthlyStore", 0.0, "TotalTimesDaily", 0, "TotalTimesDailyStore", 0, "TotalVolumeMonthly", 0.0,
                        "TransTime", now, "UpdateDatetime", now, "VolumeLastest", 0.0, "NP_Value", np);
                    db.insert("CustomerAccounts", acc);
                }
                deal.addAccountLog(info.s("AccountNo"), 0.0, info.s("ASN"), 0.0, 1, BigDecimal.ZERO, 0.0, null, 1, nowStr(), 0);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, ex.getMessage(), 0);
            }
        }
    }

    @RequestMapping("/NP_Deposit")
    public String NP_Deposit(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row r = Wire.parseRow(body);
                CenterDeal.CusCard c = deal.getCusCardInfoByNPValue(r.s("NP_ValueID"));
                if (!c.ok()) {
                    throw fail(c.error());
                }
                double amount = Conv.round(r.d("NP_Amount"), 3);
                c.accounts().put("Balance", c.accounts().d("Balance") + amount);
                c.accounts().put("UpdateDatetime", LocalDateTime.now());
                db.update("CustomerAccounts", c.accounts());
                deal.addAccountLog(c.cardInfos().s("AccountNo"), amount, c.cardInfos().s("ASN"), c.accounts().d("Balance"), 2,
                    BigDecimal.ZERO, 0.0, null, 1, nowStr(), 0);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return ret(false, ex.getMessage(), 0);
            }
        }
    }
}
