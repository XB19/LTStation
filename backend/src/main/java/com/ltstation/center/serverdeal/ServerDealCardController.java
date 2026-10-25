package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (3/…) : régions, villes, stations, carburants, employés,
 * création / modification des cartes (employé, utilisateur, crédit, groupe), numéros suivants
 * et fiches de cartes.
 */
@RestController
@RequestMapping(value = "/ServerDeal", produces = ServerDealBase.TEXT)
public class ServerDealCardController extends ServerDealBase {

    private final boolean secretSame;

    public ServerDealCardController(@Qualifier("centerSugar") Sugar db, CenterDeal deal,
                                    @Value("${app.discount-per-company:true}") boolean secretSame) {
        super(db, deal);
        this.secretSame = secretSame;
    }

    /** (modal.X == "") ? 0 : Convert.ToInt32(modal.X) */
    private static int iz(Row m, String k) {
        String s = m.str(k);
        return s == null || s.isEmpty() ? 0 : Conv.toInt(s);
    }

    private static int iOne(Row m, String k) {
        String s = m.str(k);
        return "".equals(s) ? 1 : Conv.toInt(s);
    }

    private static double dz(Row m, String k) {
        String s = m.str(k);
        return s == null || s.isEmpty() ? 0.0 : Conv.toDouble(s);
    }

    private static String fmt(Object v) {
        return Conv.toDateTime(v).format(Conv.STD);
    }

    private static String nowStr() {
        return LocalDateTime.now().format(Conv.STD);
    }

    /** CheckTypeAndNo : vrai si aucun client actif n'utilise déjà cette pièce d'identité. */
    private boolean checkTypeAndNo(int typeId, String no) {
        List<Row> list = db.list("Customers", "{CertificateTypeID} = :t AND {CertificateNo} = :n", p("t", typeId, "n", no));
        for (Row c : list) {
            if (db.any("CustomerCardInfos", "{CustomerID} = :c AND {CardStatusTypeID} = 0", p("c", c.i("CustomerID")))) {
                return false;
            }
        }
        return true;
    }

    private Row accountLog(String asn, double amount, String accountNo, double balance, int cardType, int empId, int stationId,
                           int transType, String processCode) {
        return Row.of("ASN", asn, "Amount", amount, "AccountNo", accountNo, "AccountDatetime", nowStr(), "Balance", balance,
            "CardTypeID", cardType, "CardServiceLogID", 1, "EmployeeID", empId, "FeeAmount", 0.0, "StationID", stationId,
            "TransactionTypeID", transType, "TransDatetime", nowStr(), "ProcessCode", processCode,
            "TerminalNo", "000000000001", "PaymentID", 1);
    }

    private static Row newAccount(Object id, String accountNo, double balance) {
        LocalDateTime now = LocalDateTime.now();
        return Row.of("CustomerAccountID", id, "AccountNo", accountNo, "Balance", balance, "BookBalance", 0.0,
            "ConsumeDatetime", now, "DepositDatetime", now, "LoadUpLimit", 0.0, "MileageLastest", 0.0, "ServiceFee", 0.0,
            "Status", "0", "TotalAmountDaily", 0.0, "TotalAmountDailyStore", 0.0, "TotalAmountMonthly", 0.0,
            "TotalAmountMonthlyStore", 0.0, "TotalTimesDaily", 0, "TotalTimesDailyStore", 0, "TotalVolumeMonthly", 0.0,
            "TransTime", now, "UpdateDatetime", now, "VolumeLastest", 0.0);
    }

    private void bumpWhite() {
        Row dv = db.first("DataVersions");
        dv.put("White", dv.i("White") + 1);
        db.update("DataVersions", dv);
    }

    // ------------------------------------------------------------ régions, villes, stations

    @RequestMapping("/GetRegionMax")
    public String GetRegionMax(@RequestBody(required = false) String body) {
        try {
            Row r = db.first("Regions", "ORDER BY {CodeInner} DESC", null);
            return okJson(r == null ? deal.dto().make("Modal.Regions") : ent("Regions", r));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCityMax")
    public String GetCityMax(@RequestBody(required = false) String body) {
        try {
            Row r = db.first("Cities", "ORDER BY {Code} DESC", null);
            return okJson(r == null ? deal.dto().make("Modal.Cities") : ent("Cities", r));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetStationMax")
    public String GetStationMax(@RequestBody(required = false) String body) {
        try {
            Row s = deal.dto().make("Modal.Stations");
            Integer max = db.scalar("SELECT max(CASE WHEN code ~ '^\\s*-?\\d+\\s*$' THEN trim(code)::int END) FROM stations", null,
                Integer.class);
            s.put("Code", max == null ? null : max.toString());
            return okJson(s);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CreateRegion")
    public String CreateRegion(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (db.any("Regions", "{Code} = :c OR {RegionDesc} = :d", p("c", modal.str("Code"), "d", modal.str("RegionDesc")))) {
                    throw fail("Ce code ou ce nom est déjà utilisé, inutile de le créer");
                }
                modal.remove("RegionID");
                db.insert("Regions", modal);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CreateCity")
    public String CreateCity(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                modal.remove("CityID");
                db.insert("Cities", modal);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CreateStation")
    public String CreateStation(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (modal.isEmpty()) {
                    throw fail("Informations incomplètes");
                }
                Row st = Row.of("Address", modal.get("Address"), "Name", modal.get("Name"), "Code", modal.get("Code"),
                    "CityID", modal.i("CityID"), "Latitude", modal.get("Latitude"), "Longitude", modal.get("Longitude"),
                    "Manager", modal.get("Manager"), "Phone", modal.get("Tel"), "Email1", modal.get("email"), "Is_Active", 1,
                    "StationTypeID", 1, "TransportInfoID", 11);
                if (db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                    throw fail("La station existe déjà");
                }
                Object id = db.insert("Stations", st);
                tx.commit();
                return okText(Conv.toStr(id));
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/GetRegion")
    public String GetRegion(@RequestBody(required = false) String body) {
        try {
            return okJson(ent("Regions", db.list("Regions")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCity")
    public String GetCity(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row region = db.first("Regions", "{CodeInner} = :c", p("c", modal.str("CodeInner")));
            if ("203045".equals(modal.str("Code"))) {
                return okJson(ent("Cities", db.list("Cities", "ORDER BY {CodeInner} DESC", null)));
            }
            if (region == null) {
                throw fail("");
            }
            return okJson(ent("Cities", db.list("Cities", "{RegionID} = :r ORDER BY {CodeInner} DESC", p("r", region.i("RegionID")))));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCityList")
    public String GetCityList(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            return okJson(ent("Cities", db.list("Cities", "ORDER BY {CityID}", null)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetStationList")
    public String GetStationList(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            return okJson(ent("Stations", db.list("Stations", "ORDER BY {StationID}", null)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CreateOilType")
    public String CreateOilType(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row g = req(body);
                g.put("Alias", g.get("Name"));
                db.insert("GradeSpecifics", g);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ employés

    @RequestMapping("/CreateEmployee")
    public String CreateEmployee(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                int stationId = modal.i("StationID");
                Row st = db.first("Stations", "{StationID} = :s", p("s", stationId));
                Integer maxInner = db.scalar("SELECT max(code_inner) FROM employees WHERE station_id = :s", p("s", stationId), Integer.class);
                String maxNo = db.scalar("SELECT max(employee_no) FROM employees", null, String.class);
                if (st == null) {
                    throw fail("Requête illégale");
                }
                if (modal.i("EmployeeID") != 0) {
                    Row ep = db.first("Employees", "{EmployeeID} = :e", p("e", modal.i("EmployeeID")));
                    if (ep == null) {
                        throw fail("L'utilisateur n'existe pas");
                    }
                    Row acc = db.first("GPRSAccount", "{UserName} = :u AND {Password} = :pw", p("u", ep.str("Name"), "pw", ep.str("Memo")));
                    ep.put("Memo", modal.get("Memo"));
                    ep.put("Name", modal.get("Name"));
                    ep.put("Address", modal.get("Address"));
                    ep.put("PostCode", modal.get("PostCode"));
                    ep.put("StationID", stationId);
                    ep.put("CenterLogin", modal.b("CenterLogin"));
                    db.update("Employees", ep);
                    if (modal.b("CenterLogin")) {
                        if (acc != null) {
                            acc.put("UserName", ep.get("Name"));
                            acc.put("Password", ep.get("Memo"));
                            acc.put("TypeID", 1);
                            db.update("GPRSAccount", acc);
                        } else {
                            db.insert("GPRSAccount", Row.of("UserName", ep.get("Name"), "Password", ep.get("Memo"), "TypeID", 1));
                        }
                    } else if (acc != null) {
                        db.deleteEntity("GPRSAccount", acc);
                    }
                } else {
                    if (db.any("Employees", "{StationID} = :s AND {Address} = :a AND {Memo} = :m",
                        p("s", stationId, "a", modal.str("Address"), "m", modal.str("Memo")))) {
                        throw fail("Nom d'utilisateur en double");
                    }
                    modal.put("CodeInner", (maxInner == null ? 0 : maxInner) + 1);
                    modal.put("EmployeeNo", maxNo != null && !maxNo.isEmpty() ? String.valueOf(Conv.toInt(maxNo) + 1) : "100001");
                    modal.put("CertifyKey", "");
                    modal.put("CertificateTypeID", 1);
                    modal.put("GenderID", 1);
                    modal.put("Birthday", nowStr());
                    modal.remove("EmployeeID");
                    db.insert("Employees", modal);
                    Row acc = db.first("GPRSAccount", "{UserName} = :u AND {Password} = :pw",
                        p("u", modal.str("Address"), "pw", modal.str("Memo")));
                    if (modal.b("CenterLogin")) {
                        if (acc == null) {
                            db.insert("GPRSAccount", Row.of("UserName", modal.get("Address"), "Password", modal.get("Memo"), "TypeID", 1));
                        } else {
                            acc.put("UserName", modal.get("Address"));
                            acc.put("Password", modal.get("Memo"));
                            acc.put("TypeID", 1);
                            db.update("GPRSAccount", acc);
                        }
                    } else if (acc != null) {
                        db.deleteEntity("GPRSAccount", acc);
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

    // ------------------------------------------------------------ numéros suivants

    /** Numéros maximaux d'une série d'ASN (préfixe) : { ASN, &lt;colonne&gt; } avec repli sur une valeur initiale. */
    private Row maxOf(String cls, String table, String prefix, String idCol, String asnCol, String firstAsn, long firstId,
                      String orderCol) {
        Row any = db.first(table, "left({" + asnCol + "}, 6) = :p ORDER BY {" + orderCol + "} DESC", p("p", prefix));
        Row out = deal.dto().make(cls);
        if (any == null || "".equals(any.str(asnCol))) {
            out.put(asnCol, firstAsn);
            out.put(idCol, firstId);
            return out;
        }
        out.put(asnCol, db.scalar("SELECT max(" + db.col(table, asnCol) + ") FROM " + db.t(table) + " WHERE left("
            + db.col(table, asnCol) + ", 6) = :p", p("p", prefix), String.class));
        out.put(idCol, db.scalar("SELECT max(" + db.col(table, idCol) + ") FROM " + db.t(table) + " WHERE left("
            + db.col(table, asnCol) + ", 6) = :p", p("p", prefix), Long.class));
        return out;
    }

    @RequestMapping("/GetEmployeeCardInfo")
    public String GetEmployeeCardInfo(@RequestBody(required = false) String body) {
        return employeeSeries(body, "010004", 2000000000L, 2099999999L);
    }

    @RequestMapping("/GetRepairCardInfo")
    public String GetRepairCardInfo(@RequestBody(required = false) String body) {
        return employeeSeries(body, "010006", 1600000000L, 1699999999L);
    }

    private String employeeSeries(String body, String prefix, long lo, long hi) {
        try {
            Row modal = req(body);
            Row obj = deal.dto().make("Modal.EmployeeCardInfos");
            if (db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))
                && db.any("EmployeeCardInfos", "left({ASN}, 6) = :p", p("p", prefix))) {
                obj = deal.dto().make("Modal.EmployeeCardInfos");
                obj.put("ASN", db.scalar("SELECT max(asn) FROM employee_card_infos WHERE left(asn, 6) = :p", p("p", prefix), String.class));
                Long acc = db.scalar("SELECT max(customer_account_id) FROM employee_card_infos WHERE customer_account_id > :lo "
                    + "AND customer_account_id <= :hi", p("lo", lo, "hi", hi), Long.class);
                obj.put("CustomerAccountID", acc == null ? 0 : acc);
            }
            return okJson(obj);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCustomerCardCardInfo")
    public String GetCustomerCardCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row obj = deal.dto().make("Modal.CustomerCardInfos");
            if (db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                obj = maxOf("Modal.CustomerCardInfos", "CustomerCardInfos", "010001", "CustomerID", "ASN",
                    "01000111000000000000", 2100000000L, "ASN");
            }
            return okJson(obj);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ cartes employé

    @RequestMapping("/CreateEmployeeCardInfo")
    public String CreateEmployeeCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                modal.put("IssueDatetime", LocalDateTime.now());
                Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                if (st != null) {
                    Row acc = newAccount(modal.i("CustomerAccountID"), String.valueOf(modal.i("CustomerAccountID")), 0.0);
                    db.insert("CustomerAccounts", acc);
                    db.insert("EmployeeCardInfos", modal);
                    db.insert("AccountServiceLogs", accountLog(modal.s("ASN"), 0.0, acc.s("AccountNo"), 0.0, 4, modal.i("EmployeeID"),
                        modal.i("StationID"), 1, "20"));
                    db.insert("WhiteCardLists", Row.of("ASN", modal.get("ASN"), "ValidateTime", modal.get("ValidDatetime"),
                        "StationID", modal.i("StationID")));
                    bumpWhite();
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/UpdateEmployeeCardInfo")
    public String UpdateEmployeeCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                if (st != null) {
                    Row info = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", modal.str("ASN")));
                    if (info == null) {
                        throw fail("Les informations utilisateur n'existent pas");
                    }
                    info.put("CipherFlg", modal.get("CipherFlg"));
                    info.put("EmployeeID", modal.i("EmployeeID"));
                    info.put("SpecialPrice", modal.get("SpecialPrice"));
                    db.update("EmployeeCardInfos", info);
                    Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", p("id", info.i("CustomerAccountID")));
                    if (acc == null) {
                        throw fail("Le compte utilisateur n'existe pas");
                    }
                    db.insert("AccountServiceLogs", accountLog(modal.s("ASN"), 0.0, acc.s("AccountNo"), acc.d("Balance"), 4,
                        modal.i("EmployeeID"), modal.i("StationID"), 6, "20"));
                    bumpWhite();
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ cartes utilisateur / crédit

    @RequestMapping("/CreateCusTomerCardInfo")
    public String CreateCusTomerCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                if (st == null) {
                    throw fail("Requête illégale");
                }
                if (!checkTypeAndNo(modal.i("CertificateTypeID"), modal.str("CertificateNo"))) {
                    throw fail("Ce numéro de pièce d'identité est déjà utilisé");
                }
                int id = modal.i("AccountNo");
                Row cus = Row.of("CustomerID", id, "Address", modal.get("Address"), "Birthday", modal.dt("BirthDay"),
                    "CertificateTypeID", modal.i("CertificateTypeID"), "CertificateNo", modal.get("CertificateNo"),
                    "Name", modal.get("Name"), "PostCode", modal.get("PostCode"), "Telephone", modal.get("Telephone"),
                    "VehicleNo", modal.get("LimitVehicleNo"), "VehicleColorID", modal.i("VeicleNoColor"),
                    "VehicleTypeID", modal.i("VeicleNoType"), "Code", modal.get("Code"), "GroupID", modal.i("GroupID"),
                    "Email", modal.get("Email"));
                LocalDateTime now = LocalDateTime.now();
                Row info = Row.of("AccountNo", modal.get("AccountNo"), "ASN", modal.get("Asn"), "CardStatusTypeID", 0,
                    "CipherFlg", modal.get("CipherFlg"), "CustomerAccountID", id, "CreditLines", 0.0, "CustomerID", id,
                    "Discount", modal.i("Discount"), "GroupID", modal.i("GroupID"), "isCreditCard", false,
                    "isLimitVehicleNo", modal.b("isLimitVehicleNo"), "IssueDatetime", now,
                    "LimitAmount", iz(modal, "LimitAmountDay"), "LimitAmountMonthly", iz(modal, "LimitAmountMonth"),
                    "LimitMileage", iz(modal, "LimitMileage"), "LimitOil", modal.get("LimitOil"), "LimitTime", iz(modal, "LimitTimeDay"),
                    "LimitTypeID", iz(modal, "LimitTypeID"), "LimitVehicleNo", modal.get("LimitVehicleNo"),
                    "LimitVolume", iz(modal, "LimitVolume"), "LimitVolumeMonthly", iz(modal, "LimitVolumeMonth"),
                    "MileageTolerance", iz(modal, "MileageTolerance"), "PreferentialPrice", 0.0, "Score", BigDecimal.ZERO,
                    "StationID", modal.i("StationID"), "UpdateDatetime", now, "UseConfine", modal.get("UseConfine"),
                    "UsedAmountMonthly", 0.0, "ValidDatetime", now.plusYears(5), "MediaTypeID", 1,
                    "SpecialPrice", modal.get("SpecialPrice"));
                Row acc = newAccount(id, modal.s("AccountNo"), 0.0);
                db.insert("AccountServiceLogs", accountLog(info.s("ASN"), 0.0, acc.s("AccountNo"), 0.0, 1, modal.i("EmpID"),
                    modal.i("StationID"), 1, info.s("AccountNo").substring(0, 2)));
                db.insert("Customers", cus);
                db.insert("CustomerCardInfos", info);
                db.insert("CustomerAccounts", acc);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** Champs communs de modification d'une carte utilisateur / crédit. */
    private static void applyLimits(Row info, Row modal) {
        info.put("LimitAmount", iz(modal, "LimitAmountDay"));
        info.put("LimitAmountMonthly", iz(modal, "LimitAmountMonth"));
        info.put("LimitMileage", iz(modal, "LimitMileage"));
        info.put("LimitOil", modal.get("LimitOil"));
        info.put("LimitTime", iz(modal, "LimitTimeDay"));
        info.put("LimitTypeID", iz(modal, "LimitTypeID"));
        info.put("LimitVehicleNo", modal.get("LimitVehicleNo"));
        info.put("LimitVolume", iz(modal, "LimitVolume"));
        info.put("LimitVolumeMonthly", iz(modal, "LimitVolumeMonth"));
        info.put("MileageTolerance", iz(modal, "MileageTolerance"));
        info.put("UpdateDatetime", LocalDateTime.now());
        info.put("UseConfine", modal.get("UseConfine"));
        info.put("CipherFlg", modal.get("CipherFlg"));
        info.put("GroupID", modal.i("GroupID"));
        info.put("Discount", modal.i("Discount"));
        info.put("isLimitVehicleNo", modal.b("isLimitVehicleNo"));
    }

    private static void applyCustomer(Row cus, Row modal) {
        cus.put("Address", modal.get("Address"));
        cus.put("Birthday", modal.dt("BirthDay"));
        cus.put("CertificateTypeID", modal.i("CertificateTypeID"));
        cus.put("CertificateNo", modal.get("CertificateNo"));
        cus.put("Name", modal.get("Name"));
        cus.put("PostCode", modal.get("PostCode"));
        cus.put("Telephone", modal.get("Telephone"));
        cus.put("VehicleNo", modal.get("VehicleNo"));
        cus.put("Code", modal.get("Code"));
        cus.put("GroupID", modal.i("GroupID"));
    }

    @RequestMapping("/UpdateCustomerCardInfo")
    public String UpdateCustomerCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (db.any("Stations", "{StationID} = :s", p("s", modal.i("StationID")))) {
                    Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", modal.str("Asn")));
                    if (info == null) {
                        throw fail("Les informations utilisateur n'existent pas");
                    }
                    applyLimits(info, modal);
                    info.put("SpecialPrice", modal.get("SpecialPrice"));
                    Row cus = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
                    applyCustomer(cus, modal);
                    cus.put("Email", modal.get("Email"));
                    cus.put("VehicleColorID", modal.i("VeicleNoColor") + 1);
                    cus.put("VehicleTypeID", modal.i("VeicleNoType") + 1);
                    Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", p("id", info.i("CustomerAccountID")));
                    if (acc == null) {
                        throw fail("Le compte utilisateur n'existe pas");
                    }
                    db.insert("AccountServiceLogs", accountLog(info.s("ASN"), 0.0, acc.s("AccountNo"), acc.d("Balance"), 1,
                        modal.i("EmpID"), modal.i("StationID"), 6, info.s("AccountNo").substring(0, 2)));
                    db.update("Customers", cus);
                    db.update("CustomerCardInfos", info);
                    db.update("CustomerAccounts", acc);
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CreateCreditCardInfo")
    public String CreateCreditCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                if (st != null) {
                    if (!checkTypeAndNo(modal.i("CertificateTypeID") + 1, modal.str("CertificateNo"))) {
                        throw fail("Ce numéro de pièce d'identité est déjà utilisé");
                    }
                    int id = modal.i("AccountNo");
                    Row cus = Row.of("Address", modal.get("Address"), "CertificateNo", modal.get("CertificateNo"), "Code", modal.get("Code"),
                        "GroupID", modal.i("GroupID"), "Name", modal.get("Name"), "PostCode", modal.get("PostCode"),
                        "Telephone", modal.get("Telephone"), "CustomerID", id, "CertificateTypeID", modal.i("CertificateTypeID"),
                        "VehicleNo", modal.get("VehicleNo"), "Email", modal.get("Email"), "Birthday", modal.dt("BirthDay"),
                        "VehicleColorID", 1, "VehicleTypeID", 1);
                    LocalDateTime now = LocalDateTime.now();
                    Row info = Row.of("ASN", modal.get("Asn"), "CardStatusTypeID", 0, "CipherFlg", modal.get("CipherFlg"),
                        "CreditLines", modal.d("CreditLine"), "DateAccount", iz(modal, "BillDate"), "DateRepayment", iz(modal, "RepayDate"),
                        "Discount", modal.i("Discount"), "CustomerID", id, "GroupID", modal.i("GroupID"), "isCreditCard", true,
                        "IssueDatetime", now, "LimitAmount", iz(modal, "LimitAmountDay"), "LimitAmountMonthly", iz(modal, "LimitAmountMonth"),
                        "LimitOil", modal.get("LimitOil"), "LimitTime", iz(modal, "LimitTimeDay"), "LimitVolume", iz(modal, "LimitVolume"),
                        "LimitVolumeMonthly", iz(modal, "LimitVolumeMonth"), "StationID", st.i("StationID"), "UpdateDatetime", now,
                        "ValidDatetime", now.plusYears(5), "UsedAmountMonthly", 0.0, "AccountNo", modal.get("AccountNo"),
                        "CustomerAccountID", id, "LimitMileage", dz(modal, "LimitMileage"), "MileageTolerance", dz(modal, "MileageTolerance"),
                        "LimitTypeID", modal.i("LimitTypeID"), "UseConfine", modal.get("UseConfine"), "MediaTypeID", 1);
                    if (secretSame) {
                        info.put("CompanyID", st.i("CompanyID"));
                    }
                    Row acc = Row.of("AccountNo", modal.get("AccountNo"), "Balance", modal.d("CreditLine"), "BookBalance", 0.0,
                        "ConsumeDatetime", now, "DepositDatetime", now, "CustomerAccountID", id, "ServiceFee", 0.0, "Status", "0",
                        "UpdateDatetime", now);
                    db.insert("AccountServiceLogs", accountLog(info.s("ASN"), acc.d("Balance"), acc.s("AccountNo"), acc.d("Balance"), 11,
                        modal.i("EmpID"), modal.i("StationID"), 1, "21"));
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

    @RequestMapping("/UpdateCreditCardInfo")
    public String UpdateCreditCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                if (db.any("Stations", "{StationID} = :s", p("s", modal.i("StationID")))) {
                    Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", modal.str("Asn")));
                    if (info == null) {
                        throw fail("Les informations utilisateur n'existent pas");
                    }
                    applyLimits(info, modal);
                    info.put("CreditLines", modal.d("CreditLine"));
                    Row cus = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
                    if (cus == null) {
                        throw fail("L'utilisateur n'existe pas");
                    }
                    applyCustomer(cus, modal);
                    Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :id", p("id", info.i("CustomerAccountID")));
                    if (acc == null) {
                        throw fail("Le compte utilisateur n'existe pas");
                    }
                    db.insert("AccountServiceLogs", accountLog(info.s("ASN"), 0.0, acc.s("AccountNo"), acc.d("Balance"), 11,
                        modal.i("EmpID"), modal.i("StationID"), 6, "11"));
                    db.update("Customers", cus);
                    db.update("CustomerCardInfos", info);
                    db.update("CustomerAccounts", acc);
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    // ------------------------------------------------------------ cartes de groupe

    @RequestMapping("/CreateGroupCardInfo")
    public String CreateGroupCardInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("StationCode")));
                if (st != null) {
                    String company = modal.s("CompanyName");
                    int gid = modal.i("GroupID");
                    Row g = Row.of("Address", modal.get("Address"), "CertificateNo", modal.get("CertificateNo"), "Code", modal.get("ID"),
                        "Email", modal.get("Email"), "Fax", modal.get("Fax"), "GroupID", gid, "Name", company,
                        "PostCode", modal.get("PostCode"), "Telephone", modal.get("Phone"), "Contact", modal.get("Manager"),
                        "ShortName", company.length() > 6 ? company.substring(0, 6) : company);
                    LocalDateTime now = LocalDateTime.now();
                    Row info = Row.of("ASN", modal.get("Asn"), "CardStatusTypeID", 0, "CipherFlg", modal.get("CipherFlg"),
                        "CreditLines", modal.d("CreditLine"), "DateAccount", iOne(modal, "BillDate"), "DateRepayment", iOne(modal, "RepayDay"),
                        "Discount", modal.i("Discount"), "GroupAccountID", gid, "GroupID", gid, "isCreditCard", modal.b("isCreditCard"),
                        "IssueDatetime", now, "LimitAmount", iz(modal, "LimitAmountDay"), "LimitAmountMonthly", iz(modal, "LimitAmountMonth"),
                        "LimitOil", modal.get("LimitOilType"), "LimitTime", iz(modal, "LimitTimeDay"), "LimitVolume", iz(modal, "LimitVolume"),
                        "LimitVolumeMonthly", iz(modal, "LimitVolumeMonth"), "StationID", st.i("StationID"), "UpdateDate", now,
                        "ValidDatetime", now.plusYears(5), "UsedAmountMonthly", 0.0, "MediaTypeID", 1, "MinBalance", modal.get("MinBalance"));
                    Row acc = Row.of("AccountNo", String.valueOf(gid), "Balance", modal.d("CreditLine"), "BookBalance", 0.0,
                        "ConsumeDatetime", now, "DepositDatetime", now, "GroupAccountID", gid, "ServiceFee", 0.0, "Status", "0",
                        "UpdateDatetime", now);
                    db.insert("AccountServiceLogs", accountLog(modal.s("Asn"), 0.0, acc.s("AccountNo"), 0.0, 12, modal.i("EmpID"),
                        st.i("StationID"), 1, "19"));
                    db.insert("Groups", g);
                    db.insert("GroupAccounts", acc);
                    db.insert("GroupCardInfos", info);
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/UpdateGroupCaridInfo")
    public String UpdateGroupCaridInfo(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("StationCode")));
                if (st != null) {
                    Row info = db.first("GroupCardInfos", "{ASN} = :a", p("a", modal.str("Asn")));
                    if (info == null) {
                        throw fail("Les informations du groupe n'existent pas");
                    }
                    info.put("CipherFlg", modal.get("CipherFlg"));
                    info.put("CreditLines", modal.d("CreditLine"));
                    info.put("DateAccount", iOne(modal, "BillDate"));
                    info.put("DateRepayment", iOne(modal, "RepayDay"));
                    info.put("Discount", modal.i("Discount"));
                    info.put("LimitAmount", iz(modal, "LimitAmountDay"));
                    info.put("LimitAmountMonthly", iz(modal, "LimitAmountMonth"));
                    info.put("LimitOil", modal.get("LimitOilType"));
                    info.put("LimitTime", iz(modal, "LimitTimeDay"));
                    info.put("LimitVolume", iz(modal, "LimitVolume"));
                    info.put("LimitVolumeMonthly", iz(modal, "LimitVolumeMonth"));
                    info.put("UpdateDate", LocalDateTime.now());
                    info.put("MinBalance", modal.get("MinBalance"));
                    Row g = db.first("Groups", "{GroupID} = :g", p("g", info.i("GroupID")));
                    if (g == null) {
                        throw fail("L'utilisateur n'existe pas");
                    }
                    g.put("Address", modal.get("Address"));
                    g.put("CertificateNo", modal.get("CertificateNo"));
                    g.put("Code", modal.get("ID"));
                    g.put("Email", modal.get("Email"));
                    g.put("Fax", modal.get("Fax"));
                    g.put("Name", modal.get("CompanyName"));
                    g.put("PostCode", modal.get("PostCode"));
                    g.put("ShortName", modal.get("CompanyName"));
                    g.put("Telephone", modal.get("Phone"));
                    g.put("Contact", modal.get("Manager"));
                    Row acc = db.first("GroupAccounts", "{GroupAccountID} = :g", p("g", info.i("GroupAccountID")));
                    if (acc == null) {
                        throw fail("Le compte du groupe n'existe pas");
                    }
                    db.insert("AccountServiceLogs", accountLog(modal.s("Asn"), 0.0, acc.s("AccountNo"), acc.d("Balance"), 12,
                        modal.i("EmpID"), st.i("StationID"), 6, "02"));
                    db.update("Groups", g);
                    db.update("GroupCardInfos", info);
                }
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/GetGroupCardInfoCardInfo")
    public String GetGroupCardInfoCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row obj = deal.dto().make("Modal.GroupCardInfos");
            if (modal.i("Start") == 2) {
                obj = maxOf("Modal.GroupCardInfos", "GroupCardInfos", "010002", "GroupID", "ASN", "01000211000000000000",
                    1900000000L, "GroupID");
            }
            if (modal.i("Start") == 12) {
                obj = maxOf("Modal.GroupCardInfos", "GroupCardInfos", "010012", "GroupID", "ASN", "01001211000000000000",
                    1800000000L, "GroupID");
            }
            return okJson(obj);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGroupCardByASN")
    public String GetGroupCardByASN(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row info = db.first("GroupCardInfos", "{ASN} = :a AND {CardStatusTypeID} <> 1 AND {CardStatusTypeID} <> 3",
                p("a", modal.str("Mess")));
            List<String> asns = db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE group_id = :g "
                + "AND card_status_type_id <> 1 AND card_status_type_id <> 3", p("g", info.i("GroupID")), String.class);
            return okJson(asns);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGroupCardByChildASN")
    public String GetGroupCardByChildASN(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row info = db.first("CustomerCardInfos", "{ASN} = :a AND {CardStatusTypeID} <> 1", p("a", modal.str("Mess")));
            String asn = db.scalar("SELECT asn FROM group_card_infos WHERE group_id = :g AND card_status_type_id = 0 LIMIT 1",
                p("g", info.i("GroupID")), String.class);
            return okJson(asn);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetMasterGroupCardInfo")
    public String GetMasterGroupCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            return okJson(maxOf("Modal.GroupCardInfos", "GroupCardInfos", "010014", "GroupID", "ASN", "01001411000000000000",
                1400000000L, "GroupID"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetChilrenGroupCardInfo")
    public String GetChilrenGroupCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row any = db.first("CustomerCardInfos", "left({ASN}, 6) = '010013' ORDER BY {CustomerID} DESC", null);
            Row out = deal.dto().make("Modal.CustomerCardInfos");
            if (any == null) {
                out.put("ASN", "01001311000000000000");
                out.put("CustomerID", 1300000000);
            } else {
                out.put("ASN", db.scalar("SELECT max(asn) FROM customer_card_infos WHERE left(asn, 6) = '010013'", null, String.class));
                out.put("CustomerID", db.scalar("SELECT max(customer_id) FROM customer_card_infos WHERE left(asn, 6) = '010013'", null,
                    Long.class));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCarUserCardInfo")
    public String GetCarUserCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row out = deal.dto().make("Modal.CustomerCardInfos", "ASN", "11000311000000000000", "CustomerID", 300000000);
            if (db.any("Stations", "{Code} = :c", p("c", modal.str("Code"))) && db.first("CarUser") != null) {
                out = deal.dto().make("Modal.CustomerCardInfos");
                out.put("ASN", db.scalar("SELECT max(asn) FROM car_user", null, String.class));
                out.put("CustomerID", db.scalar("SELECT max(customer_account_id) FROM car_user", null, Long.class));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetMasterMess")
    public String GetMasterMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row info = db.first("GroupCardInfos", "{GroupID} = :g", p("g", Conv.toInt(modal.str("Mess"))));
            if (info == null) {
                throw fail("Le compte n'existe pas");
            }
            return okJson(ent("GroupCardInfos", info));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCreditCardInfo")
    public String GetCreditCardInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            return okJson(maxOf("Modal.CustomerCardInfos", "CustomerCardInfos", "010011", "CustomerID", "ASN",
                "01001111000000000000", 1700000000L, "ASN"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCreditGroupMess")
    public String GetCreditGroupMess(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row info = db.first("GroupCardInfos", "{GroupID} = :g", p("g", modal.i("GroupID")));
            BigDecimal used = db.scalar("SELECT coalesce(sum(credit_lines), 0) FROM customer_card_infos WHERE group_id = :g",
                p("g", info.i("GroupID")), BigDecimal.class);
            info.put("CreditLines", info.d("CreditLines") - (used == null ? 0 : used.doubleValue()));
            return okJson(ent("GroupCardInfos", info));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    // ------------------------------------------------------------ fiches de cartes

    private Row userCardInfo(String asn, BigDecimal balance, Object name, Object status) {
        return deal.dto().make("UserCardInfo", "Asn", asn, "Balance", balance, "Name", name, "CardStatue", status);
    }

    @RequestMapping("/GetDespoitUser")
    public String GetDespoitUser(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            String asn = modal.s("Mess");
            switch (asn.substring(0, 6)) {
                case "010006", "010004": {
                    CenterDeal.EmpCard e = empStrict(asn);
                    return okJson(userCardInfo(e.cardInfos().s("ASN"), e.accounts().dec("Balance"), e.employees().get("Name"),
                        e.cardInfos().i("CardStatusTypeID")));
                }
                case "010002", "010014": {
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    if (!g.ok()) {
                        throw fail(g.error());
                    }
                    return okJson(userCardInfo(g.cardInfos().s("ASN"), g.accounts().dec("Balance"), g.groups().get("Name"),
                        g.cardInfos().i("CardStatusTypeID")));
                }
                default: {
                    CenterDeal.CusCard c = cusStrict(asn);
                    return okJson(userCardInfo(c.cardInfos().s("ASN"), c.accounts().dec("Balance"), c.customers().get("Name"),
                        c.cardInfos().i("CardStatusTypeID")));
                }
            }
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** getCus du contrôleur : messages « n.Account not exists ». */
    private CenterDeal.CusCard cusStrict(String asn) {
        Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
        if (info == null) {
            throw fail("1.Account not exists");
        }
        Row cus = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
        if (cus == null) {
            throw fail("2.Account not exists");
        }
        Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", info.i("CustomerAccountID")));
        if (acc == null) {
            throw fail("3.Account not exists");
        }
        return new CenterDeal.CusCard(true, cus, info, acc, "");
    }

    /** getEmp du contrôleur : refuse une carte employé non active. */
    private CenterDeal.EmpCard empStrict(String asn) {
        Row info = db.first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
        if (info == null) {
            throw fail("1.Account not exists");
        }
        Row emp = db.first("Employees", "{EmployeeID} = :e", p("e", info.i("EmployeeID")));
        if (emp == null) {
            throw fail("2.Account not exists");
        }
        if (info.i("CardStatusTypeID") != 0) {
            throw fail("Ce compte n'est pas autorisé");
        }
        Row acc = db.first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", info.i("CustomerAccountID")));
        if (acc == null) {
            throw fail("3.Account not exists");
        }
        return new CenterDeal.EmpCard(true, emp, info, acc, "");
    }

    @RequestMapping("/GetCardMessage")
    public String GetCardMessage(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            String[] a = modal.s("Mess").split(",", -1);
            int type = Conv.toInt(a[0]);
            String name = a[1];
            String phone = a[2];
            String cno = a[3];
            String carNo = a[4];
            switch (type) {
                case 0: {
                    StringBuilder w = new StringBuilder("customer_id > 900000000 AND customer_id < 1000000000");
                    if (!name.isEmpty()) {
                        w.append(" AND name = :n");
                    }
                    if (!phone.isEmpty()) {
                        w.append(" AND telephone = :ph");
                    }
                    if (!carNo.isEmpty()) {
                        w.append(" AND vehicle_no = :car");
                    }
                    if (!cno.isEmpty()) {
                        w.append(" AND certificate_no = :cno");
                    }
                    List<String> asns = db.jdbc().queryForList("SELECT asn FROM customer_card_infos WHERE customer_id IN "
                        + "(SELECT customer_id FROM customers WHERE " + w + ")", p("n", name, "ph", phone, "car", carNo, "cno", cno),
                        String.class);
                    return okJson(asns);
                }
                case 2: {
                    StringBuilder w = new StringBuilder("1=1");
                    if (!name.isEmpty()) {
                        w.append(" AND name = :n");
                    }
                    if (!phone.isEmpty()) {
                        w.append(" AND telephone = :ph");
                    }
                    List<String> asns = db.jdbc().queryForList("SELECT asn FROM group_card_infos WHERE group_id IN "
                        + "(SELECT group_id FROM groups WHERE " + w + ")", p("n", name, "ph", phone), String.class);
                    return okJson(asns);
                }
                default:
                    return "";
            }
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetDespoitMaster")
    public String GetDespoitMaster(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            Row g = db.first("Groups", "{Name} = :n", p("n", modal.str("Mess")));
            if (g == null) {
                throw fail("Le compte n'existe pas");
            }
            Row info = db.first("GroupCardInfos", "{GroupID} = :g", p("g", g.i("GroupID")));
            Row acc = db.first("GroupAccounts", "{GroupAccountID} = :g", p("g", info.i("GroupAccountID")));
            if (acc == null) {
                throw fail("Le compte n'existe pas");
            }
            return okJson(deal.dto().make("UserCardInfo", "Asn", info.get("ASN"), "Balance", acc.dec("Balance")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetUserMessage")
    public String GetUserMessage(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            if (!db.any("Stations", "{Code} = :c", p("c", modal.str("Code")))) {
                throw fail("Requête illégale");
            }
            String asn = modal.s("Mess");
            String prefix = asn.substring(0, 6);
            switch (prefix) {
                case "010001", "010011", "010013": {
                    CenterDeal.CusCard c = cusStrict(asn);
                    Row cus = c.customers();
                    Row info = c.cardInfos();
                    Row acc = c.accounts();
                    return okJson(deal.dto().make("PersonCard",
                        "AccountNo", acc.get("AccountNo"), "Address", cus.get("Address"), "Asn", info.get("ASN"),
                        "BirthDay", fmt(cus.get("Birthday")), "CertificateNo", cus.get("CertificateNo"),
                        "CertificateTypeID", Conv.toStr(cus.get("CertificateTypeID")), "CipherFlg", info.get("CipherFlg"),
                        "Code", cus.get("Code"), "Discount", Conv.toStr(info.get("Discount")), "GroupID", Conv.toStr(info.get("GroupID")),
                        "isLimitVehicleNo", info.b("isLimitVehicleNo"), "LimitAmountDay", Conv.toStr(info.get("LimitAmount")),
                        "LimitAmountMonth", Conv.toStr(info.get("LimitAmountMonthly")), "LimitMileage", Conv.toStr(info.get("LimitMileage")),
                        "LimitOil", info.get("LimitOil"), "LimitTimeDay", Conv.toStr(info.get("LimitTime")),
                        "LimitTypeID", Conv.toStr(info.get("LimitTypeID")), "LimitVehicleNo", info.get("LimitVehicleNo"),
                        "LimitVolume", Conv.toStr(info.get("LimitVolume")), "LimitVolumeMonth", Conv.toStr(info.get("LimitVolumeMonthly")),
                        "MileageTolerance", Conv.toStr(info.get("MileageTolerance")), "Name", cus.get("Name"),
                        "PostCode", cus.get("PostCode"), "Telephone", cus.get("Telephone"), "UseConfine", info.get("UseConfine"),
                        "VehicleNo", cus.get("VehicleNo"), "ValidDatetime", fmt(info.get("ValidDatetime")), "Email", cus.get("Email"),
                        "VeicleNoColor", String.valueOf(cus.i("VehicleColorID")), "VeicleNoType", String.valueOf(cus.i("VehicleTypeID")),
                        "CreditLine", Conv.toStr(info.get("CreditLines")), "BillDate", Conv.toStr(info.get("DateAccount")),
                        "RepayDate", Conv.toStr(info.get("DateRepayment")), "SpecialPrice", info.get("SpecialPrice"),
                        "Bal", acc.dec("Balance").setScale(2, RoundingMode.HALF_EVEN)));
                }
                case "010006", "010004": {
                    CenterDeal.EmpCard e = empStrict(asn);
                    Row emp = e.employees();
                    Row info = e.cardInfos();
                    return okJson(deal.dto().make("PersonCard",
                        "AccountNo", e.accounts().get("AccountNo"), "Address", emp.get("Address"), "Asn", info.get("ASN"),
                        "BirthDay", fmt(emp.get("Birthday")), "CertificateTypeID", Conv.toStr(emp.get("CertificateTypeID")),
                        "CipherFlg", info.get("CipherFlg"), "Code", Conv.toStr(emp.get("EmployeeID")),
                        "StationID", Conv.toStr(info.get("StationID")), "ValidDatetime", fmt(info.get("ValidDatetime")),
                        "SpecialPrice", info.get("SpecialPrice"),
                        "Bal", e.accounts().dec("Balance").setScale(2, RoundingMode.HALF_EVEN)));
                }
                case "010002", "010012", "010014": {
                    CenterDeal.GroupCard g = deal.getGroupCardInfo(asn);
                    if (!g.ok()) {
                        throw fail(g.error());
                    }
                    Row gr = g.groups();
                    Row info = g.cardInfos();
                    Row card = deal.dto().make("GroupCard",
                        "Asn", info.get("ASN"), "ValidTime", fmt(info.get("ValidDatetime")), "Discount", Conv.toStr(info.get("Discount")),
                        "GroupID", gr.i("GroupID"), "CipherFlg", info.get("CipherFlg"), "CompanyName", gr.get("Name"),
                        "ID", gr.get("Code"), "CertificateNo", gr.get("CertificateNo"), "Manager", gr.get("Contact"),
                        "Phone", gr.get("Telephone"), "PostCode", gr.get("PostCode"), "Fax", gr.get("Fax"), "Address", gr.get("Address"),
                        "Email", gr.get("Email"), "LimitOilType", info.get("LimitOil"), "LimitVolume", Conv.toStr(info.get("LimitVolume")),
                        "LimitTimeDay", Conv.toStr(info.get("LimitTime")), "LimitAmountDay", Conv.toStr(info.get("LimitAmount")),
                        "LimitVolumeMonth", Conv.toStr(info.get("LimitVolumeMonthly")),
                        "LimitAmountMonth", Conv.toStr(info.get("LimitAmountMonthly")), "MinBalance", info.get("MinBalance"));
                    if (prefix.equals("010012")) {
                        BigDecimal credit = info.dec("CreditLines");
                        card.put("CreditLine", credit);
                        card.put("BillDate", Conv.toStr(info.get("DateAccount")));
                        card.put("RepayDay", Conv.toStr(info.get("DateRepayment")));
                        Double used = db.scalar("SELECT sum(credit_lines) FROM customer_card_infos WHERE group_id = :g",
                            p("g", info.i("GroupID")), Double.class);
                        String usedStr = Conv.num(used == null ? 0 : used);
                        card.put("UseAmount", usedStr);
                        card.put("Balance", credit.subtract(Conv.toDecimal(usedStr)).toPlainString());
                    }
                    return okJson(card);
                }
                default:
                    throw fail("Erreur de requête");
            }
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/ScoreRuleDeal")
    public String ScoreRuleDeal(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            switch (modal.i("SOID")) {
                case 2: {
                    Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                    if (st == null) {
                        throw fail("Illegal request");
                    }
                    if (db.any("ScoreRate", "{OilTypeID} = :g AND {CompanyID} = :c", p("g", modal.i("GradeID"), "c", st.i("CompanyID")))) {
                        throw fail("Les points existent déjà");
                    }
                    db.insert("ScoreRate", Row.of("CompanyID", st.i("CompanyID"), "OilTypeID", modal.i("GradeID"),
                        "OilTypeName", deal.getGraName(modal.i("GradeID")), "Rate", modal.dec("Score"), "ScoreType", modal.i("OperateID"),
                        "CreateTime", CenterDeal.now(), "IsRun", true));
                    break;
                }
                case 0: {
                    Row rate = db.first("ScoreRate", "{ID} = :id", p("id", modal.i("ID")));
                    if (rate == null) {
                        throw fail("Aucun enregistrement");
                    }
                    Row st = db.first("Stations", "{StationID} = :s", p("s", modal.i("StationID")));
                    if (db.any("ScoreRate", "{ID} <> :id AND {OilTypeID} = :g AND {CompanyID} = :c",
                        p("id", modal.i("ID"), "g", modal.i("GradeID"), "c", st.i("CompanyID")))) {
                        throw fail("Enregistrement en double");
                    }
                    rate.put("OilTypeID", modal.i("GradeID"));
                    rate.put("Rate", modal.dec("Score"));
                    rate.put("ScoreType", modal.i("OperateID"));
                    db.update("ScoreRate", rate);
                    break;
                }
                case 1: {
                    Row rate = db.first("ScoreRate", "{ID} = :id", p("id", modal.i("ID")));
                    if (rate == null) {
                        throw fail("Aucun enregistrement");
                    }
                    db.deleteEntity("ScoreRate", rate);
                    break;
                }
                case 3: {
                    Row rate = db.first("ScoreRate", "{ID} = :id", p("id", modal.i("ID")));
                    if (rate == null) {
                        throw fail("Aucun enregistrement");
                    }
                    return okJson(ent("ScoreRate", rate));
                }
                default:
                    break;
            }
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetScroeList")
    public String GetScroeList(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            List<Row> list = secretSame ? db.list("ScoreRate", "{CompanyID} = :c", p("c", modal.i("CompanyID"))) : db.list("ScoreRate");
            return okJson(ent("ScoreRate", list));
        } catch (Exception ex) {
            return err(ex);
        }
    }
}
