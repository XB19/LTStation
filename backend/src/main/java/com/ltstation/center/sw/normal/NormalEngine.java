package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.BaseFunction.Holder;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.hw.TankSerial;
import com.ltstation.center.sw.normal.WirelessCmd.CmdList;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPreSet;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPrint;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPrintTransaction;
import com.ltstation.center.sw.normal.WirelessCmd.CmdStatue;
import com.ltstation.center.sw.normalforms.NozShow;
import com.ltstation.center.sw.normalforms.PLCShow;
import com.ltstation.center.sw.normalforms.RFIDShow;
import com.ltstation.center.sw.normalforms.TankShow;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Partie sans interface de NormalMainForm (mode Normal) : fils d'exécution lancés au chargement,
 * ports série des boîtiers, serveur des boîtiers sans fil, commandes de Center, clôtures et
 * messages Windows (WndProc) reçus par la fenêtre « NormalSystem ».
 *
 * <p>Les vues intégrées (pistolets, cuves, RFID, PLC) sont statiques comme dans l'original : elles
 * restent tenues par les fils même quand aucune page n'est ouverte.
 */
public final class NormalEngine {

    private NormalEngine() {
    }

    public static final List<NozSerial> Ports = new CopyOnWriteArrayList<>();
    public static final List<TouchTcpClient> TcpClientList = new CopyOnWriteArrayList<>();
    public static volatile TankSerial tankSerial;
    public static volatile SerialClass CashDraw = new SerialClass();

    public static final NozShow nozshow = new NozShow();
    public static final TankShow tankShow = new TankShow();
    public static final RFIDShow RFIDForm = new RFIDShow();
    public static final PLCShow PLCForm = new PLCShow();

    /** Fenêtre BoxCommunicatSet ouverte (progression du téléchargement, versions des boîtiers). */
    public interface BoxSetView {
        void Box_UpdateProgress(int maxNumber, int pageIndex, int type);

        void Box_ShowVersion(String version);

        void Box_ShowTCPVersion(int id);
    }

    /** Fenêtre de mise à jour des boîtiers ouverte (NozVerUpdate). */
    public interface NozVerView {
        void CheckVersion(int portID, int boxID);

        void CheckTcpVersion(int id, int portID);
    }

    /** Écran de caisse ouvert (BoxScreenV2). */
    public interface BoxScreenView {
        void UpdateNozView(int nozID, int viewStatue, String gradeName, String amount, int pumpid);
    }

    public static volatile BoxSetView BoxSet;
    public static volatile NozVerView NozVerUpdate;
    public static volatile BoxScreenView boxScreen;

    // ------------------------------------------------------------ état affiché par la fenêtre principale
    public static volatile List<Row> OilData = List.of();
    public static volatile List<Row> GradeTotal = List.of();
    public static volatile int UnUploadCount;
    public static volatile String CurrentShiftText = "";
    public static volatile String ErrorText;
    public static volatile LocalDateTime ReportTime = LocalDateTime.now();
    /** Incrémenté à chaque nouvel affichage des transactions (ShowTransaction). */
    public static volatile int DataVersion;

    private static final List<Thread> threads = new CopyOnWriteArrayList<>();
    private static volatile boolean started;

    private static Sugar db() {
        return SW.db();
    }

    private static final BigDecimal M1 = BigDecimal.valueOf(-1);

    private static BigDecimal rnd(BigDecimal d, int n) {
        return Conv.netRound(d, n);
    }

    private static long now() {
        return BaseFunction.ConvertDateTimeToInt();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void spawn(String name, Runnable r) {
        Thread t = new Thread(r, "normal-" + name);
        t.setDaemon(true);
        threads.add(t);
        t.start();
    }

    public static boolean IsStarted() {
        return started;
    }

    // ------------------------------------------------------------ chargement (NormalMainForm_Load)

    /** Stations → IniInfo (société = SendMailServer, code boîtier = SendMailAddr…). */
    public static void LoadStation() {
        Row stations = db().first("Stations");
        IniInfo.CompanyID = Conv.toInt(stations.get("SendMailServer"));
        IniInfo.StationID = stations.i("StationID");
        IniInfo.Code = stations.str("Code");
        IniInfo.StationName = stations.str("Name");
        IniInfo.StationAddress = stations.str("Address");
        IniInfo.BoxCode = stations.str("SendMailAddr");
        IniInfo.IdentifyMode = stations.i("IdentifyMode");
        IniInfo.vatRun = stations.b("AutoMeasure");
        IniInfo.DayCompletedTime = stations.i("DayCompletedTime");
    }

    /** Fils de NormalMainForm_Load (lancés une seule fois, même si la fenêtre est rouverte). */
    public static synchronized void Start() {
        if (started) {
            return;
        }
        started = true;
        IniInfo.ThreadStop = true;
        NormalFunction.MainFormMessage = NormalEngine::WndProc;
        NormalSync.ErrorMessage = s -> ErrorText = s;
        nozshow.UpdateValueEvent = NormalEngine::Noz_UpdateValueEvent;
        spawn("PortStart", NormalEngine::PortStart);
        spawn("CashDrawInit", NormalEngine::CashDrawInit);
        ShowTransaction();
        spawn("UplodaAndDowload", NormalEngine::UplodaAndDowload);
        spawn("DowloadData", NormalEngine::DowloadData);
        spawn("CloseReport", NormalEngine::CloseReport);
        spawn("CenterCommandLoad", NormalEngine::CenterCommandLoad);
        spawn("DealTankAlarm", NormalEngine::DealTankAlarm);
        spawn("DealScreenPumpData", NormalEngine::DealScreenPumpData);
        if (IniInfo.SupervisionType == 3 || IniInfo.SupervisionType == 4) {
            // EFD (Tanzanie) et eTIMS (Kenya) passent par BoxBase, pas encore porté
            NormalSync.MainLog.writeLog("Supervision " + IniInfo.SupervisionType + " : BoxBase non disponible");
        }
        spawn("DealUnTransaction", NormalEngine::DealUnTransaction);
        BoxShiftInit();
        // l'original se connecte pendant le chargement ; ici en arrière-plan (délai de connexion de 3 s par appareil)
        spawn("TouchTcpClient", () -> {
            for (Row item : db().list("StationDevice")) {
                if (item.i("DeviceType") == 0) {
                    TouchTcpClient c = new TouchTcpClient(item.i("ID"));
                    TcpClientList.add(c);
                    c.Connect();
                }
            }
        });
    }

    /** NormalMainForm_FormClosed / ExitItem : arrêt des fils, des ports et du serveur des boîtiers. */
    public static synchronized void Stop() {
        IniInfo.ThreadStop = false;
        started = false;
        for (NozSerial p : Ports) {
            p.PortRun = false;
            p.Stop();
        }
        Ports.clear();
        BoxTcpServer.Stop();
        CashDraw.closePort();
        threads.forEach(Thread::interrupt);
        threads.clear();
    }

    /** Poste des boîtiers (BoxShiftList) : reprise du poste ouvert ou création du suivant (aaaaMMjjNN). */
    private static void BoxShiftInit() {
        try {
            Row last = db().first("BoxShiftList", "ORDER BY {BoxShiftID} DESC", null);
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            int num;
            if (last != null && last.i("Complete") == 0) {
                IniInfo.BoxShift = last.i("BoxShiftID");
                return;
            }
            if (last == null) {
                num = Integer.parseInt(today + "01");
            } else {
                String id = last.str("BoxShiftID");
                String day = id.substring(0, id.length() - 2);
                num = day.equals(today)
                    ? Integer.parseInt(day + BaseFunction.SetStrLen(String.valueOf(Integer.parseInt(id.substring(id.length() - 2)) + 1), 2))
                    : Integer.parseInt(today + "01");
            }
            db().insert("BoxShiftList", Row.of("StartTime", now(), "BoxShiftID", num, "Complete", 0, "CreateTime", now(),
                "BoxDay", LocalDate.now().toString(), "DayComplete", 0));
            IniInfo.BoxShift = num;
        } catch (RuntimeException ex) {
            ErrorText = ex.getMessage();
        }
    }

    // ------------------------------------------------------------ ports

    /** PortStart : serveur des boîtiers sans fil (port 7011) ou ports série des boîtiers. */
    private static void PortStart() {
        if (IniInfo.WifiMode == 1) {
            BoxTcpServer.OnClosed = client -> {
                IBoxClientParam box = BaseInfo.BoxClientList.stream()
                    .filter(t -> t.NoHead != null && t.NoHead.equalsIgnoreCase(client.Id())).findFirst().orElse(null);
                if (box != null) {
                    for (AllNozStatue noz : box.NozList) {
                        noz.Statue = 4;
                        nozshow.Noz_UpdateView(noz.NozID, -1, "-1", "-1", M1, M1, M1, M1, M1, -1, noz.Statue, "", "-1");
                    }
                    if (box.pLCParam != null) {
                        box.pLCParam.Statue = -1;
                    }
                    if (box.priceBorad != null) {
                        box.priceBorad.Statue = -1;
                    }
                }
            };
            BoxReceive.Install();
            try {
                BoxTcpServer.Start(7011);
            } catch (IOException ex) {
                NormalSync.MainLog.writeLog("TCP Start Error:" + ex.getMessage());
            }
            BaseInfo.LoadWireLessDevice();
            return;
        }
        // l'original attend que la page des pistolets soit chargée ; ici les ports démarrent sans page ouverte
        PortInit();
    }

    /** PortInit : un NozSerial par boîtier série (BoxList.BoxPort renseigné) et la sonde des cuves. */
    public static void PortInit() {
        try {
            List<Row> list = db().list("BoxList", "{BoxPort} <> ''", null);
            for (NozSerial port : Ports) {
                port.PortRun = false;
                port.Stop();
            }
            Ports.clear();
            for (Row item : list) {
                NozSerial nozSerial = new NozSerial(item.i("ID"));
                nozSerial.TransferEvent = NormalEngine::Serial_TransferEvent;
                Ports.add(nozSerial);
            }
            Row set = db().first("NormalTankSet");
            if (set != null) {
                tankSerial = new TankSerial(Integer.parseInt(set.str("PortID").substring(3)));
                tankSerial.TransferTankEvent = (tankId, h, v, t, wh, wv) -> tankShow.Tank_ViewUpdate(tankId, tankId, h, v, wh, t);
            }
        } catch (RuntimeException ex) {
            ErrorText = ex.getMessage();
        }
    }

    private static void CashDrawInit() {
        try {
            Row ext = db().first("ExtendPort", "{MachineType} = 0", null);
            if (ext != null) {
                CashDraw.closePort();
                CashDraw = new SerialClass(ext.str("Port"), 9600, SerialClass.Parity.None, 8, SerialClass.StopBits.One);
                CashDraw.openPort(new String[1]);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public static boolean PortRunStatueChange(boolean flag) {
        try {
            if (!flag) {
                for (NozSerial port : Ports) {
                    port.PortRun = false;
                }
            } else {
                for (NozSerial port : Ports) {
                    if (!port.PortRun && !port.PortUpdateStatue(true, port.ID, new String[1])) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void UpdatePortMess(int ID) {
        try {
            Row set = db().first("NormalPortSet", "{ID} = :i", p("i", ID));
            if (set != null) {
                NozSerial nozSerial = new NozSerial(set.i("ID"));
                nozSerial.TransferEvent = NormalEngine::Serial_TransferEvent;
                Ports.add(nozSerial);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static int getPortIndex(int PortID) {
        for (int i = 0; i < Ports.size(); i++) {
            if (Ports.get(i).SerialPortID == PortID) {
                return i;
            }
        }
        return -1;
    }

    private static NozSerial portById(int id) {
        return Ports.stream().filter(t -> t.ID == id).findFirst().orElse(null);
    }

    private static IBoxClientParam boxById(int id) {
        return BaseInfo.BoxClientList.stream().filter(t -> t.ID == id).findFirst().orElse(null);
    }

    private static NormalNozStatue serialNoz(NozSerial port, int nozId) {
        return port.NozAddress.stream().filter(t -> Conv.toInt(t.NozID) == nozId).findFirst().orElse(null);
    }

    /** GetSerialNoz : pistolet des ports série (numéro sur 2 chiffres) et son port. */
    public static NormalNozStatue GetSerialNoz(String NozID, NozSerial[] serial) {
        String key = BaseFunction.SetStrLen(NozID, 2);
        for (NozSerial port : Ports) {
            for (NormalNozStatue item : port.NozAddress) {
                if (key.equals(item.NozID)) {
                    if (serial != null) {
                        serial[0] = port;
                    }
                    return item;
                }
            }
        }
        return null;
    }

    private static LTNozStatuet lt(int statue, int nozId) {
        LTNozStatuet l = new LTNozStatuet();
        l.Statue = statue;
        l.NozID = nozId;
        return l;
    }

    private static CmdList cmd(String type, String deviceNo, int nozId, String data) {
        CmdList c = new CmdList();
        c.CmdType = type;
        c.DeviceNo = deviceNo;
        c.NozID = nozId;
        c.CmdData = data;
        return c;
    }

    private static CmdList platCmd(String type, IBoxClientParam box, int nozId, Object data) {
        CmdList c = cmd(type, box.NoHead, nozId, BaseInfo.json(data));
        c.StationID = IniInfo.StationID;
        c.Deal = 0;
        c.CmdPlatType = 2;
        c.Dowload = false;
        c.CreateTime = now();
        return c;
    }

    private static CmdList priceCmd(IBoxClientParam box, int nozId, BigDecimal price) {
        CmdStatue st = new CmdStatue();
        st.PrePrice = price;
        st.PriceDigits = IniInfo.Pradio;
        st.NozID = nozId;
        CmdList c = cmd("A2", box.cmdHead, 0, BaseInfo.json(st));
        c.Deal = 0;
        return c;
    }

    /** IsOnDuty des pistolets (boîtiers sans fil ou ports série) et de la station. */
    private static void SetOnDuty(int isOnDuty) {
        if (IniInfo.WifiMode == 1) {
            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                for (AllNozStatue noz : box.NozList) {
                    noz.IsOnDuty = isOnDuty;
                }
            }
        } else {
            for (NozSerial port : Ports) {
                for (NormalNozStatue n : port.NozAddress) {
                    n.IsOnDuty = isOnDuty;
                }
            }
        }
        IniInfo.IsOnDuty = isOnDuty;
    }

    private static int OnDutyAfterClose() {
        return IniInfo.NormalNeedDuty == 1 ? 0 : 1;
    }

    // ------------------------------------------------------------ échanges avec Center

    private static void UplodaAndDowload() {
        while (IniInfo.ThreadStop) {
            try {
                ErrorText = null;
                NormalSync.DowLog.writeLog("Upload Transaction");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadTranasaction());
                NormalSync.DowLog.writeLog("Upload Tank Import");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadTankImport());
                NormalSync.DowLog.writeLog("Upload Report");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadReport());
                NormalSync.DowLog.writeLog("Upload Alarm");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadAlarm());
                NormalSync.DowLog.writeLog("Dowload Emp");
                NormalSync.DowLog.writeLog("Dowload:" + NormalSync.NormalDowLoadEmp());
                NormalSync.DowLog.writeLog("Dowload Customer");
                NormalSync.DowLog.writeLog("Dowload:" + NormalSync.NormalDowCustomer());
                NormalSync.DowLog.writeLog("Dowload CustomerCardInfo");
                NormalSync.DowLog.writeLog("Dowload:" + NormalSync.NormalDowCustomerCardInfo());
                NormalSync.DowLog.writeLog("Dowload CustomerAccount");
                NormalSync.DowLog.writeLog("Dowload:" + NormalSync.NormalDowCustomerAccount());
                NormalSync.DowLog.writeLog("Dowload Group");
                NormalSync.DowLog.writeLog("Dowload:" + NormalSync.NormalDowGroup());
                NormalSync.Ret = 1;
                NormalFunction.NetErrorCount = 0;
            } catch (RuntimeException e) {
                NormalFunction.NetErrorCount++;
            }
            sleep(60000);
        }
    }

    private static void DowloadData() {
        while (IniInfo.ThreadStop) {
            try {
                NormalSync.DowloadPersonalCredit();
                NormalSync.UploadPersonalCreditLog();
                NormalSync.DowLog.writeLog("Dowload Version");
                Row version = NormalSync.NormalDowLoadVersion();
                Row stationVersion = db().first("StationVersion");
                boolean exists = stationVersion != null;
                if (!exists) {
                    stationVersion = Row.of("StationCode", IniInfo.Code, "StationID", IniInfo.StationID, "EmpVersion", -1, "CarLicense", 0, "SVersion", 0);
                }
                boolean changed = false;
                if (version.i("EmpVersion") != 0 && version.i("EmpVersion") != stationVersion.i("EmpVersion")) {
                    NormalSync.DowLog.writeLog("Dowload EmpCardInfo");
                    boolean flag = NormalSync.NormalDowLoadEmpCardInfo();
                    NormalSync.DowLog.writeLog("Dowload:" + flag);
                    if (flag) {
                        changed = true;
                        stationVersion.put("EmpVersion", version.i("EmpVersion"));
                        if (IniInfo.WifiMode == 1) {
                            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                                box.cmdNormal.add(cmd("16", null, 0, null));
                            }
                        } else {
                            for (NozSerial port : Ports) {
                                LTNozStatuet l = lt(22, 0);
                                l.PreValue = BigDecimal.valueOf(version.i("EmpVersion"));
                                port.LTStatue.add(l);
                            }
                        }
                    }
                }
                if (version.i("CarLicense") != 0 && version.i("CarLicense") != stationVersion.i("CarLicense")) {
                    NormalSync.DowLog.writeLog("Dowload CarLicense");
                    if (NormalSync.NormalDowLoadCarLicense()) {
                        stationVersion.put("CarLicense", version.i("CarLicense"));
                    }
                    changed = true;
                }
                if (version.i("SVersion") != 0 && version.i("SVersion") != stationVersion.i("SVersion")) {
                    if (NormalSync.DowloadStationMessage()) {
                        stationVersion.put("SVersion", version.i("SVersion"));
                    }
                    changed = true;
                }
                if (changed) {
                    if (db().first("StationVersion") == null) {
                        db().insert("StationVersion", stationVersion);
                    } else {
                        db().update("StationVersion", stationVersion);
                    }
                }
                NormalSync.DowLog.writeLog("Upload HandShift");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadHandShift());
                NormalSync.DowLog.writeLog("Upload Noz");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadNoz());
                NormalSync.DowLog.writeLog("Upload Energy");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadEnergy());
                NormalSync.DowLog.writeLog("Upload Stock");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadStock());
                NormalSync.DowLog.writeLog("Upload Summary");
                NormalSync.DowLog.writeLog("Upload:" + NormalSync.NormalUploadSummary());
                CloseReportHour();
                NormalSync.UploadDutyStatue();
            } catch (RuntimeException ignored) {
                // comme l'original
            }
            sleep(5000);
        }
    }

    // ------------------------------------------------------------ commandes de Center

    /** CenterCommandLoad : commandes de Center toutes les 0,5 s, puis envoi de l'état des pistolets et des alarmes. */
    private static void CenterCommandLoad() {
        while (IniInfo.ThreadStop) {
            try {
                CustomerRespone r = BaseFunction.post("ServerDeal/NormalCommandDowload", SW.dto().make("CustomerParam", "Code", IniInfo.Code));
                if (r.Result()) {
                    for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                        String cmdType = item.str("CmdType");
                        if (cmdType == null) {
                            continue;
                        }
                        switch (cmdType) {
                            case "A2-List" -> PriceList(Wire.parseRow(item.str("CmdData")));
                            case "21" -> HandShiftClose(item.i("NozID"));
                            case "A1" -> Authorize(item);
                            case "A2" -> PriceChange(Wire.parseRow(item.str("CmdData")));
                            case "18" -> PriceBoard();
                            case "20" -> HandShiftOpen();
                            case "A4", "A4-Stop" -> StopNoz(item.i("NozID"));
                            default -> {
                            }
                        }
                    }
                }
                NormalSync.NormalUploadNozStatue();
                NormalSync.UploadAlarm();
            } catch (RuntimeException ignored) {
                // comme l'original : le lot de commandes est abandonné
            }
            sleep(500);
        }
    }

    /** A2-List : nouveau prix d'un produit envoyé à tous ses pistolets. */
    private static void PriceList(Row price) {
        List<Row> list = db().list("NormalNoz", "{GradeID} = :g", p("g", price.i("GradeSpecificID")));
        BigDecimal ppu = price.dec("NewPPU");
        if (IniInfo.WifiMode == 1) {
            for (Row n : list) {
                IBoxClientParam box = boxById(n.i("PortID"));
                if (box != null) {
                    box.cmdNormal.add(priceCmd(box, n.i("NozID"), ppu));
                }
            }
            return;
        }
        for (Row n : list) {
            for (NozSerial port : Ports) {
                if (serialNoz(port, n.i("NozID")) != null) {
                    LTNozStatuet l = lt(162, n.i("NozID"));
                    l.PrePrice = ppu;
                    port.LTStatue.add(l);
                    break;
                }
            }
        }
    }

    private static boolean emptyShift(Row t) {
        String h = t.str("HandShiftNo");
        return h == null || h.isEmpty() || h.equals("0");
    }

    private static final String NO_HAND_SHIFT = "COALESCE({NormalTransaction.HandShiftNo}, '') IN ('', '0')";

    /**
     * Commande 21 : clôture du poste manuel (HandShiftReport) : une ligne par pistolet / produit / prix,
     * cartes employé, transactions marquées, puis rapports du poste (Settle*).
     */
    private static void HandShiftClose(int cmdNozId) {
        try (Sugar.Tx tx = db().begin()) {
            Row cur = db().first("HandShiftReport", "{DealStatue} = 0 ORDER BY {ID} DESC", null);
            if (cur == null) {
                throw new IllegalStateException("Shift Lost");
            }
            cur.put("EndTime", now());
            long start = cur.l("StartTime");
            long end = cur.l("EndTime");
            String range = " and {NormalTransaction.CreateTime} between " + start + " and " + end;
            List<Row> list3 = db().query(db().sql("select {NormalTransaction.NozID} AS NozID, {NormalTransaction.GradeID} AS GradeID,"
                + " {NormalTransaction.Price} AS Price, Count(*) as TotalCount, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume, MAX({NormalTransaction.CreateTime}) as MaxTime, MIN({NormalTransaction.CreateTime}) as MinTime"
                + " from [NormalTransaction] where " + NO_HAND_SHIFT + range
                + " group by {NormalTransaction.NozID}, {NormalTransaction.GradeID}, {NormalTransaction.Price} order by 1"), null);
            Row total = db().queryFirst(db().sql("select Count(*) as TotalCount, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] where " + NO_HAND_SHIFT + range), null);
            List<Row> list4 = db().query(db().sql("select {NormalTransaction.CardNo} AS CardNo, SUM({NormalTransaction.Amount}) as Amount,"
                + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] where " + NO_HAND_SHIFT + range
                + " and SUBSTRING({NormalTransaction.CardNo}, 1, 9) = '000000000' group by {NormalTransaction.CardNo}"), null);
            if (list3.isEmpty()) {
                cur.put("DealStatue", 1);
                db().update("HandShiftReport", cur);
                tx.commit();
                SetOnDuty(OnDutyAfterClose());
                return;
            }
            String shift = cur.str("HandShift");
            for (Row item : list3) {
                // comme l'original : NozID reprend celui de la commande
                cur.put("NozID", cmdNozId);
                cur.put("TotalAmount", rnd(item.dec("Amount"), IniInfo.Mradio));
                cur.put("TotalVolume", rnd(item.dec("Volume"), IniInfo.Vradio));
                Row first = db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} = :c", p("n", item.i("NozID"), "c", item.l("MinTime")));
                Row last = db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} = :c", p("n", item.i("NozID"), "c", item.l("MaxTime")));
                BigDecimal startV = first == null ? BigDecimal.ZERO : first.dec("VolumeTotal").subtract(first.dec("Volume"));
                BigDecimal endV = last == null ? BigDecimal.ZERO : last.dec("VolumeTotal");
                cur.put("StartVolume", rnd(startV, IniInfo.Vradio));
                cur.put("EndVolume", rnd(endV, IniInfo.Vradio));
                cur.put("Error", rnd(endV.subtract(startV).subtract(cur.dec("TotalVolume")), IniInfo.Vradio));
                cur.put("NozCount", item.i("TotalCount"));
                cur.put("SumAmount", total.dec("Amount"));
                cur.put("SumVolume", total.dec("Volume"));
                cur.put("SumCount", total.i("TotalCount"));
                cur.put("DealStatue", 1);
                cur.put("GradeID", item.i("GradeID"));
                cur.put("GradeName", BaseFunction.GetNormalGradeByID(item.i("GradeID")));
                cur.put("Price", item.dec("Price"));
                Row copy = cur.copy();
                copy.remove("ID");
                db().insert("HandShiftReport", copy);
            }
            for (Row item : list4) {
                db().insert("HandShiftCardReport", Row.of("ShiftNo", shift, "StationID", IniInfo.StationID, "CardNo", item.str("CardNo"),
                    "Amount", item.dec("Amount"), "Volume", item.dec("Volume"), "Upload", false));
            }
            db().exec(db().sql("NormalTransaction", "update [NormalTransaction] set {HandShiftNo} = :h where {CreateTime} >= :s and {CreateTime} <= :e"
                + " and COALESCE({HandShiftNo}, '') = ''"), p("h", shift, "s", start, "e", end));
            db().delete("HandShiftReport", "{ID} = :i", p("i", cur.i("ID")));
            NormalFunction.SettleNoz(shift, start, end);
            if (!NormalFunction.SettleStock(shift, start, end)) {
                throw new IllegalStateException(MultiLanguage.getText("StockSettleError"));
            }
            if (!NormalFunction.SettleEnery(shift, start, end)) {
                throw new IllegalStateException(MultiLanguage.getText("EnergySettleError"));
            }
            NormalFunction.SettleSummary(shift, start, end);
            tx.commit();
            SetOnDuty(OnDutyAfterClose());
        }
    }

    /** Commande 20 : ouverture d'un poste manuel (aaaaMMjjNN) s'il n'y en a pas d'ouvert. */
    private static void HandShiftOpen() {
        if (db().any("HandShiftReport", "{DealStatue} = 0", null)) {
            return;
        }
        String text = db().scalar(db().sql("HandShiftReport", "select MAX({HandShift}) from [HandShiftReport]"), null, String.class);
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        if (text == null || text.isEmpty()) {
            text = today + "01";
        } else if (today.equals(text.substring(0, 8))) {
            text = today + BaseFunction.SetStrLen(String.valueOf(Integer.parseInt(text.substring(8, 10)) + 1), 2);
        } else {
            text = today + "01";
        }
        db().insert("HandShiftReport", Row.of("StartTime", now(), "Operate", IniInfo.LoginName, "HandShift", text, "CreateTime", now(), "DealStatue", 0));
        SetOnDuty(1);
    }

    /** A1 : autorisation (préréglage) d'un pistolet par Center (application, carte…). */
    private static void Authorize(Row item) {
        Row start = Wire.parseRow(item.str("CmdData"));
        int nozId = item.i("NozID");
        Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", nozId));
        Row boxSet = db().first("Box_Set", "{ID} = :i", p("i", noz.get("Box_Port_ID")));
        BigDecimal num2 = start.dec("PreSetValue");
        if (num2.compareTo(BigDecimal.valueOf(9000)) == 0 && boxSet.i("Box_DeviceType") != 0) {
            num2 = BigDecimal.valueOf(900000);
        }
        Row cmdRow = item.copy();
        cmdRow.remove("ID");
        db().insert("CmdList", cmdRow);
        int dealCode = 0;
        if (Conv.toInt(start.get("DealCode")) != 0) {
            dealCode = Conv.toInt(db().insert("CmdOrder", Row.of("DeviceNo", start.str("DeviceNo"), "NozID", start.i("NozID"),
                "PreSetPrice", start.dec("PreSetPrice"), "PreSetType", start.i("PreSetType"), "PreSetValue", start.dec("PreSetValue"),
                "AuthType", start.get("AuthType"), "Bal", start.dec("Bal"), "CardNo", start.str("CardNo"), "CarNo", start.str("CarNo"),
                "DealCode", start.str("DealCode"), "StationID", IniInfo.StationID)));
        }
        if (IniInfo.WifiMode == 1) {
            IBoxClientParam box = boxById(noz.i("PortID"));
            if (box == null) {
                return;
            }
            CmdPreSet set = new CmdPreSet();
            set.DeviceNo = box.NoHead;
            set.NozID = noz.i("NozID");
            set.PreSetType = start.i("PreSetType");
            set.PreSetValue = num2;
            set.PreSetPrice = start.dec("PreSetPrice");
            set.AuthType = true;
            set.DealCode = String.valueOf(dealCode);
            AllNozStatue all = box.NozList.stream().filter(t -> t.NozID == set.NozID).findFirst().orElse(null);
            if (all != null) {
                if (all.RaiseNoz != 1) {
                    box.cmdNormal.add(platCmd("A5", box, set.NozID, set));
                }
                box.cmdAuth.removeIf(t -> t.NozID == set.NozID && "A1".equals(t.CmdType));
                box.cmdAuth.add(platCmd("A1", box, set.NozID, set));
                all.AuthMessage = ShowPreSetValueInNoz(boxSet, set.NozID, num2, start.i("PreSetType"));
            }
            return;
        }
        for (NozSerial port : Ports) {
            NormalNozStatue n = serialNoz(port, nozId);
            if (n != null) {
                for (int statue : n.RaiseNoz != 1 ? new int[] {165, 161} : new int[] {161}) {
                    LTNozStatuet l = lt(statue, nozId);
                    l.PreType = 0;
                    l.PreValue = num2;
                    l.PrePrice = start.dec("PreSetPrice");
                    l.DealCode = dealCode;
                    l.CardNo = start.str("CardNo");
                    l.CarNo = start.str("CarNo");
                    l.Bal = start.dec("Bal");
                    port.LTStatue.add(l);
                }
                break;
            }
        }
    }

    /** A2 : changement du prix d'un pistolet (et du prix du produit). */
    private static void PriceChange(Row price) {
        int address = price.i("Address");
        BigDecimal newPrice = price.dec("Price");
        Row nozRun = db().first("NormalNoz", "{NozID} = :n", p("n", address));
        if (IniInfo.WifiMode == 1) {
            IBoxClientParam box = boxById(nozRun.i("PortID"));
            if (box != null) {
                box.cmdNormal.add(priceCmd(box, address, newPrice));
                box.NozList.stream().filter(t -> t.NozID == address).findFirst().ifPresent(n -> n.Price = newPrice);
            }
        } else {
            for (NozSerial port : Ports) {
                if (serialNoz(port, address) != null) {
                    LTNozStatuet l = lt(162, address);
                    l.PrePrice = newPrice;
                    port.LTStatue.add(l);
                    break;
                }
            }
        }
        Row info = db().first("NormalPriceInfo", "{GradeID} = :g AND {IsUsing} = true", p("g", price.i("GradeID")));
        if (info != null) {
            info.put("OldPrice", info.get("Price"));
            info.put("Price", newPrice);
            db().update("NormalPriceInfo", info);
        }
        Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", address));
        if (noz != null) {
            noz.put("Price", newPrice);
            db().update("NormalNoz", noz);
        }
    }

    /** 18 : rafraîchissement des afficheurs de prix. */
    private static void PriceBoard() {
        if (IniInfo.WifiMode == 1) {
            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                if (box.priceBorad != null && box.priceBorad.Statue == 1) {
                    box.cmdNormal.add(cmd("18", null, 0, null));
                }
            }
            return;
        }
        for (NozSerial port : Ports) {
            if (port.priceBorad.Statue == 1) {
                port.LTStatue.add(lt(24, 0));
            }
        }
    }

    /** A4 / A4-Stop : arrêt d'un pistolet. */
    private static void StopNoz(int nozId) {
        Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", nozId));
        if (noz == null) {
            throw new IllegalStateException("Noz Lost");
        }
        if (IniInfo.WifiMode == 1) {
            IBoxClientParam box = boxById(noz.i("PortID"));
            if (box != null) {
                CmdPreSet set = new CmdPreSet();
                set.DeviceNo = box.NoHead;
                set.NozID = nozId;
                box.cmdNormal.add(platCmd("A4-Stop", box, nozId, set));
            }
            return;
        }
        for (NozSerial port : Ports) {
            if (serialNoz(port, nozId) != null) {
                port.DealQueue(nozId, 161);
                port.LTStatue.add(lt(164, nozId));
                break;
            }
        }
    }

    // ------------------------------------------------------------ alarmes, écran de caisse

    private static void CheckStationValid() {
        try {
            int valid = BaseFunction.GetStationValid(IniInfo.Code);
            if (valid == 3 || valid == 2) {
                // Application.Exit() de l'original : la station n'est plus valide sur Center
                ErrorText = "Station invalide (Center)";
                Stop();
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** DealTankAlarm : alarmes des cuves (SystemAlarm) ouvertes / closes selon les états reçus. */
    private static void DealTankAlarm() {
        while (IniInfo.ThreadStop) {
            try {
                CheckStationValid();
                RealTankChange tank = NormalFunction.TankAlarmList.poll();
                if (tank != null) {
                    for (Row item : db().list("SystemAlarm", "{Deal} = false AND {StationID} = :s AND {TankID} = :t",
                        p("s", tank.StationID, "t", tank.TankID))) {
                        boolean close = switch (item.i("AlarmTypeID")) {
                            case 7 -> tank.TL == 0;
                            case 6 -> tank.CG == 0;
                            case 4, 5 -> tank.Ttemp == 0;
                            case 2, 3 -> tank.TLevel == 0;
                            case 1 -> tank.EG == 0;
                            default -> false;
                        };
                        if (close) {
                            item.put("Deal", true);
                            item.put("Upload", false);
                            item.put("EndTime", now());
                            db().update("SystemAlarm", item);
                        }
                    }
                    if (tank.TL != 0) {
                        openAlarm(tank, 7);
                    }
                    if (tank.CG != 0) {
                        openAlarm(tank, tank.CG);
                    }
                    if (tank.Ttemp != 0) {
                        openAlarm(tank, tank.Ttemp);
                    }
                    if (tank.TLevel != 0) {
                        openAlarm(tank, tank.TLevel);
                    }
                    if (tank.EG != 0) {
                        openAlarm(tank, 1);
                    }
                }
            } catch (RuntimeException ignored) {
                // comme l'original
            }
            sleep(2000);
        }
    }

    private static void openAlarm(RealTankChange tank, int type) {
        if (!db().any("SystemAlarm", "{AlarmTypeID} = :a AND {StationID} = :s AND {TankID} = :t AND {Deal} = false",
            p("a", type, "s", tank.StationID, "t", tank.TankID))) {
            db().insert("SystemAlarm", Row.of("StationID", tank.StationID, "TankID", tank.TankID, "Name", tank.TankName, "CompanyID", tank.CompanyID,
                "AlarmTypeID", type, "Deal", false, "StartTime", now(), "UpdateTime", now()));
        }
    }

    private static void DealScreenPumpData() {
        while (IniInfo.ThreadStop) {
            try {
                BoxScreenView screen = boxScreen;
                if (!NormalFunction.PumpNozData.isEmpty() && screen != null) {
                    for (NormalNozStatue v : NormalFunction.PumpNozData.values()) {
                        screen.UpdateNozView(Conv.toInt(v.NozID), v.View_Statue, v.GradeName, v.Amont.toPlainString(), v.pumpid);
                    }
                }
            } catch (RuntimeException ignored) {
                // comme l'original
            }
            sleep(500);
        }
    }

    // ------------------------------------------------------------ rapports

    private static LocalDate day(String dailyNo) {
        return LocalDate.parse(dailyNo.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String ymd(LocalDateTime d) {
        return d.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static String maxDailyNo(String where) {
        return db().scalar(db().sql("NormalDailySummary", "select MAX({DailyNo}) from [NormalDailySummary] where " + where), null, String.class);
    }

    /** CloseReport : rapports de poste (ShiftConfig), journaliers et mensuels quand la période est échue. */
    private static void CloseReport() {
        while (IniInfo.ThreadStop) {
            if (!IniInfo.AuthShift) {
                sleep(5000);
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            try {
                List<Row> shifts = db().list("ShiftConfig", "ORDER BY {ID}", null);
                String text = maxDailyNo("{ReportType} = 0");
                if (text == null) {
                    text = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "01";
                } else {
                    int num = Integer.parseInt(text.substring(text.length() - 2));
                    text = num >= shifts.size() ? day(text).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE) + "01"
                        : text.substring(0, text.length() - 2) + BaseFunction.SetStrLen(String.valueOf(num + 1), 2);
                }
                CurrentShiftText = MultiLanguage.getText("CurrentShift") + ":" + text;
                String text2 = maxDailyNo("{ReportType} = 0");
                if (text2 != null && !text2.isEmpty()) {
                    LocalDate date = day(text2);
                    int num2 = Integer.parseInt(text2.substring(8, 10));
                    Row sc;
                    if (num2 >= shifts.size()) {
                        sc = shifts.get(0);
                        date = date.plusDays(1);
                    } else {
                        sc = shifts.get(num2);
                    }
                    LocalDateTime s = date.atTime(LocalTime.of(sc.i("StartTime"), 0));
                    LocalDateTime e = sc.i("EndTime") <= sc.i("StartTime") ? date.plusDays(1).atTime(LocalTime.of(sc.i("EndTime"), 0))
                        : date.atTime(LocalTime.of(sc.i("EndTime"), 0));
                    if (now.isAfter(e)) {
                        CloseDayReport(s, e, ymd(s) + BaseFunction.SetStrLen(sc.str("ID"), 2));
                    }
                } else {
                    for (Row item : shifts) {
                        LocalDateTime s = now.toLocalDate().minusDays(1).atTime(LocalTime.of(item.i("StartTime"), 0));
                        LocalDateTime e = item.i("EndTime") <= item.i("StartTime")
                            ? s.toLocalDate().plusDays(1).atTime(LocalTime.of(item.i("EndTime"), 0))
                            : s.toLocalDate().atTime(LocalTime.of(item.i("EndTime"), 0));
                        String dutyNo = ymd(s) + BaseFunction.SetStrLen(item.str("ID"), 2);
                        if (!db().any("NormalDailySummary", "{DailyNo} = :d", p("d", dutyNo))) {
                            CloseDayReport(s, e, dutyNo);
                        }
                    }
                }
                LocalTime settle = LocalTime.parse(IniInfo.SettleTime);
                String text4 = maxDailyNo("LENGTH({DailyNo}) = 8");
                if (text4 != null && !text4.isEmpty()) {
                    LocalDateTime e = day(text4).atTime(settle).plusDays(2);
                    if (now.isAfter(e)) {
                        LocalDateTime s = e.minusDays(1);
                        CloseDayReport(s, e, ymd(s));
                    }
                } else {
                    LocalDateTime s = now.toLocalDate().minusDays(2).atTime(settle);
                    CloseDayReport(s, s.plusDays(1), ymd(s));
                }
                String text5 = maxDailyNo("LENGTH({DailyNo}) = 6");
                if (text5 != null && !text5.isEmpty()) {
                    LocalDateTime e = LocalDate.of(Integer.parseInt(text5.substring(0, 4)), Integer.parseInt(text5.substring(4, 6)), 1).atTime(settle)
                        .plusMonths(2);
                    if (now.isAfter(e)) {
                        LocalDateTime s = e.minusMonths(1);
                        CloseDayReport(s, e, s.format(DateTimeFormatter.ofPattern("yyyyMM")));
                    }
                } else {
                    LocalDateTime s = now.toLocalDate().minusMonths(2).withDayOfMonth(1).atTime(settle);
                    CloseDayReport(s, s.plusMonths(1), s.format(DateTimeFormatter.ofPattern("yyyyMM")));
                }
                ReportTime = now;
            } catch (RuntimeException ignored) {
                // comme l'original
            }
            sleep(5000);
        }
    }

    /** CloseReportHour : rapport de l'heure écoulée (aaaaMMjj-HH, type 3). */
    private static void CloseReportHour() {
        try {
            LocalDateTime now = LocalDateTime.now();
            String dailyNo = ymd(now) + "-" + BaseFunction.SetStrLen(String.valueOf(now.getHour()), 2);
            if (!db().any("NormalDailySummary", "{ReportType} = 3 AND {DailyNo} = :d", p("d", dailyNo))) {
                LocalDateTime dt = now.toLocalDate().atTime(now.getHour(), 0);
                long start = BaseFunction.ConvertDateTimeToInt(dt.minusHours(1));
                long end = BaseFunction.ConvertDateTimeToInt(dt);
                NormalFunction.SettleNoz(dailyNo, start, end);
                NormalFunction.SettleStock(dailyNo, start, end);
                NormalFunction.SettleEnery(dailyNo, start, end);
                NormalFunction.SettleSummary(dailyNo, start, end);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static boolean CloseDayReport(LocalDateTime dtStart, LocalDateTime dtEnd, String DailyNo) {
        try (Sugar.Tx tx = db().begin()) {
            long start = BaseFunction.ConvertDateTimeToInt(dtStart);
            long end = BaseFunction.ConvertDateTimeToInt(dtEnd);
            if (!NormalFunction.SettleNoz(DailyNo, start, end)) {
                throw new IllegalStateException("Day Noz Report Error");
            }
            if (!NormalFunction.SettleEnery(DailyNo, start, end)) {
                throw new IllegalStateException("Day Energy Report Error");
            }
            if (!NormalFunction.SettleStock(DailyNo, start, end)) {
                throw new IllegalStateException("Day Stock Report Error");
            }
            if (!NormalFunction.SettleSummary(DailyNo, start, end)) {
                throw new IllegalStateException("Day Sum Report Error");
            }
            tx.commit();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ transactions

    /** ShowTransaction : 50 dernières transactions hors poste, totaux par produit et nombre à envoyer. */
    public static void ShowTransaction() {
        try {
            List<Row> rows = new ArrayList<>();
            for (Row t : db().list("NormalTransaction", "COALESCE({HandShiftNo}, '') = '' AND COALESCE({ShiftNo}, '') = '' ORDER BY {CreateTime} DESC LIMIT 50",
                null)) {
                rows.add(Row.of("ID", t.i("ID"), "NozID", t.i("NozID"), "Volume", rnd(t.dec("Volume"), IniInfo.Vradio),
                    "Amount", rnd(t.dec("Amount"), IniInfo.Mradio),
                    "CreateTime", BaseFunction.StampToDateTime(t.str("CreateTime")).format(DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")),
                    "Settlement", t.i("Settlement")));
            }
            List<Row> grades = new ArrayList<>();
            for (Row g : db().query(db().sql("select {NormalTransaction.GradeID} as GradeID, SUM({NormalTransaction.Amount}) as Amn,"
                + " SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction] group by {NormalTransaction.GradeID}"), null)) {
                grades.add(Row.of("GradeName", BaseFunction.GetNormalGradeByID(g.i("GradeID")),
                    "TotalAmount", Conv.round(g.dec("Amn"), IniInfo.Mradio), "TotalVolume", Conv.round(g.dec("Volume"), IniInfo.Vradio)));
            }
            OilData = rows;
            GradeTotal = grades;
            UnUploadCount = (int) db().count("NormalTransaction", "{Upload} = false", null);
            DataVersion++;
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /**
     * DealUnTransaction : transactions reçues des boîtiers (UnDealTransaction) enregistrées en
     * NormalTransaction, avec saut de compteur (PumpJumpOil), commande Center et carte associées.
     */
    private static void DealUnTransaction() {
        while (IniInfo.ThreadStop) {
            long num = 0L;
            List<Row> list;
            try {
                list = db().list("UnDealTransaction", "{DealStatue} = 0 ORDER BY {TrueTime} DESC LIMIT 30", null);
            } catch (RuntimeException e) {
                list = List.of();
            }
            if (list.isEmpty()) {
                sleep(500);
                continue;
            }
            try (Sugar.Tx tx = db().begin()) {
                for (Row un : list) {
                    num = un.l("ID");
                    DealOneUnTransaction(un);
                    un.put("DealStatue", 1);
                }
                db().updateAll("UnDealTransaction", list);
                tx.commit();
            } catch (RuntimeException ex) {
                NormalSync.MainLog.writeLog("Un Error:" + ex.getMessage() + "   ID:" + num);
            }
            sleep(500);
            ShowTransaction();
        }
    }

    private static void DealOneUnTransaction(Row un) {
        int nozId = un.i("NozID");
        if (db().any("NormalTransaction", "{NozID} = :n AND {Volume} = :v AND {VolumeTotal} = :t",
            p("n", nozId, "v", un.dec("Volume"), "t", un.dec("VolumeTotal")))) {
            return;
        }
        Row lastT = db().first("NormalTransaction", "{NozID} = :n ORDER BY {CreateTime} DESC", p("n", nozId));
        if (lastT != null && lastT.dec("VolumeTotal").add(un.dec("Volume")).compareTo(un.dec("VolumeTotal")) < 0) {
            BigDecimal volume = un.dec("VolumeTotal").subtract(lastT.dec("VolumeTotal").add(un.dec("Volume")));
            db().insert("PumpJumpOil", Row.of("NozID", nozId, "SeverCreateTime", now(), "StationID", IniInfo.StationID, "TrueTime", un.get("TrueTime"),
                "Volume", volume, "VolumeTotal", un.dec("VolumeTotal").subtract(un.dec("Volume")), "Price", un.dec("Price"),
                "AmountDigits", un.get("AmountDigits"), "CardNo", "", "CompanyID", IniInfo.CompanyID, "DeviceNo", "", "GradeID", un.get("GradeID"),
                "Mileage", BigDecimal.ZERO, "TType", 0, "PaymentID", 55, "DealStatue", 0, "OrderNumber", "",
                "Amount", volume.multiply(un.dec("Price"))));
        }
        String orderNumber = un.str("OrderNumber");
        Row t = Row.of("Amount", un.dec("Amount"), "RealAmount", un.dec("Amount"), "AmountTotal", un.dec("AmountTotal"),
            "CreateTime", BaseFunction.ConvertDateTimeToInt(Conv.toDateTime(un.get("TrueTime"))), "NozID", nozId, "Price", un.dec("Price"),
            "Volume", un.dec("Volume"), "VolumeTotal", un.dec("VolumeTotal"), "EmployeeID", IniInfo.LoginID, "StationID", IniInfo.StationID,
            "OrderNo", String.valueOf(Integer.parseInt(orderNumber, 16)), "Upload", true, "PayMent", un.get("PaymentID"), "AmountDigits", IniInfo.Mradio,
            "CardNo", un.str("CardNo"), "Mileage", un.get("Mileage"), "DiscountValue", BigDecimal.valueOf(100), "ApiSend", IniInfo.SupervisionType == 0);
        Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", nozId));
        if (noz != null) {
            Row grade = db().first("NormalGrade", "{ID} = :g", p("g", noz.get("GradeID")));
            t.put("GradeID", noz.get("GradeID"));
            t.put("TankID", noz.get("TankID"));
            if (grade != null) {
                t.put("GradeName", grade.str("GradeName"));
            }
        }
        int id = Conv.toInt(db().insert("NormalTransaction", t));
        t.put("ID", id);
        BigDecimal bal = M1;
        String carNumber = "";
        if (!"00000000".equals(orderNumber)) {
            Row order = db().first("CmdOrder", "{ID} = :i", p("i", Integer.parseInt(orderNumber, 16)));
            if (order != null) {
                order.put("TransactionID", id);
                order.put("Amount", t.dec("Amount"));
                t.put("CardNo", order.str("CardNo"));
                t.put("CardBalance", Conv.round(order.dec("Bal").subtract(t.dec("Amount")), 3));
                bal = order.dec("Bal");
                carNumber = order.str("CarNo");
                t.put("Upload", false);
                if (t.i("PayMent") != 999) {
                    t.put("PayMent", 997);
                }
                t.put("Mileage", order.get("Mileage"));
                db().update("CmdOrder", order);
                BaseFunction.post("ServerDeal/UpdateOrder", SW.dto().has("CmdOrder") ? SW.dto().shape("CmdOrder", order) : order);
            }
        } else {
            t.put("Upload", false);
        }
        String cardNo = t.str("CardNo");
        if (cardNo != null) {
            if (cardNo.substring(0, 9).equals("000000000")) {
                Holder emp = BaseFunction.GetEmpMess(cardNo);
                if (emp.ok()) {
                    t.put("CardName", emp.a().str("Name"));
                }
            } else {
                Holder cus = BaseFunction.GetCustomerMess(cardNo);
                if (cus.ok()) {
                    BigDecimal discount = Conv.round(cus.b().dec("Discount"), 2);
                    t.put("Amount", t.dec("RealAmount").multiply(discount.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)).stripTrailingZeros());
                    if (bal.compareTo(M1) != 0) {
                        t.put("CardBalance", Conv.round(bal.subtract(t.dec("Amount")), 3));
                    }
                    t.put("CardName", cus.a().str("Name"));
                    t.put("CarNumber", cus.a().str("VehicleNo"));
                    t.put("CardGroup", cus.b().get("GroupID"));
                    t.put("DiscountValue", discount);
                }
                if (cardNo.substring(0, 9).equals("000000004")) {
                    t.put("CarNumber", carNumber);
                }
            }
        }
        Row saved = db().first("NormalTransaction", "{ID} = :i", p("i", id));
        saved.putAll(t);
        db().update("NormalTransaction", saved);
    }

    // ------------------------------------------------------------ événements du matériel

    private static BigDecimal pow10(int n) {
        return BigDecimal.TEN.pow(n);
    }

    private static BigDecimal digits(String s, int radio) {
        return rnd(new BigDecimal(s).divide(pow10(radio), 10, RoundingMode.HALF_EVEN).stripTrailingZeros(), radio);
    }

    /** Serial_TransferEvent : données des ports série (pistolets, cuves, versions, RFID, PLC) vers les vues. */
    public static void Serial_TransferEvent(int NozID, String Value, int Statue) {
        try {
            switch (Statue) {
                case 1025 -> nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", digits(Value, IniInfo.Pradio), M1, M1, M1, M1, -1, -1, "", "-1");
                case 1026 -> {
                    int half = Value.length() / 2;
                    nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", M1, M1, M1, digits(Value.substring(half, half * 2), IniInfo.Mradio),
                        digits(Value.substring(0, half), IniInfo.Vradio), -1, -1, "", "-1");
                }
                case 1029 -> nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", M1, M1, M1, M1, M1, -1, Conv.toInt(Value), "", "-1");
                case 1030 -> nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", M1, M1, M1, M1, M1, Conv.toInt(Value), -1, "", "-1");
                case 1031 -> {
                    String[] a = Value.split("\\|", -1);
                    if (a.length >= 2) {
                        nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", M1, new BigDecimal(a[1]), new BigDecimal(a[0]), M1, M1, -1, -1, "", "-1");
                    }
                }
                case 1032 -> {
                    ShowTransaction();
                    Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", NozID));
                    TankSerial ts = tankSerial;
                    if (noz != null && ts != null) {
                        ts.Record.add(noz.i("ID"));
                    }
                }
                case 1033 -> {
                    String[] a = Value.split("\\|", -1);
                    nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", new BigDecimal(a[4]), new BigDecimal(a[3]), new BigDecimal(a[2]), new BigDecimal(a[6]),
                        new BigDecimal(a[5]), -1, Conv.toInt(a[0]), a[7], "-1");
                }
                case 1049 -> {
                    String[] a = Value.split("\\|", -1);
                    BigDecimal h = new BigDecimal(a[0]);
                    BigDecimal v = new BigDecimal(a[1]);
                    BigDecimal w = new BigDecimal(a[2]);
                    BigDecimal tp = new BigDecimal(a[3]);
                    tankShow.Tank_ViewUpdate(NozID, NozID, h, v, w, tp);
                    Row tank = db().first("NormalTank", "CAST(NULLIF({TankNo}, '') AS integer) = :n", p("n", NozID));
                    if (tank != null) {
                        boolean flag = false;
                        for (Object[] kv : new Object[][] {{"CurrentHeight", h}, {"CurrentVolume", v}, {"CurrentWater", w}, {"CurrentTemp", tp}}) {
                            if (tank.dec((String) kv[0]).compareTo((BigDecimal) kv[1]) != 0) {
                                tank.put((String) kv[0], kv[1]);
                                flag = true;
                            }
                        }
                        if (flag) {
                            db().update("NormalTank", tank);
                        }
                    }
                }
                case 1280 -> {
                    Row box = db().first("BoxList", "{ID} = :i", p("i", NozID));
                    if (box != null) {
                        box.put("BoxVersion", Value);
                        db().update("BoxList", box);
                        BoxSetView bs = BoxSet;
                        if (bs != null) {
                            bs.Box_ShowVersion(Value);
                        }
                    }
                }
                case 1281 -> {
                    String[] a = Value.split("-");
                    BoxSetView bs = BoxSet;
                    if (bs != null) {
                        bs.Box_UpdateProgress(Conv.toInt(a[0]), Conv.toInt(a[1]), Conv.toInt(a[2]));
                    }
                }
                case 1282 -> {
                    String[] a = Value.split("\\|", -1);
                    NozVerView nv = NozVerUpdate;
                    if (nv != null) {
                        nv.CheckVersion(Conv.toInt(a[0]), Conv.toInt(a[1]));
                    }
                }
                case 1537 -> {
                    String[] a = Value.split("\\|", -1);
                    RFIDForm.UpdateOne(Conv.toInt(a[0]), Conv.toInt(a[1]), Conv.toInt(a[2]), Conv.toInt(a[3]), a[4], a[5], a[6], new BigDecimal(a[7]),
                        Boolean.parseBoolean(a[8]), Boolean.parseBoolean(a[9]));
                }
                case 1793 -> {
                    PLCParam plc = BaseInfo.fromJson(Value, PLCParam.class);
                    PLCForm.UpdateValue(s(plc.A_PistonCurrent), s(plc.InTake_Pressure), s(plc.Medium_Pressure), s(plc.Protection_Pressure),
                        s(plc.Water_Flow_Temperature), s(plc.Gas_Concentration_Inside_the_Sled_First), s(plc.Low_Pressure), s(plc.B_PistonCurrent),
                        s(plc.High_Pressure), s(plc.Instrument_Air_Pressure), s(plc.Hydraulic_Oil_Temperature), s(plc.Gas_Concentration_Second),
                        s(plc.Gas_Concentration_Third), String.valueOf(plc.Statue));
                    if (plc.Error != null && plc.Error.length != 100) {
                        StringBuilder text = new StringBuilder();
                        for (int e : plc.Error) {
                            text.append(e).append(',');
                        }
                        PLCForm.UpdateAlarm(text.length() > 0 ? text.substring(0, text.length() - 1) : "");
                    } else {
                        PLCForm.UpdateAlarm("0");
                    }
                }
                default -> {
                    // 1027, 1028 : valeurs calculées sans usage dans l'original
                }
            }
        } catch (RuntimeException ex) {
            NormalSync.MainLog.writeLog("Error Message:" + Statue + " Error Type:" + ex.getMessage());
        }
    }

    private static String s(BigDecimal d) {
        return d == null ? "0" : d.toPlainString();
    }

    /** Noz_UpdateValueEvent : boutons de la page des pistolets (verrouillage, mise à jour, arrêt). */
    public static void Noz_UpdateValueEvent(int NozID, String Value, int Statue) {
        if (IniInfo.WifiMode == 1) {
            if (Statue != 1282 && Statue != 1283) {
                return;
            }
            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                AllNozStatue all = box.NozList.stream().filter(t -> t.NozID == NozID).findFirst().orElse(null);
                if (all == null) {
                    continue;
                }
                all.AuthMessage = "";
                if (all.Statue == 3) {
                    CmdPreSet set = new CmdPreSet();
                    set.DeviceNo = box.NoHead.toUpperCase();
                    set.NozID = NozID;
                    set.PreSetValue = BigDecimal.valueOf(9000);
                    set.PreSetPrice = BigDecimal.ZERO;
                    set.AuthType = false;
                    CmdList c = platCmd("A4-Stop", box, NozID, set);
                    c.DeviceNo = box.NoHead.toUpperCase();
                    box.cmdNormal.add(c);
                } else {
                    box.cmdAuth.stream().filter(t -> "A1".equals(t.CmdType)).findFirst().ifPresent(box.cmdAuth::remove);
                }
                nozshow.Noz_UpdateView(all.NozID, -1, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, all.AuthMessage, "-1");
                break;
            }
            return;
        }
        NormalNozStatue n = GetSerialNoz(String.valueOf(NozID), null);
        switch (Statue) {
            case 1281 -> {
                if ("0".equals(Value)) {
                    n.Statue.add(11);
                }
                if ("1".equals(Value)) {
                    n.Statue.add(10);
                }
            }
            case 1282 -> n.Statue.add(14);
            case 1283 -> {
                int portIndex = getPortIndex(Conv.toInt(Value));
                if (portIndex != -1 && Ports.get(portIndex).AgreedTypeID == 0) {
                    NozSerial port = Ports.get(portIndex);
                    port.DealQueue(NozID, 161);
                    port.LTStatue.add(lt(164, NozID));
                    NormalNozStatue n2 = serialNoz(port, NozID);
                    if (n2 != null) {
                        n2.CardNo = "";
                    }
                    port.RFIDConfigs.stream().filter(t -> t.NozID == NozID).findFirst().ifPresent(r -> {
                        r.Tag = "";
                        r.UserName = "";
                        r.CarNo = "";
                        r.Bal = BigDecimal.ZERO;
                    });
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ préréglages (appelés par les fenêtres)

    /** SetAndFuel : préréglage d'un pistolet sur port série (montant / volume, prix). */
    public static void SetAndFuel(int PortID, String NozID, BigDecimal Value, int SetType, BigDecimal Price) {
        NozSerial port = portById(PortID);
        if (port == null || port.AgreedTypeID != 0) {
            return;
        }
        int noz = Conv.toInt(NozID);
        port.DealQueue(noz, 161);
        if (port.GetNozAuth(noz) != 3 && !port.NozIsRaise(noz)) {
            port.LTStatue.add(preset(165, noz, Value, SetType, Price));
        }
        port.LTStatue.add(preset(161, noz, Value, SetType, Price));
    }

    private static LTNozStatuet preset(int statue, int noz, BigDecimal value, int setType, BigDecimal price) {
        LTNozStatuet l = lt(statue, noz);
        l.PreType = setType;
        l.PreValue = value;
        l.PrePrice = price;
        return l;
    }

    /** ShowPreSetValueInNoz : texte du préréglage affiché sur la carte du pistolet ; renvoie ce texte (Auth). */
    public static String ShowPreSetValueInNoz(Row BoxPort, int NozID, BigDecimal Value, int SetType) {
        String text = "";
        try {
            int m = IniInfo.Mradio;
            if (BoxPort != null) {
                text = switch (SetType) {
                    case 0 -> MultiLanguage.getText("Amount") + ":";
                    case 1 -> MultiLanguage.getText("Volume") + ":";
                    default -> "";
                };
                BigDecimal max = BigDecimal.valueOf(BoxPort.i("Box_DeviceType") == 0 ? 900000 : 90000000);
                if (Value.compareTo(max) > 0 || Value.compareTo(M1) == 0) {
                    Value = max;
                }
                String text3 = Value.toPlainString();
                if (Value.compareTo(max) == 0 && text3.equals(max.toPlainString())) {
                    text3 = max.divide(pow10(m)).setScale(m <= 3 ? m : 0, RoundingMode.HALF_EVEN).toPlainString();
                }
                text += text3;
            }
            nozshow.Noz_UpdateView(NozID, -1, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, text, "-1");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
        return text;
    }

    /** SetAndFuelWifi : préréglage d'un pistolet d'un boîtier sans fil. */
    public static void SetAndFuelWifi(Row noz, BigDecimal Value, int SetType, BigDecimal Price) {
        IBoxClientParam box = boxById(noz.i("PortID"));
        if (box == null) {
            throw new IllegalStateException("Box Lost");
        }
        int nozId = noz.i("NozID");
        AllNozStatue all = box.NozList.stream().filter(t -> t.NozID == nozId).findFirst().orElse(null);
        Row boxPort = db().first("Box_Set", "{ID} = :i", p("i", noz.get("Box_Port_ID")));
        if (boxPort == null) {
            throw new IllegalStateException("BoxPort Lost");
        }
        CmdPreSet set = new CmdPreSet();
        set.DeviceNo = box.NoHead;
        set.NozID = nozId;
        set.PreSetType = SetType;
        set.PreSetValue = Value;
        set.PreSetPrice = Price;
        set.AuthType = true;
        if (all.AuthTypeID == 3) {
            box.cmdNormal.add(platCmd("A1", box, nozId, set));
        } else if (all.RaiseNoz != 1) {
            box.cmdNormal.add(platCmd("A5", box, nozId, set));
            box.cmdAuth.removeIf(t -> t.DeviceNo != null && t.DeviceNo.equalsIgnoreCase(box.NoHead) && t.NozID == nozId && "A1".equals(t.CmdType));
            box.cmdAuth.add(platCmd("A1", box, nozId, set));
        } else {
            box.cmdNormal.add(platCmd("A1", box, nozId, set));
        }
        all.AuthMessage = ShowPreSetValueInNoz(boxPort, nozId, Value, SetType);
    }

    private static int dutyForNewNoz() {
        if (IniInfo.NormalNeedDuty == 0) {
            return 1;
        }
        Row h = db().first("HandShiftReport", "ORDER BY {ID} DESC", null);
        return h != null && h.i("DealStatue") == 0 ? 1 : 0;
    }

    private static NormalNozStatue newNoz(Row noz, String nozId) {
        Row boxSet = db().first("Box_Set", "{ID} = :i", p("i", noz.get("Box_Port_ID")));
        int deviceType = boxSet == null ? 0 : boxSet.i("Box_DeviceType");
        return new NormalNozStatue(noz.i("ID"), nozId, noz.i("TankID"), noz.i("Accredit"), noz.dec("Price"), noz.i("MachineOpen"), deviceType,
            dutyForNewNoz(), noz.i("AllowAccredit"), noz.i("PumpID"), IniInfo.IdentifyMode);
    }

    /** UpdateNozMess : modification d'un pistolet depuis les fenêtres de réglage (SetType 0 à 14). */
    public static void UpdateNozMess(int ID, String OldNozID, String Value, int SetType) {
        try {
            Row noz = null;
            NormalNozStatue n = null;
            if (SetType < 10 || SetType > 12) {
                noz = db().first("NormalNoz", "{NozID} = :n AND {IsUsing} = true", p("n", ID));
                if (noz == null) {
                    return;
                }
                n = GetSerialNoz(OldNozID, null);
                if (n == null) {
                    return;
                }
            }
            int old = Conv.toInt(OldNozID);
            switch (SetType) {
                case 0 -> {
                    n.NozID = BaseFunction.SetStrLen(String.valueOf(ID), 2);
                    nozshow.Noz_UpdateView(old, ID, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, "", "-1");
                    for (int s : new int[] {0, 2, 3, 4, 1, 10}) {
                        n.Statue.add(s);
                    }
                }
                case 1 -> {
                    n.SetPrice = Conv.round(new BigDecimal(Value), Conv.toInt(IniInfo.PriceFormat));
                    n.Statue.add(16);
                }
                case 2 -> {
                    n.SetCloseValue = Conv.round(new BigDecimal(Value), 1);
                    n.Statue.add(17);
                }
                case 3 -> n.accredit = Conv.toInt(Value);
                case 4 -> {
                    for (NozSerial port : Ports) {
                        port.NozAddress.remove(n);
                    }
                    for (NozSerial port : Ports) {
                        if (port.SerialPortID == Conv.toInt(Value)) {
                            port.NozAddress.add(newNoz(noz, BaseFunction.SetStrLen(noz.str("NozID"), 2)));
                        }
                    }
                }
                case 5 -> {
                    Row tank = db().first("NormalTank", "{ID} = :i", p("i", Conv.toInt(Value)));
                    Row grade = tank == null ? null : db().first("NormalGrade", "{ID} = :g", p("g", tank.get("GradeID")));
                    if (tank != null && grade != null) {
                        nozshow.Noz_UpdateView(old, -1, grade.str("GradeName"), tank.str("TankName"), M1, M1, M1, M1, M1, -1, -1, "", "-1");
                    }
                }
                case 6 -> {
                    n.SetPrice = new BigDecimal(Value);
                    LTNozStatuet l = lt(162, Conv.toInt(n.NozID));
                    l.PrePrice = new BigDecimal(Value);
                    Ports.get(0).LTStatue.add(l);
                }
                case 7 -> {
                    n.accredit = Conv.toInt(Value);
                    String auth = switch (Conv.toInt(Value)) {
                        case 0 -> MultiLanguage.getText("Manualauthorization");
                        case 2 -> MultiLanguage.getText("BoxAuth");
                        case 3 -> MultiLanguage.getText("Advanceexe");
                        default -> MultiLanguage.getText("Automaticallyauthorize");
                    };
                    nozshow.Noz_UpdateView(old, ID, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, "-1", auth);
                }
                case 8 -> nozshow.Noz_UpdateView(old, Conv.toInt(Value), "-1", "-1", M1, M1, M1, M1, M1, -1, -1, "-1", "-1");
                case 9 -> nozshow.Noz_UpdateView(old, -1, Value, "-1", M1, M1, M1, M1, M1, -1, -1, "-1", "-1");
                case 10 -> ReloadTanks();
                case 11, 12 -> {
                    int allow = SetType == 11 ? 1 : 0;
                    for (NozSerial port : Ports) {
                        for (NormalNozStatue x : port.NozAddress) {
                            x.AllowAccredit = allow;
                        }
                    }
                    db().exec(db().sql("NormalNoz", "update [NormalNoz] set {AllowAccredit} = :a where {IsUsing} = true"), p("a", allow));
                }
                case 13, 14 -> {
                    int allow = SetType == 13 ? 1 : 0;
                    noz.put("AllowAccredit", allow);
                    n.AllowAccredit = allow;
                    db().update("NormalNoz", noz);
                }
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** UpdateNozMess(…, 10) : cuves du premier port rechargées depuis NormalTank (et dernier jaugeage). */
    private static void ReloadTanks() {
        if (Ports.isEmpty()) {
            return;
        }
        NozSerial port = Ports.get(0);
        port.TankStatueList.clear();
        for (Row item : db().list("NormalTank", "{IsUsing} = true", null)) {
            NormalTankSerial t = new NormalTankSerial();
            t.ID = item.i("ID");
            t.RaiseStartTime = item.l("RaiseStartTime");
            t.RaiseStartVolume = item.dec("RaiseStartVolume");
            t.CreateTime = item.l("CreateTime");
            t.CurrentHeight = item.dec("CurrentHeight");
            t.CurrentLevel = item.dec("CurrentLevel");
            t.CurrentTemp = item.dec("CurrentTemp");
            t.CurrentVolume = item.dec("CurrentVolume");
            t.CurrentWater = item.dec("CurrentWater");
            t.FirstRun = item.i("FirstRun");
            t.GradeID = item.i("GradeID");
            t.IsUsing = item.b("IsUsing");
            t.KeepTime = item.i("KeepTime");
            t.LowerHeight = item.dec("LowerHeight");
            t.LowerTemp = item.dec("LowerTemp");
            t.LowerWaterHeight = item.dec("LowerWaterHeight");
            t.MaxLevel = item.dec("MaxLevel");
            t.OverHeight = item.dec("OverHeight");
            t.OverImportLevel = item.dec("OverImportLevel");
            t.OverTemp = item.dec("OverTemp");
            t.RaiseEndTime = item.l("RaiseEndTime");
            t.OverWaterHeight = item.dec("OverWaterHeight");
            t.RaiseFlag = item.i("RaiseFlag");
            t.TankHeight = item.dec("TankHeight");
            t.RaiseLevel = item.dec("RaiseLevel");
            t.TankName = item.str("TankName");
            t.TankNo = item.str("TankNo");
            t.TankVolume = item.dec("TankVolume");
            t.UpdateTime = item.l("UpdateTime");
            Row cal = db().first("TankCableCalculate", "{TankID} = :t ORDER BY {CreateTime} DESC", p("t", item.i("ID")));
            if (cal != null) {
                t.CalculateHeight = cal.dec("CurrentHeight");
                t.CalculateVolume = cal.dec("CurrentVolume");
            }
            port.TankStatueList.add(t);
        }
        tankShow.Tank_Clear();
        tankShow.Tank_Add();
    }

    private static void NozAdd(Row noz) {
        try {
            Row tank = db().first("NormalTank", "{ID} = :t AND {IsUsing} = true", p("t", noz.get("TankID")));
            if (tank == null) {
                return;
            }
            Row grade = db().first("NormalGrade", "{ID} = :g AND {IsUsing} = true", p("g", tank.get("GradeID")));
            if (grade != null) {
                String auth = noz.i("Accredit") == 0 ? MultiLanguage.getText("Manualauthorization") : MultiLanguage.getText("Automaticallyauthorize");
                nozshow.Noz_AddView(noz.i("NozID"), grade.str("GradeName"), auth, 0);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    // ------------------------------------------------------------ messages de la fenêtre (WndProc)

    /** WndProc : messages envoyés à la fenêtre principale par les fenêtres de réglage et les boîtiers. */
    public static void WndProc(int Msg, long wParam, long lParam) {
        int w = (int) wParam;
        int l = (int) lParam;
        try {
            switch (Msg) {
                case 1026 -> UpdatePortMess(w);
                case 1027 -> PriceInfoChanged(w);
                case 1028 -> NozCreated(w);
                case 1040 -> RFIDCreated(w);
                case 1041 -> RFIDUpdated(w);
                case 1042 -> {
                    RFIDForm.RemoveRFID(w);
                    if (IniInfo.WifiMode == 1) {
                        for (IBoxClientParam box : BaseInfo.BoxClientList) {
                            if (box.RFIDList.removeIf(t -> t.ID == w)) {
                                break;
                            }
                        }
                    } else {
                        for (NozSerial port : Ports) {
                            if (port.RFIDConfigs.removeIf(t -> t.ID == w)) {
                                break;
                            }
                        }
                    }
                }
                case 1029 -> {
                    nozshow.Noz_RemoveView(w);
                    if (IniInfo.WifiMode == 1) {
                        for (IBoxClientParam box : BaseInfo.BoxClientList) {
                            if (box.NozList.removeIf(t -> t.NozID == w)) {
                                break;
                            }
                        }
                    } else {
                        for (NozSerial port : Ports) {
                            NormalNozStatue n = serialNoz(port, w);
                            if (n != null) {
                                port.NozAddress.remove(n);
                            }
                        }
                    }
                }
                case 1030 -> {
                    Row boxPort = db().first("Box_Set", "{ID} = :i", p("i", l));
                    if (boxPort == null) {
                        throw new IllegalStateException("BoxPort Lost");
                    }
                    if (IniInfo.WifiMode == 1) {
                        Row box = db().first("BoxList", "{ID} = :i", p("i", boxPort.get("Port_ID")));
                        if (box != null) {
                            BaseInfo.BoxClientList.stream().filter(t -> t.NoHead != null && t.NoHead.equalsIgnoreCase(box.str("BoxDeviceNo"))).findFirst()
                                .ifPresent(b -> b.cmdNormal.add(cmd("A3", box.str("BoxDeviceNo"), boxPort.i("Box_PortID"), null)));
                        }
                    } else {
                        NozSerial port = portById(boxPort.i("Port_ID"));
                        if (port != null) {
                            LTNozStatuet x = lt(163, 0);
                            x.SerialPort = l;
                            port.LTStatue.add(x);
                        }
                    }
                }
                case 1031 -> {
                    for (NozSerial port : Ports) {
                        if (port.SerialPortID == w) {
                            port.PortRun = false;
                            Ports.remove(port);
                            break;
                        }
                    }
                }
                case 1032 -> PortInit();
                case 1033 -> {
                    RFIDForm.RemoveRFID(w);
                    if (IniInfo.WifiMode != 1) {
                        for (NozSerial port : Ports) {
                            port.RFIDConfigs.removeIf(t -> t.ID == w);
                        }
                    }
                }
                case 1044 -> {
                    if (IniInfo.WifiMode == 1) {
                        BaseInfo.LoadWireLessDevice();
                    } else {
                        PortInit();
                    }
                }
                case 1043 -> {
                    if (IniInfo.WifiMode == 1) {
                        IBoxClientParam box = boxById(l);
                        if (box != null) {
                            box.cmdNormal.add(cmd("11", null, w, null));
                        }
                    } else {
                        NozSerial port = portById(l);
                        if (port != null) {
                            LTNozStatuet x = lt(17, 0);
                            x.SerialPort = w;
                            port.LTStatue.add(x);
                        }
                    }
                }
                case 1045 -> PrintSet();
                case 1046 -> PrintTransaction(w, l);
                case 1047 -> SetOnDuty(1);
                case 1048 -> SetOnDuty(OnDutyAfterClose());
                case 1281 -> {
                    if (IniInfo.WifiMode == 1) {
                        IBoxClientParam box = boxById(w);
                        if (box != null) {
                            box.cmdNormal.add(cmd("0E", null, 0, null));
                        }
                    } else {
                        NozSerial port = portById(w);
                        if (port != null && port.CurrentProgram == -1) {
                            port.VersionCheck = true;
                            port.LTStatue.add(lt(14, 0));
                        }
                    }
                }
                case 1282 -> new Thread(NormalEngine::DowloadFile, "normal-DowloadFile").start();
                case 1537 -> {
                    if (IniInfo.WifiMode == 1) {
                        IBoxClientParam box = boxById(l);
                        if (box != null) {
                            box.cmdNormal.add(cmd("DowLoadProgram", null, 0, null));
                        }
                    } else {
                        NozSerial port = portById(l);
                        if (port != null && port.CurrentProgram == -1) {
                            LTNozStatuet x = lt(13, 0);
                            x.ProgramType = w;
                            port.LTStatue.add(x);
                        }
                    }
                }
                case 1538 -> {
                    if (IniInfo.WifiMode == 1) {
                        IBoxClientParam box = boxById(l);
                        if (box != null) {
                            box.cmdNormal.add(cmd("12", null, w, String.valueOf(w)));
                        }
                    } else {
                        NozSerial port = portById(l);
                        if (port != null) {
                            LTNozStatuet x = lt(18, 0);
                            x.SerialPort = w;
                            port.LTStatue.add(x);
                        }
                    }
                }
                case 1793 -> {
                    BoxSetView bs = BoxSet;
                    if (bs != null) {
                        bs.Box_UpdateProgress(w, l, 0);
                    }
                }
                case 2049 -> {
                    Row cal = db().first("TankCableCalculate", "{ID} = :i", p("i", wParam));
                    if (cal != null && !Ports.isEmpty()) {
                        Ports.get(0).TankStatueList.stream().filter(t -> t.ID == cal.i("TankID")).findFirst().ifPresent(t -> {
                            t.CalculateVolume = cal.dec("CurrentVolume");
                            t.CalculateHeight = cal.dec("CurrentHeight");
                            t.SumCalculateVolume = BigDecimal.ZERO;
                            t.CreateTime = cal.l("CreateTime");
                        });
                    }
                }
                case 2050 -> {
                    // comme l'original : rien pour les boîtiers sans fil
                    if (IniInfo.WifiMode != 1) {
                        for (NozSerial port : Ports) {
                            if (port.TankStatueList.stream().anyMatch(t -> t.ID == w)) {
                                LTNozStatuet x = lt(2, 0);
                                x.TankID = w;
                                port.LTStatue.add(x);
                                break;
                            }
                        }
                    }
                }
                case 2305 -> {
                    IBoxClientParam box = boxById(w);
                    AllNozStatue a = box == null ? null : box.NozList.stream().filter(t -> t.NozID == l).findFirst().orElse(null);
                    if (a != null) {
                        String auth = switch (a.AuthTypeID) {
                            case 0 -> MultiLanguage.getText("Manualauthorization");
                            case 2 -> MultiLanguage.getText("BoxAuth");
                            case 3 -> MultiLanguage.getText("Advanceexe");
                            default -> MultiLanguage.getText("Automaticallyauthorize");
                        };
                        nozshow.Noz_UpdateView(a.NozID, -1, "-1", "-1", a.Price, a.Amount, a.Volume, a.AmountTotal, a.VolumeTotal, -1, a.Statue,
                            a.AuthMessage, auth);
                    }
                }
                case 2306 -> {
                    IBoxClientParam box = boxById(w);
                    RFIDRunData r = box == null ? null : box.RFIDList.stream().filter(t -> t.RFIDNo == l).findFirst().orElse(null);
                    if (r != null) {
                        RFIDForm.UpdateOne(r.ID, -1, -1, -1, r.Tag, r.UserName, r.CarNo, r.Bal, r.IsOnline, Boolean.parseBoolean(r.IsWork));
                    }
                }
                case 2307 -> {
                    IBoxClientParam box = boxById(w);
                    TankStatue t = box == null ? null : box.TankList.stream().filter(x -> x.TankID == l).findFirst().orElse(null);
                    if (t != null) {
                        tankShow.Tank_ViewUpdate(t.TankID, t.TankID, t.OilHeight, t.OilVolume, t.WaterHeight, t.Temp);
                    }
                }
                case 2308 -> {
                    BoxSetView bs = BoxSet;
                    if (bs != null) {
                        bs.Box_ShowTCPVersion(w);
                    }
                }
                case 2309 -> {
                    NozVerView nv = NozVerUpdate;
                    if (nv != null) {
                        nv.CheckTcpVersion(w, l);
                    }
                }
                case 2310 -> AuthMessageRefresh(w);
                case 2311 -> {
                    IBoxClientParam box = boxById(w);
                    TankStatue t = box == null ? null : box.TankList.stream().filter(x -> x.ID == l).findFirst().orElse(null);
                    if (t != null) {
                        Row tank = db().first("NormalTank", "{ID} = :i", p("i", l));
                        if (tank != null) {
                            tank.put("CurrentHeight", t.OilHeight);
                            tank.put("CurrentVolume", t.OilVolume);
                            tank.put("CurrentTemp", t.Temp);
                            tank.put("CurrentWater", t.WaterHeight);
                            tank.put("CurrentLevel", t.OilVolume);
                            db().update("NormalTank", tank);
                        }
                    }
                }
                case 2312 -> {
                    Row data = db().first("PLCParamData");
                    if (data != null) {
                        PLCForm.ShowData(data, String.valueOf(w));
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            NormalSync.MainLog.writeLog("WndProc " + Msg + " Error:" + ex.getMessage());
        }
    }

    /** 1027 : prix d'un produit modifié (NormalPriceInfo) envoyé à ses pistolets et aux afficheurs. */
    private static void PriceInfoChanged(int id) {
        Row info = db().first("NormalPriceInfo", "{ID} = :i", p("i", id));
        if (info == null) {
            return;
        }
        BigDecimal price = info.dec("Price");
        for (Row item : db().list("NormalNoz", "{GradeID} = :g AND {IsUsing} = true", p("g", info.get("GradeID")))) {
            item.put("Price", price);
            db().update("NormalNoz", item);
            int nozId = item.i("NozID");
            if (IniInfo.WifiMode == 1) {
                IBoxClientParam box = boxById(item.i("PortID"));
                if (box != null) {
                    box.cmdNormal.add(priceCmd(box, nozId, price));
                    box.NozList.stream().filter(t -> t.NozID == nozId).findFirst().ifPresent(n -> n.Price = price);
                }
            } else {
                NozSerial[] serial = new NozSerial[1];
                NormalNozStatue n = GetSerialNoz(String.valueOf(nozId), serial);
                if (n != null && serial[0] != null) {
                    LTNozStatuet x = lt(162, Conv.toInt(n.NozID));
                    x.PrePrice = price;
                    serial[0].LTStatue.add(x);
                }
            }
            nozshow.Noz_UpdateView(nozId, -1, BaseFunction.GetNormalGradeByID(item.i("GradeID")), "-1", price, M1, M1, M1, M1, -1, -1, "-1", "-1");
        }
        PriceBoard();
    }

    /** 1028 : pistolet ajouté. */
    private static void NozCreated(int id) {
        Row noz = db().first("NormalNoz", "{ID} = :i", p("i", id));
        if (noz == null) {
            return;
        }
        NozAdd(noz);
        if (IniInfo.WifiMode == 1) {
            return;
        }
        for (NozSerial port : Ports) {
            if (port.ID == noz.i("PortID")) {
                port.NozAddress.add(newNoz(noz, BaseFunction.SetStrLen(noz.str("NozID"), 2)));
                break;
            }
        }
    }

    private static void RFIDCreated(int id) {
        Row rfid = db().first("RFIDConfig", "{ID} = :i", p("i", id));
        if (rfid == null) {
            return;
        }
        RFIDForm.CreateOne(rfid.i("ID"), rfid.i("RFIDNo"), rfid.i("Address"), rfid.i("NozID"));
        nozshow.Noz_UpdateView(rfid.i("NozID"), -1, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, "-1", MultiLanguage.getText("Manualauthorization"));
        if (IniInfo.WifiMode == 1) {
            return;
        }
        Row boxPort = db().first("Box_Set", "{ID} = :i", p("i", rfid.get("Box_Port")));
        if (boxPort == null) {
            throw new IllegalStateException("Box Port Lost");
        }
        if (Ports.isEmpty()) {
            return;
        }
        NozSerial port = portById(boxPort.i("Port_ID"));
        if (port == null) {
            throw new IllegalStateException("Port Lost");
        }
        RFIDRunData r = new RFIDRunData();
        r.ID = rfid.i("ID");
        r.Address = rfid.i("Address");
        r.StationID = IniInfo.StationID;
        r.StationName = IniInfo.StationName;
        r.Bal = BigDecimal.ZERO;
        r.Box_Port = rfid.i("Box_Port");
        r.CompanyID = IniInfo.CompanyID;
        r.IsOnline = false;
        r.NozID = rfid.i("NozID");
        r.RFIDNo = rfid.i("RFIDNo");
        r.Tag = "";
        r.UpdateTime = LocalDateTime.now().format(Conv.STD);
        r.UserName = "";
        port.RFIDConfigs.add(r);
        NormalNozStatue n = serialNoz(port, r.NozID);
        if (n != null) {
            n.accredit = 0;
            if (db().any("NormalNoz", "{NozID} = :n", p("n", Conv.toInt(n.NozID)))) {
                LTNozStatuet x = lt(163, 0);
                x.SerialPort = boxPort.i("Box_PortID");
                port.LTStatue.add(x);
            }
        }
    }

    private static void RFIDUpdated(int id) {
        Row rfid = db().first("RFIDConfig", "{ID} = :i", p("i", id));
        if (rfid == null) {
            return;
        }
        if (IniInfo.WifiMode == 1) {
            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                RFIDRunData r = box.RFIDList.stream().filter(t -> t.ID == id).findFirst().orElse(null);
                if (r != null) {
                    RFIDForm.UpdateOne(rfid.i("ID"), rfid.i("RFIDNo"), rfid.i("Address"), rfid.i("NozID"), "-1", "-1", "-1", M1, r.IsOnline,
                        !r.Tag.trim().isEmpty());
                    r.RFIDNo = rfid.i("RFIDNo");
                    r.Address = rfid.i("Address");
                    r.NozID = rfid.i("NozID");
                    break;
                }
            }
            return;
        }
        if (Ports.isEmpty()) {
            return;
        }
        NozSerial port = Ports.get(0);
        RFIDRunData r = port.RFIDConfigs.stream().filter(t -> t.ID == id).findFirst().orElseThrow();
        RFIDForm.UpdateOne(rfid.i("ID"), rfid.i("RFIDNo"), rfid.i("Address"), rfid.i("NozID"), "-1", "-1", "-1", M1, r.IsOnline, !r.Tag.trim().isEmpty());
        r.RFIDNo = rfid.i("RFIDNo");
        r.Address = rfid.i("Address");
        r.NozID = rfid.i("NozID");
        NormalNozStatue n = serialNoz(port, rfid.i("NozID"));
        if (n == null) {
            return;
        }
        n.accredit = 0;
        nozshow.Noz_UpdateView(Conv.toInt(n.NozID), -1, "-1", "-1", M1, M1, M1, M1, M1, -1, -1, "-1", MultiLanguage.getText("Manualauthorization"));
        Row nozBox = db().first("NormalNoz", "{NozID} = :n", p("n", Conv.toInt(n.NozID)));
        if (nozBox != null) {
            Row boxSet = db().first("Box_Set", "{Box_PortID} = :b", p("b", nozBox.get("Box_Port_ID")));
            if (boxSet != null) {
                LTNozStatuet x = lt(163, 0);
                x.SerialPort = boxSet.i("Box_PortID");
                port.LTStatue.add(x);
            }
        }
    }

    /** 1045 : texte du ticket des boîtiers (DevicePrintSet) envoyé aux boîtiers / ports. */
    private static void PrintSet() {
        if (IniInfo.WifiMode == 1) {
            Row d = db().first("DevicePrintSet");
            if (d == null) {
                throw new IllegalStateException("Print Empty");
            }
            for (IBoxClientParam box : BaseInfo.BoxClientList) {
                CmdPrint c = new CmdPrint();
                c.Count = 15;
                c.FirstIndex = d.str("DeviceFirstLine");
                c.SecondIndex = d.str("DeviceSecondLine");
                c.ThirdIndex = d.str("DeviceThirdLine");
                c.FouthIndex = d.str("DeviceFouthLine");
                c.FifthIndex = d.str("DeviceFifthLine");
                c.SixthIndex = d.str("DeviceSixthLine");
                c.NozID = d.str("DeviceNozID");
                c.Amount = d.str("DeviceAmount");
                c.Volume = d.str("DeviceVolume");
                c.Price = d.str("DevicePrice");
                c.Seventh = d.str("DeviceSevenLine");
                c.TimeIndex = d.str("DeviceTime");
                c.EighthIndex = d.str("DeviceEighthLine");
                c.NinthIndex = d.str("DeviceNinthLine");
                c.TenthIndex = d.str("DeviceTenthLine");
                box.cmdNormal.add(platCmd("14", box, 0, c));
            }
            return;
        }
        for (NozSerial port : Ports) {
            port.LTStatue.add(lt(20, 0));
        }
    }

    private static String scaled(BigDecimal v, int digits) {
        return v.multiply(pow10(digits)).setScale(0, RoundingMode.HALF_EVEN).toPlainString().replace(".", "");
    }

    /** 1046 : impression d'une transaction sur le boîtier. */
    private static void PrintTransaction(int transactionId, int portId) {
        if (IniInfo.WifiMode == 1) {
            IBoxClientParam box = boxById(portId);
            if (box != null) {
                Row t = db().first("NormalTransaction", "{ID} = :i", p("i", transactionId));
                CmdPrintTransaction c = new CmdPrintTransaction();
                c.Amount = scaled(t.dec("Amount"), IniInfo.Mradio);
                c.NozID = t.i("NozID");
                c.Price = scaled(t.dec("Price"), IniInfo.Pradio);
                c.Time = BaseFunction.StampToDateTime(t.str("CreateTime")).format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
                c.Volume = scaled(t.dec("Volume"), IniInfo.Vradio);
                c.TID = t.l("ID");
                box.cmdNormal.add(platCmd("15", box, t.i("NozID"), c));
            }
            return;
        }
        NozSerial port = portById(portId);
        if (port != null) {
            LTNozStatuet x = lt(21, 0);
            x.PreValue = BigDecimal.valueOf(transactionId);
            port.LTStatue.add(x);
        }
    }

    /** 2310 : texte du préréglage en attente (commande A1) réaffiché sur le pistolet. */
    private static void AuthMessageRefresh(int boxId) {
        IBoxClientParam box = boxById(boxId);
        if (box == null) {
            return;
        }
        CmdList c = box.cmdNormal.stream().filter(t -> "A1".equals(t.CmdType)).findFirst().orElse(null);
        if (c == null) {
            return;
        }
        CmdPreSet pre = BaseInfo.fromJson(c.CmdData, CmdPreSet.class);
        Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", pre.NozID));
        if (noz == null) {
            return;
        }
        Row boxSet = db().first("Box_Set", "{ID} = :i", p("i", noz.get("Box_Port_ID")));
        if (boxSet != null) {
            String auth = ShowPreSetValueInNoz(boxSet, pre.NozID, pre.PreSetValue, pre.PreSetType);
            box.NozList.stream().filter(t -> t.NozID == pre.NozID).findFirst().ifPresent(a -> a.AuthMessage = auth);
        }
    }

    /** DowloadFile (1282) : demande des fichiers de programme à Center (réponse non utilisée, comme l'original). */
    private static void DowloadFile() {
        try {
            for (String type : new String[] {"Communication", "LTLG", "BPro", "RFID"}) {
                CustomerRespone r = BaseFunction.post("Station/GetDowloadFile", Row.of("fileType", type));
                if (r.Result()) {
                    Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
