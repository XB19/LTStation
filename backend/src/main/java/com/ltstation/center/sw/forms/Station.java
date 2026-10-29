package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.engine.StationEngine;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CameraFtpServer;
import com.ltstation.center.sw.hw.PriceSerial;
import com.ltstation.center.sw.hw.TCPClientQY;
import com.ltstation.center.sw.hw.TankSerial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Station : fenêtre principale du mode IC (Station.cs). Barre de menus et d'outils selon les droits
 * (TypeSwitch), pistolets (Nozzle) et jauges des cuves (Tank) intégrés, tableaux des dernières
 * transactions, des totaux par employé et de l'état des pistolets, barre d'état ; démarrage des fils
 * de la station (StationEngine) et du matériel (sonde, afficheurs, supervision QY, FTP des caméras).
 */
@SwWindow("Station")
public class Station extends SwForm {

    /** Matériel et fils de la station : uniques pour le poste, conservés si la page est rechargée. */
    private static final List<Nozzle> nozzles = new CopyOnWriteArrayList<>();
    private static Tank tankShow;
    private static TankSerial tankserial;
    private static TCPClientQY TCPClientQY;
    private static final List<PriceSerial> priceSerials = new CopyOnWriteArrayList<>();
    private static CameraFtpServer ftp;
    private static volatile boolean hardwareStarted;
    private static final LegacyLog StationLog = new LegacyLog("StationLog");
    /** Fenêtre principale ouverte (Owner des fenêtres de configuration du matériel). */
    private static volatile Station current;

    public TankVolume TVolume;
    private String Curbutton;
    private final Map<String, String> users = new LinkedHashMap<>();
    private List<Row> shownOil;
    private List<Row> shownTotal;
    private List<Row> shownNoz;

    private static StationEngine engine() {
        return StationEngine.get();
    }

    @Override
    protected void init() {
        current = this;
        SetIniInfo();
    }

    // ------------------------------------------------------------ droits

    private void TypeSwitch(int id) {
        try {
            Row a = db().first("AuthorityMess", "{User_IDMess} = :id", p("id", id));
            if (a == null) {
                MessageBox(L("AuthorityProblem"));
                Close();
                return;
            }
            String content = IniInfo.SpecialInt == 1 ? "3,4,7,8,10,13,15,16,17,18" : a.str("AuthorityContent");
            String[] array = content.split(",", -1);
            for (String b : List.of("OilPrice", "StationPrint", "CardMake", "CardUpDate", "CardLost", "CardChange", "Deposit", "TransactionSearch",
                "EmpManager", "Shift", "DailyReport", "MonthReport", "CardSearch", "ScoreDeal", "DisCount", "KeyManagement", "PSAMMake", "BusinessReport",
                "PasswodLost", "AuthorityManagement", "ShiftCancel")) {
                c(b).Visible(false);
            }
            for (String m : List.of("TopSystemSeting", "TopStationManager", "TopCommunication", "ExtentTool", "TopUserCheck")) {
                c(m).Visible(false);
            }
            for (String m : List.of("StationPrint", "TankItem", "TankVolumeItem", "TankPortItem", "BaseMessage", "SuperviseItem", "PortItem", "NozItem",
                "RFIDItem", "PriceDisplay")) {
                c(m).Visible(true);
            }
            for (String m : List.of("ShiftItem", "TransactionR", "ShiftReport", "DailyR", "MonthR", "AppItem", "DisplayPay")) {
                c(m).Visible(false);
            }
            if (array.length != 0) {
                for (String s : array) {
                    switch (s) {
                        case "1" -> c("OilPrice").Visible(true);
                        case "4" -> c("CardMake").Visible(true);
                        case "7" -> c("CardUpDate").Visible(true);
                        case "10" -> c("CardLost").Visible(true);
                        case "13" -> c("CardChange").Visible(true);
                        case "16" -> c("Deposit").Visible(true);
                        case "2" -> {
                            c("TransactionSearch").Visible(true);
                            c("TransactionR").Visible(true);
                        }
                        case "5" -> c("EmpManager").Visible(true);
                        case "8" -> {
                            c("Shift").Visible(true);
                            c("ShiftReport").Visible(true);
                        }
                        case "11" -> {
                            c("DailyReport").Visible(true);
                            c("DailyR").Visible(true);
                        }
                        case "14" -> {
                            c("MonthReport").Visible(true);
                            c("MonthR").Visible(true);
                        }
                        case "17" -> c("CardSearch").Visible(true);
                        case "3" -> c("ScoreDeal").Visible(true);
                        case "6" -> c("DisCount").Visible(true);
                        case "9" -> c("KeyManagement").Visible(true);
                        case "12" -> {
                            for (String m : List.of("PSAMMake", "BaseMessage", "SuperviseItem", "PortItem", "NozItem", "ShiftItem")) {
                                c(m).Visible(true);
                            }
                        }
                        case "15" -> c("BusinessReport").Visible(true);
                        case "18" -> c("PasswodLost").Visible(true);
                        case "19" -> c("TopSystemSeting").Visible(true);
                        case "20" -> c("TopStationManager").Visible(true);
                        case "21" -> c("TopCommunication").Visible(true);
                        case "22" -> c("ExtentTool").Visible(true);
                        case "23" -> c("TopUserCheck").Visible(true);
                        case "24" -> c("AuthorityManagement").Visible(true);
                        default -> {
                        }
                    }
                }
            }
            if (a.i("User_IDMess") == 1) {
                c("AuthorityManagement").Visible(true);
            }
            c("TankItem").Visible(true);
            c("TankVolumeItem").Visible(true);
            c("TankPortItem").Visible(true);
            c("ShiftCancel").Visible(true);
            Row reg = db().first("RegisterInfo", "{SourceInfo} = 'CarUser'", null);
            if (reg != null && BaseFunction.DecryptByAES(reg.str("SecretInfo")).contains("CarUserActive")) {
                c("CarNoManager").Visible(true);
                c("CarNoVerify").Visible(true);
                c("SurveillanceCamera").Visible(true);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void SetIniInfo() {
        IniInfo.ThreadStop = true;
        Row st = db().first("Stations");
        if (st != null) {
            IniInfo.Code = st.s("Code");
            IniInfo.StationID = st.i("StationID");
            IniInfo.StationName = st.s("Name");
        }
        TypeSwitch(IniInfo.LoginType);
    }

    // ------------------------------------------------------------ chargement

    public void Station_Load() {
        try {
            g("OilDataView").ColumnVisible("Deal", IniInfo.ScoreDeal != 0);
            c("GunAndTankTab").SelectedIndex(0);
            NozSet();
            TankSet();
            if (!hardwareStarted) {
                hardwareStarted = true;
                if (IniInfo.NonLevel) {
                    thread("TankStockUpdate", this::TankStockUpdate);
                } else {
                    MakeTankSerial();
                }
                OtherSerialDeal();
                if (IniInfo.SupervisionType == 1 && !IniInfo.UNITID.isEmpty() && !IniInfo.USN.isEmpty() && !IniInfo.ServerIP.isEmpty() && IniInfo.Port != 0) {
                    TCPClientQY = new TCPClientQY(IniInfo.ServerIP, IniInfo.Port);
                    TCPClientQY.TcpConnect();
                    thread("SusperWork", this::SusperWork);
                }
                engine().start();
                if (IniInfo.CameraSet == 1) {
                    users.put("admin", "admin");
                    if (ftp == null) {
                        ftp = new CameraFtpServer(IniInfo.FORanIP, users, StationLog, Path.of("station"), Station::DealCarNo);
                        thread("FTP", ftp::ListenClientConnect);
                        StationLog.writeLog("FTP Start");
                    }
                    if (IniInfo.SupervisionType == 2) {
                        thread("FoRanCyDetialDowload", this::FoRanCyDetialDowload);
                    }
                }
            }
            c("LoginEmp").Text(L("LoginEmployee") + ":" + IniInfo.LoginName);
            if (IniInfo.NormalType == 2) {
                for (String m : List.of("AppItem", "RFIDItem", "PriceDisplay", "DisplayPay", "RegisiterValid", "VirtueScore", "TankPortItem")) {
                    c(m).Visible(false);
                }
                c("TankShow").Visible(false);
                g("OilDataView").HeaderText("dataGridViewTextBoxColumn2", L("KKG"));
                g("ToatlEmp").HeaderText("TotalVolume", L("TotalKG"));
            }
            // Mise à jour de l'affichage depuis les fils de la station (Invoke / BeginInvoke de l'original)
            Timer("RefreshView", 1000);
            RefreshView();
        } catch (RuntimeException ex) {
            StationLog.writeLog("Load Error：" + ex.getMessage());
        }
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, "station-" + name);
        t.setDaemon(true);
        t.start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Tableaux de gauche, poste courant, progression et erreurs (état du moteur de la station). */
    public void RefreshView() {
        StationEngine en = engine();
        if (en == null) {
            return;
        }
        Map<String, Object> v = en.view();
        Row view = new Row(v);
        List<Row> oil = view.rows("oilData");
        if (oil != shownOil) {
            shownOil = oil;
            g("OilDataView").DataSource(oil);
        }
        List<Row> total = view.rows("totalOilData");
        if (total != shownTotal) {
            shownTotal = total;
            g("ToatlEmp").DataSource(total);
        }
        List<Row> noz = view.rows("nozStatue");
        if (noz != shownNoz) {
            shownNoz = noz;
            g("NozStatueView").DataSource(noz);
        }
        if (!view.b("loading")) {
            c("LoadPro").Visible(false);
        }
        c("StationLabel").Text(view.str("progressText"));
        String shift = view.str("currentShift");
        if (!shift.isEmpty()) {
            c("LoginShiftMessage").Text(L("CurrentShift") + ":" + shift);
        }
        String err = view.str("errorMessage");
        if (!err.isEmpty()) {
            c("ErrorMessage").Text(err);
            c("ErrorMessage").Visible(true);
        } else {
            c("ErrorMessage").Visible(false);
        }
    }

    /** NozSet : un panneau Nozzle par pistolet, dans l'onglet des pistolets. */
    private void NozSet() {
        try {
            if (!nozzles.isEmpty()) {
                for (Nozzle n : nozzles) {
                    Embed(n, "GunPage");
                }
                return;
            }
            for (Row gun : db().list("GunConfig", "ORDER BY {GUNCODE}", null)) {
                Nozzle n = Embed(new Nozzle(), "GunPage");
                n.NozzleSeting(gun.i("GUNCODE"));
                nozzles.add(n);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Afficheurs de prix / de paiement (Device). */
    private void OtherSerialDeal() {
        try {
            for (Row item : db().list("Device")) {
                PriceSerial ps = new PriceSerial(item.i("ID"), item.str("Name"), item.i("Port"), item.i("DeviceTypeID"));
                ps.DeviceStart();
                priceSerials.add(ps);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void TankSet() {
        tankShow = tankShow == null ? Embed(new Tank(), "TankShow") : Embed(tankShow, "TankShow");
    }

    private void MakeTankSerial() {
        try {
            Row m = db().first("MonitorConfig");
            if (m == null) {
                throw new IllegalStateException(L("PortNotExsit"));
            }
            tankserial = new TankSerial(m.i("PORTCODE"));
            tankserial.TransferTankEvent = Station::Tankserial_TransferTankEvent;
            tankserial.TransferDowloadEvent = Station::Tankserial_TransferDowloadEvent;
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static void Tankserial_TransferDowloadEvent(int i, int total) {
        Station s = current;
        if (s != null && s.TVolume != null) {
            s.TVolume.DowloadMessage(i, total);
        }
    }

    private static double r2(BigDecimal d) {
        return Conv.round(d.doubleValue(), 2);
    }

    /** Mesure reçue de la sonde : jauge, état de la cuve et historique (toutes les 5 minutes). */
    private static void Tankserial_TransferTankEvent(int tankId, BigDecimal height, BigDecimal vol, BigDecimal temp, BigDecimal wHeight,
                                                     BigDecimal wVolume) {
        Tank t = tankShow;
        if (t != null) {
            synchronized (t) {
                t.TankUpdateView(tankId, tankId, height, vol, wHeight, temp);
            }
        }
        var db = com.ltstation.center.sw.core.SW.db();
        Row status = db.first("TankStatus", "{ID} = :id", com.ltstation.center.legacy.Sugar.p("id", tankId));
        LocalDateTime now = LocalDateTime.now();
        if (status != null) {
            status.put("OilHeight", r2(height));
            status.put("OilVol", vol.doubleValue());
            status.put("WaterHeight", r2(wHeight));
            status.put("WaterVol", r2(wVolume));
            status.put("OilTemp", r2(temp));
            status.put("Time", now);
            status.put("StationID", IniInfo.StationID);
            status.put("OilCode", BaseFunction.GetTankOilCode(tankId));
            Row last = db.first("TankMeasurements", "{TankCode} = :t ORDER BY {Time} DESC", com.ltstation.center.legacy.Sugar.p("t", tankId));
            if (last == null || Conv.toDateTime(last.get("Time")).plusMinutes(5).isBefore(now)) {
                db.insert("TankMeasurements", measurement(tankId, status, wHeight, wVolume, now));
            }
            db.update("TankStatus", status);
        } else {
            status = new Row();
            status.put("OilHeight", r2(height));
            status.put("OilVol", BaseFunction.GetTankVolumeByHeight(tankId, Conv.toDouble(status.get("OilHeight"))));
            status.put("WaterHeight", r2(wHeight));
            status.put("WaterVol", r2(wVolume));
            status.put("OilTemp", r2(temp));
            status.put("ID", tankId);
            status.put("OilCode", BaseFunction.GetTankOilCode(tankId));
            status.put("Time", now);
            status.put("StationID", IniInfo.StationID);
            db.insert("TankStatus", status);
            db.insert("TankMeasurements", measurement(tankId, status, wHeight, wVolume, now));
        }
    }

    private static Row measurement(int tankId, Row status, BigDecimal wHeight, BigDecimal wVolume, LocalDateTime now) {
        return Row.of("TankCode", tankId, "StationID", IniInfo.StationID, "Time", now, "OilHeight", status.get("OilHeight"), "OilVolume",
            status.get("OilVol"), "OilTemp", status.get("OilTemp"), "EmployeeID", IniInfo.LoginID, "Shift", IniInfo.CurrentShift, "Upload", 0,
            "GradeSpecificID", BaseFunction.GetOilIDByCode(Conv.toInt(status.get("OilCode"))), "WaterHeight", wHeight.doubleValue(),
            "WaterVolume", wVolume.doubleValue(), "OilDensity", 0.0, "SampOilDensity", 0.0, "SampOilTemp", 0.0);
    }

    /** Gestion du stock sans sonde (NonLevel) : jauges mises à jour depuis TankStatus toutes les 10 s. */
    private void TankStockUpdate() {
        while (IniInfo.ThreadStop) {
            try {
                String sql = "select {tankConfig.ID} AS ID, {TankStatus.OilVol} AS OilVol from [tankConfig] left join [TankStatus]"
                    + " on {tankConfig.ID} = {TankStatus.ID} ORDER BY {tankConfig.ID}";
                for (Row item : db().query(sql, null)) {
                    Tank t = tankShow;
                    if (t != null) {
                        synchronized (t) {
                            t.TankUpdateView(item.i("ID"), item.i("ID"), BigDecimal.ZERO,
                                BigDecimal.valueOf(Conv.toDouble(item.get("OilVol"))).setScale(3, RoundingMode.HALF_EVEN), BigDecimal.ZERO, BigDecimal.ZERO);
                        }
                    }
                }
                sleep(10000);
            } catch (RuntimeException ignored) {
                // comme l'original
            }
        }
    }

    /** Supervision QY : jeton de connexion et envoi des transactions par carte de client. */
    private void SusperWork() {
        while (IniInfo.ThreadStop) {
            try {
                String text = TCPClientQY.TcpReceive();
                if (text.contains("ScoketBroken") || text.isEmpty()) {
                    IniInfo.token = "";
                    StationLog.writeLog("Déconnexion");
                    TCPClientQY.TcpConnect();
                    StationLog.writeLog("Reconnexion");
                    for (Nozzle n : nozzles) {
                        if (n.TCPClientQY != null && n.TCPClientQY.tcpClient != null) {
                            n.TCPClientQY.TcpConnect();
                        }
                    }
                }
                if (IniInfo.token == null || IniInfo.token.isEmpty() || LocalDateTime.now().isAfter(IniInfo.TokenTime)) {
                    Map<String, Object> obj = new LinkedHashMap<>();
                    obj.put("command", "igasv_online");
                    obj.put("igasvid", IniInfo.USN);
                    obj.put("appkey", IniInfo.UNITID);
                    TCPClientQY.TcpSend(Wire.json(obj));
                    StationLog.writeLog(Wire.json(obj));
                    sleep(100);
                    text = TCPClientQY.TcpReceive();
                    StationLog.writeLog(text);
                    if (!text.isEmpty() && !text.contains("ErrorCode")) {
                        IniInfo.token = Wire.parseRow(text).str("token");
                        IniInfo.TokenTime = LocalDateTime.now().plusHours(6);
                    }
                }
                for (Row item : db().list("OilTransactions", "{Reserved2} = '0' AND {TType} <> 5 AND {TType} <> 6", null)) {
                    var tx = db().begin();
                    try {
                        if (item.str("Asn").startsWith("010001")) {
                            Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", item.get("Asn")));
                            Row cus = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID")));
                            String time = Conv.toDateTime(item.get("TrueTime")).format(Conv.STD);
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("cpcolor", Integer.toHexString(cus.i("VehicleColorID")));
                            data.put("cphm", cus.get("VehicleNo"));
                            data.put("czrq", Conv.toDateTime(item.get("TrueTime")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                            data.put("czyl", "0");
                            data.put("czwd", "0");
                            data.put("czmd", "0");
                            data.put("czltype", "升");
                            data.put("czl", Conv.num(Conv.round(Conv.toDouble(item.get("Volume")), IniInfo.Vradio)));
                            data.put("czks", time);
                            data.put("czjs", time);
                            data.put("gunno", item.str("POSP"));
                            data.put("czry", "0");
                            Map<String, Object> obj2 = new LinkedHashMap<>();
                            obj2.put("command", "igasv_upczrecord");
                            obj2.put("token", IniInfo.token);
                            obj2.put("flag", "9000");
                            obj2.put("data", data);
                            TCPClientQY.TcpSend(Wire.json(obj2));
                            StationLog.writeLog(Wire.json(obj2));
                            sleep(100);
                            text = TCPClientQY.TcpReceive();
                            StationLog.writeLog(text);
                            if ("0000".equals(Wire.parseRow(text).str("errorcode"))) {
                                item.put("Reserved2", "1");
                                db().update("OilTransactions", item);
                            }
                        } else {
                            item.put("Reserved2", "199");
                            db().update("OilTransactions", item);
                        }
                        tx.commit();
                    } catch (RuntimeException e) {
                        tx.rollback();
                    }
                }
            } catch (RuntimeException ex3) {
                StationLog.writeLog("Other:" + ex3.getMessage());
            }
            sleep(500);
        }
    }

    /** Plateforme XJ : à 3 h, mise à jour des bouteilles de gaz de chaque véhicule depuis la plateforme. */
    private void FoRanCyDetialDowload() {
        while (IniInfo.ThreadStop) {
            IniInfo.cyDowloadFlag = LocalDateTime.now().getHour() == 3;
            if (IniInfo.cyDowloadFlag) {
                var tx = db().begin();
                try {
                    for (Row car : db().list("CarUser")) {
                        List<Row> carIDs = new ArrayList<>();
                        String[] error = {""};
                        if (!SupervisionFunction.LoadCarUserHttp(car.str("PlateNumber"), carIDs, error)) {
                            throw new IllegalStateException(error[0]);
                        }
                        List<Row> cys = new ArrayList<>();
                        if (!SupervisionFunction.LoadCylinderHttp(carIDs.get(0).str("plateNumber"), carIDs.get(0).str("vehicleId"), cys, error)) {
                            throw new IllegalStateException(error[0]);
                        }
                        for (Row cy : cys) {
                            Row req = Row.of("VehicleID", cy.get("vehicleId"), "BfDate", cy.get("bfDate"), "XjDate", cy.get("xjDate"), "MjDate",
                                cy.get("mjDate"), "CylinderTypeId", cy.get("cylinderTypeId"), "CylinderType", cy.get("cylinderType"), "ProductName",
                                cy.get("productName"), "Medium", cy.get("medium"), "MediumId", cy.get("mediumId"), "WorkPressure", cy.get("workPressure"),
                                "Cubage", cy.get("cubage"), "WallThickness", Conv.toDecimal(cy.get("wallThickness")), "ManufactureId", cy.get("manufactureId"),
                                "ManufactureName", cy.get("manufactureName"), "ProductionDate", cy.get("productionDate"), "ProductNo", cy.get("productNo"),
                                "OwnedNo", cy.get("ownedNo"), "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()), "CarUserID", car.get("ID"),
                                "UpdateType", "Add", "CylinderId", cy.get("cylinderId"));
                            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CylinderUpdate",
                                Wire.json(com.ltstation.center.sw.core.SW.dto().shape("CylinderRequest", req)));
                            if (r.Result()) {
                                Row ret = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                                Row local = db().first("CylinderData", "{ID} = :id", p("id", ret.get("ID")));
                                String[] cols = {"CarUserID", "VehicleID", "CylinderTypeId", "CylinderType", "ProductName", "MediumId", "Medium",
                                    "WorkPressure", "Cubage", "WallThickness", "ManufactureId", "ManufactureName", "ProductionDate", "ProductNo", "OwnedNo",
                                    "MjDate", "XjDate", "BfDate", "CreateTime", "UpdateTime", "CylinderId"};
                                if (local != null) {
                                    for (String col : cols) {
                                        local.put(col, ret.get(col));
                                    }
                                    db().update("CylinderData", local);
                                } else {
                                    Row ins = Row.of("ID", ret.get("ID"));
                                    for (String col : cols) {
                                        ins.put(col, ret.get(col));
                                    }
                                    db().insert("CylinderData", ins);
                                }
                            }
                        }
                    }
                    tx.commit();
                } catch (RuntimeException ex) {
                    tx.rollback();
                    StationLog.writeLog("FoRanCyDetialDowload Error:" + ex.getMessage());
                }
                IniInfo.cyDowloadFlag = false;
                sleep(3600000);
            }
            sleep(600000);
        }
    }

    /** Photo d'une caméra : plaque reconnue → autorisation de remplissage sur les pistolets de la caméra. */
    private static boolean DealCarNo(String verfind) {
        var db = com.ltstation.center.sw.core.SW.db();
        try {
            String[] array = verfind.split("_", -1);
            StationLog.writeLog("path:" + verfind);
            if (array.length > 3) {
                StationLog.writeLog("Plnumbers:" + array[0]);
                String ip = array[0].substring(5);
                StationLog.writeLog("IP:" + ip);
                String carNo = array[2];
                if (IniInfo.SupervisionType == 2) {
                    carNo = "粤" + carNo.substring(1);
                }
                List<Row> list = db.query("SELECT {CameraSupervise.IP} AS IP, {CameraSupervise.NozID} AS NozID, {GradeSpecifics.GradeSpecificID} AS GradeSpecificID"
                    + " FROM [CameraSupervise] JOIN [GunConfig] ON {CameraSupervise.NozID} = {GunConfig.GUNCODE}"
                    + " JOIN [tankConfig] ON {GunConfig.TANKCODE} = {tankConfig.ID}"
                    + " JOIN [GradeSpecifics] ON CAST({tankConfig.OilCode} AS varchar) = {GradeSpecifics.Code}", null);
                if (list.isEmpty()) {
                    throw new IllegalStateException("Aucune caméra");
                }
                StationLog.writeLog("Camera:Deal");
                Row start = new Row();
                if (IniInfo.SupervisionType == 2) {
                    SupervisionFunction.FoRanCheckUp(carNo, start, new String[1]);
                } else {
                    Row car = db.first("CarUser", "{PlateNumber} = :p", com.ltstation.center.legacy.Sugar.p("p", carNo));
                    if (car == null) {
                        throw new IllegalStateException("Immatriculation introuvable");
                    }
                    start = Row.of("Amount", car.get("Balance"), "Asn", car.get("Asn"), "Balance", car.get("Balance"), "PlNumber", carNo, "Code", 0,
                        "GradeID", car.get("GradeID"));
                }
                final Row st = start;
                final String camIp = ip;
                for (Row item : list.stream().filter(t -> t.i("GradeSpecificID") == st.i("GradeID") && camIp.equals(t.str("IP"))).toList()) {
                    db.insert("CmdList", Row.of("CmdPlatType", 3, "CmdType", "63", "CreateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()),
                        "Deal", 0, "Dowload", true, "StationID", IniInfo.StationID, "NozID", item.get("NozID"), "CmdData", Wire.json(st)));
                }
                StationLog.writeLog("Cmd Deal");
            }
            return true;
        } catch (RuntimeException ex) {
            StationLog.writeLog("Insert Error:" + ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ menus

    public void BaseMessage_Click() {
        switch (sender) {
            case "BaseMessage" -> ShowDialog("BaseMessage", r -> { }, "ID", 2);
            case "ShiftReport" -> ShowDialog("ShiftForm", r -> { });
            case "VirtueScore" -> ShowDialog("VirtualScore", r -> { });
            case "CardMessage" -> ShowDialog("UserCardMessage", r -> { });
            case "PortItem" -> ShowDialog("StationPort", r -> { }, "ID", 1);
            case "TankItem" -> ShowDialog("StationTankList", r -> { }, "ID", 1);
            case "ExitItem" -> {
                IniInfo.ThreadStop = false;
                Close();
            }
            case "TankVolumeItem" -> ShowDialog("TankVolume", r -> TVolume = null);
            case "RegisiterValid" -> ShowDialog("NozValid", r -> { });
            case "TankPortItem" -> ShowDialog("TankPortMonitor", r -> { });
            case "EmpLoginItem" -> ShowDialog("Login", r -> TypeSwitch(IniInfo.LoginType));
            case "TransactionR" -> ShowDialog("OilTransacation", r -> { });
            case "StockManager" -> ShowDialog("StockManager", r -> { });
            case "DailyR" -> ShowDialog("RDaily", r -> { }, "ID", 0);
            case "MonthR" -> ShowDialog("RDaily", r -> { }, "ID", 1);
            case "LogSet" -> IniInfo.logwrite = IniInfo.logwrite == 1 ? 0 : 1;
            case "SuperviseItem" -> ShowDialog("SuperviseSelect", r -> { });
            case "NozItem" -> ShowDialog("StationNozzleSet", r -> { }, "ID", 1);
            case "ShiftItem" -> ShowDialog("ShiftItemForm", r -> { });
            default -> {
            }
        }
    }

    /** Barre d'outils : un seul gestionnaire pour tous les boutons (sender = bouton). */
    public void CardMake_Click() {
        c(Curbutton == null ? "OilPrice" : Curbutton).Checked(false);
        c(sender).Checked(true);
        Curbutton = sender;
        switch (Curbutton) {
            case "CardLost" -> ShowDialog("CardLost", r -> { });
            case "CardMake" -> ShowDialog("CardMake", r -> { }, "IsCreate", true);
            case "OilPrice" -> ShowDialog("OilTypeAndPrice", r -> { }, "ID", 1);
            case "DisCount" -> ShowDialog("CenterDisCount", r -> { });
            case "PSAMMake" -> ShowDialog("CreatePSAM", r -> { });
            case "CardUpDate" -> ShowDialog("CardUpDate", r -> { });
            case "CardChange" -> ShowDialog("CardChange", r -> { });
            case "EmpManager" -> ShowDialog("EmpForm", r -> { });
            case "CardSearch" -> ShowDialog("CardLog", r -> { });
            case "DailyReport" -> {
                switch (IniInfo.ReportType) {
                    case 0 -> ShowDialog("RDaily", r -> { }, "ID", 0);
                    case 1 -> ShowDialog("DailyClose", r -> { });
                    default -> {
                    }
                }
            }
            case "MonthReport" -> {
                switch (IniInfo.ReportType) {
                    case 0 -> ShowDialog("RDaily", r -> { }, "ID", 1);
                    case 1 -> ShowDialog("MonthClose", r -> { });
                    default -> {
                    }
                }
            }
            case "PasswodLost" -> ShowDialog("PasswordLost", r -> { });
            case "ShiftCancel" -> ShowDialog("CancleDuty", r -> { });
            case "Deposit" -> ShowDialog("Deposit", r -> { });
            case "TransactionSearch" -> ShowDialog("OilTransacation", r -> { });
            case "Shift" -> ShowDialog("ShiftForm", r -> { });
            case "ScoreDeal" -> ShowDialog("ScoreDeal", r -> { });
            case "KeyManagement" -> ShowDialog("KeyMainBoard", r -> { }, "ID", 0);
            case "BusinessReport" -> ShowDialog("BusinessReportMess", r -> { });
            case "AuthorityManagement" -> ShowDialog("Authority", r -> { });
            case "StationPrint" -> ShowDialog("StationPrintForm", r -> { });
            default -> {
            }
        }
    }

    public void GetOilTransaction_Click() {
        if ("GetOilTransaction".equals(sender)) {
            ShowDialog("GetOiltransactionByHands", r -> { });
        } else if ("Relieve".equals(sender)) {
            ShowDialog("RelieveCard", r -> { });
        }
    }

    /** Relevé manuel des transactions d'un pistolet (de PSStart à PSEnd). */
    public boolean SetGetOil(int gunId, long psStart, long psEnd) {
        try {
            for (Nozzle n : nozzles) {
                if (n.NozID == gunId) {
                    n.PSStart = psStart;
                    n.PSEnd = psEnd;
                    break;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void DisplayPay_Click() {
        switch (sender) {
            case "PriceDisplay" -> ShowDialog("PriceDeviceSet", r -> { });
            case "CarNoManager" -> ShowDialog("CarNoUserManager", r -> { });
            case "RFIDItem" -> ShowDialog("RFIDForm", r -> { });
            case "DisplayPay" -> ShowDialog("DisplayPayShow", r -> { });
            case "CarNoVerify" -> ShowDialog("CarNoVerifyFrom", r -> { });
            case "SurveillanceCamera" -> ShowDialog("SurveillanceCameraForm", r -> { });
            default -> {
            }
        }
    }

    public void UserList_Click() {
        if ("UserList".equals(sender)) {
            ShowDialog("CustomerUserList", r -> { });
        }
    }

    public void ChineseLanguage_Click() {
        IniInfo.Language = "ChineseLanguage".equals(sender) ? "zh-CN" : "en-US";
        MultiLanguage.languageChange(IniInfo.Language);
    }

    /** Double-clic sur une transaction : validation manuelle du paiement (ScoreCheck). */
    public void OilDataView_CellDoubleClick() {
        int index = g("OilDataView").CurrentRowIndex();
        if (index < 0) {
            return;
        }
        int d = Conv.toInt(g("OilDataView").Cell(index, "ID"));
        if (Conv.toBool(g("OilDataView").Cell(index, "Deal"))) {
            MessageBox("Traité");
            return;
        }
        ShowDialog("ScoreCheck", r -> {
            if (r.ok()) {
                engine().updateLeftView();
                RefreshView();
            }
        }, "D", d);
    }

    /** Téléchargement du barème d'une cuve dans la sonde (fenêtre TankVolume). */
    public void DoloadVolumeTable(int tankId) {
        try {
            tankserial.DowLoadTable(tankId);
        } catch (RuntimeException ignored) {
            // sonde absente
        }
    }

    /** Blocage des pistolets jusqu'à une date (NozValid) ; "" = déblocage. */
    public static void LockMachine(String str) {
        try {
            if (str.isEmpty()) {
                str = "0000000000";
            }
            for (Nozzle n : nozzles) {
                n.LockTime = str;
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Station_FormClosing / Quitter : arrêt des fils et du matériel. */
    public void Station_FormClosing() {
        IniInfo.ThreadStop = false;
        StationEngine en = engine();
        if (en != null) {
            en.stop();
        }
        for (Nozzle n : nozzles) {
            n.Stop();
        }
        nozzles.clear();
        priceSerials.forEach(PriceSerial::Stop);
        priceSerials.clear();
        if (tankserial != null) {
            tankserial.close();
            tankserial = null;
        }
        if (ftp != null) {
            ftp.stop();
            ftp = null;
        }
        tankShow = null;
        hardwareStarted = false;
        current = null;
    }
}
