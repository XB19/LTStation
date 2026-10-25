package com.ltstation.center.sw.engine;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.BaseSql;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Moteur de la fenêtre principale « Station SoftWare » (Station.cs) : les fils d'exécution lancés
 * au chargement (Station_Load) et l'état affiché (tableaux de gauche, barre d'état, erreurs).
 *
 * <ul>
 *   <li>ThreadWork (10 s) : tableaux de gauche ; au premier passage, société et clé plateforme ;</li>
 *   <li>DowloadAndUpload (Speed × 100 ms) : envoi des transactions et des mesures, téléchargement
 *       des listes dont la version a changé sur Center ;</li>
 *   <li>LoadPluse (5 s) : impulsions, état des pistolets et des cuves, poste courant ;</li>
 *   <li>CmdDowload (0,5 s) : commandes de Center (préréglages…) ;</li>
 *   <li>SendExtednTransaction, DLog, supervision (SusperWork / FoRan) et matériel.</li>
 * </ul>
 */
@Component
public class StationEngine {

    private final LegacyLog stationLog = new LegacyLog("StationLog");
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private volatile boolean first = true;
    private volatile boolean startReady;
    private final Row customerParam = SW.dto().make("CustomerParam");

    // ------------------------------------------------------------ état affiché
    private volatile List<Row> oilData = List.of();
    private volatile List<Row> totalOilData = List.of();
    private volatile List<Row> nozStatue = List.of();
    private volatile String errorMessage = "";
    private volatile String progressText = "";
    private volatile int progressValue;
    private volatile int progressMax;
    private volatile boolean loading = true;

    /** Hooks du matériel (pistolets, jauges, afficheurs), branchés par le module matériel. */
    public interface Hardware {
        void start();

        void stop();
    }

    private final List<Hardware> hardware = new CopyOnWriteArrayList<>();

    private static Sugar db() {
        return SW.db();
    }

    public void register(Hardware hw) {
        hardware.add(hw);
    }

    public boolean running() {
        return IniInfo.ThreadStop;
    }

    // ------------------------------------------------------------ démarrage / arrêt

    /** Station_Load : démarre les fils d'exécution (une seule fois). */
    public synchronized void start() {
        if (IniInfo.ThreadStop) {
            return;
        }
        setIniInfo();
        IniInfo.ThreadStop = true;
        first = true;
        for (Hardware h : hardware) {
            try {
                h.start();
            } catch (RuntimeException e) {
                stationLog.writeLog("Load Error：" + e.getMessage());
            }
        }
        spawn("ThreadWork", this::threadWork);
        spawn("DowloadAndUpload", this::dowloadAndUpload);
        spawn("LoadPluse", this::loadPluse);
        spawn("DLog", this::dLog);
        spawn("CmdDowload", this::cmdDowload);
        if (!IniInfo.ExtendUrlRequest.isEmpty()) {
            spawn("SendExtednTransaction", this::sendExtednTransaction);
        }
        if (IniInfo.SupervisionType == 2 && IniInfo.CameraSet == 1) {
            spawn("FoRanUpLoad", this::foRanUpLoad);
        }
    }

    /** ExitItem / FormClosing : arrêt des fils d'exécution. */
    public synchronized void stop() {
        IniInfo.ThreadStop = false;
        for (Hardware h : hardware) {
            try {
                h.stop();
            } catch (RuntimeException ignored) {
                // arrêt silencieux
            }
        }
        threads.forEach(Thread::interrupt);
        threads.clear();
    }

    private void spawn(String name, Runnable r) {
        Thread t = new Thread(r, "station-" + name);
        t.setDaemon(true);
        threads.add(t);
        t.start();
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** SetIniInfo (Station.cs) : station locale et code envoyé à Center. */
    public void setIniInfo() {
        Row st = BaseSql.GetStation();
        if (st != null) {
            IniInfo.Code = st.s("Code");
            IniInfo.StationID = st.i("StationID");
            IniInfo.StationName = st.s("Name");
            customerParam.put("Code", st.s("Code"));
        }
    }

    // ------------------------------------------------------------ état pour l'interface

    public Map<String, Object> view() {
        return Row.of("oilData", oilData, "totalOilData", totalOilData, "nozStatue", nozStatue,
            "errorMessage", errorMessage, "progressText", progressText, "progressValue", progressValue,
            "progressMax", progressMax, "loading", loading, "currentShift", IniInfo.CurrentShift == null ? "" : IniInfo.CurrentShift,
            "loginName", IniInfo.LoginName == null ? "" : IniInfo.LoginName, "running", IniInfo.ThreadStop);
    }

    /** UpdateLeftView : relit les trois tableaux de gauche. */
    public void updateLeftView() {
        try {
            oilData = BaseSql.GetOilData(IniInfo.ScoreDeal);
            totalOilData = BaseSql.GetTotalOilData();
            List<Row> ns = BaseSql.GetNozStatue();
            nozStatue = ns == null ? List.of() : ns;
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void showError(String err) {
        errorMessage = err == null ? "" : err;
    }

    public void setProBarMessage(int pos, String info, int max) {
        if (pos == -1) {
            progressMax = max;
            progressValue = 0;
        } else {
            progressValue = pos;
        }
        progressText = info;
    }

    // ------------------------------------------------------------ ThreadWork

    private void threadWork() {
        while (IniInfo.ThreadStop) {
            try {
                updateLeftView();
                if (first) {
                    Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code);
                    CustomerRespone r = BaseFunction.post("ServerDeal/GetCompanyID", obj);
                    if (r.Result()) {
                        IniInfo.CompanyID = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())).i("CompanyID");
                    }
                    loading = false;
                    first = false;
                    r = BaseFunction.post("ServerDeal/GetPlatformKey", obj);
                    if (r.Result()) {
                        IniInfo.platform = BaseFunction.DecryptByAES(r.Mess());
                    }
                }
            } catch (RuntimeException ignored) {
                // Center injoignable : nouvel essai au tour suivant
            }
            if (!sleep(10000)) {
                return;
            }
        }
    }

    // ------------------------------------------------------------ envoi vers Center

    private Row ent(String cls, Row r) {
        String full = "DataBaseModel." + cls;
        return SW.dto().has(full) ? SW.dto().shape(full, r) : r;
    }

    /** UploadTransaction : 50 transactions non envoyées, puis une ancienne transaction (upload = 9). */
    private boolean uploadTransaction() {
        try {
            for (Row item : db().list("OilTransactions", "{upload} = '0' ORDER BY {TrueTime} LIMIT 50", null)) {
                stationLog.writeLog("Up:" + item.i("ID"));
                CustomerRespone r = BaseFunction.post("ServerDeal/TransactionDeal", ent("OilTransactions", item));
                if (r.Result()) {
                    String asn = item.s("Asn");
                    if (!asn.startsWith("000000") && !asn.startsWith("010004")) {
                        item.put("PayMentDealID", 1);
                    }
                    item.put("upload", "1");
                    db().update("OilTransactions", item);
                } else {
                    stationLog.writeLog("Transaction Error:" + BaseFunction.DecryptByAES(r.Mess()));
                }
            }
            Row old = db().first("OilTransactions", "{upload} = '9' ORDER BY {TrueTime}", null);
            if (old != null) {
                old.put("upload", BaseFunction.post("ServerDeal/OldTransactionDeal", ent("OilTransactions", old)).Result() ? "1" : "8");
                db().update("OilTransactions", old);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** DealTransactionTank : déduit le volume vendu du stock de la cuve et enregistre une mesure. */
    private boolean dealTransactionTank() {
        try (Sugar.Tx tx = db().begin()) {
            try {
                for (Row item : db().list("OilTransactions", "{TankDeal} = false ORDER BY {TrueTime}", null)) {
                    Row noz = db().first("GunConfig", "{GUNCODE} = :g", Sugar.p("g", item.i("POSP")));
                    if (noz == null) {
                        continue;
                    }
                    Row ts = db().first("TankStatus", "{ID} = :t", Sugar.p("t", noz.i("TANKCODE")));
                    if (ts == null) {
                        continue;
                    }
                    ts.put("OilVol", ts.d("OilVol") - item.d("Volume"));
                    db().update("TankStatus", ts);
                    db().insert("TankMeasurements", Row.of("EmployeeID", IniInfo.LoginID, "RecSeq", 0, "SampOilDensity", item.d("Volume"),
                        "OilVolume", ts.d("OilVol"), "Time", LocalDateTime.now(), "Upload", 0,
                        "Shift", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH")), "TankCode", ts.i("ID")));
                    item.put("TankDeal", true);
                    db().update("OilTransactions", item);
                }
                tx.commit();
                return true;
            } catch (RuntimeException e) {
                tx.rollback();
                return false;
            }
        }
    }

    private boolean uploadTankStatue() {
        try {
            List<Row> list = db().query("SELECT a.id AS \"ID\", a.oil_code AS \"OilCode\", a.height AS \"Height\", a.vol AS \"Vol\", "
                + "b.oil_height AS \"OilHeight\", b.oil_vol AS \"OilVol\", b.oil_temp AS \"OilTemp\", b.water_height AS \"WaterHeight\", "
                + "b.water_vol AS \"WaterVol\" FROM tank_config a LEFT JOIN tank_status b ON a.id = b.id ORDER BY a.id", null);
            Row st = SW.dto().make("StationTank", "StationID", IniInfo.StationID, "list", SW.dto().shapeAll("TankMessage", list));
            return BaseFunction.post("ServerDeal/UpLoadTankStatueDeal", st).Result();
        } catch (RuntimeException e) {
            stationLog.writeLog("Upload TankStatue Error:" + e.getMessage());
            return false;
        }
    }

    private boolean uploadTankHistory() {
        try {
            for (Row item : db().list("TankMeasurements", "{Upload} = 0 OR {Upload} IS NULL", null)) {
                if (BaseFunction.post("ServerDeal/UpLoadTankHistoryDeal", ent("TankMeasurements", item)).Result()) {
                    item.put("Upload", 1);
                    db().update("TankMeasurements", item);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** UploadNozStatue : supprime les états de plus de 30 s puis envoie la vue NozView. */
    private boolean uploadNozStatue() {
        try {
            long now = BaseFunction.ConvertDateTimeToInt();
            db().delete("NozStatue", "{UpdateTime} <= :t", Sugar.p("t", now - 30000));
            List<Row> list = db().query("SELECT guncode AS \"GUNCODE\", current_ppu AS \"CurrentPPU\", new_ppu AS \"NewPPU\", "
                + "validate_time AS \"ValidateTime\", gun_id AS \"GunID\", statue AS \"Statue\", amount AS \"Amount\", volume AS \"Volume\", "
                + "code AS \"Code\", vol_total AS \"VolTotal\", station_id AS \"StationID\" FROM noz_view", null);
            BaseFunction.post("ServerDeal/UpLoadNozStatue", SW.dto().make("V_NozViewList", "StationID", IniInfo.StationID,
                "list", SW.dto().shapeAll("V_NozView", list)));
            return true;
        } catch (RuntimeException e) {
            stationLog.writeLog("UploadNozStatue:" + e.getMessage());
            return false;
        }
    }

    private void upload() {
        if (!uploadTransaction()) {
            stationLog.writeLog("Transaction Upload Error");
        }
        if (!dealTransactionTank()) {
            stationLog.writeLog("Transaction Tank Deal Error");
        }
    }

    // ------------------------------------------------------------ téléchargements depuis Center

    private void dowloadAndUpload() {
        while (IniInfo.ThreadStop) {
            try {
                upload();
                Row dv = db().first("DataVersions");
                boolean isNew = dv == null;
                if (dv == null) {
                    dv = SW.dto().make("DataVersions");
                }
                Row center = getCenterVersion();
                if (center != null) {
                    boolean flag = true;
                    flag &= dowloadCustomer();
                    flag &= dowloadCustomerCardInform();
                    flag &= dowloadGroup();
                    flag &= dowloadCustomerAccount();
                    flag &= dowGroupCardInfos();
                    flag &= dowGroupAccounts();
                    flag &= dowTicket();
                    flag &= dowEmployeeCardInfo();
                    if (!IniInfo.VIDDO) {
                        flag &= dowCarGroup();
                        flag &= dowCarUser();
                        flag &= dowCarCY();
                    }
                    flag &= versioned(dv, center, "Coupon", this::dowCoupon);
                    flag &= versioned(dv, center, "MachineCfg", this::dowStation);
                    flag &= versioned(dv, center, "GradeSpec", this::dowGradeSpec);
                    flag &= versioned(dv, center, "PPU", this::dowOilPrice);
                    flag &= versioned(dv, center, "BasicBlack", this::dowBasicBlack);
                    flag &= versioned(dv, center, "AppendBlack", this::dowAppendBlack);
                    flag &= versioned(dv, center, "DelBlack", this::dowDelBlack);
                    flag &= versioned(dv, center, "White", this::dowWhite);
                    flag &= versioned(dv, center, "Gray", this::dowGray);
                    flag &= versioned(dv, center, "Employee", this::dowEmployee);
                    flag &= versioned(dv, center, "DistributionPoints", this::dowDistributionPoint);
                    flag &= versioned(dv, center, "TransportInfoes", this::dowTransportInfo);
                    if (isNew) {
                        db().insert("DataVersions", dv);
                    } else {
                        db().update("DataVersions", dv);
                    }
                    startReady = true;
                    showError("");
                }
            } catch (RuntimeException e) {
                showError(e.getMessage());
            }
            if (!sleep(Math.max(IniInfo.SpeedRun, 1) * 100L)) {
                return;
            }
        }
    }

    private interface Dow {
        boolean run();
    }

    /** Télécharge une liste si sa version a changé sur Center, puis retient la nouvelle version. */
    private static boolean versioned(Row local, Row center, String key, Dow dow) {
        if (local.i(key) != center.i(key)) {
            if (dow.run()) {
                local.put(key, center.i(key));
                return true;
            }
            return false;
        }
        return true;
    }

    private Row getCenterVersion() {
        try {
            CustomerRespone r = BaseFunction.post("ServerDeal/GetVersion", SW.dto().make("CustomerParam", "Code", IniInfo.Code));
            if (!r.Result()) {
                if (r.Mess() != null) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                throw new IllegalStateException("Échec de la connexion au réseau");
            }
            return Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException e) {
            showError("Version" + e.getMessage());
            return null;
        }
    }

    private List<Row> rows(CustomerRespone r) {
        return Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
    }

    private void setAll(boolean byReady) {
        customerParam.put("DowloadAll", !byReady || !startReady);
    }

    private boolean dowloadCustomer() {
        try {
            customerParam.put("Start", 1);
            setAll(true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetCustomer", customerParam);
            int num = 0;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    try {
                        Row c = db().first("Customers", "{CustomerID} = :c", Sugar.p("c", item.i("CustomerID")));
                        boolean add = c == null;
                        if (add) {
                            c = Row.of("CustomerID", item.i("CustomerID"));
                        }
                        c.put("Name", item.get("Name"));
                        c.put("PostCode", item.get("PostCode"));
                        c.put("Telephone", item.get("Telephone"));
                        c.put("VehicleNo", item.get("VehicleNo"));
                        c.put("Address", item.get("Address"));
                        c.put("CertificateNo", item.get("CertificateNo"));
                        c.put("CertificateTypeID", item.get("CertificateTypeID"));
                        c.put("VehicleColorID", item.i("VehicleColorID"));
                        c.put("VehicleTypeID", item.i("VehicleTypeID"));
                        if (add) {
                            db().insert("Customers", c);
                        } else {
                            db().update("Customers", c);
                        }
                    } catch (RuntimeException ignored) {
                        // ligne suivante
                    }
                }
                num++;
                if (num > r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post("ServerDeal/GetCustomer", customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowloadCustomerCardInform() {
        try {
            customerParam.put("Start", 0);
            setAll(true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetCustomerCardInform", customerParam);
            int num = 0;
            while (r.Result()) {
                try {
                    for (Row item : rows(r)) {
                        int cid = item.i("CustomerID");
                        Row c = Row.of("ASN", item.get("ASN"), "CustomerID", cid, "GroupID", item.i("GroupID"),
                            "isCreditCard", item.b("isCreditCard"), "Suspend", item.get("CardStatusTypeID"), "CreditLines", item.get("CreditLines"),
                            "UsedAmountMonthly", item.get("UsedAmountMonthly"), "PreferentialPrice", item.get("PreferentialPrice"),
                            "Discount", item.get("Discount"), "LimitType", item.get("LimitTypeID"), "UseConfine", item.get("UseConfine"),
                            "LimitOil", item.get("LimitOil"), "OiConsumption", item.get("LimitMileage"), "LimitVolume", item.get("LimitVolume"),
                            "LimitTime", item.get("LimitTime"), "LimitAmount", item.get("LimitAmount"),
                            "LimitVolumeMonthly", item.get("LimitVolumeMonthly"), "LimitAmountMonthly", item.get("LimitAmountMonthly"),
                            "UpdateDatetime", item.get("UpdateDatetime"), "Name", BaseFunction.GetCustomer(cid, "Name"),
                            "Telephone", BaseFunction.GetCustomer(cid, "Telephone"), "VehicleNo", BaseFunction.GetCustomer(cid, "VehicleNo"),
                            "SpecialPrice", item.get("SpecialPrice"), "CipherFlg", item.get("CipherFlg"));
                        if (db().any("CustomerCardInfos", "{ASN} = :a", Sugar.p("a", item.str("ASN")))) {
                            db().update("CustomerCardInfos", c);
                        } else {
                            db().insert("CustomerCardInfos", c);
                        }
                    }
                    num++;
                    if (num <= r.TotalCount()) {
                        customerParam.put("Start", num);
                        r = BaseFunction.post("ServerDeal/GetCustomerCardInform", customerParam);
                        continue;
                    }
                } catch (RuntimeException e) {
                    stationLog.writeLog("Card Info Error:" + e.getMessage());
                    if (!sleep(1)) {
                        return false;
                    }
                    break;
                }
                break;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowloadCustomerAccount() {
        try {
            customerParam.put("Start", 0);
            setAll(true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetCustomerAccount", customerParam);
            int num = 0;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    Row a = Row.of("CustomerAccountID", item.i("CustomerAccountID"), "Balance", item.get("Balance"),
                        "TotalTimesDaily", item.get("TotalTimesDaily"), "TotalAmountDaily", item.get("TotalAmountDaily"),
                        "TotalAmountMonthly", item.get("TotalAmountMonthly"), "TotalVolumeMonthly", item.get("TotalVolumeMonthly"),
                        "TotalVolumeDaily", item.get("TotalVolumeDaily"), "MileageLastest", item.get("MileageLastest"),
                        "VolumeLastest", item.get("VolumeLastest"), "UpdateDatetime", Conv.toDateTime(item.get("UpdateDatetime")),
                        "TransTime", Conv.toDateTime(item.get("TransTime")));
                    if (db().any("CustomerAccounts", "{CustomerAccountID} = :a", Sugar.p("a", item.i("CustomerAccountID")))) {
                        db().update("CustomerAccounts", a);
                    } else {
                        db().insert("CustomerAccounts", a);
                    }
                }
                num++;
                if (num >= r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post("ServerDeal/GetCustomerAccount", customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowloadGroup() {
        try {
            customerParam.put("Start", 0);
            customerParam.put("Code", IniInfo.Code);
            setAll(true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetGroup", customerParam);
            int num = 0;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    Row g = Row.of("GroupID", item.i("GroupID"), "Name", item.get("Name"), "ShortName", item.get("ShortName"),
                        "Code", item.get("Code"), "CertificateNo", item.get("CertificateNo"), "Address", item.get("Address"),
                        "Email", item.get("Email"), "Fax", item.get("Fax"), "PostCode", item.get("PostCode"), "Telephone", item.get("Telephone"));
                    if (db().any("Group", "{GroupID} = :g", Sugar.p("g", item.i("GroupID")))) {
                        db().update("Group", g);
                    } else {
                        db().insert("Group", g);
                    }
                }
                num++;
                if (num >= r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post("ServerDeal/GetGroup", customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowGroupCardInfos() {
        try {
            customerParam.put("Start", 0);
            setAll(true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetGroupCardInfos", customerParam);
            int num = 0;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    String asn = item.str("Asn") != null ? item.str("Asn") : item.str("ASN");
                    Row g = Row.of("GroupID", item.i("GroupID"), "UsedAmountMonthly", item.get("UsedAmountMonthly"),
                        "UpdateDate", item.get("UpdateDate"), "CreditLines", item.get("CreditLines"), "Balance", item.get("Balance"),
                        "MinBalance", item.get("MinBalance"), "Asn", asn, "CardStatusTypeID", item.get("CardStatusTypeID"));
                    if (db().any("GroupCardInfos", "{Asn} = :a", Sugar.p("a", asn))) {
                        db().update("GroupCardInfos", g);
                    } else {
                        db().insert("GroupCardInfos", g);
                    }
                }
                num++;
                if (num >= r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post("ServerDeal/GetGroupCardInfos", customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowGroupAccounts() {
        try {
            customerParam.put("Start", 0);
            customerParam.put("DowloadAll", true);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetGroupAccounts", customerParam);
            int num = 0;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    Row a = Row.of("AccountNo", item.get("AccountNo"), "Balance", item.get("Balance"), "BookBalance", item.get("BookBalance"),
                        "CardStationCode", item.get("CardStationCode") == null ? "" : item.get("CardStationCode"),
                        "ConsumeDatetime", item.get("ConsumeDatetime"), "DepositDatetime", item.get("DepositDatetime"),
                        "LoadUpLimit", item.get("LoadUpLimit"), "ServiceFee", item.get("ServiceFee"), "Status", item.get("Status"),
                        "UpdateDatetime", item.get("UpdateDatetime"));
                    Row existing = db().first("GroupAccounts", "{AccountNo} = :n", Sugar.p("n", item.str("AccountNo")));
                    Row gci = db().first("GroupCardInfos", "{GroupID} = :g", Sugar.p("g", Conv.toInt(item.str("AccountNo"))));
                    if (gci != null) {
                        gci.put("Balance", a.get("Balance"));
                        db().update("GroupCardInfos", gci);
                    }
                    if (existing != null) {
                        db().update("GroupAccounts", a);
                    } else {
                        db().insert("GroupAccounts", a);
                    }
                }
                num++;
                if (num >= r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post("ServerDeal/GetGroupAccounts", customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Téléchargement paginé « remplacer ou ajouter » d'une liste par sa clé (ticket, coupons…). */
    private boolean pagedUpsert(String action, String table, String keyCol, boolean deleteFirst, boolean skipExisting, boolean whileTotal) {
        try {
            customerParam.put("Start", 0);
            int num = 0;
            CustomerRespone r = BaseFunction.post(action, customerParam);
            if (r.Result() && deleteFirst) {
                db().delete(table, null, null);
            }
            while (whileTotal ? num < r.TotalCount() : r.Result()) {
                for (Row item : rows(r)) {
                    boolean exists = keyCol != null && db().any(table, "{" + keyCol + "} = :k", Sugar.p("k", item.get(keyCol)));
                    if (exists) {
                        if (!skipExisting) {
                            db().update(table, item);
                        }
                        continue;
                    }
                    if (table.equals("WhiteCardLists")) {
                        item.put("StationCode", customerParam.get("Code"));
                    }
                    db().insert(table, item);
                }
                num++;
                if (num > r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post(action, customerParam);
                if (!whileTotal && !r.Result()) {
                    break;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowTicket() {
        return pagedUpsert("ServerDeal/GetTicket", "TicketManagements", "TicketManagementID", false, false, false);
    }

    private boolean dowCoupon() {
        return pagedUpsert("ServerDeal/GetCouponInfoes", "CouponInfoes", null, false, false, false);
    }

    private boolean dowBasicBlack() {
        return pagedUpsert("ServerDeal/GetBasicBlack", "BasicBlackLists", "ASN", true, true, false);
    }

    private boolean dowAppendBlack() {
        return pagedUpsert("ServerDeal/GetAppendBlack", "AppendBlackLists", "ASN", true, true, false);
    }

    private boolean dowDelBlack() {
        return pagedUpsert("ServerDeal/GetDelBlack", "DeleteBlackLists", null, true, false, false);
    }

    private boolean dowWhite() {
        return pagedUpsert("ServerDeal/GetWhite", "WhiteCardLists", "ASN", true, true, true);
    }

    private boolean dowGray() {
        return pagedUpsert("ServerDeal/GetGray", "GrayCardLists", null, true, false, true);
    }

    private boolean dowDistributionPoint() {
        return pagedUpsert("ServerDeal/GetDistributionPoint", "DistributionPoints", null, true, false, false);
    }

    private boolean dowTransportInfo() {
        return pagedUpsert("ServerDeal/GetTransportInfo", "TransportInfoes", null, true, false, false);
    }

    private boolean dowStation() {
        try {
            CustomerRespone r = BaseFunction.post("ServerDeal/GetStation", customerParam);
            if (r.Result()) {
                Row st = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                st.put("MeasureTime", LocalDateTime.now().format(Conv.STD));
                db().updatePresent("Stations", st);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowGradeSpec() {
        try {
            customerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetOilType", customerParam);
            if (r.Result()) {
                db().delete("GradeSpecifics", null, null);
                for (Row item : rows(r)) {
                    db().insert("GradeSpecifics", item);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowOilPrice() {
        try {
            customerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.post("ServerDeal/GetPrice", customerParam);
            if (r.Result()) {
                db().delete("PriceInfos", null, null);
                for (Row item : rows(r)) {
                    db().insert("PriceInfos", Row.of("Code", item.get("Code"), "CurrentDensity", item.get("CurrentDensity"),
                        "CurrentPPU", item.get("CurrentPPU"), "Newdensity", item.get("Newdensity"), "NewPPU", item.get("NewPPU"),
                        "StationID", item.get("StationID"), "ValidateTime", Conv.toDateTime(item.get("ValidateTime"))));
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowEmployee() {
        try {
            customerParam.put("Start", 0);
            int num = 0;
            CustomerRespone r = BaseFunction.post("ServerDeal/GetEmployee", customerParam);
            if (r.Result()) {
                db().delete("Employees", null, null);
                while (num < r.TotalCount()) {
                    for (Row item : rows(r)) {
                        db().insert("Employees", Row.of("StationID", item.get("StationID"), "EmployeeID", item.i("EmployeeID"),
                            "Code", Conv.toStr(item.get("CodeInner")), "Name", item.get("Name"), "Memo1", item.get("Address"),
                            "Memo2", "".equals(item.str("Memo")) ? "654789" : item.get("Memo"), "StationType", item.get("PostCode"),
                            "CertifyKey", item.get("CertifyKey"), "Birthday", item.get("Birthday"), "Status", "0",
                            "Gender", Conv.toStr(item.get("GenderID"))));
                    }
                    num++;
                    if (num > r.TotalCount()) {
                        break;
                    }
                    customerParam.put("Start", num);
                    r = BaseFunction.post("ServerDeal/GetEmployee", customerParam);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowEmployeeCardInfo() {
        try {
            customerParam.put("Start", 0);
            int num = 1;
            CustomerRespone r = BaseFunction.post("ServerDeal/GetEmployeeCardInfoMessage", customerParam);
            if (r.Result()) {
                while (num <= r.TotalCount()) {
                    for (Row item : rows(r)) {
                        Row e = Row.of("ASN", item.get("ASN"), "AccountNo", Conv.toStr(item.get("CustomerAccountID")), "StationCode", IniInfo.Code,
                            "CardType", Conv.toStr(item.get("CardTypeID")), "EmployeeCode", Conv.toStr(item.get("EmployeeID")),
                            "Name", BaseFunction.GetEmpName(item.i("EmployeeID")),
                            "CipherFlg", item.get("CipherFlg") == null ? "9999" : item.get("CipherFlg"),
                            "Status", Conv.toStr(item.get("CardStatusTypeID")), "IssueDatetime", item.get("IssueDatetime"),
                            "ValidDatetime", item.get("ValidDatetime"), "SpecialPrice", item.get("SpecialPrice"));
                        if (db().any("EmployeeCardInfos", "{ASN} = :a", Sugar.p("a", item.str("ASN")))) {
                            db().update("EmployeeCardInfos", e);
                        } else {
                            db().insert("EmployeeCardInfos", e);
                        }
                    }
                    num++;
                    if (num > r.TotalCount()) {
                        break;
                    }
                    customerParam.put("Start", num);
                    r = BaseFunction.post("ServerDeal/GetEmployeeCardInfoMessage", customerParam);
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** DowCarGroup / DowCarUser / DowCarCY : listes des véhicules (copie champ par champ). */
    private boolean dowCars(String action, String table) {
        try {
            customerParam.put("Start", 1);
            setAll(true);
            CustomerRespone r = BaseFunction.post(action, customerParam);
            int num = 1;
            while (r.Result()) {
                for (Row item : rows(r)) {
                    Row existing = db().first(table, "{ID} = :id", Sugar.p("id", item.i("ID")));
                    if (existing != null) {
                        existing.putAll(item);
                        db().update(table, existing);
                    } else {
                        db().insert(table, item);
                    }
                }
                num++;
                if (num > r.TotalCount()) {
                    break;
                }
                customerParam.put("Start", num);
                r = BaseFunction.post(action, customerParam);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean dowCarGroup() {
        return dowCars("ServerDeal/DowLoadCarGroup", "CarGroup");
    }

    private boolean dowCarUser() {
        return dowCars("ServerDeal/DowLoadCarUser", "CarUser");
    }

    private boolean dowCarCY() {
        return dowCars("ServerDeal/DowLoadCarCY", "CylinderData");
    }

    // ------------------------------------------------------------ LoadPluse

    private void loadPluse() {
        while (IniInfo.ThreadStop) {
            try {
                CustomerRespone r = BaseFunction.post("ServerDeal/GetPluse", SW.dto().make("CustomerParam", "Code", IniInfo.Code));
                if (r.Result()) {
                    Row pl = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                    if (pl != null) {
                        for (Row gun : db().list("GunConfig")) {
                            db().insert("NozPluse", Row.of("Createtime", LocalDateTime.now().format(Conv.STD), "NozID", gun.i("GUNCODE"),
                                "PLUSE", pl.get("PLUSE"), "Deal", false));
                        }
                    }
                }
                if (!uploadNozStatue()) {
                    stationLog.writeLog("NozStatue Upload Error");
                }
                if (!uploadTankStatue()) {
                    stationLog.writeLog("Tank Update Error");
                }
                if (!uploadTankHistory()) {
                    stationLog.writeLog("Tank History Upload Error");
                }
                IniInfo.CurrentShift = currentShift();
            } catch (RuntimeException ignored) {
                // tour suivant
            }
            if (!sleep(5000)) {
                return;
            }
        }
    }

    /** Poste courant : dernier poste clôturé + 1 (ou poste 01 du lendemain), sinon AAAAMMJJ01. */
    public static String currentShift() {
        String text = db().scalar("SELECT max(shift) FROM duty_gun_info", null, String.class);
        if (text == null) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "01";
        }
        long count = db().count("ShiftConfig", null, null);
        int num = Conv.toInt(text.substring(text.length() - 2));
        if (num >= count) {
            LocalDate d = LocalDate.parse(text.substring(0, 8), DateTimeFormatter.ofPattern("yyyyMMdd")).plusDays(1);
            return d.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "01";
        }
        return text.substring(0, text.length() - 2) + BaseFunction.SetStrLen(String.valueOf(num + 1), 2);
    }

    // ------------------------------------------------------------ CmdDowload, envoi externe, journaux

    private void cmdDowload() {
        while (IniInfo.ThreadStop) {
            try {
                CustomerRespone r = BaseFunction.post("ServerDeal/CmdDowload", SW.dto().make("DowloadCmd", "StationID", IniInfo.StationID));
                if (r.Result()) {
                    List<Row> list = rows(r);
                    List<Row> starts = new ArrayList<>();
                    for (Row item : list) {
                        db().insert("CmdList", item);
                        if ("62".equals(item.str("CmdType"))) {
                            stationLog.writeLog("DowloadCmd :" + item.s("CmdData"));
                            Row ds = Wire.parseRow(item.s("CmdData"));
                            ds.put("Deal", 0);
                            starts.add(ds);
                        }
                    }
                    db().insertAll("DealStart", starts);
                } else if (r.Mess() != null) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
            } catch (RuntimeException e) {
                stationLog.writeLog("DowloadCmdError:" + e.getMessage());
            }
            if (!sleep(500)) {
                return;
            }
        }
    }

    private void sendExtednTransaction() {
        while (IniInfo.ThreadStop) {
            try {
                List<Row> list = db().queryAs("OilTransactions",
                    "SELECT * FROM oil_transactions WHERE unit = 224 AND coalesce(nullif(reserved2, '')::int, 0) < 4 LIMIT 50", null);
                for (Row item : list) {
                    item.put("Reserved2", SupervisionFunction.NPSendExtendTransation(item) ? "4"
                        : String.valueOf(Conv.toInt(item.str("Reserved2") == null ? "0" : item.str("Reserved2")) + 1));
                }
                db().updateAll("OilTransactions", list);
            } catch (RuntimeException ignored) {
                return;
            }
            if (!sleep(2000)) {
                return;
            }
        }
    }

    /** DLog : purge des journaux IIS (C:/inetpub/logs/LogFiles), comme l'original. */
    private void dLog() {
        while (IniInfo.ThreadStop) {
            logDelete(Path.of("C:/inetpub/logs/LogFiles"));
            if (!sleep(60000)) {
                return;
            }
        }
    }

    private static void logDelete(Path dir) {
        try {
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (var s = Files.list(dir)) {
                for (Path p : s.toList()) {
                    if (Files.isRegularFile(p)) {
                        if (Files.getLastModifiedTime(p).toInstant().isBefore(java.time.Instant.now().plus(java.time.Duration.ofDays(15)))) {
                            Files.deleteIfExists(p);
                        }
                    } else {
                        logDelete(p);
                    }
                }
            }
            Files.deleteIfExists(dir);
        } catch (Exception ignored) {
            // comme l'original
        }
    }

    private void foRanUpLoad() {
        try {
            while (IniInfo.ThreadStop) {
                List<Row> list = db().list("OilTransactions", "{Reserved2} = '0' AND {Unit} = 240 ORDER BY {TrueTime} LIMIT 5", null);
                for (Row item : list) {
                    if (SupervisionFunction.FoRanTransactionUp(item, new String[1])) {
                        item.put("Reserved2", "1");
                    }
                }
                db().updateAll("OilTransactions", list);
                if (!sleep(10000)) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            stationLog.writeLog("FoRan Upload Error:" + e.getMessage());
        }
    }

    /** Texte de la langue courante (raccourci). */
    static String t(String key) {
        return MultiLanguage.getText(key);
    }
}
