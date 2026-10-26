package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NormalClass.NozSerial : boîtier de pistolets relié par port série (mode Normal), protocole « F5 » / « FA »
 * chiffré. Une instance par boîtier (BoxList.ID) : lecture des trames, réponses, et file des commandes à envoyer
 * (autorisations, prix, initialisation, téléchargement de programmes…).
 *
 * Les méthodes DealLTAgreedMent, MakeCmd, GetByteBit et DealTankCable de l'original ne sont jamais appelées :
 * elles ne sont pas reprises. SendMessToMainForm (message Windows à NormalMainForm) devient MainFormMessage.
 */
public class NozSerial {

    /** TransferData(NozID, Value, Statue) : notification vers NormalMainForm. */
    public interface TransferData {
        void accept(int NozID, String Value, int Statue);
    }

    /** Message Windows (CmdType, wParam, lParam) envoyé à la fenêtre « NormalSystem ». */
    public interface MainMessage {
        void accept(int CmdType, long data1, long data2);
    }

    /** SendMessToMainForm : abonné unique (NormalMainForm.WndProc). */
    public static volatile MainMessage MainFormMessage = (a, b, c) -> { };

    private static final Charset DEFAULT = Charset.forName("GBK");
    private static final BigDecimal M1 = BigDecimal.valueOf(-1);

    private SerialClass sp = new SerialClass();
    public final List<NormalNozStatue> NozAddress = new CopyOnWriteArrayList<>();
    public final List<RFIDRunData> RFIDConfigs = new CopyOnWriteArrayList<>();
    public volatile Deque<LTNozStatuet> LTStatue = new ConcurrentLinkedDeque<>();
    public final List<LTNozStatuet> cmdAuth = new CopyOnWriteArrayList<>();
    public final List<LTNozPrice> UPNozPrice = new CopyOnWriteArrayList<>();
    public final List<NormalTankSerial> TankStatueList = new CopyOnWriteArrayList<>();
    public PLCParam pLCParam = new PLCParam();
    public PriceBorad priceBorad = new PriceBorad();
    public volatile String cmdHead = "F5";
    private final Queue<Row> transactionQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Row> calFuelEventsQueue = new ConcurrentLinkedQueue<>();
    public volatile int CommunicationBroken;
    public int SerialPortID = -1;
    public int AgreedTypeID;
    public volatile boolean PortRun;
    private final long PriceDateNow = BaseFunction.ConvertDateTimeToInt();
    public volatile int CurrentProgram = -1;
    public volatile boolean VersionCheck;
    public volatile boolean TankUpdate;
    public volatile boolean PriceUpdate;
    private volatile boolean Communicationflag;
    public int ID;
    public Row Port;
    private LegacyLog Log;
    public TransferData TransferEvent = (a, b, c) -> { };

    public NozSerial(int ID) {
        IniPort(ID);
    }

    private static Sugar db() {
        return SW.db();
    }

    private static String setLen(Object s, int n) {
        return BaseFunction.SetStrLen(String.valueOf(s), n);
    }

    private static String X2(byte b) {
        return String.format("%02X", b & 0xFF);
    }

    private static String x2(byte b) {
        return String.format("%02x", b & 0xFF);
    }

    /** Concaténation hexadécimale (majuscules) de count octets à partir de from. */
    private static String hex(byte[] bits, int from, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(X2(bits[from + i]));
        }
        return sb.toString();
    }

    private static String hexLower(byte[] bits, int from, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(x2(bits[from + i]));
        }
        return sb.toString();
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    /** Convert.ToDecimal(digits) : chiffres uniquement (pas d'exposant comme en Java). */
    private static BigDecimal digits(String s) {
        if (!s.matches("[0-9]+")) {
            throw new NumberFormatException("Input string was not in a correct format.");
        }
        return new BigDecimal(s);
    }

    /** Convert.ToDecimal(digits) / 10^n (division décimale .NET), en texte. */
    private static String divPow(String digits, int n) {
        return digits(digits).divide(BigDecimal.TEN.pow(n)).toPlainString();
    }

    private static BigDecimal pow10(int n) {
        return BigDecimal.TEN.pow(n);
    }

    /** Convert.ToDecimal(double) : décimal .NET (sans zéros superflus). */
    private static BigDecimal decD(double d) {
        return new BigDecimal(Conv.num(d));
    }

    private static BigDecimal dec(String s) {
        return new BigDecimal(s);
    }

    private static boolean ne(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) != 0;
    }

    /** Math.Round(decimal, n) : l'échelle n'est jamais augmentée. */
    private static BigDecimal round(BigDecimal d, int n) {
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static int toInt(String s) {
        return Integer.parseInt(s.trim());
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private static float toFloat(byte[] bits, int from) {
        byte[] a = new byte[4];
        System.arraycopy(bits, from, a, 0, 4);
        return NormalFunction.DealToFloat(a);
    }

    private static String num(double d) {
        return Conv.num(d);
    }

    private NormalNozStatue nozById(int nozId) {
        for (NormalNozStatue t : NozAddress) {
            if (Conv.toInt(t.NozID) == nozId) {
                return t;
            }
        }
        return null;
    }

    private static int onDuty() {
        if (IniInfo.NormalNeedDuty == 0) {
            return 1;
        }
        Row h = db().first("HandShiftReport", "ORDER BY {ID} DESC", null);
        return h != null && h.i("DealStatue") == 0 ? 1 : 0;
    }

    private NormalNozStatue nozStatue(Row noz, Row boxSet) {
        Row tank = db().first("NormalTank", "{ID} = :t", p("t", noz.get("TankID")));
        NormalNozStatue n = new NormalNozStatue(noz.i("ID"), setLen(noz.get("NozID"), 2), noz.i("TankID"), noz.i("Accredit"),
            Conv.toDecimal(noz.get("Price")), noz.i("MachineOpen"), boxSet.i("Box_DeviceType"), IniInfo.IsOnDuty, noz.i("AllowAccredit"),
            noz.i("PumpID"), IniInfo.IdentifyMode);
        if (tank != null) {
            n.GradeID = tank.i("GradeID");
            n.GradeName = BaseFunction.GetNormalGradeByID(tank.i("GradeID"));
        }
        n.Address = -(noz.i("Address") + boxSet.i("Box_PortID") * 100 + boxSet.i("Port_ID") * 10000);
        return n;
    }

    private RFIDRunData rfidRun(Row c) {
        RFIDRunData r = new RFIDRunData();
        r.ID = c.i("ID");
        r.StationID = IniInfo.StationID;
        r.StationName = IniInfo.StationName;
        r.Address = c.i("Address");
        r.Bal = BigDecimal.ZERO;
        r.Box_Port = c.i("Box_Port");
        r.CompanyID = IniInfo.CompanyID;
        r.DeviceNo = c.str("DeviceNo");
        r.IsOnline = false;
        r.NozID = c.i("NozID");
        r.RFIDNo = c.i("RFIDNo");
        r.Tag = " ";
        r.UpdateTime = now();
        r.UserName = " ";
        return r;
    }

    private void IniPort(int ID) {
        this.ID = ID;
        SerialPortID = ID;
        Row boxList = db().first("BoxList", "{ID} = :i", p("i", ID));
        if (boxList == null) {
            throw new IllegalStateException("Box Lost");
        }
        Port = Row.of("PortID", Conv.toStr(boxList.get("BoxPort")), "Baud", 115200, "Parity", 0, "StopBit", 1, "AgreementTypeID", 0, "DataBit", 8);
        Log = new LegacyLog("NormalNoz-" + Port.str("PortID"));
        SerialClass.Parity parity = switch (Port.i("Parity")) {
            case 1 -> SerialClass.Parity.Odd;
            case 2 -> SerialClass.Parity.Even;
            default -> SerialClass.Parity.None;
        };
        SerialClass.StopBits stopBits = Port.i("StopBit") == 2 ? SerialClass.StopBits.Two : SerialClass.StopBits.One;
        AgreedTypeID = Port.i("AgreementTypeID");
        sp = new SerialClass(Port.str("PortID"), Port.i("Baud"), parity, Port.i("DataBit"), stopBits);
        sp.onDataReceived(this::Sp_DataReceived);
        NozAddress.clear();
        for (Row item : db().list("NormalNoz", "{PortID} = :p AND {IsUsing} = true ORDER BY {NozID}", p("p", ID))) {
            Row boxSet = db().first("Box_Set", "{ID} = :b", p("b", item.get("Box_Port_ID")));
            if (boxSet == null) {
                continue;
            }
            IniInfo.IsOnDuty = onDuty();
            NozAddress.add(nozStatue(item, boxSet));
        }
        TankStatueList.clear();
        for (Row item : db().list("NormalTank", "{IsUsing} = true", null)) {
            NormalTankSerial normal = new NormalTankSerial();
            normal.ID = item.i("ID");
            normal.RaiseStartTime = Conv.toLong(item.get("RaiseStartTime"));
            normal.RaiseStartVolume = Conv.toDecimal(item.get("RaiseStartVolume"));
            normal.CreateTime = Conv.toLong(item.get("CreateTime"));
            normal.CurrentHeight = Conv.toDecimal(item.get("CurrentHeight"));
            normal.CurrentLevel = Conv.toDecimal(item.get("CurrentLevel"));
            normal.CurrentTemp = Conv.toDecimal(item.get("CurrentTemp"));
            normal.CurrentVolume = Conv.toDecimal(item.get("CurrentVolume"));
            normal.CurrentWater = Conv.toDecimal(item.get("CurrentWater"));
            normal.FirstRun = item.i("FirstRun");
            normal.GradeID = item.i("GradeID");
            normal.IsUsing = item.b("IsUsing");
            normal.KeepTime = item.i("KeepTime");
            normal.LowerHeight = Conv.toDecimal(item.get("LowerHeight"));
            normal.LowerTemp = Conv.toDecimal(item.get("LowerTemp"));
            normal.LowerWaterHeight = Conv.toDecimal(item.get("LowerWaterHeight"));
            normal.MaxLevel = Conv.toDecimal(item.get("MaxLevel"));
            normal.OverHeight = Conv.toDecimal(item.get("OverHeight"));
            normal.OverImportLevel = Conv.toDecimal(item.get("OverImportLevel"));
            normal.OverTemp = Conv.toDecimal(item.get("OverTemp"));
            normal.RaiseEndTime = Conv.toLong(item.get("RaiseEndTime"));
            normal.OverWaterHeight = Conv.toDecimal(item.get("OverWaterHeight"));
            normal.RaiseFlag = item.i("RaiseFlag");
            normal.TankHeight = Conv.toDecimal(item.get("TankHeight"));
            normal.RaiseLevel = Conv.toDecimal(item.get("RaiseLevel"));
            normal.TankName = item.str("TankName");
            normal.TankNo = item.str("TankNo");
            normal.TankVolume = Conv.toDecimal(item.get("TankVolume"));
            normal.UpdateTime = Conv.toLong(item.get("UpdateTime"));
            String tankError = item.str("TankError");
            if (item.get("TankError") != null) {
                int[] e = alarmBits(tankError);
                normal.TL = e[0];
                normal.CG = e[1];
                normal.Ttemp = e[2];
                normal.TLevel = e[3];
                normal.EG = e[4];
            }
            Row cal = db().first("TankCableCalculate", "{TankID} = :t ORDER BY {CreateTime} DESC", p("t", item.get("ID")));
            if (cal != null) {
                normal.CalculateHeight = Conv.toDecimal(cal.get("CurrentHeight"));
                normal.CalculateVolume = Conv.toDecimal(cal.get("CurrentVolume"));
                normal.CalculateTime = Conv.toLong(cal.get("CreateTime"));
            }
            List<Integer> nozIds = NozAddress.stream().filter(t -> t.TankID == normal.ID).map(t -> Conv.toInt(t.NozID)).toList();
            BigDecimal sum = BigDecimal.ZERO;
            if (normal.CreateTime != 0L && !nozIds.isEmpty()) {
                sum = Conv.toDecimal(db().scalar(db().sql("select coalesce(sum({NormalTransaction.Volume}), 0) from [NormalTransaction]"
                    + " where {NormalTransaction.CreateTime} >= :c and {NormalTransaction.NozID} in (:n)"), p("c", normal.CalculateTime, "n", nozIds),
                    BigDecimal.class));
            }
            normal.SumCalculateVolume = sum;
            TankStatueList.add(normal);
        }
        List<Integer> boxPorts = db().list("Box_Set", "{Port_ID} = :p", p("p", ID)).stream().map(r -> r.i("ID")).toList();
        RFIDConfigs.clear();
        if (!boxPorts.isEmpty()) {
            for (Row c : db().list("RFIDConfig", "{Box_Port} IN (:b)", p("b", boxPorts))) {
                RFIDConfigs.add(rfidRun(c));
            }
        }
        pLCParam = new PLCParam();
        pLCParam.MW28Data = new int[16];
        pLCParam.MB10Data = new int[16];
        pLCParam.MB5Data = new int[16];
        pLCParam.Error = new int[100];
        pLCParam.Statue = -1;
        Row plc = db().first("PLCParamData");
        if (plc != null) {
            copyPlc(plc, pLCParam);
            List<Integer> errors = new ArrayList<>();
            readBits(plc.str("MW28Data"), pLCParam.MW28Data, 100, errors);
            readBits(plc.str("MB10Data"), pLCParam.MB10Data, 300, errors);
            readBits(plc.str("MB5Data"), pLCParam.MB5Data, 200, errors);
            if (!errors.isEmpty()) {
                pLCParam.Error = errors.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        priceBorad.Statue = -1;
        try {
            String[] error = {""};
            if (!sp.openPort(error)) {
                throw new IllegalStateException(error[0]);
            }
            Log.writeLog("Open " + Port.str("PortID") + " Successed!");
            if (!PortRun) {
                PortRun = true;
                start(this::SendData, "NozSerial-send-" + ID);
                start(this::DealBoxTransactionThread, "NozSerial-transaction-" + ID);
                start(this::DealBoxCalEventThread, "NozSerial-cal-" + ID);
            }
        } catch (RuntimeException ex) {
            PortRun = false;
            Log.writeLog("Open Failed:" + ex.getMessage());
        }
    }

    private static void start(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
    }

    private static void readBits(String data, int[] target, int base, List<Integer> errors) {
        for (int j = 0; j < data.length() && j < target.length; j++) {
            target[j] = Integer.parseInt(String.valueOf(data.charAt(j)));
            if (target[j] != 0) {
                errors.add(base + j);
            }
        }
    }

    private static final String[] PLC_VALUES = {"A_PistonCurrent", "B_PistonCurrent", "InTake_Pressure", "Low_Pressure", "Instrument_Air_Pressure",
        "Medium_Pressure", "Gas_Concentration_Inside_the_Sled_First", "Hydraulic_Oil_Temperature", "High_Pressure", "Protection_Pressure",
        "Water_Flow_Temperature", "Gas_Concentration_Second", "Gas_Concentration_Third"};

    private static BigDecimal[] plcValues(PLCParam p) {
        return new BigDecimal[] {p.A_PistonCurrent, p.B_PistonCurrent, p.InTake_Pressure, p.Low_Pressure, p.Instrument_Air_Pressure, p.Medium_Pressure,
            p.Gas_Concentration_Inside_the_Sled_First, p.Hydraulic_Oil_Temperature, p.High_Pressure, p.Protection_Pressure, p.Water_Flow_Temperature,
            p.Gas_Concentration_Second, p.Gas_Concentration_Third};
    }

    private static void setPlcValues(PLCParam p, BigDecimal[] v) {
        p.A_PistonCurrent = v[0];
        p.B_PistonCurrent = v[1];
        p.InTake_Pressure = v[2];
        p.Low_Pressure = v[3];
        p.Instrument_Air_Pressure = v[4];
        p.Medium_Pressure = v[5];
        p.Gas_Concentration_Inside_the_Sled_First = v[6];
        p.Hydraulic_Oil_Temperature = v[7];
        p.High_Pressure = v[8];
        p.Protection_Pressure = v[9];
        p.Water_Flow_Temperature = v[10];
        p.Gas_Concentration_Second = v[11];
        p.Gas_Concentration_Third = v[12];
    }

    private static void copyPlc(Row from, PLCParam to) {
        BigDecimal[] v = new BigDecimal[PLC_VALUES.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = Conv.toDecimal(from.get(PLC_VALUES[i]));
        }
        setPlcValues(to, v);
    }

    /** Bits d'alarme d'une cuve (texte binaire sur 8 caractères) : TL, CG, Ttemp, TLevel, EG. */
    private static int[] alarmBits(String error) {
        int tl = 0;
        int cg = 0;
        int ttemp = 0;
        int tlevel = 0;
        int eg = 0;
        for (int i = 1; i < error.length(); i++) {
            if (error.charAt(i) == '1') {
                switch (i) {
                    case 7 -> tl = 7;
                    case 6 -> cg = 6;
                    case 5 -> ttemp = 5;
                    case 4 -> ttemp = 4;
                    case 3 -> tlevel = 3;
                    case 2 -> tlevel = 2;
                    case 1 -> eg = 1;
                    default -> {
                    }
                }
            }
        }
        return new int[] {tl, cg, ttemp, tlevel, eg};
    }

    public boolean PortUpdateStatue(boolean run, int ID, String[] Error) {
        Error[0] = "";
        try {
            IniPort(ID);
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** Arrêt du boîtier (PortRun = false) : le fil d'envoi ferme le port. */
    public void Stop() {
        PortRun = false;
    }

    private void Sp_DataReceived(byte[] bits) {
        try {
            String text = SerialClass.byteToHexStr(bits);
            Log.writeLog("Receive:" + text);
            if (AgreedTypeID != 0) {
                return;
            }
            while (text.length() > 0) {
                String text2;
                String text3;
                if (text.startsWith("F5")) {
                    cmdHead = "F5";
                    text3 = text.substring(2, 6);
                    int num = Integer.parseInt(text.substring(2, 6), 16) * 2 + 4;
                    text2 = text.substring(0, num);
                    text = text.substring(num);
                } else if (text.startsWith("FA")) {
                    cmdHead = "FA";
                    text3 = text.substring(14, 18);
                    int num2 = Integer.parseInt(text.substring(14, 18), 16) * 2 + 4;
                    text2 = text.substring(0, num2);
                    text = text.substring(num2);
                } else {
                    break;
                }
                if (text2.isEmpty()) {
                    break;
                }
                byte[] array = SerialClass.strToToHexByte(text2);
                byte[] crc = {array[array.length - 2], array[array.length - 1]};
                if (!CRC16(array).equals(SerialClass.byteToHexStr(crc))) {
                    throw new IllegalStateException("CRC Error");
                }
                if (cmdHead.equals("FA")) {
                    text3 = setLen(String.format("%02x", Integer.parseInt(text3, 16) - 6), 4);
                    text2 = text2.substring(0, 2) + text3 + text2.substring(18);
                    array = SerialClass.strToToHexByte(text2);
                }
                byte[] outdata = BaseFunction.DealCmdSecret(array);
                if (outdata != null) {
                    DealLTAgreedMentSecond(outdata);
                }
            }
        } catch (RuntimeException ex) {
            Log.writeLog("Error:" + ex.getMessage());
        }
    }

    private boolean DealAuthorize(int NozID, int Statue, NormalNozStatue NZ) {
        try {
            if (NZ.IsOnDuty == 0) {
                return false;
            }
            if (NZ.TurnNowStatue != 0) {
                return false;
            }
            if ((Statue & 0x20) >> 5 == 1) {
                return false;
            }
            NormalNozStatue n = nozById(NozID);
            if (n == null) {
                return false;
            }
            if (NZ.AllowAccredit == 1) {
                return false;
            }
            if (n.accredit == 1) {
                if (LTStatue.stream().anyMatch(t -> t.NozID == NozID && t.Statue == 161)) {
                    return true;
                }
                Log.writeLog("Deal Auth Price:" + n.Price.toPlainString());
                LTNozStatuet lt = new LTNozStatuet();
                lt.NozID = NozID;
                lt.Statue = 161;
                lt.PrePrice = n.Price;
                lt.PreType = n.Price.compareTo(BigDecimal.ONE) > 0 ? 0 : 1;
                lt.PreValue = M1;
                LTStatue.add(lt);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private NormalNozStatue GetNozStatue(String NozID) {
        String key = BaseFunction.SetStrLen(NozID, 2);
        for (NormalNozStatue item : NozAddress) {
            if (key.equals(item.NozID)) {
                return item;
            }
        }
        return null;
    }

    private boolean NozCardIsRun(RFIDRunData RFID, String Tag) {
        for (NormalNozStatue item : NozAddress) {
            if (Tag.equals(item.CardNo)) {
                if ((item.View_Statue == 0 || item.View_Statue == -1 || item.View_Statue == 4)
                    && RFIDConfigs.stream().noneMatch(t -> "true".equals(t.IsWork) && Tag.equals(t.Tag))) {
                    item.CardNo = "";
                }
                return true;
            }
        }
        return false;
    }

    private int GetTankStatue(String TankNo) {
        for (int i = 0; i < TankStatueList.size(); i++) {
            if (BaseFunction.SetStrLen(TankStatueList.get(i).TankNo, 2).equals(TankNo)) {
                return i;
            }
        }
        return -1;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void DealBoxTransactionThread() {
        while (PortRun) {
            try {
                List<Row> list = new ArrayList<>();
                Row normal = transactionQueue.poll();
                if (normal != null) {
                    Log.writeLog("Noz" + normal.get("NozID") + ":Search");
                    Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", normal.get("NozID")));
                    if (noz == null) {
                        throw new NullPointerException("Object reference not set to an instance of an object.");
                    }
                    list.add(Row.of("StationID", IniInfo.StationID, "NozID", normal.get("NozID"), "TrueTime", now(), "GradeID", noz.get("GradeID"),
                        "Amount", round(Conv.toDecimal(normal.get("Amount")), IniInfo.Mradio), "Price", Conv.toDecimal(normal.get("Price")), "Volume",
                        Conv.toDecimal(normal.get("Volume")), "VolumeTotal", Conv.toDecimal(normal.get("VolumeTotal")), "AmountTotal",
                        Conv.toDecimal(normal.get("AmountTotal")), "AmountDigits", IniInfo.Mradio, "DeviceNo", "", "SeverCreateTime",
                        BaseFunction.ConvertDateTimeToInt(), "CompanyID", IniInfo.CompanyID, "OrderNumber", normal.get("OrderNo"), "PaymentID",
                        normal.get("PayMent"), "DealStatue", 0, "Mileage", normal.get("Mileage")));
                }
                if (!list.isEmpty()) {
                    db().insertAll("UnDealTransaction", list);
                }
            } catch (RuntimeException ex) {
                Log.writeLog("transaction Error:" + ex.getMessage());
            }
            sleep(500);
        }
    }

    private void DealBoxCalEventThread() {
        while (PortRun) {
            try {
                Row calEvent = calFuelEventsQueue.poll();
                if (calEvent != null) {
                    String max = "select coalesce(max({CalFuelEvent.CalIndex}), 0) from [CalFuelEvent] where {CalFuelEvent.TankID} = :t";
                    int num = Conv.toInt(db().scalar(db().sql(max), p("t", calEvent.get("TankID")), Integer.class));
                    int open = Conv.toInt(db().scalar(db().sql(max + " and {CalFuelEvent.CalComplete} = 0"), p("t", calEvent.get("TankID")),
                        Integer.class));
                    if (open == 0) {
                        num++;
                    }
                    db().insert("CalFuelEvent", Row.of("NozID", calEvent.get("NozID"), "TankID", calEvent.get("TankID"), "StartHeight",
                        calEvent.get("StartHeight"), "EndHeight", calEvent.get("EndHeight"), "StartTime", calEvent.get("StartTime"), "EndTime",
                        calEvent.get("EndTime"), "CalIndex", num, "CalComplete", 0, "Volume", calEvent.get("Volume")));
                }
            } catch (RuntimeException ex) {
                Log.writeLog("calFuelEventsQueue Error:" + ex.getMessage());
            }
            sleep(500);
        }
    }

    /** Commande 161 de la file : retirée après traitement ; Auth_Statue remis à false (sauf si elle manque dans la file). */
    public void DealQueue(int NozID, int Statue) {
        try {
            if (!LTStatue.isEmpty()) {
                LTNozStatuet item = LTStatue.stream().filter(t -> t.NozID == NozID && t.Statue == Statue).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Sequence contains no elements"));
                LTStatue.remove(item);
            }
            if (Statue == 161) {
                GetNozStatue(String.valueOf(NozID)).Auth_Statue = false;
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public boolean NozIsRaise(int NozID) {
        return GetNozStatue(String.valueOf(NozID)).RaiseNoz == 1;
    }

    public int GetNozAuth(int NozID) {
        NormalNozStatue n = GetNozStatue(String.valueOf(NozID));
        return n == null ? 0 : n.accredit;
    }

    // ------------------------------------------------------------ trames du boîtier

    /** Pistolet décodé d'une trame A0 / 10 (statut et, si présentes, les valeurs de la transaction). */
    private static final class NozFrame {
        String volume = "-1";
        String amount = "-1";
        String price = "-1";
        String volumeTotal = "-1";
        String amountTotal = "-1";
    }

    private static final DateTimeFormatter REPLY_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private void DealLTAgreedMentSecond(byte[] bits) {
        try {
            Log.writeLog("Real Data:" + SerialClass.byteToHexStr(bits));
            Communicationflag = true;
            CommunicationBroken = 0;
            boolean flag = false;
            String text = "";
            StringBuilder text2 = new StringBuilder();
            int num = (u(bits[1]) << 8) | u(bits[2]);
            String text3 = hex(bits, 3, 4);
            int num2 = 0;
            switch (u(bits[7])) {
                case 163 -> Log.writeLog("A3 Deal");
                case 160 -> {
                    text = "A0";
                    Log.writeLog("Get A0");
                    flag = frameA0(bits, num, text2) || flag;
                }
                case 10 -> text = "0A";
                case 11 -> {
                    Log.writeLog("Time");
                    text = "0B";
                    text2.append(LocalDateTime.now().format(REPLY_TIME));
                }
                case 14 -> {
                    Log.writeLog("Version Program");
                    byte[] array = new byte[10];
                    System.arraycopy(bits, 8, array, 0, 10);
                    String version = new String(array, DEFAULT).replace("\0", "");
                    Log.writeLog("Version Program:" + version);
                    flag = true;
                    if (VersionCheck) {
                        TransferEvent.accept(ID, version, 1280);
                        VersionCheck = false;
                    }
                    if (bits.length >= 21) {
                        int empVersion = u(bits[18]);
                        Log.writeLog("EMP Version Program:" + empVersion);
                        Row sv = db().first("StationVersion");
                        if (sv != null && empVersion != sv.i("EmpVersion")) {
                            LTNozStatuet lt = new LTNozStatuet();
                            lt.Statue = 22;
                            lt.PreValue = BigDecimal.valueOf(sv.i("EmpVersion"));
                            LTStatue.add(lt);
                        }
                    }
                }
                case 13 -> {
                    if (CurrentProgram != -1) {
                        text = "0D";
                        int index = (u(bits[8]) << 8) | u(bits[9]);
                        Log.writeLog("Dowloa Index:" + index);
                        NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(CurrentProgram, index);
                        if (!pg.ok()) {
                            if (!pg.Error().equals("Finished")) {
                                throw new IllegalStateException("Dowload Error" + index + ": Message:" + pg.Error());
                            }
                            CurrentProgram = -1;
                            flag = true;
                        }
                        SendMessToMainForm(1793, pg.TotalPage(), index);
                        text2.append(setLen(String.format("%02x", pg.TotalPage()), 4)).append(setLen(String.format("%02x", index), 4))
                            .append(pg.ProgramData());
                        Log.writeLog("Dowload befor " + index + ":" + pg.ProgramData());
                        Log.writeLog("Dowload befor NozStatueReturn" + index + ":" + text2);
                    }
                }
                case 2 -> {
                    int TankID = Integer.parseInt(String.valueOf(u(bits[8])), 16);
                    int index = ((u(bits[9]) << 8) | u(bits[10])) + 1;
                    NormalTankSerial tank = TankStatueList.stream().filter(t -> Conv.toInt(t.TankNo) == TankID).findFirst().orElse(null);
                    if (tank != null) {
                        String cable = BaseFunction.DowloadTankCable(tank.ID, index, new int[2]);
                        text2.append(BaseFunction.SetStrLen(tank.TankNo, 2)).append(cable);
                    } else {
                        Log.writeLog("Dow Cable Empty");
                    }
                }
                case 16 -> {
                    Log.writeLog("Got 10");
                    text = "10";
                    flag = frame10(bits, num, text2) || flag;
                }
                case 15 -> {
                    Log.writeLog("Got 0F");
                    text = "0F";
                    frame0F(bits, text2);
                }
                case 31 -> {
                    Log.writeLog("Got 1F");
                    text = "1F";
                    frame1F(bits, text2);
                }
                case 17 -> {
                    Log.writeLog("Client 11");
                    flag = true;
                    frame11(bits, num);
                }
                case 18 -> {
                    Log.writeLog("Client 12");
                    text = "12";
                    flag = frame12(bits, text2) || flag;
                }
                case 22 -> {
                    Log.writeLog("0x16 Deal");
                    text = "16";
                    int page = (u(bits[8]) << 8) | u(bits[9]);
                    if (page != 0) {
                        text2.append(employeeCards(page));
                    }
                }
                default -> {
                }
            }
            if (!flag) {
                String body = text2.toString();
                String text35 = "F5" + setLen(String.format("%04X", body.length() / 2 + 8), 4) + text3 + text + body + "0000";
                if (cmdHead.equals("FA")) {
                    text35 = cmdHead + setLen(String.format("%04X", body.length() / 2 + 14), 4) + text3 + text + body + "0000";
                }
                Log.writeLog("Real Reply:" + text35);
                String[] outdata = {""};
                boolean ok = BaseFunction.DealCmdEncry(text35, text3, outdata);
                String out = outdata[0];
                if (cmdHead.equals("FA")) {
                    out = cmdHead + LocalDateTime.now().format(REPLY_TIME) + out.substring(2);
                }
                if (!ok) {
                    throw new IllegalStateException("Encry Error");
                }
                out = out.substring(0, out.length() - 4) + CRC16(SerialClass.strToToHexByte(out));
                byte[] reply = SerialClass.strToToHexByte(out);
                Log.writeLog("Reply To Box: " + out);
                if (CurrentProgram == -1 || u(bits[7]) == 13) {
                    sp.sendbyte(reply, 0, reply.length);
                }
            }
            Communicationflag = false;
        } catch (RuntimeException ex) {
            Communicationflag = false;
            Log.writeLog("Second Analysis Error:" + ex.getMessage());
        }
    }

    /** Valeurs d'une transaction (volume, montant, prix, totaux) lues à l'offset donné d'une trame A0. */
    private static NozFrame readValues(byte[] bits, int at) {
        NozFrame f = new NozFrame();
        f.volume = divPow(hex(bits, at, 4), IniInfo.Vradio);
        f.amount = divPow(hex(bits, at + 4, 4), IniInfo.Mradio);
        f.price = digits(hex(bits, at + 8, 3)).divide(pow10(IniInfo.Pradio)).toPlainString();
        f.volumeTotal = divPow(hex(bits, at + 11, 6), IniInfo.Vradio);
        f.amountTotal = divPow(hex(bits, at + 17, 6), IniInfo.Mradio);
        return f;
    }

    private Row transaction(int nozId, NozFrame f, String orderNo) {
        Row r = Row.of("NozID", nozId, "Volume", dec(f.volume), "Amount", dec(f.amount), "Price", dec(f.price), "AmountTotal", dec(f.amountTotal),
            "VolumeTotal", dec(f.volumeTotal), "PayMent", 998);
        if (orderNo != null) {
            r.put("OrderNo", orderNo);
        }
        return r;
    }

    /** Trame A0 (ancien format) : statuts des pistolets (bloc 1) et des cuves (bloc 2). */
    private boolean frameA0(byte[] bits, int num, StringBuilder text2) {
        boolean flag = false;
        int num82 = 0;
        while (num82 + 10 < num) {
            int type = u(bits[8 + num82]);
            int len = (u(bits[9 + num82]) << 8) | u(bits[10 + num82]);
            if (type == 1) {
                int k = 0;
                while (k < len) {
                    int nozId = u(bits[11 + k + num82]);
                    NormalNozStatue nz = GetNozStatue(String.valueOf(nozId));
                    if (nz == null) {
                        Log.writeLog("Get Noz Error");
                        break;
                    }
                    String raise = "0";
                    int b2 = u(bits[12 + k + num82]);
                    int fueling = (b2 & 2) >> 1;
                    int pause = (b2 & 4) >> 2;
                    int done = (b2 & 8) >> 3;
                    int online = (b2 & 0x80) >> 7;
                    int up = b2 & 1;
                    int locked = (b2 & 0x20) >> 5;
                    NozFrame f = new NozFrame();
                    if (fueling == 1 || done == 1 || pause == 1) {
                        f = readValues(bits, 13 + k + num82);
                        if (ne(nz.SetPrice, dec(f.price))) {
                            LTNozStatuet lt = new LTNozStatuet();
                            lt.NozID = Conv.toInt(nz.NozID);
                            lt.PrePrice = nz.SetPrice;
                            lt.Statue = 162;
                            LTStatue.add(lt);
                        }
                        k += 23;
                        nz.Volume = dec(f.volume);
                        nz.Amont = dec(f.amount);
                        nz.VolumeTotal = dec(f.volumeTotal);
                        nz.AmountTotal = dec(f.amountTotal);
                    }
                    k += 2;
                    Log.writeLog("Noz " + nozId + " : Statue:" + Integer.toBinaryString(b2));
                    if (up == 0) {
                        raise = "1";
                        nz.RaiseNoz = 0;
                    } else {
                        raise = "0";
                        Log.writeLog("Deal Auth");
                        if (nz.RaiseNoz == 0) {
                            flag = DealAuthorize(nozId, b2, nz);
                            nz.RaiseNoz = 1;
                        }
                    }
                    if (fueling == 0) {
                        raise = "0";
                        if (nz.MachineOpen == 1) {
                            nz.MachineOpen = 0;
                        }
                    } else {
                        raise = "3";
                        if (nz.MachineOpen != 1) {
                            nz.MachineOpen = 1;
                        }
                    }
                    if (done == 0) {
                        if (nz.RecordUpload == 1) {
                            nz.RecordUpload = 0;
                        }
                    } else if (nz.RecordUpload != 1) {
                        Log.writeLog("DealBoxTransaction");
                        nz.RecordUpload = 1;
                        nz.CardNo = "";
                        transactionQueue.add(transaction(nozId, f, null));
                        nz.Auth_Statue = false;
                        b2 &= 0xF7;
                    }
                    nz.NozStatue = b2;
                    int view = 0;
                    if (online == 0) {
                        nz.RaiseNoz = -1;
                        view = 4;
                    } else if (fueling == 1) {
                        view = 3;
                    } else if (up == 1) {
                        view = 1;
                    }
                    String auth = locked == 0 && !nz.Auth_Statue ? "" : "-1";
                    nz.View_Statue = view;
                    if (PriceUpdate && BaseFunction.ConvertDateTimeToInt() - 5000 > PriceDateNow) {
                        PriceUpdate = false;
                    }
                    text2.append(String.format("%02x", pause == 1 ? 1 : done == 1 ? 2 : 0));
                    String value = nz.View_Statue + "|" + raise + "|" + f.volume + "|" + f.amount + "|" + f.price + "|" + f.volumeTotal + "|"
                        + f.amountTotal + "|" + auth + "|" + nz.GradeName;
                    TransferEvent.accept(nozId, value, 1033);
                }
            } else if (type == 2) {
                int count = len / 18;
                for (int i = 0; i < count; i++) {
                    int base = 11 + num82 + i * 18;
                    int tankNo = u(bits[base]);
                    String error = BaseFunction.SetStrLen(Integer.toBinaryString(u(bits[base + 1])), 8);
                    double h = Conv.round(toFloat(bits, base + 2), 3);
                    double v = Conv.round(toFloat(bits, base + 6), 3);
                    double w = Conv.round(toFloat(bits, base + 10), 3);
                    double t = Conv.round(toFloat(bits, base + 14), 3);
                    int idx = GetTankStatue(BaseFunction.SetStrLen(String.valueOf(tankNo), 2));
                    long now = BaseFunction.ConvertDateTimeToInt();
                    if (idx != -1) {
                        NormalTankSerial tank = TankStatueList.get(idx);
                        tank.CurrentHeight = decD(h);
                        tank.CurrentVolume = decD(v);
                        tank.CurrentWater = decD(w);
                        tank.CurrentTemp = decD(t);
                        if (now - tank.UpdateTime > 3600000) {
                            tank.UpdateTime = now;
                            tank.IsUsing = true;
                        }
                        DealTankError(tank, error);
                    }
                    TransferEvent.accept(tankNo, num(h) + "|" + num(v) + "|" + num(w) + "|" + num(t), 1049);
                }
            }
            num82 = num82 + len + 3;
        }
        return flag;
    }

    /** Trame 10 : blocs pistolets (1), cuves (2), RFID (3), version (4), afficheur de prix (5, 12), cuves du port 5 (6), PLC (7). */
    private boolean frame10(byte[] bits, int num, StringBuilder text2) {
        boolean flag = false;
        int num2 = 0;
        int off = 0;
        while (off + 10 < num) {
            int type = u(bits[8 + off]);
            int len = (u(bits[9 + off]) << 8) | u(bits[10 + off]);
            switch (type) {
                case 1 -> flag = block10Noz(bits, off, len, text2) || flag;
                case 2 -> num2 += block10Tank(bits, off, len, num2, text2);
                case 3 -> block10Rfid(bits, off, len, text2);
                case 4 -> {
                    int n = u(bits[9 + off]);
                    len = n - 1;
                    text2.append("01");
                }
                case 5, 12 -> {
                    Log.writeLog("Price:");
                    if (priceBorad != null) {
                        int st = u(bits[13 + off]);
                        priceBorad.Statue = (st & 0x80) >> 7;
                        priceBorad.UpdateTime = BaseFunction.ConvertDateTimeToInt();
                        text2.append((st & 4) >> 2 == 1 ? "01" : "00");
                    }
                }
                case 6 -> {
                    Log.writeLog("Port 5 Tank");
                    int count = u(bits[9 + off]);
                    int size = u(bits[10 + off]);
                    len = count * size;
                    num2 += block10Port5Tank(bits, off, count, size, num2, text2);
                }
                case 7 -> len = block10Plc(bits, off, text2);
                default -> {
                }
            }
            off = off + len + 3;
        }
        return flag;
    }

    private boolean block10Noz(byte[] bits, int off, int len, StringBuilder text2) {
        boolean flag = false;
        int k = 0;
        int shortLen = u(bits[11 + off + k]);
        int longLen = u(bits[12 + off + k]);
        k += 2;
        while (k < len) {
            int nozId = u(bits[11 + k + off]);
            NormalNozStatue NS = GetNozStatue(String.valueOf(nozId));
            String raise = "0";
            int b = u(bits[12 + k + off]);
            int fueling = (b & 2) >> 1;
            int pause = (b & 4) >> 2;
            int done = (b & 8) >> 3;
            int online = (b & 0x80) >> 7;
            int up = b & 1;
            int locked = (b & 0x20) >> 5;
            if (NS == null) {
                Log.writeLog("Get Noz Error");
                k += fueling != 1 && done != 1 && pause != 1 ? shortLen : longLen;
                continue;
            }
            NozFrame f = new NozFrame();
            String orderNo = "";
            if (fueling == 1 || done == 1 || pause == 1) {
                orderNo = hex(bits, 13 + k + off, 4);
                f = readValues(bits, 17 + k + off);
                Log.writeLog("Volume:" + f.volume);
                Log.writeLog("Amount:" + f.amount);
                NS.Price = dec(f.price);
                NS.Volume = dec(f.volume);
                NS.Amont = dec(f.amount);
                NS.VolumeTotal = dec(f.volumeTotal);
                NS.AmountTotal = dec(f.amountTotal);
                k += longLen;
            } else {
                k += shortLen;
            }
            Log.writeLog("Noz " + nozId + " : Statue:" + Integer.toBinaryString(b));
            NS.LastRaiseNoz = NS.RaiseNoz;
            if (up == 0) {
                raise = "1";
                NS.RaiseNoz = 0;
            } else {
                raise = "0";
                if (NS.RaiseNoz == 0 && NS.accredit != 2 && fueling != 1 && done != 1) {
                    Log.writeLog("Deal Auth");
                    flag = DealAuthorize(nozId, b, NS);
                    NS.RaiseNoz = 1;
                }
            }
            if (fueling == 0) {
                raise = "0";
                if (NS.MachineOpen == 1) {
                    NS.MachineOpen = 0;
                }
            } else {
                raise = "3";
                if (NS.MachineOpen != 1) {
                    NS.MachineOpen = 1;
                }
            }
            if (done == 0) {
                if (NS.RecordUpload == 1) {
                    NS.RecordUpload = 0;
                }
            } else if (NS.RecordUpload != 1) {
                Log.writeLog("DealBoxTransaction");
                NS.RecordUpload = 1;
                NS.CardNo = "";
                transactionQueue.add(transaction(nozId, f, orderNo));
                NormalTankSerial tank = TankStatueList.stream().filter(t -> t.ID == NS.TankID).findFirst().orElse(null);
                if (tank != null) {
                    calFuelEventsQueue.add(Row.of("NozID", nozId, "TankID", tank.ID, "StartHeight", NS.startHeight, "EndHeight", tank.CurrentHeight,
                        "StartTime", NS.StartTime, "EndTime", BaseFunction.ConvertDateTimeToInt(), "Volume", dec(f.volume)));
                }
                NS.Auth_Statue = false;
                b &= 0xF7;
            }
            NS.NozStatue = b;
            int view = 0;
            if (online == 0) {
                NS.RaiseNoz = -1;
                view = 4;
            } else if (fueling == 1) {
                view = 3;
                if (NS.LastStatue != 3) {
                    NormalTankSerial tank = TankStatueList.stream().filter(t -> t.ID == NS.TankID).findFirst().orElse(null);
                    if (tank != null) {
                        NS.startHeight = tank.CurrentHeight;
                        NS.StartTime = BaseFunction.ConvertDateTimeToInt();
                    }
                }
            } else if (up == 1) {
                view = 1;
            }
            String auth = locked == 0 && !NS.Auth_Statue ? "" : "-1";
            NS.LastStatue = NS.View_Statue;
            NS.View_Statue = view;
            if (PriceUpdate && BaseFunction.ConvertDateTimeToInt() - 5000 > PriceDateNow) {
                PriceUpdate = false;
            }
            text2.append(String.format("%02x", pause == 1 ? 1 : done == 1 ? 2 : 0));
            if (NS.LastRaiseNoz != 0 && NS.RaiseNoz == 0) {
                Log.writeLog("Set 0");
                NS.CardNo = "";
                NS.Bal = BigDecimal.ZERO;
                NS.UserName = "";
                NS.CarNo = "";
            }
            if (NS.View_Statue == 3) {
                NormalFunction.PumpNozData.put(NS.pumpid, NS);
            }
            String value = NS.View_Statue + "|" + raise + "|" + f.volume + "|" + f.amount + "|" + f.price + "|" + f.volumeTotal + "|" + f.amountTotal
                + "|" + auth + "|" + NS.GradeName + "|" + NS.pumpid;
            TransferEvent.accept(nozId, value, 1033);
        }
        return flag;
    }

    /** Mesures d'une cuve : alarmes, montée (livraison), historique, notification. */
    private void tankMeasure(int idx, BigDecimal h, BigDecimal v, BigDecimal w, BigDecimal t, String error, int historyIndex) {
        int[] e = alarmBits(error);
        long now = BaseFunction.ConvertDateTimeToInt();
        NormalTankSerial tank = TankStatueList.get(idx);
        if (tank.OverImportLevel.signum() != 0) {
            DealTankRaise(idx, h, v, w, t);
        }
        boolean changed = ne(tank.CurrentHeight, h) || ne(tank.CurrentVolume, v) || ne(tank.CurrentWater, w) || ne(tank.CurrentTemp, t);
        if (tank.TL != e[0] || tank.EG != e[4] || tank.CG != e[1] || tank.Ttemp != e[2] || tank.TLevel != e[3]) {
            RealTankChange c = new RealTankChange();
            c.TankID = tank.ID;
            c.StationID = IniInfo.StationID;
            c.CompanyID = IniInfo.CompanyID;
            c.TankName = tank.TankName;
            c.UpdateTime = now;
            c.TL = e[0];
            c.EG = e[4];
            c.CG = e[1];
            c.TLevel = e[3];
            c.Ttemp = e[2];
            NormalFunction.TankAlarmList.add(c);
            tank.TL = e[0];
            tank.EG = e[4];
            tank.CG = e[1];
            tank.Ttemp = e[2];
            tank.TLevel = e[3];
            db().exec(db().sql("NormalTank", "update [NormalTank] set {TankError} = :e where {ID} = :i"), p("e", error, "i", tank.ID));
        }
        tank.CurrentHeight = h;
        tank.CurrentVolume = v;
        tank.CurrentWater = w;
        tank.CurrentTemp = t;
        if (now - tank.UpdateTime > 3600000) {
            tank.UpdateTime = now;
            tank.IsUsing = true;
        }
        if (changed) {
            // Comme l'original : l'historique reprend la cuve à la position du bloc dans la trame
            NormalTankSerial hist = TankStatueList.get(historyIndex);
            db().insert("NormalTankHistory", Row.of("TankID", hist.ID, "TankNo", hist.TankNo, "TankName", hist.TankName, "CreateTime", now, "Upload", 0,
                "CurrentHeight", hist.CurrentHeight, "CurrentVolume", hist.CurrentVolume, "CurrentTemp", hist.CurrentTemp, "CurrentWaterHeight",
                hist.CurrentWater));
        }
        DealTankError(tank, error);
    }

    private int block10Tank(byte[] bits, int off, int len, int num2, StringBuilder text2) {
        int count = len / 18;
        for (int i = 0; i < count; i++) {
            int base = 11 + off + i * 18;
            int tankNo = u(bits[base]) + num2;
            String Error = BaseFunction.SetStrLen(Integer.toBinaryString(u(bits[base + 1])), 8);
            Log.writeLog("TankError:" + Error);
            double h = Conv.round(toFloat(bits, base + 2), 3);
            double v = Conv.round(toFloat(bits, base + 6), 3);
            double w = Conv.round(toFloat(bits, base + 10), 3);
            double t = Conv.round(toFloat(bits, base + 14), 3);
            if (h == 0.0 && v == 0.0 && w == 0.0 && t == 0.0) {
                continue;
            }
            int idx = GetTankStatue(BaseFunction.SetStrLen(String.valueOf(tankNo), 2));
            if (idx != -1) {
                tankMeasure(idx, decD(h), decD(v), decD(w), decD(t), Error, i);
            }
            TransferEvent.accept(tankNo, num(h) + "|" + num(v) + "|" + num(w) + "|" + num(t), 1049);
        }
        text2.append("01");
        return count;
    }

    private int block10Port5Tank(byte[] bits, int off, int count, int size, int num2, StringBuilder text2) {
        for (int i = 0; i < count; i++) {
            int base = 11 + off + i * size;
            int tankNo = u(bits[base]) + num2;
            if (i >= TankStatueList.size()) {
                continue;
            }
            String TankError = BaseFunction.SetStrLen(Integer.toBinaryString(u(bits[base + 1])), 8);
            Log.writeLog("TankError:" + TankError);
            BigDecimal h = decD(Conv.round(toFloat(bits, base + 2), 1));
            BigDecimal v = decD(Conv.round(toFloat(bits, base + 6), 1));
            BigDecimal w = decD(Conv.round(toFloat(bits, base + 10), 1));
            BigDecimal t = decD(Conv.round(toFloat(bits, base + 14), 1));
            Log.writeLog("oHeight:" + h.toPlainString());
            Log.writeLog("oVolume:" + v.toPlainString());
            Log.writeLog("wHeight:" + w.toPlainString());
            if (h.signum() == 0 && v.signum() == 0 && w.signum() == 0 && t.signum() == 0) {
                continue;
            }
            int idx = GetTankStatue(BaseFunction.SetStrLen(String.valueOf(tankNo), 2));
            if (idx != -1) {
                tankMeasure(idx, h, v, w, t, TankError, i);
            }
            TransferEvent.accept(tankNo, h.toPlainString() + "|" + v.toPlainString() + "|" + w.toPlainString() + "|" + t.toPlainString(), 1049);
            TankUpdate = true;
        }
        text2.append("01");
        return count;
    }

    private void block10Rfid(byte[] bits, int off, int len, StringBuilder text2) {
        Log.writeLog("RFID");
        int k = 0;
        int shortLen = u(bits[11 + off + k]);
        int longLen = u(bits[12 + off + k]);
        k += 2;
        while (k < len) {
            int RFIDNo = u(bits[11 + off + k]);
            int st = u(bits[12 + off + k]);
            int online = (st & 0x80) >> 7;
            int tagRead = (st & 8) >> 3;
            int tagLeft = (st & 4) >> 2;
            int skip = tagRead != 1 ? shortLen : longLen;
            RFIDRunData RFID = RFIDConfigs.stream().filter(t -> t.RFIDNo == RFIDNo).findFirst().orElse(null);
            if (RFID == null) {
                text2.append("10");
                k += skip;
                continue;
            }
            RFID.IsOnline = online != 0;
            NormalNozStatue noz = nozById(RFID.NozID);
            NormalNozStatue NS2 = noz == null ? null : GetNozStatue(noz.NozID);
            if (noz == null || NS2 == null || NS2.AllowAccredit == 1) {
                text2.append("20");
                k += skip;
                continue;
            }
            if (tagRead == 1 || tagLeft == 1) {
                String tag = hexLower(bits, 13 + off + k, 10);
                String password = hexLower(bits, 23 + off + k, 2);
                String mileage = "000000";
                if (longLen >= 17) {
                    mileage = hexLower(bits, 25 + off + k, 3);
                }
                Log.writeLog("RFID No:" + RFIDNo);
                Log.writeLog("mileage:" + mileage);
                String[] returnMessage = {""};
                if (tag.startsWith("000000003")) {
                    Log.writeLog("Tag:" + tag);
                    DealScore(tag, noz, returnMessage);
                } else {
                    String oldRFIDTag = tag;
                    String carNumber = "";
                    int insertCmdAuth = 0;
                    if (IniInfo.IdentifyMode == 1) {
                        LTNozStatuet lt = cmdAuth.stream().filter(t -> t.NozID == Conv.toInt(NS2.NozID) && t.Statue == 161).findFirst().orElse(null);
                        if (lt != null && lt.CardNo != null && lt.CardNo.length() >= 20 && !lt.CardNo.startsWith("000000000") && tag.startsWith("000000000")
                            && !tag.equals("00000000000000000000")) {
                            tag = lt.CardNo;
                            insertCmdAuth = 1;
                            carNumber = lt.CarNo;
                        }
                    }
                    int res = DealRFIDTag(RFID, NS2, tag, password, mileage, insertCmdAuth, oldRFIDTag, carNumber, returnMessage);
                    if (insertCmdAuth == 1 && res == 0) {
                        LTNozStatuet lt = cmdAuth.stream().filter(t -> t.NozID == Conv.toInt(NS2.NozID) && t.Statue == 161).findFirst().orElse(null);
                        if (lt != null) {
                            LTStatue.add(lt);
                            cmdAuth.remove(lt);
                        }
                    }
                }
                text2.append(returnMessage[0]);
                k += longLen;
            } else {
                if (NS2.View_Statue == 3) {
                    long rest = round(NS2.Bal.subtract(NS2.Amont), 3).multiply(pow10(IniInfo.Mradio)).setScale(0, RoundingMode.HALF_EVEN).longValue();
                    if (rest < 0) {
                        rest = 0L;
                    }
                    text2.append("10").append(setLen(IniInfo.Mradio, 2)).append(setLen(String.format("%02X", rest), 10));
                    RFID.Tag = NS2.CardNo;
                    RFID.UserName = NS2.UserName;
                    RFID.CarNo = NS2.CarNo;
                    RFID.Bal = NS2.Bal.subtract(NS2.Amont);
                    String v = RFID.ID + "|-1|-1|-1|" + NS2.CardNo + "|" + NS2.UserName + "|" + NS2.CarNo + "|" + round(RFID.Bal, IniInfo.Mradio).toPlainString()
                        + "|" + bool(RFID.IsOnline) + "|true";
                    Log.writeLog("RFID Data 1:" + v);
                    TransferEvent.accept(RFID.ID, v, 1537);
                } else {
                    String v = RFID.ID + "|-1|-1|-1|" + NS2.CardNo + "|" + NS2.UserName + "|" + NS2.CarNo + "|" + round(RFID.Bal, IniInfo.Mradio).toPlainString()
                        + "|" + bool(RFID.IsOnline) + "|false";
                    Log.writeLog("RFID Data 2:" + v);
                    TransferEvent.accept(RFID.ID, v, 1537);
                    text2.append("00");
                    RFID.Tag = NS2.CardNo;
                    RFID.UserName = NS2.UserName;
                    RFID.CarNo = NS2.CarNo;
                }
                k += shortLen;
            }
            RFID.UpdateTime = now();
        }
    }

    /** bool.ToString() de .NET. */
    private static String bool(boolean b) {
        return b ? "True" : "False";
    }

    /** Bloc PLC : 13 mesures puis trois registres d'alarme (MW28, MB5, MB10) ; renvoie la longueur du bloc. */
    private int block10Plc(byte[] bits, int off, StringBuilder text2) {
        Log.writeLog("PLC:");
        int blockLen = u(bits[10 + off]);
        int st = u(bits[11 + off]);
        Log.writeLog("PLCStatue:" + st);
        boolean changed = false;
        pLCParam.Statue = (st & 0x80) >> 7;
        BigDecimal[] values = new BigDecimal[13];
        for (int j = 0; j < 13; j++) {
            values[j] = decD(Conv.round(toFloat(bits, 12 + off + j * 4), 1));
        }
        BigDecimal[] old = plcValues(pLCParam);
        for (int j = 0; j < 13; j++) {
            if (ne(old[j], values[j])) {
                changed = true;
            }
        }
        setPlcValues(pLCParam, values);
        int mw28 = (u(bits[12 + off + 53]) << 8) | u(bits[12 + off + 52]);
        int mb5 = (u(bits[12 + off + 55]) << 8) | u(bits[12 + off + 54]);
        int mb10 = (u(bits[12 + off + 57]) << 8) | u(bits[12 + off + 56]);
        String t12 = NormalFunction.SetLength(Integer.toBinaryString(mw28), 16);
        String t13 = NormalFunction.SetLength(Integer.toBinaryString(mb5), 16);
        String t14 = NormalFunction.SetLength(Integer.toBinaryString(mb10), 16);
        changed = plcAlarms("MW28", t12, pLCParam.MW28Data) || changed;
        changed = plcAlarms("MB5", t13, pLCParam.MB5Data) || changed;
        changed = plcAlarms("MB10", t14, pLCParam.MB10Data) || changed;
        if (changed) {
            List<Integer> errors = new ArrayList<>();
            readBits(t12, pLCParam.MW28Data, 100, errors);
            readBits(t14, pLCParam.MB10Data, 300, errors);
            readBits(t13, pLCParam.MB5Data, 200, errors);
            pLCParam.Error = errors.isEmpty() ? new int[100] : errors.stream().mapToInt(Integer::intValue).toArray();
            Row data = db().first("PLCParamData", "{StationID} = :s AND {CompanyID} = :c", p("s", IniInfo.StationID, "c", IniInfo.CompanyID));
            Row row = data != null ? data : new Row();
            for (int j = 0; j < PLC_VALUES.length; j++) {
                row.put(PLC_VALUES[j], plcValues(pLCParam)[j]);
            }
            row.put("MW28Data", NormalFunction.SetLength(t12, 16));
            row.put("MB5Data", NormalFunction.SetLength(t13, 16));
            row.put("MB10Data", NormalFunction.SetLength(t14, 16));
            row.put("UpdateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now(java.time.ZoneOffset.UTC)));
            row.put("StationID", IniInfo.StationID);
            row.put("CompanyID", IniInfo.CompanyID);
            // Comme l'original : une nouvelle ligne est insérée même quand la ligne existe
            row.remove("ID");
            db().insert("PLCParamData", row);
        }
        TransferEvent.accept(0, Wire.json(pLCParam), 1793);
        text2.append("01");
        return blockLen;
    }

    private boolean plcAlarms(String region, String bitsText, int[] current) {
        boolean changed = false;
        for (int k = 0; k < bitsText.length(); k++) {
            int bit = bitsText.charAt(k) - '0';
            if (bit != current[k]) {
                NormalFunction.DealPLCAlarm(IniInfo.StationID, IniInfo.CompanyID, region, k, bit, new String[1]);
                current[k] = bit;
                changed = true;
            }
        }
        return changed;
    }

    /** Math.Round(decimal.Parse(s) / 10^n, n) */
    private static BigDecimal parsed(String s, int n) {
        return round(digits(s).divide(pow10(n)), n);
    }

    private static boolean isDecimal(String s) {
        return s.matches("[0-9]+");
    }

    private static String trueTime(byte[] bits, int at) {
        return "20" + X2(bits[at]) + "-" + X2(bits[at + 1]) + "-" + X2(bits[at + 2]) + " " + X2(bits[at + 3]) + ":" + X2(bits[at + 4]) + ":"
            + X2(bits[at + 5]);
    }

    /** Trame 0F : transactions faites hors ligne (une par pistolet) ; réponse = numéro du lot si toutes sont acceptées. */
    private boolean frame0F(byte[] bits, StringBuilder text2) {
        int batch = u(bits[8]);
        int count = u(bits[9]);
        int size = u(bits[10]);
        boolean all = true;
        for (int i = 0; i < count; i++) {
            int base = i * size;
            int nozID = u(bits[11 + base]);
            NormalNozStatue nozStatue = GetNozStatue(String.valueOf(nozID));
            String s = hex(bits, 12 + base, 4);
            String s2 = hex(bits, 16 + base, 4);
            String s3 = hex(bits, 20 + base, 3);
            String s4 = hex(bits, 23 + base, 6);
            String s5 = hex(bits, 29 + base, 6);
            String timeStr = trueTime(bits, 35 + base);
            String order = "00000000";
            String cardNo = "000000000000";
            if (size > 30) {
                order = hex(bits, 41 + base, 4);
            }
            if (size > 34) {
                cardNo = hex(bits, 45 + base, 6);
            }
            boolean ok = isDecimal(s) && isDecimal(s2) && isDecimal(s3) && isDecimal(s4) && isDecimal(s5);
            boolean saved = ok && DealBoxTransaction_BoxOffline(nozID, parsed(s, IniInfo.Vradio), parsed(s2, IniInfo.Mradio), parsed(s3, IniInfo.Pradio),
                parsed(s5, IniInfo.Mradio), parsed(s4, IniInfo.Vradio), timeStr, order, cardNo, nozStatue);
            all = ok && saved && all;
        }
        if (all) {
            text2.append(String.format("%02X", batch));
        }
        return all;
    }

    /** Trame 1F : lot de transactions hors ligne (enregistrées directement dans UnDealTransaction). */
    private void frame1F(byte[] bits, StringBuilder text2) {
        int batch = (u(bits[8]) << 8) | u(bits[9]);
        int count = u(bits[10]);
        int size = u(bits[11]);
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int base = i * size;
            int NozID = u(bits[12 + base]);
            NormalNozStatue nozStatue2 = GetNozStatue(String.valueOf(NozID));
            if (nozStatue2 == null) {
                Log.writeLog("NozID Error:" + NozID);
                Log.writeLog("i:" + i);
                continue;
            }
            String s6 = hex(bits, 13 + base, 4);
            String s7 = hex(bits, 17 + base, 4);
            String s8 = hex(bits, 21 + base, 3);
            String s9 = hex(bits, 24 + base, 6);
            String s10 = hex(bits, 30 + base, 6);
            String time = trueTime(bits, 36 + base);
            String orderNumber = "00000000";
            String card = "000000000000";
            if (size > 30) {
                orderNumber = hex(bits, 42 + base, 4);
            }
            if (size > 34) {
                card = hex(bits, 46 + base, 6);
            }
            BigDecimal r6 = isDecimal(s6) ? parsed(s6, IniInfo.Vradio) : BigDecimal.ZERO;
            BigDecimal r7 = isDecimal(s7) ? parsed(s7, IniInfo.Mradio) : BigDecimal.ZERO;
            BigDecimal r8 = isDecimal(s8) ? parsed(s8, IniInfo.Pradio) : BigDecimal.ZERO;
            BigDecimal r9 = isDecimal(s9) ? parsed(s9, IniInfo.Vradio) : BigDecimal.ZERO;
            BigDecimal r10 = isDecimal(s10) ? parsed(s10, IniInfo.Mradio) : BigDecimal.ZERO;
            NormalNozStatue n = nozById(NozID);
            if (n == null) {
                Log.writeLog("Noz Lost");
                continue;
            }
            list.add(Row.of("StationID", IniInfo.StationID, "NozID", NozID, "TrueTime", time, "GradeID", nozStatue2.GradeID, "Amount",
                round(r7, IniInfo.Mradio), "Price", r8, "Volume", r6, "VolumeTotal", r9, "TType", n.accredit == 2 ? 53 : 54, "AmountTotal",
                round(r10, IniInfo.Mradio), "AmountDigits", IniInfo.Mradio, "DeviceNo", "", "SeverCreateTime", BaseFunction.ConvertDateTimeToInt(),
                "CompanyID", IniInfo.CompanyID, "OrderNumber", orderNumber, "PaymentID", n.accredit == 2 ? 18 : 999, "DealStatue", 0, "CardNo",
                "00000000" + card));
        }
        if (DealBoxTransaction_BoxOfflineList(list)) {
            text2.append(setLen(String.format("%02X", batch), 4));
        }
    }

    /** Trame 11 : versions des appareils d'un port du boîtier (lecteurs RFID ou pistolets). */
    private void frame11(byte[] bits, int num) {
        int port = u(bits[8]);
        Row BoxSet = db().first("Box_Set", "{Box_PortID} = :p AND {Port_ID} = :i", p("p", port, "i", ID));
        if (BoxSet != null) {
            int type = BoxSet.i("Box_CommunicationTypeID");
            boolean rfid = type == 6;
            boolean noz = type == 1 || type == 2 || type == 3 || type == 4 || type == 7 || type == 10;
            if (rfid || noz) {
                int k = 0;
                while (9 + k < num) {
                    int deviceId = u(bits[9 + k]);
                    k++;
                    byte[] array = new byte[10];
                    System.arraycopy(bits, 9 + k, array, 0, 10);
                    k += 10;
                    String version = new String(array, DEFAULT).replace("\0", "").trim();
                    Row r = rfid ? db().first("RFIDConfig", "{RFIDNo} = :d", p("d", deviceId))
                        : db().first("NormalNoz", "{Box_Port_ID} = :b AND {NozID} = :d", p("b", BoxSet.get("ID"), "d", deviceId));
                    if (r != null) {
                        r.put("Version", version);
                        db().update(rfid ? "RFIDConfig" : "NormalNoz", r);
                    }
                }
            }
        }
        TransferEvent.accept(0, port + "|" + ID, 1282);
    }

    /** Programme à télécharger pour un port du boîtier : 1 = lecteur RFID, 2 / 3 selon la version des pistolets. */
    private int programFor(Row boxPort, int defaultType) {
        int type = boxPort.i("Box_CommunicationTypeID");
        if (type == 6) {
            return 1;
        }
        if (type == 1 || type == 2 || type == 3 || type == 4 || type == 7 || type == 10) {
            Row noz = db().first("NormalNoz", "{Box_Port_ID} = :b ORDER BY {NozID}", p("b", boxPort.get("ID")));
            if (noz == null) {
                return -2;
            }
            String v = noz.str("Version").substring(0, 2);
            return v.equals("06") ? 2 : v.equals("11") ? 3 : defaultType;
        }
        return defaultType;
    }

    private static String fileName20(String fileName) {
        String t = "20202020202020202020" + BaseFunction.StringConvertToAscii(fileName);
        return t.substring(t.length() - 20);
    }

    /** Trame 12 : page suivante du programme d'un appareil. */
    private boolean frame12(byte[] bits, StringBuilder text2) {
        boolean flag = false;
        int port = u(bits[8]);
        int index = (u(bits[9]) << 8) | u(bits[10]);
        Log.writeLog("Dowload Program:" + CurrentProgram + "; Index:" + index);
        Row boxPort = db().first("Box_Set", "{Box_PortID} = :p AND {Port_ID} = :i", p("p", port, "i", ID));
        if (boxPort == null) {
            throw new NullPointerException("Object reference not set to an instance of an object.");
        }
        int program = programFor(boxPort, -1);
        if (program < 0) {
            return false;
        }
        NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(program, index);
        if (!pg.ok()) {
            if (!pg.Error().equals("Finished")) {
                throw new IllegalStateException("Dowload Program Error：" + pg.Error());
            }
            flag = true;
        }
        if (program != 1) {
            Log.writeLog("programIndex: " + program);
            Log.writeLog("Index:" + index);
            Log.writeLog("Data:" + pg.ProgramData());
        }
        text2.append(setLen(port, 2)).append(fileName20(pg.FileName())).append(setLen(String.format("%02x", pg.TotalPage()), 4))
            .append(setLen(String.format("%02x", index), 4)).append(pg.ProgramData());
        return flag;
    }

    /** Liste des cartes employé actives (10 par page) pour les trames 16. */
    private String employeeCards(int page) {
        Sugar.Page pg = db().page("EmployeeCardInfos", "{Status} = '0'", null, "{ASN}", page, 10);
        StringBuilder cards = new StringBuilder();
        for (Row item : pg.rows()) {
            cards.append(item.str("ASN").substring(8));
        }
        int totalPage = pg.totalPage();
        int empVersion = 0;
        Row sv = db().first("StationVersion");
        if (sv != null) {
            empVersion = sv.i("EmpVersion");
        }
        return setLen(String.format("%02x", empVersion % 255), 2) + setLen(String.format("%02x", totalPage), 4) + setLen(String.format("%02x", page), 4)
            + cards;
    }

    private void DealTankRaise(int Index, BigDecimal oHeight, BigDecimal oVolume, BigDecimal wHeight, BigDecimal Tep) {
        try {
            NormalTankSerial t = TankStatueList.get(Index);
            t.CurrentLevel = t.CurrentVolume;
            t.CurrentVolume = oVolume;
            if (t.RaiseFlag == 0) {
                Log.writeLog("Raise Start:");
                t.FirstRun = 0;
                if (t.CurrentVolume.compareTo(t.CurrentLevel) > 0) {
                    t.RaiseStartVolume = t.CurrentLevel;
                    t.RaiseStartTime = BaseFunction.ConvertDateTimeToInt();
                    t.RaiseFlag = 1;
                    t.RaiseLevel = t.RaiseLevel.add(t.CurrentVolume.subtract(t.CurrentLevel));
                    t.MaxLevel = oVolume;
                    t.KeepTime = 0;
                }
            } else {
                if (t.CurrentVolume.compareTo(t.MaxLevel) > 0) {
                    t.RaiseLevel = t.RaiseLevel.add(t.CurrentVolume.subtract(t.MaxLevel));
                    t.KeepTime = 0;
                    t.MaxLevel = oVolume;
                } else {
                    t.KeepTime++;
                }
                if (t.KeepTime > 60) {
                    if (t.RaiseLevel.compareTo(t.OverImportLevel) > 0) {
                        long now = BaseFunction.ConvertDateTimeToInt();
                        db().insert("NormalTankStock", Row.of("TankID", t.ID, "TankName", t.TankName, "GradeName", BaseFunction.GetNormalGradeByID(t.GradeID),
                            "StartTime", t.RaiseStartTime, "EndTime", now, "StartVolume", t.RaiseStartVolume, "FinalVolume", oVolume, "StockVolume",
                            t.RaiseLevel, "CreateTime", now, "Upload", false));
                        Log.writeLog("Raise End:");
                        BigDecimal v = BaseFunction.RaiseTankVolume(t.ID, t.CurrentHeight, t.CurrentVolume, new String[1]);
                        if (v.compareTo(M1) != 0) {
                            t.CalculateVolume = v;
                        }
                    }
                    t.RaiseLevel = BigDecimal.ZERO;
                    t.RaiseStartTime = 0L;
                    t.RaiseFlag = 0;
                    t.RaiseStartVolume = BigDecimal.ZERO;
                    t.KeepTime = 0;
                }
            }
            if (t.FirstRun == 0 && t.RaiseLevel.compareTo(t.OverImportLevel) > 0) {
                t.FirstRun = 1;
                Row obj = SW.dto().make("NormalTankStock", "TankID", t.ID, "TankName", t.TankName, "GradeName", BaseFunction.GetNormalGradeByID(t.GradeID),
                    "StartTime", t.RaiseStartTime, "StartVolume", t.RaiseStartVolume, "ID", IniInfo.StationID);
                BaseFunction.CreateTankCable(t.ID, new String[1]);
                BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UploadNormalTankImportStart", Wire.json(obj));
            }
        } catch (RuntimeException ex) {
            Log.writeLog("Raise Error:" + ex.getMessage());
        }
    }

    private void DealScore(String RFIDTag, NormalNozStatue Noz, String[] Message) {
        Message[0] = "";
        try {
            Log.writeLog("Score");
            int NozID = Conv.toInt(Noz.NozID);
            Holder cus = BaseFunction.GetCustomerMess(RFIDTag);
            if (!cus.ok()) {
                Message[0] = "04";
                throw new IllegalStateException(cus.error());
            }
            Row card = cus.b();
            Row tr = db().first("NormalTransaction", "{NozID} = :n ORDER BY {CreateTime} DESC", p("n", NozID));
            if (tr == null) {
                throw new NullPointerException("Object reference not set to an instance of an object.");
            }
            String score = setLen(String.format("%02x", Conv.toInt(card.get("Score"))), 8);
            if (tr.i("ScoreDeal") == 1) {
                Message[0] = "";
                return;
            }
            if (tr.i("ScoreDeal") == 2) {
                Message[0] = "20" + score;
                return;
            }
            Row obj = SW.dto().make("StationScoreUpdate", "StationID", IniInfo.StationID, "CardNo", RFIDTag, "GradeID", tr.get("GradeID"), "Volume",
                tr.get("Volume"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateScore", Wire.json(obj));
            if (r.Result()) {
                Log.writeLog("Score Deal Success");
                tr.put("ScoreDeal", 1);
                db().update("NormalTransaction", tr);
                Log.writeLog("old:" + Conv.toStr(card.get("Score")));
                Log.writeLog("new:" + r.Mess());
                Row back = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                card.put("Score", Conv.toInt(back.get("Score")));
                db().update("CustomerCardInfos", card);
                tr.put("ScoreDeal", 2);
                db().update("NormalTransaction", tr);
                Log.writeLog("newS:" + Conv.toStr(card.get("Score")));
                Message[0] = "20" + setLen(String.format("%02x", Conv.toInt(card.get("Score"))), 8);
                return;
            }
            Log.writeLog("Score Deal Failed");
            Message[0] = "20" + score;
            throw new IllegalStateException("Score Update Error");
        } catch (RuntimeException ex) {
            Log.writeLog("Score Error:" + ex.getMessage());
        }
    }

    /** Cumul des transactions (montant, volume, nombre) depuis la mise à jour du compte ou du groupe. */
    private SumTransaction sums(String where, Object value, long since) {
        Row r = db().queryFirst(db().sql("select coalesce(sum({NormalTransaction.Amount}), 0) AS SumAmount, coalesce(sum({NormalTransaction.Volume}), 0)"
            + " AS SumVolume, count({NormalTransaction.ID}) AS SumCount from [NormalTransaction] where " + where
            + " and {NormalTransaction.CreateTime} > :s"), p("v", value, "s", since));
        SumTransaction s = new SumTransaction();
        s.SumAmount = Conv.toDecimal(r.get("SumAmount"));
        s.SumVolume = Conv.toDecimal(r.get("SumVolume"));
        s.SumCount = Conv.toInt(r.get("SumCount"));
        return s;
    }

    /** Convert.ToInt64(Math.Round(Math.Round(x, 3) * 10^Mradio)) sur des doubles. */
    private static long scaled(double x) {
        return (long) Math.rint(Conv.round(x, 3) * Math.pow(10.0, IniInfo.Mradio));
    }

    private LTNozStatuet auth(Row order, int dealCode, String tag, String carNo, BigDecimal bal, int preType) {
        LTNozStatuet lt = new LTNozStatuet();
        lt.Statue = 161;
        lt.PreType = preType;
        lt.NozID = Conv.toInt(order.get("NozID"));
        lt.PreValue = Conv.toDecimal(order.get("PreSetValue"));
        lt.PrePrice = Conv.toDecimal(order.get("PreSetPrice"));
        lt.DealCode = dealCode;
        lt.CardNo = tag;
        lt.CarNo = carNo;
        lt.Bal = bal;
        return lt;
    }

    private void removeLastAuth(NormalNozStatue NS) {
        LTNozStatuet lt = LTStatue.stream().filter(t -> t.NozID == Conv.toInt(NS.NozID) && t.Statue == 161 && t.DealCode != 0)
            .max(Comparator.comparingInt(t -> t.DealCode)).orElse(null);
        if (lt != null) {
            LTStatue.remove(lt);
        }
    }

    private int DealRFIDTag(RFIDRunData RFID, NormalNozStatue NS, String Tag, String Password, String mileage, int InsertCmdAuth, String oldRFIDTag,
                            String RFIDCarNumber, String[] returnMessage) {
        int num = 0;
        returnMessage[0] = "";
        Row customerCardInfos = null;
        Row customerAccounts = null;
        try {
            Password = Password.toUpperCase().replace("F", "");
            Log.writeLog("Get RFID Tag:" + Tag);
            if (NormalFunction.NetErrorCount > IniInfo.OverCount) {
                num = 2;
                returnMessage[0] = "04";
                throw new IllegalStateException("NetWork Broken");
            }
            if (NS.IsOnDuty == 0) {
                num = 2;
                returnMessage[0] = "F9";
                throw new IllegalStateException("Need Onduty");
            }
            if (Tag.equals("00000000000000000000")) {
                returnMessage[0] = "40";
                if (IniInfo.IdentifyMode == 0) {
                    removeLastAuth(NS);
                } else {
                    LTNozStatuet lt = cmdAuth.stream().filter(t -> t.NozID == Conv.toInt(NS.NozID) && t.Statue == 161).findFirst().orElse(null);
                    if (lt != null) {
                        cmdAuth.remove(lt);
                    } else {
                        removeLastAuth(NS);
                    }
                }
                NS.Bal = BigDecimal.ZERO;
                NS.CardNo = "";
                NS.CarNo = "";
                NS.UserName = "";
                RFID.IsWork = "false";
                TransferEvent.accept(RFID.ID, RFID.ID + "|-1|-1|-1||||-1|" + bool(RFID.IsOnline) + "|true", 1537);
                NS.Auth_Statue = false;
                throw new IllegalStateException("RFID Cancel");
            }
            long num2 = 0L;
            BigDecimal num3 = BigDecimal.ZERO;
            String text = "";
            String text2 = "";
            SumTransaction sum = new SumTransaction();
            BigDecimal limitHigh = NS.Box_DeviceType == 0 ? BigDecimal.valueOf(900000) : BigDecimal.valueOf(90000000);
            long limitLow = NS.Box_DeviceType == 0 ? 900000L : 90000000L;
            String bin9 = Tag.substring(0, 9);
            if (InsertCmdAuth == 1) {
                Holder emp = BaseFunction.GetEmpMess(oldRFIDTag);
                if (!emp.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new IllegalStateException(emp.error());
                }
                if (!"0".equals(emp.b().str("Status"))) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new IllegalStateException("Statue Error");
                }
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (!cus.ok()) {
                    // Comme l'original : client introuvable → référence nulle
                    throw new NullPointerException("Object reference not set to an instance of an object.");
                }
                Row customers = cus.a();
                customerCardInfos = cus.b();
                customerAccounts = cus.c();
                if (bin9.equals("000000004")) {
                    text = RFIDCarNumber;
                    text2 = customers.str("Name");
                    num2 = limitLow;
                    num3 = limitHigh;
                } else {
                    Row group = db().first("Group", "{GroupID} = :g", p("g", customerCardInfos.get("GroupID")));
                    if (group == null) {
                        num = 2;
                        returnMessage[0] = "F7";
                        throw new IllegalStateException("Statue Error");
                    }
                    if (group.i("GroupType") == 1) {
                        num3 = Conv.toDecimal(group.get("Balance"));
                        Log.writeLog("Group UpdateDatetimelong:" + Conv.toStr(customerAccounts.get("UpdateDatetime")));
                        sum = sums("{NormalTransaction.CardGroup} = :v", group.get("GroupID"), Conv.toLong(group.get("UpdateTime")));
                        Log.writeLog("oil Sum:" + Wire.json(sum));
                        num2 = scaled(Conv.toDouble(customerAccounts.get("Balance")) - sum.SumAmount.doubleValue());
                        text = customers.str("VehicleNo");
                        text2 = customers.str("Name");
                        if (num2 > limitLow) {
                            num2 = limitLow;
                        }
                        if (num3.compareTo(limitHigh) > 0) {
                            num3 = limitHigh;
                        }
                    } else {
                        num3 = Conv.toDecimal(customerAccounts.get("Balance"));
                        Log.writeLog("UpdateDatetimelong:" + Conv.toStr(customerAccounts.get("UpdateDatetime")));
                        sum = sums("{NormalTransaction.CardNo} = :v", Tag, BaseFunction.ConvertDateTimeToInt(Conv.toDateTime(customerAccounts.get("UpdateDatetime"))));
                        Log.writeLog("oil Sum:" + Wire.json(sum));
                        num2 = scaled(Conv.toDouble(customerAccounts.get("Balance")) - sum.SumAmount.doubleValue());
                        text = customers.str("VehicleNo");
                        text2 = customers.str("Name");
                    }
                }
            } else if (bin9.equals("000000004")) {
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (!cus.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new IllegalStateException(cus.error());
                }
                customerCardInfos = cus.b();
                customerAccounts = cus.c();
                if (customerCardInfos.i("Suspend") != 0) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new IllegalStateException("Statue Error");
                }
                num = checkLimits(customerCardInfos, NS, returnMessage);
                Row carNumberGroup = db().first("CarNumberGroup", "{CardDigits} = :d", p("d", Password));
                if (carNumberGroup == null) {
                    num = 2;
                    returnMessage[0] = "02";
                    throw new IllegalStateException("Password Error");
                }
                text = carNumberGroup.str("CarNumber");
                text2 = cus.a().str("Name");
                num2 = limitLow;
                num3 = limitHigh;
            } else if (!bin9.equals("000000000")) {
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (!cus.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new IllegalStateException(cus.error());
                }
                customerCardInfos = cus.b();
                customerAccounts = cus.c();
                String cipher = customerCardInfos.str("CipherFlg");
                if (!cipher.isEmpty() && !cipher.equals(Password)) {
                    num = 2;
                    returnMessage[0] = "02";
                    throw new IllegalStateException("Password Error");
                }
                if (customerCardInfos.i("Suspend") != 0) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new IllegalStateException("Statue Error");
                }
                num = checkLimits(customerCardInfos, NS, returnMessage);
                Row group = db().first("Group", "{GroupID} = :g", p("g", customerCardInfos.get("GroupID")));
                if (group == null) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new IllegalStateException("Statue Error");
                }
                Row customers3 = cus.a();
                if (group.i("GroupType") == 1) {
                    num3 = Conv.toDecimal(group.get("Balance"));
                    Log.writeLog("Group UpdateDatetimelong:" + Conv.toStr(group.get("UpdateTime")));
                    Log.writeLog("Group UpdateDatetimelong:" + Conv.toStr(group.get("UpdateTime")));
                    sum = sums("{NormalTransaction.CardGroup} = :v", group.get("GroupID"), Conv.toLong(group.get("UpdateTime")));
                    Log.writeLog("oil Sum:" + Wire.json(sum));
                    num2 = scaled(Conv.toDecimal(group.get("Balance")).subtract(sum.SumAmount).doubleValue());
                    text = customers3.str("VehicleNo");
                    text2 = customers3.str("Name");
                    if (num2 > limitLow) {
                        num2 = limitLow;
                    }
                } else {
                    num3 = Conv.toDecimal(customerAccounts.get("Balance"));
                    Log.writeLog("UpdateDatetimelong:" + Conv.toStr(customerAccounts.get("UpdateDatetime")));
                    LocalDateTime since = Conv.toDateTime(customerAccounts.get("UpdateDatetime"));
                    sum = sums("{NormalTransaction.CardNo} = :v", Tag, BaseFunction.ConvertDateTimeToInt(since));
                    BigDecimal salerTemp = Conv.toDecimal(db().scalar(db().sql("select coalesce(sum({OilSalerTemp.Amount}), 0) from [OilSalerTemp]"
                        + " where {OilSalerTemp.CardNumber} = :c and {OilSalerTemp.Operater} = 4 and {OilSalerTemp.UpdateTime} > :u"),
                        p("c", Tag, "u", since), BigDecimal.class));
                    sum.SumAmount = sum.SumAmount.add(salerTemp);
                    Log.writeLog("oil Sum:" + Wire.json(sum));
                    num2 = scaled(Conv.toDouble(customerAccounts.get("Balance")) - sum.SumAmount.doubleValue());
                    text = customers3.str("VehicleNo");
                    text2 = customers3.str("Name");
                }
            } else {
                Holder emp = BaseFunction.GetEmpMess(Tag);
                customerAccounts = emp.c();
                if (!emp.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new IllegalStateException(emp.error());
                }
                if (!"0".equals(emp.b().str("Status"))) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new IllegalStateException("Statue Error");
                }
                num2 = limitLow;
                num3 = limitHigh;
            }
            BigDecimal num4 = BigDecimal.ZERO;
            // Comme l'original : Substring(0, 4) n'est jamais égal à « 000000004 »
            if (!bin9.equals("000000000")) {
                if (num2 <= 0) {
                    num = 8;
                    returnMessage[0] = "08";
                    throw new IllegalStateException("Bal Lost");
                }
                num4 = num3.subtract(sum.SumAmount);
                if (num4.signum() <= 0) {
                    num = 8;
                    returnMessage[0] = "08";
                    throw new IllegalStateException("Bal Lost");
                }
                if (NozCardIsRun(RFID, Tag) && InsertCmdAuth == 0) {
                    num = 2;
                    returnMessage[0] = "FA";
                    throw new IllegalStateException("Card is Using");
                }
            }
            String text3 = "20202020202020202020" + BaseFunction.StringConvertToAscii(text);
            String text4 = "202020202020202020202020202020" + BaseFunction.StringConvertToAscii(text2);
            returnMessage[0] = "01" + text4.substring(text4.length() - 30) + text3.substring(text3.length() - 20) + setLen(IniInfo.Mradio, 2)
                + setLen(String.format("%02x", num2), 10);
            RFID.IsWork = "true";
            Log.writeLog("RFID Success:" + returnMessage[0]);
            int mil = Integer.parseInt(mileage, 16);
            if (!bin9.equals("000000000") && !bin9.equals("000000004")) {
                NormalFunction.RFIDLimit lim = NormalFunction.GetRFIDLimit(customerCardInfos, customerAccounts, NS, sum);
                Log.writeLog("Limit:" + lim.PreSetValue().toPlainString());
                if (!lim.ok()) {
                    num = Integer.parseInt(lim.res());
                    returnMessage[0] = String.format("%02x", num);
                    throw new IllegalStateException(lim.Error());
                }
                if (InsertCmdAuth == 0) {
                    Row order = Row.of("NozID", Conv.toInt(NS.NozID), "PreSetPrice", NS.Price, "PreSetType", lim.PreSetType(), "PreSetValue",
                        lim.PreSetValue(), "AuthType", true, "Bal", num4, "CardNo", Tag, "CarNo", text, "StationID", IniInfo.StationID, "Mileage", mil);
                    int dealCode = Conv.toInt(db().insert("CmdOrder", order));
                    LTNozStatuet lt = auth(order, dealCode, Tag, text, num4, lim.PreSetType());
                    if (NS.IdentifyMode == 1) {
                        cmdAuth.removeIf(t -> t.NozID == Conv.toInt(NS.NozID));
                        cmdAuth.add(lt);
                    } else {
                        LTStatue.add(lt);
                    }
                }
                NS.Bal = num4;
                NS.CardNo = Tag;
                NS.CarNo = text;
                NS.UserName = text2;
                TransferEvent.accept(RFID.ID, RFID.ID + "|-1|-1|-1|" + Tag + "|" + text2 + "|" + text + "|-1|" + bool(RFID.IsOnline) + "|true", 1537);
            } else {
                num4 = BigDecimal.valueOf(num2);
                Log.writeLog("Get RFID Tag:" + Tag);
                if (InsertCmdAuth == 0) {
                    Row order = Row.of("NozID", Conv.toInt(NS.NozID), "PreSetPrice", NS.Price, "PreSetType", 0, "PreSetValue", M1, "AuthType", true, "Bal",
                        BigDecimal.valueOf(num2).divide(pow10(IniInfo.Mradio)), "CardNo", Tag, "CarNo", text, "StationID", IniInfo.StationID, "Mileage", mil);
                    int dealCode = Conv.toInt(db().insert("CmdOrder", order));
                    LTNozStatuet lt = auth(order, dealCode, Tag, text, num4, 0);
                    if (bin9.equals("000000004") && NS.IdentifyMode == 1) {
                        cmdAuth.removeIf(t -> t.NozID == Conv.toInt(NS.NozID));
                        cmdAuth.add(lt);
                    } else {
                        LTStatue.add(lt);
                    }
                }
                NS.Bal = num4.divide(pow10(IniInfo.Mradio));
                NS.CardNo = Tag;
                NS.CarNo = text;
                NS.UserName = text2;
                TransferEvent.accept(RFID.ID, RFID.ID + "|-1|-1|-1|" + Tag + "|" + text2 + "|" + text + "|-1|" + bool(RFID.IsOnline) + "|true", 1537);
            }
            return num;
        } catch (RuntimeException ex) {
            Log.writeLog("RFID Error:" + ex.getMessage());
            if (ex instanceof LimitException le) {
                num = le.code;
            }
            if (num == 0) {
                num = 9;
            }
            return num;
        }
    }

    /** Carburant et station autorisés pour la carte ; lève l'erreur et place le code de réponse. */
    private int checkLimits(Row card, NormalNozStatue NS, String[] returnMessage) {
        String limitOil = card.str("LimitOil");
        if (card.get("LimitOil") != null && !limitOil.equals("0") && !limitOil.isEmpty() && Conv.toInt(limitOil) != NS.GradeID) {
            returnMessage[0] = "F0";
            throw new LimitException(240, "Limit Grade");
        }
        String confine = card.str("UseConfine");
        if (card.get("UseConfine") != null && !confine.equals("0") && !confine.isEmpty() && !confine.contains(String.valueOf(IniInfo.StationID))) {
            returnMessage[0] = "EF";
            throw new LimitException(239, "Limit Station");
        }
        return 0;
    }

    /** Refus de limite : le code est rendu par DealRFIDTag (240 carburant, 239 station). */
    private static final class LimitException extends IllegalStateException {
        final int code;

        LimitException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private boolean DealBoxTransaction_BoxOffline(int nozID, BigDecimal volRes, BigDecimal amountRes, BigDecimal priceRes, BigDecimal amounTotalRes,
                                                  BigDecimal volTotalRes, String timeStr, String order, String cardNo, NormalNozStatue nS) {
        try {
            Log.writeLog("OFFLine Amount:" + amountRes.toPlainString());
            Log.writeLog("OFFLine Volume:" + volRes.toPlainString());
            Log.writeLog("OFFLine Price:" + priceRes.toPlainString());
            Log.writeLog("OFFLine AmountTotal:" + amounTotalRes.toPlainString());
            Log.writeLog("OFFLine VolumeTotal:" + volTotalRes.toPlainString());
            LocalDateTime result;
            boolean flag = true;
            try {
                result = Conv.toDateTime(timeStr);
            } catch (RuntimeException e) {
                result = LocalDateTime.now();
                flag = false;
            }
            long DTInt = BaseFunction.ConvertDateTimeToInt(result);
            if (flag && db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} = :c", p("n", nozID, "c", DTInt)) != null) {
                Log.writeLog("Offline Transaction Exist");
                return true;
            }
            if (nS == null) {
                throw new NullPointerException("Object reference not set to an instance of an object.");
            }
            transactionQueue.add(Row.of("Amount", amountRes, "AmountTotal", amounTotalRes, "CreateTime", DTInt, "NozID", nozID, "Price", priceRes, "Volume",
                volRes, "VolumeTotal", volTotalRes, "EmployeeID", IniInfo.LoginID, "StationID", IniInfo.StationID, "GradeID", nS.GradeID, "GradeName",
                nS.GradeName, "PayMent", 999, "OrderNo", order, "CardNo", "00000000" + cardNo));
            return true;
        } catch (RuntimeException ex) {
            Log.writeLog("DealBoxTransaction_BoxOffline:" + ex.getMessage());
            return false;
        }
    }

    private boolean DealBoxTransaction_BoxOfflineList(List<Row> list) {
        try {
            for (Row item : list) {
                Log.writeLog("OFFLine Amount:" + Conv.toStr(item.get("Amount")));
                Log.writeLog("OFFLine Volume:" + Conv.toStr(item.get("Volume")));
                Log.writeLog("OFFLine Price:" + Conv.toStr(item.get("Price")));
                Log.writeLog("OFFLine AmountTotal:" + Conv.toStr(item.get("AmountTotal")));
                Log.writeLog("OFFLine VolumeTotal:" + Conv.toStr(item.get("VolumeTotal")));
            }
            db().insertAll("UnDealTransaction", list);
            return true;
        } catch (RuntimeException ex) {
            Log.writeLog("DealBoxTransaction_BoxOffline:" + ex.getMessage());
            return false;
        }
    }

    /** Alarmes de la cuve ; comme l'original, CreateTime reçoit l'indicateur d'erreur de communication. */
    private void DealTankError(NormalTankSerial tank, String Error) {
        try {
            int num = 0;
            int num2 = 0;
            int num3 = 0;
            int num4 = 0;
            int num5 = 0;
            for (int i = 0; i < Error.length(); i++) {
                if (Error.charAt(i) == '1') {
                    switch (i) {
                        case 7 -> num = 7;
                        case 6 -> num2 = 6;
                        case 5 -> num3 = 5;
                        case 4 -> num3 = 4;
                        case 3 -> num4 = 3;
                        case 2 -> num4 = 2;
                        case 1 -> num5 = 1;
                        default -> {
                        }
                    }
                }
            }
            tank.OverHeight = BigDecimal.valueOf(num4 == 3 ? 1 : 0);
            tank.LowerHeight = BigDecimal.valueOf(num4 == 2 ? 1 : 0);
            tank.OverTemp = BigDecimal.valueOf(num3 == 5 ? 1 : 0);
            tank.LowerTemp = BigDecimal.valueOf(num3 == 4 ? 1 : 0);
            tank.OverWaterHeight = BigDecimal.valueOf(num == 7 ? 1 : 0);
            tank.LowerWaterHeight = BigDecimal.valueOf(num2 == 6 ? 1 : 0);
            tank.CreateTime = num5 == 1 ? 1 : 0;
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    // ------------------------------------------------------------ envoi des commandes

    private void SendData() {
        while (PortRun) {
            try {
                if (!Communicationflag) {
                    CommunicationBroken++;
                    if (CommunicationBroken > 10) {
                        for (NormalNozStatue item : NozAddress) {
                            item.View_Statue = -1;
                            TransferEvent.accept(Conv.toInt(item.NozID), "4|-1|-1|-1|-1|-1|-1|-1|-1", 1033);
                        }
                        for (RFIDRunData r : RFIDConfigs) {
                            r.IsOnline = false;
                            TransferEvent.accept(r.ID, r.ID + "|-1|-1|-1|-1|-1|-1|-1|false|false", 1537);
                        }
                        pLCParam.Statue = -1;
                        priceBorad.Statue = -1;
                    }
                    String[] data = {""};
                    if (AgreedTypeID == 0 && MakeCmdCommunication_Box(data) && !data[0].isEmpty()) {
                        byte[] array = SerialClass.strToToHexByte(data[0]);
                        Log.writeLog("Send To Box: " + data[0]);
                        sp.sendbyte(array, 0, array.length);
                    }
                }
                sleep(100);
            } catch (RuntimeException ex) {
                Log.writeLog("Error:" + ex.getMessage());
            }
        }
        sp.closePort();
        Log.writeLog("Closed");
        for (NormalNozStatue item : NozAddress) {
            item.View_Statue = 4;
            TransferEvent.accept(Conv.toInt(item.NozID), "4|-1|-1|-1|-1|-1|-1||-1|-1", 1033);
        }
    }

    /** CRC16 Modbus (polynôme A001) sur toute la trame sauf les deux derniers octets. */
    private static String CRC16(byte[] bits) {
        try {
            int num = 0xFFFF;
            for (int i = 0; i < bits.length - 2; i++) {
                num ^= bits[i] & 0xFF;
                for (int j = 0; j < 8; j++) {
                    if ((num & 1) == 1) {
                        num = (num >> 1) ^ 0xA001;
                    } else {
                        num >>= 1;
                    }
                }
            }
            return String.format("%04X", num & 0xFFFF);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String format(BigDecimal d, int digits) {
        String pattern = switch (digits) {
            case 1 -> "0.0";
            case 2 -> "0.00";
            case 3 -> "0.000";
            default -> "0";
        };
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT)).format(d);
    }

    /** Prochaine commande de la file LTStatue, chiffrée et prête à envoyer. */
    private boolean MakeCmdCommunication_Box(String[] Data) {
        String text = "F5";
        String random = GetRandom();
        StringBuilder text2 = new StringBuilder();
        String text3 = "";
        Data[0] = "";
        try {
            if (LTStatue.isEmpty() || CurrentProgram != -1) {
                return false;
            }
            LTNozStatuet nz = LTStatue.poll();
            if (nz == null) {
                return false;
            }
            NormalNozStatue NS = GetNozStatue(String.valueOf(nz.NozID));
            switch (nz.Statue) {
                case 161, 165 -> {
                    if (NS == null) {
                        return false;
                    }
                    NormalNozStatue noz2 = nozById(nz.NozID);
                    String text33 = nz.PreValue.toPlainString();
                    text3 = String.format("%02x", nz.Statue);
                    BigDecimal num4 = nz.PreValue;
                    BigDecimal high = noz2 == null || noz2.Box_DeviceType != 0 ? BigDecimal.valueOf(90000000) : BigDecimal.valueOf(900000);
                    if (NS.RaiseNoz == 1 || text3.equalsIgnoreCase("A5") || NS.accredit == 3) {
                        Log.writeLog("Set " + text3);
                        Log.writeLog("A1B  Value:" + nz.PreValue.toPlainString());
                        if (noz2 == null && nz.PreValue.compareTo(BigDecimal.valueOf(9000)) > 0) {
                            nz.PreValue = BigDecimal.valueOf(9000);
                        }
                        int radio = nz.PreType == 0 ? IniInfo.Mradio : IniInfo.Vradio;
                        nz.PreValue = nz.PreValue.multiply(pow10(radio)).setScale(0, RoundingMode.HALF_EVEN);
                        if (noz2 == null) {
                            throw new NullPointerException("Object reference not set to an instance of an object.");
                        }
                        if (nz.PreValue.compareTo(high) > 0) {
                            nz.PreValue = high;
                        }
                        if (num4.compareTo(M1) == 0) {
                            nz.PreValue = high;
                        }
                        Log.writeLog("A1F  Value:" + nz.PreValue.toPlainString());
                        String text34 = BaseFunction.SetStrLen(nz.PreValue.toPlainString(), 8);
                        text2.append(setLen(nz.NozID, 2)).append(setLen(nz.PreType, 2)).append(text34)
                            .append(setLen(NS.SetPrice.multiply(pow10(IniInfo.Pradio)).setScale(0, RoundingMode.HALF_EVEN).toPlainString(), 6));
                        Log.writeLog("NS.SetPrice:" + NS.SetPrice.toPlainString());
                        if (nz.DealCode != 0) {
                            text2.append(setLen(String.format("%02x", nz.DealCode), 8)).append(setLen("0", 10));
                        } else {
                            text2.append(setLen("0", 8));
                        }
                        Log.writeLog("PreSet");
                    } else if (LTStatue.stream().noneMatch(t -> t.NozID == Conv.toInt(NS.NozID) && t.Statue == 161)) {
                        LTStatue.add(nz);
                    }
                    NS.Auth_Statue = true;
                    String text35 = switch (nz.PreType) {
                        case 0 -> MultiLanguage.getText("Amount") + ":";
                        case 1 -> MultiLanguage.getText("Volume") + ":";
                        default -> "";
                    };
                    if (noz2 == null) {
                        throw new NullPointerException("Object reference not set to an instance of an object.");
                    }
                    BigDecimal unit = high.divide(pow10(IniInfo.Mradio));
                    if (noz2.Box_DeviceType == 0) {
                        if (num4.compareTo(high) > 0) {
                            num4 = high;
                        }
                        if (num4.compareTo(M1) == 0) {
                            num4 = high;
                        }
                        text33 = num4.toPlainString();
                        if (text33.equals("900000")) {
                            text33 = format(unit, IniInfo.Mradio);
                        }
                    } else {
                        if (num4.compareTo(high) > 0) {
                            num4 = high;
                            text33 = num4.toPlainString();
                        }
                        if (num4.compareTo(M1) == 0) {
                            text33 = nz.PreValue.toPlainString();
                        }
                        if (text33.equals("90000000")) {
                            text33 = format(unit, IniInfo.Mradio);
                        }
                    }
                    if (text33.equals("-1")) {
                        text33 = format(unit, IniInfo.Mradio);
                    }
                    TransferEvent.accept(nz.NozID, "-1|-1|-1|-1|-1|-1|-1|" + text35 + text33 + "|" + NS.GradeName, 1033);
                }
                case 162 -> {
                    text3 = "A2";
                    text2.append(setLen(nz.NozID, 2)).append(setLen(nz.PrePrice.multiply(pow10(IniInfo.Pradio)).intValue(), 6));
                    NS.SetPrice = nz.PrePrice;
                    NS.Price = nz.PrePrice;
                    TransferEvent.accept(nz.NozID, "-1|-1|-1|-1|" + NS.SetPrice.toPlainString() + "|-1|-1|-1", 1033);
                    UPNozPrice.clear();
                    Log.writeLog("Price Update");
                }
                case 163 -> {
                    text3 = "A3";
                    if (Port == null) {
                        return false;
                    }
                    if (!cmdInit(nz, text2)) {
                        return false;
                    }
                    Log.writeLog("Init");
                }
                case 164 -> {
                    text3 = "A4";
                    text2.append(setLen(nz.NozID, 2));
                }
                case 14 -> text3 = "0E";
                case 13 -> {
                    text3 = "0D";
                    NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(nz.ProgramType, 1);
                    if (!pg.ok()) {
                        throw new IllegalStateException("0x0D" + pg.Error());
                    }
                    text2.append(setLen(String.format("%04x", pg.TotalPage()), 4)).append(setLen("1", 4)).append(pg.ProgramData());
                    CurrentProgram = nz.ProgramType;
                    SendMessToMainForm(1793, pg.TotalPage(), 1L);
                    Log.writeLog("Dowload befor 1:" + pg.ProgramData());
                    TransferEvent.accept(0, pg.TotalPage() + "-1-0", 1281);
                }
                case 17 -> {
                    Log.writeLog("GetDeviceVersion");
                    text3 = "11";
                    text2.append(setLen(nz.SerialPort, 2));
                }
                case 18 -> {
                    Log.writeLog("DowloadDeviceVersion");
                    text3 = "12";
                    Row boxSet2 = db().first("Box_Set", "{Box_PortID} = :p AND {Port_ID} = :i", p("p", nz.SerialPort, "i", ID));
                    if (boxSet2 == null) {
                        throw new NullPointerException("Object reference not set to an instance of an object.");
                    }
                    int programType = programFor(boxSet2, 1);
                    if (programType < 0) {
                        programType = 1;
                    }
                    NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(programType, 1);
                    if (!pg.ok()) {
                        throw new IllegalStateException("12 Error:" + pg.Error());
                    }
                    text2.append(setLen(nz.SerialPort, 2)).append(fileName20(pg.FileName())).append(setLen(String.format("%02x", pg.TotalPage()), 4))
                        .append(setLen("1", 4)).append(pg.ProgramData());
                    CurrentProgram = nz.ProgramType;
                    Log.writeLog("Dowload Device 1:" + pg.ProgramData());
                }
                case 2 -> {
                    Log.writeLog("Cmd02:");
                    NormalTankSerial tank = TankStatueList.stream().filter(t -> t.ID == nz.TankID).findFirst().orElse(null);
                    if (tank != null) {
                        text2.append(BaseFunction.SetStrLen(tank.TankNo, 2)).append(BaseFunction.DowloadTankCable(tank.ID, 1, new int[2]));
                    } else {
                        Log.writeLog("Tank Lost");
                    }
                }
                case 20 -> {
                    Log.writeLog("Cmd14:");
                    text3 = "14";
                    cmdPrintSet(text2);
                }
                case 21 -> {
                    Log.writeLog("Cmd 15:" + nz.PreValue.toPlainString());
                    text3 = "15";
                    Row tr = db().first("NormalTransaction", "{ID} = :i", p("i", nz.PreValue.intValue()));
                    if (tr != null) {
                        String noz = setLen(String.format("%02x", tr.i("NozID")), 2);
                        Log.writeLog("NozID:" + noz);
                        String amount = setLen(round(Conv.toDecimal(tr.get("Amount")), IniInfo.Mradio).toPlainString().replace(".", ""), 8);
                        Log.writeLog("Amount:" + amount);
                        String volume = setLen(round(Conv.toDecimal(tr.get("Volume")), IniInfo.Vradio).toPlainString().replace(".", ""), 8);
                        Log.writeLog("Volume:" + volume);
                        String price = setLen(round(Conv.toDecimal(tr.get("Price")), IniInfo.Pradio).toPlainString().replace(".", ""), 6);
                        Log.writeLog("Price:" + price);
                        String tid = setLen(String.format("%02x", tr.i("ID")), 8);
                        Log.writeLog("TID:" + tid);
                        text2.append(noz).append(amount).append(volume).append(price)
                            .append(BaseFunction.StampToDateTime(Conv.toStr(tr.get("CreateTime"))).format(REPLY_TIME)).append(tid);
                    }
                }
                case 22 -> {
                    Log.writeLog("Cmd 16:");
                    text3 = "16";
                    Sugar.Page pg = db().page("EmployeeCardInfos", "{Status} = '0'", null, "{ASN}", 1, 10);
                    StringBuilder cards = new StringBuilder();
                    for (Row item : pg.rows()) {
                        cards.append(item.str("ASN").substring(8));
                    }
                    if (nz.PreValue.compareTo(M1) == 0) {
                        nz.PreValue = BigDecimal.ZERO;
                    }
                    int totalPage = pg.total() == 0 ? 1 : pg.totalPage();
                    text2.append(setLen(String.format("%02x", nz.PreValue.remainder(BigDecimal.valueOf(255)).intValue()), 2))
                        .append(setLen(String.format("%02x", totalPage), 4)).append("0001").append(cards);
                }
                case 24 -> {
                    Log.writeLog("Cmd 18:");
                    text3 = "18";
                    int n = 0;
                    for (Row item : db().list("NormalPriceInfo", "ORDER BY {ID}", null)) {
                        text2.append(setLen(n + 1, 2)).append(setLen(Conv.toDecimal(item.get("Price")).multiply(pow10(IniInfo.Pradio)).intValue(), 6))
                            .append(setLen(IniInfo.Pradio, 2));
                        n++;
                    }
                }
                default -> {
                }
            }
            String body = text2.toString();
            if (nz.Statue == 161 && body.isEmpty()) {
                return false;
            }
            String text4 = text + setLen(String.format("%04X", body.length() / 2 + 8), 4) + random + text3 + body + "0000";
            if (cmdHead.equals("FA")) {
                text4 = cmdHead + setLen(String.format("%04X", body.length() / 2 + 14), 4) + random + text3 + body + "0000";
            }
            Log.writeLog("Send Old Data:" + text4);
            String[] outdata = {""};
            boolean ok = BaseFunction.DealCmdEncry(text4, random, outdata);
            String out = outdata[0];
            if (cmdHead.equals("FA")) {
                out = cmdHead + LocalDateTime.now().format(REPLY_TIME) + out.substring(2);
            }
            if (!ok) {
                throw new IllegalStateException("Encry Error");
            }
            Data[0] = out.substring(0, out.length() - 4) + CRC16(SerialClass.strToToHexByte(out));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Commande A3 : configuration d'un port du boîtier (pistolets, cuves, lecteurs RFID, PLC, afficheurs, appareils du port 5). */
    private boolean cmdInit(LTNozStatuet nz, StringBuilder text2) {
        if (nz.PreType == -99) {
            text2.append(setLen(nz.NozID, 2)).append(setLen("0", 6)).append(setLen("0", 2)).append(setLen("0", 2)).append(setLen("0", 2))
                .append(setLen("0", 2)).append(setLen("0", 2)).append(setLen("0", 2));
            return true;
        }
        Row BoxSet = db().first("Box_Set", "{ID} = :i", p("i", nz.SerialPort));
        if (BoxSet == null) {
            return text2.length() > 0;
        }
        int portId = BoxSet.i("Box_PortID");
        if (portId == 5) {
            List<Row> devices = db().list("StationDeviceSet", "{BoxID} = :b", p("b", ID));
            text2.append(setLen(portId, 2)).append(setLen(devices.size(), 2));
            for (Row d : devices) {
                int type = switch (d.i("StationDeviceType")) {
                    case 1 -> 4;
                    case 2 -> 6;
                    case 3 -> 5;
                    default -> 0;
                };
                text2.append(setLen(d.get("DeviceAddress"), 2)).append(setLen(type, 2)).append(setLen(BaseFunction.Buads[d.i("DeviceBuad")], 6))
                    .append(setLen(d.get("DeviceStop"), 2)).append(setLen(d.get("DeviceParity"), 2))
                    .append(setLen((80 + 10 * d.i("DeviceInterval")) / 10, 2)).append("0000");
            }
            return text2.length() > 0;
        }
        int comm = BoxSet.i("Box_CommunicationTypeID");
        int interval = BoxSet.i("Box_IntervalTime") / 10;
        text2.append(setLen(portId, 2)).append(setLen(BoxSet.get("Box_Baud"), 6)).append(setLen(BoxSet.get("Box_Stop"), 2))
            .append(setLen(BoxSet.get("Box_Parity"), 2)).append(setLen(comm, 2));
        if (comm == 5) {
            if (BoxSet.i("Box_DeviceType") == 0) {
                text2.append("01").append(setLen(interval, 2)).append("0001");
            } else {
                List<Row> tanks = db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null);
                text2.append(setLen(tanks.size(), 2)).append(setLen(interval, 2)).append(setLen(BoxSet.get("Box_DeviceType"), 2));
                for (Row t : tanks) {
                    text2.append(setLen(t.get("TankNo"), 2)).append(setLen(t.get("TankNo"), 2));
                }
            }
        } else if (comm == 6) {
            Log.writeLog("RFID");
            List<Row> list5 = db().list("RFIDConfig", "{Box_Port} = :b ORDER BY {RFIDNo}", p("b", BoxSet.get("ID")));
            text2.append(setLen(list5.size(), 2)).append(setLen(interval, 2)).append(setLen(BoxSet.get("Box_DeviceType"), 2));
            for (Row r : list5) {
                text2.append(setLen(r.get("RFIDNo"), 2)).append(setLen(r.get("Address"), 2)).append(setLen(r.get("NozID"), 2));
            }
            int boxId = BoxSet.i("ID");
            RFIDConfigs.removeIf(t -> t.Box_Port == boxId);
            for (Row r : list5) {
                RFIDConfigs.add(rfidRun(r));
            }
        } else if (comm == 11 || comm == 12) {
            Log.writeLog("CNG_PLC||Price Borad");
            text2.append("01").append(setLen(interval, 2)).append("0001");
        } else {
            List<Row> list6 = db().list("NormalNoz", "{Box_Port_ID} = :b ORDER BY {NozID}", p("b", nz.SerialPort));
            text2.append(setLen(list6.size(), 2)).append(setLen(interval, 2)).append(BoxSet.i("Box_DeviceType") != 0 ? "01" : "00");
            for (Row n : list6) {
                text2.append(setLen(n.get("NozID"), 2)).append(setLen(n.get("Address"), 2));
            }
            text2.append("0").append(IniInfo.Vradio).append(IniInfo.Mradio).append(IniInfo.Pradio);
            for (Row n : list6) {
                int acc = n.i("Accredit");
                text2.append(acc == 0 ? setLen("1", 2) : acc == 1 ? setLen("0", 2) : setLen(acc, 2));
            }
            NozAddress.clear();
            for (Row item : db().list("NormalNoz", "{PortID} = :p AND {IsUsing} = true ORDER BY {NozID}", p("p", SerialPortID))) {
                Row boxSet = db().first("Box_Set", "{ID} = :b", p("b", item.get("Box_Port_ID")));
                if (boxSet == null) {
                    boxSet = Row.of("Box_DeviceType", 0);
                }
                NormalNozStatue n = new NormalNozStatue(item.i("ID"), setLen(item.get("NozID"), 2), item.i("TankID"), item.i("Accredit"),
                    Conv.toDecimal(item.get("Price")), item.i("MachineOpen"), boxSet.i("Box_DeviceType"), onDuty(), item.i("AllowAccredit"),
                    item.i("PumpID"), IniInfo.IdentifyMode);
                Row tank = db().first("NormalTank", "{ID} = :t", p("t", item.get("TankID")));
                if (tank != null) {
                    n.GradeID = tank.i("GradeID");
                    n.GradeName = BaseFunction.GetNormalGradeByID(tank.i("GradeID"));
                }
                n.Address = -(item.i("Address") + boxSet.i("Box_PortID") * 100 + boxSet.i("Port_ID") * 10000);
                NozAddress.add(n);
            }
        }
        return text2.length() > 0;
    }

    private static String line(Object text) {
        String t = BaseFunction.StringConvertToAscii(Conv.toStr(text)) + "2020202020202020202020202020202020202020202020202020202020202020";
        return t.substring(0, 64);
    }

    /** Commande 14 : lignes du ticket imprimé par le boîtier (DevicePrintSet). */
    private void cmdPrintSet(StringBuilder text2) {
        Row d = db().first("DevicePrintSet");
        if (d == null) {
            return;
        }
        text2.append(setLen(String.format("%02x", 15), 2)).append(setLen(String.format("%02x", 32), 2));
        for (String f : new String[] {"DeviceFirstLine", "DeviceSecondLine", "DeviceThirdLine", "DeviceFouthLine", "DeviceFifthLine", "DeviceSixthLine",
            "DeviceNozID", "DeviceAmount", "DeviceVolume", "DevicePrice", "DeviceSevenLine", "DeviceTime", "DeviceEighthLine", "DeviceNinthLine",
            "DeviceTenthLine"}) {
            text2.append(line(d.get(f)));
        }
    }

    private static String GetRandom() {
        StringBuilder text = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            text.append(1 + random.nextInt(9));
        }
        return text.toString();
    }

    private void SendMessToMainForm(int CmdType, long data1, long data2) {
        try {
            MainFormMessage.accept(CmdType, data1, data2);
        } catch (RuntimeException ex) {
            Log.writeLog("Send Mess Error:" + ex.getMessage());
        }
    }
}
