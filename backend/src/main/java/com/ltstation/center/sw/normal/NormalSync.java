package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Échanges de NormalMainForm avec Center (fils UplodaAndDowload et DowloadData) : envoi des
 * transactions, rapports, alarmes et états ; téléchargement des employés, clients, cartes, comptes,
 * groupes, crédits et version de la station.
 */
public final class NormalSync {

    private NormalSync() {
    }

    static final LegacyLog DowLog = new LegacyLog("Dowload");
    static final LegacyLog MainLog = new LegacyLog("NormalMainLog");

    /** Ret : 0 jusqu'au premier tour complet de UplodaAndDowload (téléchargement complet), puis 1. */
    public static volatile int Ret;

    /** ErrorMessage (étiquette rouge de la fenêtre principale) : texte, ou null pour la masquer. */
    public static volatile Consumer<String> ErrorMessage = s -> { };

    private static Sugar db() {
        return SW.db();
    }

    private static boolean fail(String log, String shown, Exception ex) {
        DowLog.writeLog(log + ex.getMessage());
        if (shown != null) {
            ErrorMessage.accept(shown + ex.getMessage());
        }
        return false;
    }

    private static Row param(Object... kv) {
        return SW.dto().make("CustomerParam", kv);
    }

    private static List<Row> rows(CustomerRespone r) {
        return Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
    }

    private static Row copy(Row from, Row to, String... cols) {
        for (String c : cols) {
            to.put(c, from.get(c));
        }
        return to;
    }

    private static void markUploaded(String table, List<Row> list, Object value) {
        for (Row item : list) {
            item.put("Upload", value);
        }
        db().updateAll(table, list);
    }

    // ------------------------------------------------------------ envois

    /** UploadDutyStatue : poste (HandShift) en cours envoyé à Center. */
    public static boolean UploadDutyStatue() {
        try {
            Row h = db().first("HandShiftReport", "ORDER BY {CreateTime} DESC", null);
            String shiftNo = "";
            int shiftNoStatue = 0;
            String operater = "";
            if (h != null) {
                shiftNo = h.str("HandShift");
                shiftNoStatue = h.i("DealStatue");
                if (h.i("DealStatue") != 1) {
                    operater = h.str("Operate");
                }
            }
            BaseFunction.post("Station/UploadShiftStatue", SW.dto().make("ShiftStatue", "ShiftNo", shiftNo, "ShiftNoStatue", shiftNoStatue,
                "StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID, "Operater", operater));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean NormalUploadAlarm() {
        try {
            List<Row> list = db().list("NormalAlarm", "{Upload} = false", null);
            if (BaseFunction.post("ServerDeal/NormalAlarmUpload", SW.dto().shapeAll("NormalAlarm", list)).Result()) {
                markUploaded("NormalAlarm", list, true);
                return true;
            }
            throw new IllegalStateException("Upload Failed");
        } catch (RuntimeException ex) {
            return fail("Upload Alarm Error:", "Upload Alarm Error:", ex);
        }
    }

    public static boolean NormalUploadHandShift() {
        try {
            List<Row> list = db().list("HandShiftReport", "{Upload} = 0 AND {DealStatue} = 1 LIMIT 50", null);
            if (!list.isEmpty()) {
                List<Row> list2 = new ArrayList<>();
                for (Row item : list) {
                    Row u = copy(item, new Row(), "StartTime", "EndTime", "StartVolume", "EndVolume", "SumAmount", "SumCount", "SumVolume",
                        "DealStatue", "HandShift", "CreateTime", "Error", "GradeID", "GradeName", "NozCount", "NozID", "Operate", "Price",
                        "TotalAmount", "TotalVolume");
                    u.put("StationID", IniInfo.StationID);
                    u.put("CompanyID", IniInfo.CompanyID);
                    list2.add(u);
                }
                if (!BaseFunction.post("Station/UploadHandShiftReport", list2).Result()) {
                    throw new IllegalStateException("Upload Failed");
                }
                markUploaded("HandShiftReport", list, 1);
            }
            return true;
        } catch (RuntimeException ex) {
            return fail("NormalUploadHandShift Error:", "NormalUploadHandShift:", ex);
        }
    }

    /** Envoi des résumés de clôture (50 au plus) vers Center sous la forme Center* (avec StationID / CompanyID). */
    private static boolean uploadSummary(String table, String centerCls, String action, String log, String shown) {
        try {
            List<Row> list = db().list(table, "{Upload} = false LIMIT 50", null);
            if (!list.isEmpty()) {
                List<Row> list2 = new ArrayList<>();
                for (Row item : list) {
                    Row c = SW.dto().shape(centerCls, item);
                    c.put("StationID", IniInfo.StationID);
                    c.put("CompanyID", IniInfo.CompanyID);
                    list2.add(c);
                }
                if (!BaseFunction.post(action, list2).Result()) {
                    throw new IllegalStateException("Upload Failed");
                }
                markUploaded(table, list, true);
            }
            return true;
        } catch (RuntimeException ex) {
            return fail(log, shown, ex);
        }
    }

    // les libellés d'erreur reprennent ceux de l'original (NormalUploadNoz et NormalUploadSummary affichent « Energy »)
    public static boolean NormalUploadNoz() {
        return uploadSummary("NormalDailyNozSummary", "CenterNormalDailyNozSummary", "Station/UploadNormalDailyNozReport",
            "NormalUploadEnergy Error:", "NormalUploadEnergy:");
    }

    public static boolean NormalUploadEnergy() {
        return uploadSummary("NormalDailyEnergySummary", "CenterNormalDailyEnergySummary", "Station/UploadNormalDailyEnergySummaryReport",
            "NormalUploadEnergy Error:", "NormalUploadEnergy:");
    }

    public static boolean NormalUploadStock() {
        return uploadSummary("NormalDailyStockSummary", "CenterNormalDailyStockSummary", "Station/UploadNormalDailyStockSummaryReport",
            "NormalUploadStock Error:", "NormalUploadStock:");
    }

    public static boolean NormalUploadSummary() {
        return uploadSummary("NormalDailySummary", "CenterNormalDailySummary", "Station/UploadNormalDailySummaryReport",
            "NormalUploadEnergy Error:", "NormalUploadEnergy:");
    }

    public static boolean UploadPersonalCreditLog() {
        try {
            List<Row> list = db().list("PersonCreditLog", "{Upload} = 0 LIMIT 50", null);
            CustomerRespone r = BaseFunction.post("Station/UploadPersonalCreditLog", SW.dto().shapeAll("PersonCreditLog", list));
            if (r.Result()) {
                markUploaded("PersonCreditLog", list, 1);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** NormalUploadTranasaction : 50 transactions non envoyées, une par une (« transaction Exit » = déjà reçue par Center). */
    public static boolean NormalUploadTranasaction() {
        try {
            synchronized (NormalFunction.transactionDealFlag) {
                for (Row item : db().list("NormalTransaction", "{Upload} = false LIMIT 50", null)) {
                    CustomerRespone r = BaseFunction.post("ServerDeal/UploadNormalTransaction", SW.dto().shape("NormalModel.NormalTransaction", item));
                    if (r.Result() || "transaction Exit".equals(BaseFunction.DecryptByAES(r.Mess()))) {
                        item.put("Upload", true);
                        db().update("NormalTransaction", item);
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            return fail("Upload Transaction Error:", null, ex);
        }
    }

    public static boolean NormalUploadTankImport() {
        try {
            List<Row> list = db().list("NormalTankStock", "{Upload} = false", null);
            Row obj = Row.of("StationID", IniInfo.StationID, "list", SW.dto().shapeAll("DataBaseDeal.NormalModel.NormalTankStock", list));
            CustomerRespone r = BaseFunction.post("ServerDeal/UploadNormalTankImport", obj);
            if (r.Result()) {
                markUploaded("NormalTankStock", list, true);
                return true;
            }
            if (r.Mess() != null) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            throw new IllegalStateException("NetWork Error");
        } catch (RuntimeException ex) {
            return fail("Upload Tank Import Error:", null, ex);
        }
    }

    public static boolean NormalUploadReport() {
        return true;
    }

    public static boolean UploadAlarm() {
        try {
            List<Row> list = db().list("SystemAlarm", "{Upload} = false", null);
            if (!list.isEmpty() && BaseFunction.post("Station/UploadAlarm", SW.dto().shapeAll("SystemAlarm", list)).Result()) {
                markUploaded("SystemAlarm", list, true);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Row tankMes(NormalTankSerial item) {
        return Row.of("StationID", IniInfo.StationID, "ID", item.ID, "TankNo", item.TankNo, "CurrentHeight", item.CurrentHeight,
            "CurrentVolume", item.CurrentVolume, "TankHeight", item.TankHeight, "TankVolume", item.TankVolume,
            "UpdateTime", String.valueOf(item.UpdateTime), "CurrentWater", item.CurrentWater, "CurrentTemp", item.CurrentTemp,
            "TankName", item.TankName, "GradeID", item.GradeID);
    }

    /** Alarme de cuve (NormalAlarm) ouverte si elle ne l'est pas déjà. */
    private static void openTankAlarm(int type, NormalTankSerial item) {
        if (!db().any("NormalAlarm", "{AlarmTypeID} = :a AND {TankID} = :t AND {Deal} = false", p("a", type, "t", item.ID))) {
            db().insert("NormalAlarm", Row.of("AlarmTypeID", type, "Deal", false, "StartTime", BaseFunction.ConvertDateTimeToInt(),
                "StationID", IniInfo.StationID, "TankID", item.ID, "TankName", item.TankName, "Upload", false));
        }
    }

    /** NormalUploadTankStatue : historique et alarmes des cuves du premier port série, puis envoi à Center. */
    public static boolean NormalUploadTankStatue() {
        try {
            if (!NormalEngine.Ports.isEmpty()) {
                List<Row> list = new ArrayList<>();
                for (NormalTankSerial item : NormalEngine.Ports.get(0).TankStatueList) {
                    list.add(tankMes(item));
                    if (item.IsUsing) {
                        if (!db().any("NormalTankHistory", "{TankID} = :t AND {CreateTime} = :c", p("t", item.ID, "c", String.valueOf(item.UpdateTime)))) {
                            db().insert("NormalTankHistory", Row.of("TankID", item.ID, "TankName", item.TankName, "TankNo", item.TankNo,
                                "CreateTime", item.UpdateTime, "CurrentHeight", item.CurrentHeight, "CurrentTemp", item.CurrentTemp,
                                "CurrentVolume", item.CurrentVolume, "CurrentWaterHeight", item.CurrentWater, "Upload", 0));
                        }
                        item.IsUsing = false;
                    }
                    for (Row alarm : db().list("NormalAlarm", "{TankID} = :t AND {Deal} = false", p("t", item.ID))) {
                        boolean deal = switch (alarm.i("AlarmTypeID")) {
                            case 7 -> item.OverWaterHeight.signum() == 0;
                            case 6 -> item.LowerWaterHeight.signum() == 0;
                            case 4 -> item.LowerTemp.signum() == 0;
                            case 5 -> item.OverTemp.signum() == 0;
                            case 3 -> item.OverHeight.signum() == 0;
                            case 2 -> item.LowerHeight.signum() == 0;
                            case 1 -> item.CreateTime == 0L;
                            default -> alarm.b("Deal");
                        };
                        if (deal) {
                            alarm.put("Deal", true);
                            alarm.put("EndTime", BaseFunction.ConvertDateTimeToInt());
                            db().update("NormalAlarm", alarm);
                        }
                    }
                    if (item.OverWaterHeight.signum() != 0) {
                        openTankAlarm(7, item);
                    }
                    if (item.LowerWaterHeight.signum() != 0) {
                        openTankAlarm(6, item);
                    }
                    if (item.OverTemp.signum() != 0) {
                        openTankAlarm(5, item);
                    }
                    if (item.LowerTemp.signum() != 0) {
                        openTankAlarm(4, item);
                    }
                    if (item.OverHeight.signum() != 0) {
                        openTankAlarm(3, item);
                    }
                    if (item.LowerHeight.signum() != 0) {
                        openTankAlarm(2, item);
                    }
                    if (item.CreateTime == 1) {
                        openTankAlarm(1, item);
                    }
                }
                CustomerRespone r = BaseFunction.post("ServerDeal/NormalTankUpload", list);
                if (!r.Result()) {
                    throw new IllegalStateException(r.Mess());
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Object state(Object o) {
        return o == null ? null : Wire.parse(BaseInfo.json(o));
    }

    /** NormalUploadNozStatue : état des pistolets, cuves, lecteurs RFID, PLC et afficheur de prix (boîtiers sans fil ou ports série). */
    public static boolean NormalUploadNozStatue() {
        try {
            List<NormalNozStatue> nozs = new ArrayList<>();
            List<Row> tanks = new ArrayList<>();
            List<RFIDRunData> rfids = new ArrayList<>();
            PLCParam plc = null;
            PriceBorad price = null;
            boolean serial = IniInfo.WifiMode != 1;
            if (IniInfo.WifiMode == 1) {
                for (IBoxClientParam box : BaseInfo.BoxClientList) {
                    for (AllNozStatue noz : box.NozList) {
                        NormalNozStatue n = new NormalNozStatue(noz.NozID, String.valueOf(noz.NozID), noz.TankID, 0, BigDecimal.ZERO, 0, 0, 0, 0, 0,
                            IniInfo.IdentifyMode);
                        n.ID = noz.NozID;
                        n.NozID = String.valueOf(noz.NozID);
                        n.Amont = noz.Amount;
                        n.Volume = noz.Volume;
                        n.AmountTotal = noz.AmountTotal;
                        n.VolumeTotal = noz.VolumeTotal;
                        n.View_Statue = noz.Statue == 4 ? -1 : noz.Statue;
                        n.SetPrice = noz.Price;
                        n.CompanyID = IniInfo.CompanyID;
                        n.GradeID = noz.GradeID;
                        n.GradeName = noz.GradeName;
                        n.Price = noz.Price;
                        n.IdentifyMode = IniInfo.IdentifyMode;
                        n.Address = noz.Address;
                        nozs.add(n);
                    }
                    rfids.addAll(box.RFIDList);
                    if (plc == null || box.pLCParam != null && box.pLCParam.Statue == 1) {
                        plc = box.pLCParam;
                    }
                    if (price == null || box.priceBorad != null && box.priceBorad.Statue == 1) {
                        price = box.priceBorad;
                    }
                }
                for (IBoxClientParam box : BaseInfo.BoxClientList) {
                    if (box.TankRun != 1) {
                        continue;
                    }
                    for (TankStatue tank : box.TankList) {
                        tanks.add(Row.of("StationID", IniInfo.StationID, "ID", tank.ID, "TankNo", String.valueOf(tank.TankID),
                            "CurrentHeight", tank.OilHeight, "CurrentVolume", tank.OilVolume, "TankHeight", tank.TotalHeight,
                            "TankVolume", tank.TotalVolume, "UpdateTime", String.valueOf(tank.UpdateTime), "CurrentWater", tank.WaterHeight,
                            "CurrentTemp", tank.Temp, "TankName", tank.TankName, "GradeID", tank.GradeID));
                    }
                }
            } else {
                MainLog.writeLog("Noz Upload:" + NormalEngine.Ports.size());
                if (NormalEngine.Ports.isEmpty()) {
                    return true;
                }
                for (NozSerial port : NormalEngine.Ports) {
                    for (NormalNozStatue n : port.NozAddress) {
                        n.CompanyID = IniInfo.CompanyID;
                        n.IdentifyMode = IniInfo.IdentifyMode;
                        nozs.add(n);
                    }
                    for (NormalTankSerial t : port.TankStatueList) {
                        boolean known = tanks.stream().anyMatch(x -> String.valueOf(t.TankNo).equals(x.str("TankNo")));
                        if (known) {
                            if (!port.TankUpdate) {
                                continue;
                            }
                            tanks.removeIf(x -> String.valueOf(t.TankNo).equals(x.str("TankNo")));
                        }
                        tanks.add(tankMes(t));
                    }
                    rfids.addAll(port.RFIDConfigs);
                    if (plc == null || port.pLCParam.Statue == 1) {
                        plc = port.pLCParam;
                    }
                    if (price == null || port.priceBorad.Statue == 1) {
                        price = port.priceBorad;
                    }
                }
            }
            MainLog.writeLog("Noz Statue:" + BaseInfo.json(nozs));
            CustomerRespone r = BaseFunction.post("ServerDeal/NormalNozStatueUploadV2", Row.of("StationID", IniInfo.StationID, "list", state(nozs)));
            if (serial && !r.Result()) {
                DowLog.writeLog("Upload Noz Error:" + r.Mess());
            }
            Row tankList = Row.of("StationID", IniInfo.StationID, "list", tanks);
            if (serial) {
                MainLog.writeLog("Tank Statue:" + Wire.json(tankList));
            }
            r = BaseFunction.post("ServerDeal/NormalTankUploadV2", tankList);
            if (serial && !r.Result()) {
                DowLog.writeLog("Upload Tank Error:" + BaseFunction.DecryptByAES(r.Mess()));
            }
            Row rfidList = Row.of("StationID", IniInfo.StationID, "list", state(rfids));
            MainLog.writeLog("RFID Statue:" + Wire.json(rfidList));
            r = BaseFunction.post("ServerDeal/NormalRFIDStatueUploadV2", rfidList);
            if (!r.Result()) {
                DowLog.writeLog("Upload RFID Error:" + BaseFunction.DecryptByAES(r.Mess()));
            }
            r = BaseFunction.post("ServerDeal/NormalPLCStatueUploadV2", Row.of("StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID,
                "pLC", state(plc)));
            if (!r.Result()) {
                DowLog.writeLog("Upload PLC Error:" + BaseFunction.DecryptByAES(r.Mess()));
            }
            r = BaseFunction.post("ServerDeal/NormalPriceStatueUploadV2", Row.of("StationID", IniInfo.StationID, "CompanyID", IniInfo.CompanyID,
                "priceBorad", state(price)));
            if (!r.Result()) {
                DowLog.writeLog("Upload Price Error:" + BaseFunction.DecryptByAES(r.Mess()));
            }
            return true;
        } catch (RuntimeException ex) {
            MainLog.writeLog("Noz Error:" + ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ téléchargements

    /** NormalDowLoadVersion : versions des listes sur Center (EmpVersion = 0 si Center ne répond pas). */
    public static Row NormalDowLoadVersion() {
        Row version = SW.dto().make("StationVersion", "StationCode", IniInfo.Code, "StationID", IniInfo.StationID, "EmpVersion", 0);
        try {
            CustomerRespone r = BaseFunction.post("Station/GetVersion", param("Code", IniInfo.Code));
            if (r.Mess() == null && !r.Result()) {
                throw new IllegalStateException("response Empty");
            }
            if (r.Result()) {
                return SW.dto().shape("StationVersion", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            fail("NormalDowLoadVersion Error:", "NormalDowLoadVersion Error:", ex);
            return version;
        }
    }

    public static boolean NormalDowLoadEmp() {
        try {
            CustomerRespone r = BaseFunction.post("ServerDeal/GetNormalEmp", param("Code", IniInfo.Code));
            if (!r.Result()) {
                throw new IllegalStateException("Dowload Failed");
            }
            for (Row item : rows(r)) {
                Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", item.i("EmployeeID")));
                if (emp != null) {
                    emp.put("Telephone", item.str("Telephone"));
                    emp.put("Name", item.str("Name"));
                    emp.put("Memo1", item.str("Name"));
                    emp.put("Memo2", item.str("Memo"));
                    db().update("Employees", emp);
                    continue;
                }
                db().insert("Employees", Row.of("Name", item.str("Name"), "Gender", "1", "StationID", IniInfo.StationID, "Code", item.str("EmployeeNo"),
                    "Memo1", item.str("Name"), "Memo2", item.str("Memo"), "Telephone", item.str("Telephone"), "Birthday", LocalDateTime.now(),
                    "Status", "0", "Address", "", "PostCode", "", "StationType", "4", "Auth", item.str("PostCode"), "EmployeeID", item.i("EmployeeID")));
            }
            return true;
        } catch (RuntimeException ex) {
            return fail("Dowload Emp Error:", "Dowload Emp Error:", ex);
        }
    }

    public static boolean NormalDowLoadEmpCardInfo() {
        try {
            CustomerRespone r = BaseFunction.post("Station/GetNormalEmpCardInfo", param("Code", IniInfo.Code));
            if (!r.Result()) {
                throw new IllegalStateException("Dowload Failed");
            }
            List<Row> list = rows(r);
            db().delete("EmployeeCardInfos", null, null);
            for (Row emp : list) {
                Row card = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", emp.str("ASN")));
                if (card != null) {
                    card.put("SpecialPrice", emp.get("SpecialPrice"));
                    card.put("Status", emp.str("CardStatusTypeID"));
                    card.put("AccountNo", emp.str("CustomerAccountID"));
                    card.put("CipherFlg", emp.get("CipherFlg"));
                    card.put("IssueDatetime", emp.get("IssueDatetime"));
                    card.put("Memo1", emp.get("Memo1"));
                    card.put("EmployeeCode", emp.str("EmployeeID"));
                    card.put("ValidDatetime", emp.get("ValidDatetime"));
                    db().update("EmployeeCardInfos", card);
                } else {
                    db().insert("EmployeeCardInfos", Row.of("ASN", emp.str("ASN"), "SpecialPrice", emp.get("SpecialPrice"),
                        "EmployeeCode", emp.str("EmployeeID"), "AccountNo", emp.str("CustomerAccountID"), "CipherFlg", emp.get("CipherFlg"),
                        "IssueDatetime", emp.get("IssueDatetime"), "Memo1", emp.get("Memo1"), "ValidDatetime", emp.get("ValidDatetime"),
                        "StationCode", IniInfo.Code, "Name", emp.str("EmployeeID"), "CardType", emp.str("CardTypeID"),
                        "Status", emp.str("CardStatusTypeID")));
                }
            }
            return true;
        } catch (RuntimeException ex) {
            return fail("Dowload Emp Error:", "Dowload EmpCardInfo Error:", ex);
        }
    }

    public static boolean NormalDowLoadCarLicense() {
        try {
            CustomerRespone r = BaseFunction.post("Station/GetNormalCarLicense", param("StationID", IniInfo.StationID));
            if (!r.Result()) {
                throw new IllegalStateException("Dowload Failed");
            }
            List<Row> list = rows(r);
            db().delete("CarNumberGroup", null, null);
            db().insertAll("CarNumberGroup", list);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Téléchargement par pages (Start = 1..TotalCount) : complet au premier tour (Ret = 0), puis les modifications. */
    private static boolean paged(String action, Consumer<Row> each) {
        Row cp = param("Code", IniInfo.Code, "DowloadAll", Ret == 0, "Start", 1);
        CustomerRespone r = BaseFunction.post(action, cp);
        while (r.Result()) {
            rows(r).forEach(each);
            cp.put("Start", cp.i("Start") + 1);
            if (cp.i("Start") > r.TotalCount()) {
                break;
            }
            r = BaseFunction.post(action, cp);
        }
        return true;
    }

    public static boolean NormalDowCustomer() {
        try {
            return paged("ServerDeal/GetNormalCustomerV2", item -> {
                Row c = db().first("Customers", "{CustomerID} = :c", p("c", item.i("CustomerID")));
                boolean exists = c != null;
                if (!exists) {
                    c = Row.of("CustomerID", item.i("CustomerID"));
                }
                copy(item, c, "Name", "CertificateTypeID", "CertificateNo", "Address", "PostCode", "Telephone", "VehicleNo");
                c.put("VehicleTypeID", Conv.toInt(item.get("VehicleTypeID")));
                c.put("VehicleColorID", Conv.toInt(item.get("VehicleColorID")));
                if (exists) {
                    db().update("Customers", c);
                } else {
                    db().insert("Customers", c);
                }
            });
        } catch (RuntimeException ex) {
            return fail("Dowload Customer Error:", "Dowload Customer Error:", ex);
        }
    }

    public static boolean NormalDowCustomerCardInfo() {
        try {
            return paged("ServerDeal/GetNormalCustomerCardInfoV2", item -> {
                Row c = db().first("CustomerCardInfos", "{ASN} = :a", p("a", item.str("ASN")));
                boolean exists = c != null;
                if (!exists) {
                    c = Row.of("ASN", item.str("ASN"), "Name", "", "Telephone", "");
                }
                copy(item, c, "CipherFlg", "CreditLines", "CustomerID", "Discount", "GroupID", "isCreditCard", "LimitAmount", "LimitAmountMonthly",
                    "LimitOil", "LimitTime", "LimitVolume", "LimitVolumeMonthly", "MileageTolerance", "PreferentialPrice", "SpecialPrice",
                    "UpdateDatetime", "UseConfine", "UsedAmountMonthly", "ScoreRuleID");
                c.put("LimitType", item.get("LimitTypeID"));
                c.put("Suspend", item.get("CardStatusTypeID"));
                c.put("Score", Conv.toInt(item.get("Score")));
                if (exists) {
                    // comme l'original : la plaque n'est reprise que pour une carte existante
                    c.put("VehicleNo", item.get("LimitVehicleNo"));
                    db().update("CustomerCardInfos", c);
                } else {
                    db().insert("CustomerCardInfos", c);
                }
            });
        } catch (RuntimeException ex) {
            return fail("Dowload CustomerCardInfo Error:", null, ex);
        }
    }

    public static boolean NormalDowCustomerAccount() {
        try {
            return paged("ServerDeal/GetNormalCustomerAccountV2", item -> {
                Row a = db().first("CustomerAccounts", "{CustomerAccountID} = :a", p("a", item.i("CustomerAccountID")));
                boolean exists = a != null;
                if (!exists) {
                    a = Row.of("CustomerAccountID", item.i("CustomerAccountID"));
                }
                copy(item, a, "Balance", "MileageLastest", "TotalAmountDaily", "TotalAmountMonthly", "TotalTimesDaily", "TotalVolumeMonthly",
                    "VolumeLastest");
                a.put("TransTime", Conv.toDateTime(item.get("TransTime")));
                a.put("UpdateDatetime", Conv.toDateTime(item.get("UpdateDatetime")));
                if (exists) {
                    // comme l'original : TotalVolumeDaily n'est repris que pour un compte existant
                    a.put("TotalVolumeDaily", item.get("TotalVolumeDaily"));
                    db().update("CustomerAccounts", a);
                } else {
                    db().insert("CustomerAccounts", a);
                }
            });
        } catch (RuntimeException ex) {
            return fail("Dowload CustomerAccount Error:", null, ex);
        }
    }

    public static boolean NormalDowGroup() {
        try {
            return paged("ServerDeal/GetNormalGroup", item -> {
                Row g = db().first("Group", "{GroupID} = :g", p("g", item.i("GroupID")));
                boolean exists = g != null;
                if (!exists) {
                    g = Row.of("GroupID", item.i("GroupID"), "Email", item.get("Email"));
                }
                copy(item, g, "Name", "ShortName", "Code", "Address", "Telephone", "Fax", "PostCode", "Contact", "CertificateNo", "Openbank",
                    "BankAccount", "LownFlg", "Memo", "GroupType", "Balance", "UpdateTime", "GroupStatue");
                if (exists) {
                    db().update("Group", g);
                } else {
                    db().insert("Group", g);
                }
            });
        } catch (RuntimeException ex) {
            return fail("Dowload Group Error:", null, ex);
        }
    }

    public static boolean DowloadPersonalCredit() {
        try {
            CustomerRespone r = BaseFunction.post("Station/DowloadPersonalCredit", param("StationID", IniInfo.StationID));
            if (r.Result()) {
                List<Row> list = rows(r);
                for (Row item : list) {
                    Row pc = db().first("PersonalCredit", "{ID} = :i", p("i", item.i("ID")));
                    if (pc == null) {
                        db().insert("PersonalCredit", item);
                        continue;
                    }
                    copy(item, pc, "CardNumber", "FaxNumber", "TaxIDNumber", "AccountName", "AccountPassword", "CarLicense", "CompanyID",
                        "CreateTime", "CreditLimit", "CustomerGroup", "Email", "FirstAddress", "SecondAddress", "ThirdAddress", "Phone",
                        "UpdateTime", "StationID", "BillDate", "UseAmount");
                    db().update("PersonalCredit", pc);
                }
                // comme l'original : la liste est insérée une seconde fois (échoue sur les clés existantes, d'où false)
                db().insertAll("PersonalCredit", list);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean DowloadStationMessage() {
        try {
            CustomerRespone r = BaseFunction.post("Station/StationUpdateMessage", param("StationID", IniInfo.StationID));
            if (r.Result()) {
                Row station = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                Row s = db().first("Stations", "{StationID} = :s", p("s", station.i("StationID")));
                if (s != null) {
                    s.put("Name", station.str("StationName"));
                    s.put("DayCompletedTime", station.get("DayTime"));
                    db().update("Stations", s);
                    IniInfo.DayCompletedTime = Conv.toInt(station.get("DayTime"));
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
