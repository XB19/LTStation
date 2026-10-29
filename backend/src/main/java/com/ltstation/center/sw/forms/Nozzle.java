package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.engine.SupervisionFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.hw.TCPClientQY;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nozzle : un pistolet (pompe) de la station. Panneau d'état affiché dans la fenêtre principale
 * et dialogue avec le calculateur de la pompe sur le port série (trames « FA », CRC 16 Modbus) :
 * interrogation, transactions, listes noires / blanches, prix, contrôle des cartes de crédit,
 * supervision QY, commandes de prépaiement. Sans port série, le panneau reste « hors ligne ».
 */
@SwWindow("Nozzle")
public class Nozzle extends SwForm {

    /** Encoding.Default du poste d'origine (Windows chinois). */
    private static final Charset ANSI = Charset.forName("GBK");

    private Row stations = new Row();
    public TCPClientQY TCPClientQY;
    public int NozID;
    public volatile String LockTime = "-1";
    private String Asn = "";
    private String AMN = "";
    private String Vol = "";
    public LegacyLog NozLog = new LegacyLog("Noz0");
    public SerialClass sp = new SerialClass();
    public Thread thread;
    public volatile boolean ThreadStop;
    private volatile boolean OnCommitFlg = true;
    private int Frame = 63;
    public volatile long PSStart;
    public volatile long PSEnd;
    private BigDecimal NozPPU = BigDecimal.ZERO;
    private int Port;
    private int Baurate;
    private int Databits;
    private int NozOilTypeCode;
    private BigDecimal VolTotal = BigDecimal.ZERO;
    private String Cache = "";
    private int CacheLen;
    private volatile int a;
    private int b;
    private int c;
    private int CommitFlagFalse;

    /** CreditCardInfo : réponse au contrôle d'une carte de crédit / de groupe. */
    static final class CreditCardInfo {
        int SuspendType;
        String Pass;
        String Asn;
        int LimitVehicleNo;
        String VehicleNo;
        int MileageTplerance;
        BigDecimal PreferentialPrice = BigDecimal.ZERO;
        BigDecimal Discount = BigDecimal.ZERO;
        BigDecimal OiConsumption = BigDecimal.ZERO;
        BigDecimal MileageLastest = BigDecimal.ZERO;
        BigDecimal VolumeLastest = BigDecimal.ZERO;
        BigDecimal MaxVolume = BigDecimal.ZERO;
        BigDecimal MaxAmount = BigDecimal.ZERO;
        BigDecimal Balance = BigDecimal.ZERO;
    }

    /** CreditCount : transactions de la carte depuis la dernière mise à jour du compte. */
    static final class CreditCount {
        String Asn;
        BigDecimal Volume = BigDecimal.ZERO;
        BigDecimal Money = BigDecimal.ZERO;
        int Count;
        BigDecimal VolumeLastest = BigDecimal.ZERO;
        int MileageLastest;
        LocalDateTime TransDt;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String hex(long v) {
        return Long.toHexString(v);
    }

    private static int i(BigDecimal d) {
        return d.intValue();
    }

    private static BigDecimal dec(Object v) {
        return Conv.toDecimal(v);
    }

    private static BigDecimal mul(int k, BigDecimal d) {
        return BigDecimal.valueOf(k).multiply(d);
    }

    /** Math.Round(decimal, n) : arrondi bancaire sans ajouter de zéros. */
    private static BigDecimal round(BigDecimal d, int n) {
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static String now(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    // ------------------------------------------------------------ construction

    @Override
    protected void init() {
        sp.SleeepTime = 100;
        stations = db().first("Stations");
        if (stations == null) {
            stations = new Row();
            Close();
        }
    }

    /** Pistolet, port, débit, carburant et prix (jointure GunConfig / ComConfig / tankConfig / PriceInfos). */
    private Row nozInfo(int nozId) {
        String sql = "SELECT {GunConfig.GUNCODE} AS NozID, {ComConfig.PORT} AS NozPort, {ComConfig.BAUDRATE} AS NozBaurate,"
            + " {ComConfig.DATABIT} AS NozDatabits, {tankConfig.OilCode} AS NozOilType, {PriceInfos.NewPPU} AS NozPPU,"
            + " {PriceInfos.ValidateTime} AS ValidTime FROM [GunConfig] JOIN [ComConfig] ON {GunConfig.PORTCODE} = {ComConfig.CODE}"
            + " JOIN [tankConfig] ON {GunConfig.TANKCODE} = {tankConfig.ID} JOIN [PriceInfos] ON CAST({tankConfig.OilCode} AS varchar) = {PriceInfos.Code}"
            + " WHERE {GunConfig.GUNCODE} = :id LIMIT 1";
        return db().queryFirst(sql, p("id", nozId));
    }

    public void NozChangeByKeyboard(int ID) {
        try {
            Row r = nozInfo(NozID);
            UpdateView(r.i("NozID"), BaseFunction.GetOilNameByCode(r.i("NozOilType")), dec(r.get("NozPPU")));
        } catch (RuntimeException ex) {
            NozLog.writeLog(String.valueOf(ex.getMessage()));
        }
    }

    public void NozChangeByRoll(int id) {
        try {
            if (id == 2) {
                c("NozzleStatue").Image("res/Fuel.png");
            }
            if (id == 1) {
                c("NozzleStatue").Image("res/OFF.png");
            }
            c("CardNo").Text(Asn.isEmpty() ? nz(L("NoCard")) : Asn);
            c("Amount").Text(AMN.isEmpty() ? L("Amount") + ":0" : L("Amount") + ":" + AMN);
            c("Volume").Text(Vol.isEmpty() ? L("Volume") + ":0" : L("Volume") + ":" + Vol);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** NozzleSeting : configuration du pistolet, ouverture du port série et démarrage de l'interrogation. */
    public void NozzleSeting(int nozId) {
        NozLog = new LegacyLog("Noz" + nozId);
        try {
            Row r = nozInfo(nozId);
            if (r != null) {
                NozID = r.i("NozID");
                NozPPU = dec(r.get("NozPPU"));
                Port = r.i("NozPort");
                Baurate = r.i("NozBaurate");
                Databits = r.i("NozDatabits");
                NozOilTypeCode = r.i("NozOilType");
                UpdateView(NozID, BaseFunction.GetOilNameByCode(NozOilTypeCode), NozPPU);
                UpdateVolumeTotal(NozID);
                sp = new SerialClass("COM" + Port, Baurate, SerialClass.Parity.None, Databits, SerialClass.StopBits.One);
                sp.SleeepTime = 100;
                sp.onDataReceived(this::Sp_DataReceived);
                try {
                    String[] error = {""};
                    if (!sp.openPort(error)) {
                        throw new IllegalStateException(error[0]);
                    }
                    NozLog.writeLog("Open Port-" + r.i("NozPort") + " Successed!");
                    ThreadStop = true;
                    if (IniInfo.SupervisionType == 1 && IniInfo.TransactionType == 0) {
                        TCPClientQY = new TCPClientQY(IniInfo.ServerIP, IniInfo.Port);
                        TCPClientQY.TcpConnect();
                    }
                    thread = new Thread(this::SendData, "Noz" + NozID);
                    thread.setDaemon(true);
                    thread.start();
                    return;
                } catch (RuntimeException ex) {
                    NozLog.writeLog("Open Port-" + r.i("NozPort") + " Failed!");
                    return;
                }
            }
            NozLog.writeLog("Lost important Message,Please Complete");
        } catch (RuntimeException ex2) {
            NozLog.writeLog("Error:" + ex2.getMessage());
        }
    }

    public void UpdateView(int nozId, String oil, BigDecimal price) {
        if ("zh-CN".equals(IniInfo.Language)) {
            c("NozName").Text(nozId + "号枪");
            c("OilType").Text("能源类型:" + oil);
            c("PPU").Text("单价:" + price.toPlainString());
        } else {
            c("NozName").Text("N°" + nozId);
            c("OilType").Text("Énergie : " + oil);
            c("PPU").Text("Prix : " + price.toPlainString());
        }
        Row o = db().first("OilTransactions", "{GunId} = :g AND ({TType} = 5 OR {TType} = 6) ORDER BY {TrueTime} DESC", p("g", nozId));
        if (o != null) {
            switch (o.i("TType")) {
                case 5 -> {
                    Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", o.get("EmployeeID")));
                    if (emp != null) {
                        c("EmpName").Text(emp.get("Name"));
                    }
                }
                case 6 -> c("EmpName").Text(L("NoEmployee"));
                default -> {
                }
            }
        } else {
            c("EmpName").Text(L("NoEmployee"));
        }
    }

    public String StrReplace(String str) {
        StringBuilder text = new StringBuilder();
        for (int n = 0; n < str.length(); n += 2) {
            String t2 = str.substring(n, n + 2).toUpperCase();
            text.append(t2.equals("FA") ? "FAFA" : t2);
        }
        return text.toString();
    }

    public String ReplaceFA(String str) {
        StringBuilder text = new StringBuilder();
        for (int n = 0; n < str.length(); n += 2) {
            String t2 = str.substring(n, n + 2);
            if (t2.equals("FA") && str.substring(n + 2, n + 4).equals("FA")) {
                n += 2;
            }
            text.append(t2);
        }
        return text.toString();
    }

    public void UpdateVolumeTotal(int id) {
        boolean zh = "zh-CN".equals(IniInfo.Language);
        try {
            Row o = db().first("OilTransactions", "{GunId} = :g ORDER BY {TrueTime} DESC", p("g", NozID));
            if (o != null) {
                c("VTLabel").Text((zh ? "总累:" : "Index : ") + o.str("VolumeTotal"));
                VolTotal = BigDecimal.valueOf(Conv.toDouble(o.get("VolumeTotal"))).setScale(3, RoundingMode.HALF_EVEN).stripTrailingZeros();
            } else {
                c("VTLabel").Text(zh ? "总累:0" : "Index : 0");
                VolTotal = BigDecimal.ZERO;
            }
            Row g = db().first("GunConfig", "{GUNCODE} = :g", p("g", NozID));
            if (g != null) {
                g.put("VolTotal", VolTotal);
                db().update("GunConfig", g);
            }
        } catch (RuntimeException e) {
            c("VTLabel").Text(zh ? "总累:0" : "Index : 0");
        }
    }

    // ------------------------------------------------------------ réception d'une trame

    private void send(String hexText) {
        if (!sp.PortIsOpen()) {
            throw new IllegalStateException("InvalidOperationException");
        }
        byte[] arr = SerialClass.strToToHexByte(hexText);
        sp.sendbyte(arr, 0, arr.length);
    }

    private String suspendOnly(byte cmd, int suspend) {
        return BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "0002" + hex(cmd & 0xFF) + BaseFunction.ConverToStr(hex(suspend), 2);
    }

    private String frameEnd(String text) {
        text = (text + CRC(text)).toUpperCase();
        return "FA" + StrReplace(text);
    }

    /** Sp_DataReceived : trame reçue de la pompe (éventuellement en plusieurs morceaux). */
    private void Sp_DataReceived(byte[] bits) {
        synchronized (this) {
            a++;
            LabelC(0);
            OnCommitFlg = false;
            String text = "";
            String text2 = "";
            try {
                String text3 = SerialClass.byteToHexStr(bits);
                NozLog.writeLog("Data:" + text3);
                Cache += text3;
                if (Cache.substring(0, 2).equals("FA")) {
                    CacheLen = Integer.parseInt(Cache.substring(8, 12)) * 2 + 16;
                    if (Cache.length() < CacheLen) {
                        return;
                    }
                    NozLog.writeLog("Receive:" + Cache);
                }
                String text4 = Cache.substring(0, 2) + ReplaceFA(Cache.substring(2));
                if (text4.length() < 14) {
                    throw new IllegalStateException("Message Format Error-1");
                }
                String dt = text4.substring(2, text4.length() - 4);
                String crc = CRC(dt);
                text2 = text4.substring(12, 14);
                NozLog.writeLog("dealcmd:" + text2);
                if (crc.equals(text4.substring(text4.length() - 4))) {
                    byte[] array = SerialClass.strToToHexByte(text4);
                    byte cmd = array[6];
                    switch (cmd & 0xFF) {
                        case 49 -> {
                            NozUIDeal(text4);
                            text = Cmd_Poll();
                        }
                        case 50 -> {
                            text = Cmd_HandTransaction(text4);
                            NozUIDeal("");
                        }
                        case 51 -> text = Cmd_Download(array);
                        case 52 -> text = Cmd_Dowload_Data(array, text4.substring(16, 22));
                        case 53 -> text = Cmd_GetGrayTransaction(text4);
                        case 54 -> text = Cmd_QueryWhiteAndBlack(text4);
                        case 56 -> NozLog.writeLog("38");
                        case 58 -> NozLog.writeLog("3A");
                        case 59 -> NozLog.writeLog("3B");
                        case 60 -> NozLog.writeLog("3C");
                        case 64 -> NozLog.writeLog("40");
                        case 68 -> NozLog.writeLog("44");
                        case 97 -> {
                            try {
                                CreditCardInfo ci3 = Cmd_CreditCheck(text4);
                                text = ci3.SuspendType != 0 && ci3.SuspendType != 1 ? suspendOnly(cmd, ci3.SuspendType)
                                    : HexFormat(Port, 2) + "00" + hex(Frame) + "0058" + hex(cmd & 0xFF) + HexFormat(ci3.SuspendType, 2)
                                        + HexFormat(i(mul(IniInfo.MFormat, ci3.PreferentialPrice)), 4) + HexFormat(i(mul(100, ci3.Discount)), 4)
                                        + HexFormat(mul(10000, ci3.Balance).longValue(), 10) + HexFormat(i(mul(10000, ci3.MaxAmount)), 10)
                                        + CheckRVolume(ci3.MaxVolume) + HexFormat(i(ci3.MileageLastest), 6)
                                        + HexFormat(i(mul(IniInfo.VFormat, ci3.VolumeLastest)), 6) + HexFormat(i(mul(IniInfo.VFormat, ci3.OiConsumption)), 4)
                                        + BaseFunction.ConverToStr(String.valueOf(ci3.MileageTplerance), 2) + HexFormat(ci3.LimitVehicleNo, 2) + nz(ci3.VehicleNo)
                                        + "0000" + text4.substring(14, 34);
                            } catch (RuntimeException e) {
                                text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "0002" + (cmd & 0xFF) + BaseFunction.ConverToStr(hex(5), 2);
                            }
                            text = frameEnd(text);
                            NozLog.writeLog("RFID Message:" + text);
                        }
                        case 80 -> {
                            try {
                                CreditCardInfo ci2 = Cmd_CreditCheck(text4);
                                if (ci2.SuspendType == 0 || ci2.SuspendType == 1) {
                                    ci2.Pass = "".equals(ci2.Pass) ? "9999" : ci2.Pass;
                                    text = HexFormat(Port, 2) + "00" + hex(Frame) + "0045" + hex(cmd & 0xFF) + HexFormat(ci2.SuspendType, 2)
                                        + HexFormat(i(mul(IniInfo.MFormat, ci2.PreferentialPrice)), 4) + HexFormat(i(mul(100, ci2.Discount)), 4)
                                        + HexFormat(i(mul(IniInfo.MFormat, ci2.Balance)), 8) + HexFormat(i(mul(IniInfo.MFormat, ci2.MaxAmount)), 8)
                                        + CheckVolume(ci2.MaxVolume) + HexFormat(i(ci2.MileageLastest), 6) + HexFormat(i(mul(IniInfo.VFormat, ci2.VolumeLastest)), 6)
                                        + HexFormat(i(mul(IniInfo.VFormat, ci2.OiConsumption)), 4) + BaseFunction.ConverToStr(String.valueOf(ci2.MileageTplerance), 2)
                                        + HexFormat(ci2.LimitVehicleNo, 2) + nz(ci2.VehicleNo) + nz(ci2.Pass);
                                } else {
                                    text = suspendOnly(cmd, ci2.SuspendType);
                                }
                            } catch (RuntimeException e) {
                                text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "0002" + (cmd & 0xFF) + BaseFunction.ConverToStr(hex(5), 2);
                            }
                            text = frameEnd(text);
                            NozLog.writeLog("CreditCard Message:" + text);
                        }
                        case 81 -> {
                            text = IniInfo.SupervisionType == 0 ? "00" : Cmd_Supervivon(text4);
                            text = frameEnd(BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000251" + text);
                            NozLog.writeLog("Supervision : " + text);
                        }
                        case 82 -> {
                            NozLog.writeLog("App");
                            String[] ret = {""};
                            DealApp(text4, ret);
                            text = ret[0];
                        }
                        case 83 -> NozLog.writeLog("53");
                        case 84 -> NozLog.writeLog("54");
                        case 85 -> {
                            try {
                                CreditCardInfo ci = Cmd_MasterCheck(text4);
                                text = ci.SuspendType != 0 && ci.SuspendType != 1
                                    ? BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000255" + BaseFunction.ConverToStr(hex(ci.SuspendType), 2)
                                    : HexFormat(Port, 2) + "00" + hex(Frame) + "004355" + HexFormat(ci.SuspendType, 2)
                                        + HexFormat(i(mul(IniInfo.MFormat, ci.PreferentialPrice)), 4) + HexFormat(i(mul(100, ci.Discount)), 4)
                                        + HexFormat(i(mul(IniInfo.MFormat, ci.Balance)), 8) + HexFormat(i(mul(IniInfo.MFormat, ci.MaxAmount)), 8)
                                        + CheckVolume(ci.MaxVolume) + HexFormat(i(ci.MileageLastest), 6) + HexFormat(i(mul(IniInfo.VFormat, ci.VolumeLastest)), 6)
                                        + HexFormat(i(mul(IniInfo.VFormat, ci.OiConsumption)), 4) + BaseFunction.ConverToStr(String.valueOf(ci.MileageTplerance), 2)
                                        + HexFormat(ci.LimitVehicleNo, 2) + nz(ci.VehicleNo);
                            } catch (RuntimeException e) {
                                text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000255" + BaseFunction.ConverToStr(hex(5), 2);
                            }
                            text = frameEnd(text);
                            NozLog.writeLog("Carte secondaire de groupe : " + text);
                        }
                        case 86 -> NozLog.writeLog("Receive Valid");
                        default -> {
                        }
                    }
                    if ((cmd & 0xFF) != 49) {
                        NozStatueUp(1, "0", "0", VolTotal);
                    }
                    send(text);
                    OnCommitFlg = true;
                    Cache = "";
                    return;
                }
                throw new IllegalStateException("CRC Error");
            } catch (RuntimeException ex4) {
                if (text2.equals("35")) {
                    String t5 = frameEnd(BaseFunction.ConverToStr(hex(NozID), 2) + "000200023200");
                    try {
                        send(t5);
                    } catch (RuntimeException ignored) {
                        // port fermé
                    }
                }
                Cache = "";
                NozLog.writeLog(String.valueOf(ex4.getMessage()));
                OnCommitFlg = true;
            }
        }
    }

    private void NozStatueUp(int statue, String amn, String vol, BigDecimal volumeTotal) {
        try {
            Row s = db().first("NozStatue", "{GunID} = :g", p("g", NozID));
            boolean add = s == null;
            if (add) {
                s = new Row();
            }
            s.put("GunID", NozID);
            s.put("StationID", IniInfo.StationID);
            s.put("Amount", round(dec(amn), 3));
            s.put("Volume", round(dec(vol), 3));
            s.put("Statue", statue);
            s.put("UpdateTime", BaseFunction.ConvertDateTimeToInt());
            if (add) {
                db().insert("NozStatue", s);
            } else {
                db().update("NozStatue", s);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void NozUIDeal(String cache) {
        try {
            if (cache.length() < 30) {
                Asn = "";
                NozW(1);
                NozStatueUp(1, "0", "0", VolTotal);
                return;
            }
            String t = cache.substring(16, 18);
            int i = Integer.parseInt(t, 16);
            if (t.equals("01")) {
                Asn = cache.substring(22, 42);
                NozW(i);
                NozStatueUp(1, "0", "0", VolTotal);
            } else if (t.equals("02")) {
                if (IniInfo.DigitsType == 0) {
                    AMN = Conv.num((double) Integer.parseInt(cache.substring(22, 28), 16) / IniInfo.MFormat);
                    Vol = Conv.num((double) Integer.parseInt(cache.substring(28, 34), 16) / IniInfo.VFormat);
                } else {
                    AMN = Conv.num((double) Integer.parseInt(cache.substring(22, 30), 16) / IniInfo.MFormat);
                    Vol = Conv.num((double) Integer.parseInt(cache.substring(30, 38), 16) / IniInfo.VFormat);
                }
                NozStatueUp(2, AMN, Vol, VolTotal);
                NozW(i);
            }
        } catch (RuntimeException ex) {
            Asn = "";
            AMN = "";
            Vol = "";
            NozW(1);
            NozLog.writeLog(String.valueOf(ex.getMessage()));
        }
    }

    // ------------------------------------------------------------ commandes envoyées à la pompe

    /** Commande en attente (prépaiement 62, autorisation véhicule 63, impression 64). */
    public String Deal_Active_Cmd(Row cmd) {
        String text = "";
        try {
            switch (cmd.str("CmdType")) {
                case "62" -> {
                    Row pre = Wire.parseRow(cmd.str("CmdData"));
                    BigDecimal value = dec(pre.get("PreValue"));
                    int num;
                    switch (pre.i("PreType")) {
                        case 1 -> {
                            text += "01";
                            num = value.multiply(BigDecimal.TEN.pow(IniInfo.Vradio)).setScale(0, RoundingMode.HALF_EVEN).intValueExact();
                        }
                        case 2 -> {
                            text += "02";
                            num = 0;
                        }
                        default -> {
                            text += "00";
                            num = value.multiply(BigDecimal.TEN.pow(IniInfo.Mradio)).setScale(0, RoundingMode.HALF_EVEN).intValueExact();
                        }
                    }
                    text += BaseFunction.SetStrLen(Integer.toHexString(num), 8);
                    text += BaseFunction.SetStrLen(Integer.toHexString(Conv.toInt(pre.get("MarkPoint"))), 8);
                    cmd.put("Deal", 99);
                    db().update("CmdList", cmd);
                    text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "001062" + text;
                    text += CRC(text);
                    text = "FA" + StrReplace(text);
                    NozLog.writeLog("PreSet:" + text);
                }
                case "63" -> {
                    Row st = Wire.parseRow(cmd.str("CmdData"));
                    String pl = BaseFunction.SetLenBack(SerialClass.byteToHexStr(nz(st.str("PlNumber")).getBytes(ANSI)), 32);
                    text += BaseFunction.SetStrLen(st.str("Code"), 4);
                    if (st.i("Code") == 0) {
                        text += BaseFunction.SetStrLen(st.str("Asn"), 20);
                        text += BaseFunction.SetStrLen(Integer.toHexString((int) (Conv.toDouble(st.get("Balance")) * Math.pow(10.0, IniInfo.Mradio))), 8);
                        text += BaseFunction.SetStrLen(Integer.toHexString((int) (Conv.toDouble(st.get("Amount")) * Math.pow(10.0, IniInfo.Mradio))), 8);
                    } else {
                        text += BaseFunction.SetStrLen("0", 20) + BaseFunction.SetStrLen("0", 8) + BaseFunction.SetStrLen("0", 8);
                    }
                    text += pl;
                    cmd.put("Deal", 99);
                    int num3 = text.length() / 2 + 1;
                    text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + BaseFunction.SetStrLen(String.valueOf(num3), 4) + "63" + text;
                    text += CRC(text);
                    text = "FA" + StrReplace(text);
                    db().update("CmdList", cmd);
                    NozLog.writeLog("PLNumber Send:" + text);
                }
                case "64" -> {
                    text += SerialClass.byteToHexStr(cmd.str("CmdData").getBytes(ANSI));
                    cmd.put("Deal", 99);
                    int num2 = text.length() / 2 + 1;
                    text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + BaseFunction.SetStrLen(String.valueOf(num2), 4) + "64" + text;
                    text += CRC(text);
                    text = "FA" + StrReplace(text);
                    db().update("CmdList", cmd);
                    NozLog.writeLog("Print:" + text);
                }
                default -> {
                }
            }
            return text;
        } catch (RuntimeException ex) {
            NozLog.writeLog("PreSet Error:" + ex.getMessage());
            return "";
        }
    }

    /** Interrogation : impulsion en attente, commande en attente, sinon heure et versions des listes. */
    public String Cmd_Poll() {
        FrameAdd();
        Row pluse = db().first("NozPluse", "{NozID} = :n AND {Deal} = false ORDER BY {Createtime}", p("n", NozID));
        if (pluse != null) {
            String text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000357" + BaseFunction.ConverToStr(pluse.str("PLUSE"), 4);
            text += CRC(text);
            text = "FA" + StrReplace(text);
            pluse.put("Deal", true);
            db().update("NozPluse", pluse);
            return text;
        }
        Row cmd = db().first("CmdList", "{NozID} = :n AND {Deal} = 0 ORDER BY {CreateTime}", p("n", NozID));
        if (cmd != null) {
            return Deal_Active_Cmd(cmd);
        }
        String text2 = now("yyyyMMddHHmmss");
        Row v = BaseFunction.VersionSet();
        String text3 = v == null ? "000000000000000000"
            : BaseFunction.ConverToStr(hex(v.i("BasicBlack")), 4) + BaseFunction.ConverToStr(hex(v.i("AppendBlack")), 2)
                + BaseFunction.ConverToStr(hex(v.i("DelBlack")), 2) + BaseFunction.ConverToStr(hex(v.i("White")), 2)
                + BaseFunction.ConverToStr(hex(v.i("PPU")), 2) + BaseFunction.ConverToStr(hex(v.i("MachineCfg")), 2) + "0000";
        String text4 = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "001730" + text2 + text3;
        NozLog.writeLog("Poll DataSend:" + text4);
        text4 += CRC(text4);
        return "FA" + StrReplace(text4);
    }

    public String Cmd_GetTransaction() {
        FrameAdd();
        String text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "00053E" + BaseFunction.ConverToStr(hex(PSStart), 8);
        text += CRC(text);
        text = "FA" + text;
        NozLog.writeLog("Cmd_GetTransaction:" + text);
        return text;
    }

    private String Cmd_LockNoz() {
        try {
            FrameAdd();
            String text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000656" + LockTime;
            text += CRC(text);
            text = "FA" + text;
            NozLog.writeLog("Cmd_Noz:" + text);
            return text;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Contrôle du véhicule auprès de la plateforme QY avant remplissage. */
    private String Cmd_Supervivon(String dataBlack) {
        try {
            String asn = dataBlack.substring(14, 34);
            Row info = db().first("CustomerCardInfos", "{ASN} = :a AND {Suspend} = 0", p("a", asn));
            if (info == null) {
                throw new IllegalStateException("Client introuvable");
            }
            Row cus = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID")));
            if (cus == null) {
                throw new IllegalStateException("Client introuvable");
            }
            if (!IniInfo.token.isEmpty()) {
                String color = Integer.toHexString(cus.i("VehicleColorID"));
                Map<String, Object> check = new LinkedHashMap<>();
                check.put("token", IniInfo.token);
                check.put("command", "igasv_czcheck");
                check.put("cphm", cus.get("VehicleNo"));
                check.put("cpcolorcode", color);
                TCPClientQY.TcpSend(Wire.json(check));
                NozLog.writeLog("QY Send:" + Wire.json(check));
                sleep(100);
                String text2 = TCPClientQY.TcpReceive();
                NozLog.writeLog("QY Receive:" + text2);
                Row ret = Wire.parseRow(text2);
                String code = ret == null ? "" : ret.str("checkcode");
                if (code.equals("9000")) {
                    return "00";
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("cpcolor", color);
                data.put("cphm", cus.get("VehicleNo"));
                data.put("czrq", now("yyyy-MM-dd"));
                data.put("czyl", "0");
                data.put("czwd", "0");
                data.put("czmd", "0");
                data.put("czltype", "升");
                data.put("czl", "0");
                data.put("czks", now("yyyy-MM-dd HH:mm:ss"));
                data.put("czjs", now("yyyy-MM-dd HH:mm:ss"));
                data.put("gunno", String.valueOf(NozID));
                data.put("czry", "0");
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("command", "igasv_upczrecord");
                obj.put("token", IniInfo.token);
                obj.put("flag", code);
                obj.put("data", data);
                TCPClientQY.TcpSend(Wire.json(obj));
                NozLog.writeLog("QY Send Refused:" + Wire.json(obj));
                sleep(100);
                text2 = TCPClientQY.TcpReceive();
                NozLog.writeLog("QY Receive Refused:" + text2);
                return code.length() > 4 ? "01" : "02";
            }
            return "04";
        } catch (RuntimeException ex) {
            NozLog.writeLog(String.valueOf(ex.getMessage()));
            return "03";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Taille d'une liste à télécharger (en-tête de 16 octets + 10 octets par carte). */
    public String Cmd_Download(byte[] data) {
        try {
            int num = 0;
            switch (data[7]) {
                case 0 -> {
                    NozLog.writeLog("Dowload Info:Basic Black");
                    num = 16 + db().list("BasicBlackLists").size() * 10;
                }
                case 1 -> {
                    NozLog.writeLog("Dowload Info:Added Black");
                    num = 16 + db().list("AppendBlackLists").size() * 10;
                }
                case 2 -> {
                    NozLog.writeLog("Dowload Info:Deleted Black");
                    num = 16 + db().list("DeleteBlackLists").size() * 10;
                }
                case 3 -> {
                    NozLog.writeLog("Dowload Info:White");
                    num = 16 + db().list("EmployeeCardInfos", "{Status} = '0'", null).size() * 10;
                }
                case 4 -> {
                    NozLog.writeLog("Dowload Info:Oil Info");
                    num = 28;
                }
                case 5 -> {
                    NozLog.writeLog("Dowload Info:Common Info");
                    num = 14;
                }
                case 6 -> NozLog.writeLog("Dowload Info:Private Data");
                case 7 -> NozLog.writeLog("Dowload Info:Programme");
                default -> {
                }
            }
            FrameAdd();
            String t2 = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "000633" + BaseFunction.ConverToStr(hex(num), 8)
                + BaseFunction.ConverToStr(String.valueOf(data[7]), 2);
            String text = frameEnd(t2);
            NozLog.writeLog("Send Cmd_Download Message:" + text);
            return text;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String listSegment(String head, List<Row> list, int num, int num2) {
        StringBuilder sb = new StringBuilder(head);
        for (Row item : list) {
            sb.append(item.str("ASN"));
        }
        String t = sb.toString();
        String rest = t.substring(num * 16 * 2);
        return rest.length() <= num2 * 16 * 2 ? rest : rest.substring(0, num2 * 16 * 2);
    }

    private static String listHead(int version, int count, boolean versionHex) {
        String today = now("yyyyMMdd");
        String end = LocalDateTime.now().plusDays(365).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return BaseFunction.ConverToStr(versionHex ? hex(version) : String.valueOf(version), 4) + today + end + "FFFF"
            + BaseFunction.ConverToStr(hex(count), 8);
    }

    /** Segment d'une liste (noire de base, ajouts, suppressions, blanche), du prix ou de la configuration. */
    public String Cmd_Dowload_Data(byte[] data, String segs) {
        String text = "";
        try {
            FrameAdd();
            Row v = BaseFunction.VersionSet();
            int num = Integer.parseInt(segs.substring(0, 4), 16);
            int num2 = Integer.parseInt(segs.substring(4, 6), 16);
            NozLog.writeLog("StartSeg:" + num);
            NozLog.writeLog("SegNo:" + num2);
            String head = BaseFunction.ConverToStr(hex(Port), 2) + "00" + BaseFunction.ConverToStr(hex(Frame), 2);
            switch (data[7]) {
                case 0, 1, 2, 3 -> {
                    String[] tables = {"BasicBlackLists", "AppendBlackLists", "DeleteBlackLists", "EmployeeCardInfos"};
                    String[] logs = {"Download Message Basic Black", "Download Message Append Black", "Download Message Delete Black",
                        "Download Message White"};
                    String[] ver = {"BasicBlack", "AppendBlack", "DelBlack", "White"};
                    int k = data[7];
                    List<Row> list = k == 3 ? db().list("EmployeeCardInfos", "{Status} = '0'", null) : db().list(tables[k]);
                    NozLog.writeLog(logs[k]);
                    // La liste blanche écrit sa version en décimal (Convert.ToString sans base), comme l'original
                    String seg = listSegment(listHead(v.i(ver[k]), list.size(), k != 3), list, num, num2);
                    text = head + BaseFunction.ConverToStr(String.valueOf(5 + seg.length() / 2), 4) + "340" + k + segs + seg;
                }
                case 4 -> {
                    Row noz = db().queryFirst("SELECT {GunConfig.GUNCODE} AS NozID, {tankConfig.OilCode} AS NozOilType FROM [GunConfig]"
                        + " JOIN [tankConfig] ON {GunConfig.TANKCODE} = {tankConfig.ID} WHERE {GunConfig.GUNCODE} = :n LIMIT 1", p("n", NozID));
                    Row pr = db().queryFirst("SELECT {GradeSpecifics.Code} AS Code, {PriceInfos.CurrentDensity} AS CurrentDensity,"
                        + " {PriceInfos.CurrentPPU} AS CurrentPPU, {PriceInfos.Newdensity} AS Newdensity, {PriceInfos.NewPPU} AS NewPPU,"
                        + " {PriceInfos.ValidateTime} AS ValidateTime FROM [GradeSpecifics] JOIN [PriceInfos] ON {GradeSpecifics.Code} = {PriceInfos.Code}"
                        + " WHERE {GradeSpecifics.Code} = :c LIMIT 1", p("c", noz.str("NozOilType")));
                    String t2 = pr.dt("ValidateTime").format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
                    int w = IniInfo.DigitsType == 0 ? 4 : 6;
                    String cur = BaseFunction.ConverToStr(hex(round(dec(pr.get("CurrentPPU")), IniInfo.Pradio).multiply(BigDecimal.valueOf(IniInfo.PFormat)).intValue()), w);
                    String nw = BaseFunction.ConverToStr(hex(round(dec(pr.get("NewPPU")), IniInfo.Pradio).multiply(BigDecimal.valueOf(IniInfo.PFormat)).intValue()), w);
                    String nozHex = BaseFunction.ConverToStr(hex(NozID), 2);
                    text = head + (IniInfo.DigitsType == 0 ? "00333404" : "00353404") + segs + BaseFunction.ConverToStr(hex(v.i("PPU")), 2) + t2 + "01"
                        + nozHex + pr.str("Code") + BaseFunction.ConverToStr(Conv.num(Conv.toDouble(pr.get("CurrentDensity")) * 10000.0), 8) + "01" + cur
                        + nozHex + pr.str("Code") + BaseFunction.ConverToStr(Conv.num(Conv.toDouble(pr.get("Newdensity")) * 10000.0), 8) + "01" + nw;
                }
                case 5 -> {
                    Row st = db().first("Stations");
                    text = BaseFunction.ConverToStr(String.valueOf(Port), 2) + "00" + hex(Frame) + "00213405" + segs;
                    text += BaseFunction.ConverToStr(hex(v.i("MachineCfg")), 2);
                    text += "0000";
                    text += BaseFunction.ConverToStr(st.str("SuperCode"), 8);
                    text += BaseFunction.ConverToStr(st.str("Code"), 8);
                    text += BaseFunction.ConverToStr(hex(Port), 2);
                    text += "01";
                    text += BaseFunction.ConverToStr(hex(NozID), 2);
                    text += BaseFunction.ConverToStr(IniInfo.platform, 4);
                    NozLog.writeLog("Noz DowLoad Machine:" + text);
                }
                default -> {
                }
            }
            text += CRC(text);
            text = "FA" + StrReplace(text);
        } catch (RuntimeException ex) {
            NozLog.writeLog("Error:" + ex.getMessage());
            text = "";
        }
        NozLog.writeLog("Send Download Data Message:" + text);
        return text;
    }

    public String Cmd_HandTransaction(String cache) {
        try {
            return cache.length() >= 286 ? HandTransactionNew(cache) : HandTransactionOld(cache);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String Cmd_QueryWhiteAndBlack(String cache) {
        String asn = cache.substring(14, 34);
        try {
            boolean listed;
            if (asn.startsWith("010004")) {
                listed = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn)) != null;
            } else {
                listed = db().first("BasicBlackLists", "{ASN} = :a", p("a", asn)) != null
                    || db().first("AppendBlackLists", "{ASN} = :a", p("a", asn)) != null
                    || db().first("DeleteBlackLists", "{ASN} = :a", p("a", asn)) != null;
            }
            String text = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "001236" + (listed ? "00" : "01") + asn;
            text = frameEnd(text);
            NozLog.writeLog("Liste noire (confirmation) : " + text);
            return text;
        } catch (RuntimeException ex) {
            String text = frameEnd(BaseFunction.ConverToStr(hex(NozID), 2) + "00" + hex(Frame) + "00123601" + asn);
            NozLog.writeLog("Liste noire (erreur) : " + ex.getMessage());
            return text;
        }
    }

    private String Cmd_GetGrayTransaction(String cache) {
        try {
            String asn = cache.substring(14, 34);
            Row o = db().first("OilTransactions", "{Asn} = :a AND {TType} <> 0 AND {TType} <> 5 AND {TType} <> 6 ORDER BY {TrueTime} DESC",
                p("a", asn));
            String text;
            if (o != null) {
                String t2 = Conv.toDateTime(o.get("TrueTime")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String t3 = o.str("Asn") + HexFormat((int) (Conv.toDouble(o.get("BAL")) * IniInfo.MFormat), 8)
                    + HexFormat((int) (Conv.toDouble(o.get("TruelyPay")) * IniInfo.MFormat), 6)
                    + HexFormat((int) (Conv.toDouble(o.get("PreferentialPrice")) * IniInfo.MFormat), 4)
                    + HexFormat((int) (Conv.toDouble(o.get("Discount")) * 100.0), 4) + HexFormat((int) (Conv.toDouble(o.get("AMN")) * IniInfo.MFormat), 6)
                    + o.str("CTC") + BaseFunction.ConverToStr(o.str("DS"), 2) + t2 + o.str("GMAC") + o.str("PSAMTID") + o.str("PSAMTTC");
                text = HexFormat(Port, 2) + "00" + hex(Frame) + "00503500" + t3;
            } else {
                text = HexFormat(Port, 2) + "00" + hex(Frame) + "00023501";
            }
            text = frameEnd(text);
            NozLog.writeLog("Transactions grises (résultat) : " + text);
            return text;
        } catch (RuntimeException ex) {
            NozLog.writeLog(String.valueOf(ex.getMessage()));
            return frameEnd(HexFormat(Port, 2) + "00" + hex(Frame) + "00503501");
        }
    }

    // ------------------------------------------------------------ contrôle des cartes de crédit

    private static final class Suspend extends RuntimeException {
        Suspend(String m) {
            super(m, null, false, false);
        }
    }

    private CreditCardInfo CheckStatue(Row cus, BigDecimal bal, BigDecimal preAmount) {
        CreditCardInfo ci = new CreditCardInfo();
        try {
            ci.Pass = cus.get("CipherFlg") == null ? "9999" : cus.str("CipherFlg");
            ci.Asn = cus.str("ASN");
            int limitType = Conv.toInt(cus.get("LimitType"));
            ci.LimitVehicleNo = Conv.toBool(cus.get("LimitVehicleNo")) ? 0 : 255;
            String vehicleNo = cus.str("VehicleNo");
            ci.VehicleNo = !vehicleNo.isEmpty() ? (BaseFunction.ConvertToHex(vehicleNo) + "202020202020202020202020202020").substring(0, 30)
                : "202020202020202020202020202020";
            ci.MileageTplerance = Conv.toInt(cus.get("MileageTolerance"));
            ci.SuspendType = Conv.toDouble(cus.get("OiConsumption")) <= 0.0 ? 0 : 1;
            switch (Conv.toInt(cus.get("Suspend"))) {
                case 1 -> {
                    ci.SuspendType = 14;
                    throw new Suspend("Carte déclarée perdue");
                }
                case 2, 3, 4, 5, 6, 7 -> {
                    ci.SuspendType = 14;
                    throw new Suspend("La carte n'est pas dans un état normal");
                }
                case 8 -> {
                    ci.SuspendType = 4;
                    throw new Suspend("La carte n'est pas dans un état normal");
                }
                case 9 -> {
                    ci.SuspendType = 3;
                    throw new Suspend("La carte n'est pas dans un état normal");
                }
                case 10 -> {
                    ci.SuspendType = 2;
                    throw new Suspend("La carte n'est pas dans un état normal");
                }
                default -> {
                }
            }
            String confine = cus.str("UseConfine");
            switch (limitType) {
                case 1 -> {
                    if (confine.indexOf(stations.str("RegionCodeInner")) <= 0) {
                        ci.SuspendType = 11;
                        throw new Suspend("Région non autorisée");
                    }
                }
                case 2 -> {
                    if (confine.indexOf(stations.str("CityCodeInner")) <= 0) {
                        ci.SuspendType = 12;
                        throw new Suspend("Ville non autorisée");
                    }
                }
                case 4 -> {
                    if (confine.indexOf(stations.str("Code")) <= 0) {
                        ci.SuspendType = 13;
                        throw new Suspend("Station non autorisée");
                    }
                }
                default -> {
                }
            }
            String limitOil = cus.get("LimitOil") == null ? "" : cus.str("LimitOil");
            if (!limitOil.isEmpty()) {
                String code = String.valueOf(NozOilTypeCode);
                if (limitOil.substring(1).equals("000")) {
                    if (!(code.charAt(0) + "000").equals(limitOil)) {
                        ci.SuspendType = 6;
                        throw new Suspend("Énergie non autorisée");
                    }
                } else if (!limitOil.equals(code)) {
                    ci.SuspendType = 6;
                    throw new Suspend("Énergie non autorisée");
                }
            }
            ci.PreferentialPrice = round(dec(cus.get("PreferentialPrice")), IniInfo.Mradio);
            ci.Discount = round(dec(cus.get("Discount")), 0);
            ci.OiConsumption = round(dec(cus.get("OiConsumption")), 2);
            Row acc = db().first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", cus.get("CustomerID")));
            if (acc == null) {
                ci.SuspendType = 5;
                throw new Suspend("Compte introuvable");
            }
            double num2 = Conv.round(bal.doubleValue(), IniInfo.Mradio);
            int num3 = Conv.toInt(acc.get("TotalTimesDaily"));
            double num4 = Conv.round(Conv.toDouble(acc.get("TotalAmountDaily")), IniInfo.Mradio);
            double num5 = Conv.round(Conv.toDouble(acc.get("TotalAmountMonthly")), IniInfo.Mradio);
            double num6 = Conv.round(Conv.toDouble(acc.get("TotalVolumeMonthly")), IniInfo.Vradio);
            double num7 = Conv.round(Conv.toDouble(acc.get("TotalVolumeDaily")), 0);
            ci.MileageLastest = dec(acc.get("MileageLastest"));
            ci.VolumeLastest = dec(acc.get("VolumeLastest"));
            cus.put("UpdateDatetime", acc.get("UpdateDatetime"));
            NozLog.writeLog("UpdateTime:" + Conv.toDateTime(cus.get("UpdateDatetime")).format(Conv.STD));
            CreditCount cc = CheckCredit(cus);
            if (cc != null) {
                double money = Conv.round(cc.Money.doubleValue(), IniInfo.Mradio);
                num2 -= money;
                num3 += cc.Count;
                num4 += money;
                num5 += money;
                num6 += Conv.round(cc.Volume.doubleValue(), IniInfo.Vradio);
                ci.MileageLastest = round(BigDecimal.valueOf(cc.MileageLastest), IniInfo.Mradio);
                ci.VolumeLastest = round(cc.VolumeLastest, IniInfo.Vradio);
            }
            if (num2 <= 0.0) {
                num2 = 0.0;
            }
            int limitTime = Conv.toInt(cus.get("LimitTime"));
            double limitVolume = Conv.toDouble(cus.get("LimitVolume"));
            double limitAmount = Conv.toDouble(cus.get("LimitAmount"));
            double limitAmountMonthly = Conv.toDouble(cus.get("LimitAmountMonthly"));
            double limitVolumeMonthly = Conv.toDouble(cus.get("LimitVolumeMonthly"));
            if (limitTime > 0 && num3 >= limitTime) {
                ci.SuspendType = 7;
                throw new Suspend("Nombre de pleins par jour dépassé");
            }
            if (limitVolume > 0.0 && num7 >= limitVolume) {
                ci.SuspendType = 7;
                throw new Suspend("Limite de litres par jour dépassée");
            }
            if (limitAmount > 0.0 && num4 >= limitAmount) {
                ci.SuspendType = 8;
                throw new Suspend("Limite de montant par jour dépassée");
            }
            if (limitAmountMonthly > 0.0 && num5 >= limitAmountMonthly) {
                ci.SuspendType = 9;
                throw new Suspend("Limite de montant par mois dépassée");
            }
            if (limitVolumeMonthly > 0.0 && num6 > limitVolumeMonthly) {
                ci.SuspendType = 10;
                throw new Suspend("Limite de litres par mois dépassée");
            }
            ci.MaxVolume = BigDecimal.valueOf(-1);
            if (limitVolume > 0.0 && limitVolumeMonthly == 0.0) {
                ci.MaxVolume = BigDecimal.valueOf(limitVolume - num7);
            }
            if (limitVolume == 0.0 && limitVolumeMonthly > 0.0) {
                ci.MaxVolume = BigDecimal.valueOf(limitVolumeMonthly - num6);
            }
            if (limitVolume > 0.0 && limitVolumeMonthly > 0.0) {
                ci.MaxVolume = BigDecimal.valueOf(IsMin(limitVolumeMonthly - num6, limitVolume - num7));
            }
            if (ci.MaxVolume.compareTo(BigDecimal.ONE) < 0 && ci.MaxVolume.compareTo(BigDecimal.valueOf(-1)) != 0) {
                ci.SuspendType = 10;
                throw new Suspend("Le plein doit faire au moins 1 litre");
            }
            ci.MaxAmount = round(BigDecimal.valueOf(num2), IniInfo.Mradio);
            if (limitAmount > 0.0 && limitAmountMonthly == 0.0) {
                ci.MaxAmount = BigDecimal.valueOf(limitAmount - num4);
            }
            if (limitAmount == 0.0 && limitAmountMonthly > 0.0) {
                ci.MaxAmount = BigDecimal.valueOf(limitAmountMonthly - num5);
            }
            if (limitAmount > 0.0 && limitAmountMonthly > 0.0) {
                ci.MaxAmount = BigDecimal.valueOf(IsMin(limitAmount - num4, limitAmountMonthly - num5));
            }
            if (preAmount.signum() != 0) {
                ci.MaxAmount = BigDecimal.valueOf(IsMin(ci.MaxAmount.doubleValue(), preAmount.doubleValue()));
            }
            ci.Balance = round(BigDecimal.valueOf(num2), IniInfo.Mradio);
            return ci;
        } catch (RuntimeException e) {
            return ci;
        }
    }

    private String AppCodeDeal(String code) {
        try {
            StringBuilder text = new StringBuilder();
            boolean flag = false;
            for (int n = code.length() - 1; n >= 0; n--) {
                if (!flag) {
                    if (code.charAt(n) != '0') {
                        flag = true;
                        text.insert(0, code.charAt(n));
                    }
                } else {
                    text.insert(0, code.charAt(n));
                }
            }
            return new String(SerialClass.strToToHexByte(text.toString()), StandardCharsets.US_ASCII);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Paiement par l'application mobile (code affiché sur le téléphone). */
    private boolean DealApp(String catchData, String[] returnData) {
        try {
            Row user = Row.of("Code", AppCodeDeal(catchData.substring(34, 74)));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/AppCodeUserDeal", Wire.json(user));
            boolean flag;
            if (r.Result()) {
                Row ret = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
                String text = BaseFunction.SetStrLen(Long.toHexString(dec(ret.get("Amount")).multiply(BigDecimal.valueOf(10000)).setScale(0, RoundingMode.HALF_EVEN).longValue()), 12);
                String text2 = BaseFunction.SetStrLen(Long.toHexString(dec(ret.get("Balance")).multiply(BigDecimal.valueOf(10000)).setScale(0, RoundingMode.HALF_EVEN).longValue()), 12);
                returnData[0] = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "00325200" + BaseFunction.SetStrLen(ret.str("Asn"), 20) + text
                    + "FFFFFFFFFFFF" + text2 + HexFormat(i(mul(100, dec(ret.get("Discount")))), 4);
                flag = true;
            } else {
                returnData[0] = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "00325201";
                flag = false;
            }
            returnData[0] = frameEnd(returnData[0]);
            NozLog.writeLog("App Return:" + returnData[0]);
            return flag;
        } catch (RuntimeException ex) {
            NozLog.writeLog("App Error:" + ex.getMessage());
            returnData[0] = BaseFunction.ConverToStr(hex(Port), 2) + "00" + hex(Frame) + "00325201";
            return false;
        }
    }

    /** Carte secondaire d'un groupe : solde du compte du groupe. */
    private CreditCardInfo Cmd_MasterCheck(String cache) {
        CreditCardInfo ci = new CreditCardInfo();
        try {
            String asn = cache.substring(14, 34);
            Row cus = db().first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
            if (cus == null) {
                // Comme l'original : la requête suivante échoue sur une carte inconnue
                throw new IllegalStateException("Object reference not set to an instance of an object.");
            }
            if (db().first("Stations") == null) {
                ci.SuspendType = 5;
                throw new Suspend("Station non valide");
            }
            Row ga = db().first("GroupAccounts", "{AccountNo} = :g", p("g", cus.str("GroupID")));
            if (ga == null) {
                ci.SuspendType = 5;
                throw new Suspend("Groupe introuvable");
            }
            Row gc = db().first("GroupCardInfos", "{GroupID} = :g AND ({CardStatusTypeID} = 0 OR {CardStatusTypeID} = 9)", p("g", cus.get("GroupID")));
            if (gc == null || gc.i("CardStatusTypeID") == 1) {
                ci.SuspendType = 5;
                throw new Suspend("Le compte du groupe est bloqué");
            }
            BigDecimal num = round(dec(ga.get("Balance")), IniInfo.Mradio);
            Row gc2 = db().first("GroupCardInfos", "{GroupID} = :g", p("g", cus.get("GroupID")));
            if (gc2 != null && num.compareTo(dec(gc2.get("MinBalance"))) < 0) {
                ci.SuspendType = 9;
            } else {
                ci = CheckStatue(cus, num, BigDecimal.ZERO);
            }
        } catch (RuntimeException ex) {
            NozLog.writeLog(String.valueOf(ex.getMessage()));
        }
        return ci;
    }

    /** Carte de crédit / carte RFID : compte du client (et du groupe si le crédit n'est pas payé d'avance). */
    private CreditCardInfo Cmd_CreditCheck(String cache) {
        CreditCardInfo ci = new CreditCardInfo();
        try {
            String asn = cache.substring(14, 34);
            Row credit = db().first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
            BigDecimal preAmount = BigDecimal.ZERO;
            if (credit != null) {
                if (db().first("Stations") == null) {
                    ci.SuspendType = 5;
                    throw new Suspend("Station non valide");
                }
                Row acc = db().first("CustomerAccounts", "{CustomerAccountID} = :c", p("c", credit.get("CustomerID")));
                if (acc == null) {
                    ci.SuspendType = 5;
                    throw new Suspend("Compte client introuvable");
                }
                if (asn.indexOf("E2") != 0) {
                    if (!IniInfo.CreditPayAdvance) {
                        Row gci = db().first("GroupCardInfos", "{GroupID} = :g", p("g", credit.get("GroupID")));
                        if (gci == null) {
                            ci.SuspendType = 5;
                            throw new Suspend("Groupe introuvable");
                        }
                        Row ga = db().first("GroupAccounts", "{AccountNo} = :g", p("g", gci.str("GroupID")));
                        if (ga == null) {
                            throw new Suspend("Compte du groupe introuvable");
                        }
                        if (Conv.toDouble(ga.get("Balance")) <= 0.0) {
                            ci.SuspendType = 5;
                            throw new Suspend("Solde du groupe insuffisant");
                        }
                    }
                } else {
                    preAmount = new BigDecimal(cache.substring(38, 46)).divide(BigDecimal.valueOf(10000));
                }
                return CheckStatue(credit, dec(acc.get("Balance")), preAmount);
            }
            ci.SuspendType = 5;
            throw new Suspend("Client introuvable");
        } catch (RuntimeException ex) {
            NozLog.writeLog(String.valueOf(ex.getMessage()));
        }
        return ci;
    }

    private static double IsMin(double a, double b) {
        return a >= b ? b : a;
    }

    private String CheckVolume(BigDecimal vol) {
        if (vol.compareTo(BigDecimal.valueOf(-1)) == 0) {
            return "FFFFFFFF";
        }
        return BaseFunction.ConverToStr(hex(i(mul(IniInfo.VFormat, vol))), 8);
    }

    private String CheckRVolume(BigDecimal vol) {
        if (vol.compareTo(BigDecimal.valueOf(-1)) == 0) {
            return "FFFFFFFFFF";
        }
        return BaseFunction.ConverToStr(hex(i(mul(IniInfo.VFormat, vol))), 10);
    }

    private static String HexFormat(long val, int len) {
        return BaseFunction.ConverToStr(hex(val), len);
    }

    private CreditCount CheckCredit(Row cus) {
        try {
            LocalDateTime time = Conv.toDateTime(cus.get("UpdateDatetime"));
            List<Row> list = db().list("OilTransactions", "{Asn} = :a ORDER BY {TrueTime}", p("a", cus.get("ASN"))).stream()
                .filter(t -> Conv.toDateTime(t.get("TrueTime")).isAfter(time)).toList();
            if (list.isEmpty()) {
                return null;
            }
            CreditCount cc = new CreditCount();
            for (Row item : list) {
                cc.Asn = item.str("Asn");
                cc.Volume = cc.Volume.add(dec(item.get("Volume")));
                cc.Money = cc.Money.add(dec(item.get("TruelyPay")));
                cc.Count++;
                cc.VolumeLastest = dec(item.get("Volume"));
                cc.MileageLastest = Conv.toInt(item.get("Mileage"));
                cc.TransDt = Conv.toDateTime(item.get("TrueTime"));
            }
            return cc;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ affichage (Invoke depuis les fils série)

    private void VLabelC(int i) {
        try {
            synchronized (this) {
                UpdateVolumeTotal(i);
            }
        } catch (RuntimeException ex) {
            NozLog.writeLog("update Volume Total:" + ex.getMessage());
        }
    }

    private void LabelC(int i) {
        synchronized (this) {
            boolean zh = "zh-CN".equals(IniInfo.Language);
            if (i == 0) {
                c("FuelType").BackColor("LightGreen");
                c("FuelType").Text(zh ? "通讯正常" : "Communication établie");
            } else {
                c("FuelType").BackColor("LightGray");
                c("FuelType").Text(zh ? "通讯失败" : "Échec de la communication");
            }
        }
    }

    private void NozW(int i) {
        synchronized (this) {
            NozChangeByRoll(i);
        }
    }

    private void NozChange(int i) {
        synchronized (this) {
            NozChangeByKeyboard(NozID);
        }
    }

    // ------------------------------------------------------------ transactions

    private static String ansi(String hexText) {
        return new String(SerialClass.strToToHexByte(hexText), ANSI).replace("\0", "");
    }

    private static int h(String s) {
        return Integer.parseInt(s, 16);
    }

    /** Transaction au format récent (≥ 286 caractères) : enregistrement, supervision, SMS. */
    public String HandTransactionNew(String cache) {
        String text;
        int ttype = 0;
        var tx = db().begin();
        try {
            String value = cache.substring(24, 28) + "-" + cache.substring(28, 30) + "-" + cache.substring(30, 32) + " " + cache.substring(32, 34) + ":"
                + cache.substring(34, 36) + ":" + cache.substring(36, 38);
            Row oil = new Row();
            boolean d0 = IniInfo.DigitsType == 0;
            // décalages des champs : format à 6 chiffres (DigitsType 0) ou à 8 chiffres
            int k = d0 ? 0 : 2;
            oil.put("POSTTC", cache.substring(14, 22));
            oil.put("TType", Integer.parseInt(cache.substring(22, 24)));
            ttype = oil.i("TType");
            oil.put("TrueTime", Conv.toDateTime(value).format(Conv.STD));
            oil.put("Asn", cache.substring(38, 58));
            oil.put("BAL", Conv.round((double) h(cache.substring(58, 66)) / IniInfo.MFormat, IniInfo.Mradio));
            oil.put("AMN", Conv.round((double) h(cache.substring(66, d0 ? 72 : 74)) / IniInfo.MFormat, IniInfo.Mradio));
            oil.put("CTC", cache.substring(72 + k, 76 + k));
            oil.put("TAC", cache.substring(76 + k, 84 + k));
            oil.put("GMAC", cache.substring(84 + k, 92 + k));
            oil.put("PSAMASN", cache.substring(100 + k, 120 + k));
            oil.put("PSAMTID", cache.substring(120 + k, 132 + k));
            oil.put("PSAMTTC", cache.substring(132 + k, 140 + k));
            oil.put("DS", h(cache.substring(140 + k, 142 + k)));
            oil.put("Unit", h(cache.substring(142 + k, 144 + k)));
            oil.put("CardType", Integer.parseInt(cache.substring(144 + k, 146 + k)));
            oil.put("Version", Integer.parseInt(cache.substring(146 + k, 148 + k)));
            oil.put("GunId", h(cache.substring(148 + k, 150 + k)));
            oil.put("GradeSpecificID", BaseFunction.GetOilIDByCode(Integer.parseInt(cache.substring(150 + k, 154 + k))));
            if (d0) {
                oil.put("Volume", Conv.round((double) h(cache.substring(154, 160)) / IniInfo.VFormat, IniInfo.Vradio));
                oil.put("PPU", Conv.round((double) h(cache.substring(160, 164)) / IniInfo.PFormat, IniInfo.Pradio));
                oil.put("EmployeeCode", h(cache.substring(164, 166)));
                oil.put("EmployeeID", GetEmpID(oil.str("EmployeeCode")));
                oil.put("VolumeTotal", Conv.round((double) h(cache.substring(166, 174)) / IniInfo.VFormat, IniInfo.Vradio));
                oil.put("PreferentialPrice", Conv.round((double) h(cache.substring(174, 178)) / IniInfo.MFormat, IniInfo.Mradio));
                oil.put("Discount", Conv.round((double) h(cache.substring(178, 182)) / 100.0, 2));
                oil.put("TruelyPay", Conv.round((double) h(cache.substring(182, 188)) / IniInfo.MFormat, IniInfo.Mradio));
                oil.put("Mileage", (double) h(cache.substring(188, 194)));
                oil.put("Coupon", oil.i("Unit") == 224 ? String.valueOf(h(cache.substring(196, 212))) : ansi(cache.substring(194, 234)));
                oil.put("CouponValue", (double) h(cache.substring(234, 240)));
                oil.put("CarNumber", ansi(cache.substring(240, 272)));
                oil.put("MAC", cache.substring(272, 280));
            } else {
                oil.put("Volume", Conv.round((double) h(cache.substring(156, 164)) / IniInfo.VFormat, IniInfo.Vradio));
                oil.put("PPU", Conv.round((double) h(cache.substring(164, 170)) / IniInfo.PFormat, IniInfo.Pradio));
                oil.put("EmployeeID", GetEmpID(String.valueOf(h(cache.substring(170, 172)))));
                oil.put("VolumeTotal", Conv.round((double) h(cache.substring(172, 180)) / IniInfo.VFormat, IniInfo.Vradio));
                oil.put("PreferentialPrice", Conv.round((double) h(cache.substring(180, 184)) / IniInfo.MFormat, IniInfo.Mradio));
                oil.put("Discount", Conv.round((double) h(cache.substring(184, 188)) / 100.0, 2));
                oil.put("TruelyPay", Conv.round((double) h(cache.substring(188, 196)) / IniInfo.MFormat, IniInfo.Mradio));
                oil.put("Mileage", (double) h(cache.substring(196, 202)));
                oil.put("Coupon", oil.i("Unit") == 224 ? String.valueOf(h(cache.substring(196, 212))) : ansi(cache.substring(196, 236)));
                oil.put("CouponValue", (double) h(cache.substring(242, 248)));
                oil.put("CarNumber", ansi(cache.substring(248, 280)));
                oil.put("MAC", cache.substring(280, 288));
            }
            oil.put("StationID", stations.get("StationID"));
            oil.put("POSP", NozID);
            oil.put("CardType", switch (oil.str("Asn").substring(0, 6)) {
                case "010001" -> 1;
                case "010004" -> 4;
                case "010011" -> 11;
                default -> 0;
            });
            oil.put("Shift", "1");
            oil.put("upload", "0");
            oil.put("Flg", "1");
            oil.put("BankSID", "");
            oil.put("ErrorFlgID", 0);
            oil.put("GTAC", "");
            oil.put("TankDeal", !IniInfo.NonLevel);
            Asn = oil.str("Asn");
            AMN = Conv.num(Conv.round(Conv.toDouble(oil.get("AMN")), IniInfo.Mradio));
            Vol = Conv.num(Conv.round(Conv.toDouble(oil.get("Volume")), IniInfo.Vradio));
            Row card = db().first("CustomerCardInfos", "{ASN} = :a", p("a", oil.get("Asn")));
            if (card != null) {
                oil.put("CarNumber", card.get("VehicleNo"));
            }
            if (db().first("OilTransactions", "{GunId} = :g AND {POSTTC} = :t AND {TrueTime} = :tt",
                p("g", oil.get("GunId"), "t", oil.get("POSTTC"), "tt", Conv.toDateTime(oil.get("TrueTime")))) == null) {
                if (IniInfo.SendMessage == 1) {
                    SendNote(oil);
                }
                if (IniInfo.SupervisionType == 2 && oil.i("Unit") == 241) {
                    String[] error = {""};
                    if (!SupervisionFunction.FoRanTransactionUp(oil, error)) {
                        oil.put("Reserved2", "0");
                        NozLog.writeLog("FoRan:" + error[0]);
                    } else {
                        oil.put("Reserved2", "1");
                        NozLog.writeLog("FoRan:Success");
                    }
                    Row car = db().first("CarUser", "{PlateNumber} = :p", p("p", oil.get("CarNumber")));
                    if (car != null) {
                        oil.put("BAL", Conv.round(dec(car.get("Balance")).subtract(dec(oil.get("TruelyPay"))).doubleValue(), 3));
                    }
                }
                oil.put("UploadTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()));
                db().insert("OilTransactions", oil);
            }
            tx.commit();
            text = BaseFunction.ConverToStr(hex(NozID), 2) + "000200023200";
        } catch (RuntimeException ex) {
            NozLog.writeLog("Oil Insert Error" + ex.getMessage());
            tx.rollback();
            text = BaseFunction.ConverToStr(hex(NozID), 2) + "000200023200";
        }
        text = frameEnd(text);
        VLabelC(NozID);
        if (ttype == 8) {
            NozChange(NozID);
        }
        return text;
    }

    /** SMS de notification au client après une transaction (service sierrahive, identifiants dans la configuration). */
    private void SendNote(Row oil) {
        try {
            BaseFunction.Holder hm = BaseFunction.GetCustomerMess(oil.str("Asn"));
            if (hm.ok()) {
                Row acc = hm.c();
                String text = "Transaction ID:" + oil.str("ID") + "\n" + "Date and Time:" + oil.str("TrueTime") + "\n" + "Station Name:"
                    + BaseFunction.GetStationName(oil.i("StationID")) + "\n" + "Card number:" + oil.str("Asn") + "\n" + "Previous Amount :"
                    + acc.str("Balance") + "\n" + "Dispense /fueling amount:" + oil.str("TruelyPay") + "\n" + "Current Balance:" + acc.str("Balance") + "\n";
                Map<String, String> dic = new LinkedHashMap<>();
                dic.put("clientid", IniInfo.SmsClientId);
                dic.put("clientsecret", IniInfo.SmsClientSecret);
                dic.put("token", IniInfo.SmsToken);
                dic.put("from", "NP");
                dic.put("to", hm.a().str("Telephone"));
                dic.put("reference", "TXNREF123");
                dic.put("content", text);
                String[] result = {""};
                String[] error = {""};
                if (!BaseFunction.HTTPGetWithParam("http://api.sierrahive.com/v1/messages/sms", dic, result, error)) {
                    NozLog.writeLog("Message Send:" + result[0] + "  Error:" + error[0]);
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Transaction à l'ancien format (< 286 caractères). */
    public String HandTransactionOld(String cache) {
        String text;
        var tx = db().begin();
        try {
            String trueTime = cache.substring(24, 28) + "-" + cache.substring(28, 30) + "-" + cache.substring(30, 32) + " " + cache.substring(32, 34) + ":"
                + cache.substring(34, 36) + ":" + cache.substring(36, 38);
            Row oil = new Row();
            oil.put("POSP", NozID);
            oil.put("POSTTC", cache.substring(14, 22));
            oil.put("TType", Integer.parseInt(cache.substring(22, 24)));
            oil.put("TrueTime", trueTime);
            oil.put("Asn", cache.substring(38, 58));
            oil.put("BAL", Conv.round((double) h(cache.substring(58, 66)) / IniInfo.MFormat, IniInfo.Mradio));
            oil.put("AMN", Conv.round((double) h(cache.substring(66, 72)) / IniInfo.MFormat, IniInfo.Mradio));
            oil.put("CTC", cache.substring(72, 76));
            oil.put("TAC", cache.substring(76, 84));
            oil.put("GMAC", cache.substring(84, 92));
            oil.put("PSAMASN", cache.substring(100, 120));
            oil.put("PSAMTID", cache.substring(120, 132));
            oil.put("PSAMTTC", cache.substring(132, 140));
            oil.put("DS", h(cache.substring(140, 142)));
            oil.put("Unit", Integer.parseInt(cache.substring(142, 144)));
            oil.put("CardType", Integer.parseInt(cache.substring(144, 146)));
            oil.put("Version", Integer.parseInt(cache.substring(146, 148)));
            oil.put("GunId", h(cache.substring(148, 150)));
            oil.put("GradeSpecificID", BaseFunction.GetOilIDByCode(Integer.parseInt(cache.substring(150, 154))));
            oil.put("Volume", Conv.round((double) h(cache.substring(154, 160)) / IniInfo.VFormat, IniInfo.Vradio));
            oil.put("PPU", Conv.round((double) h(cache.substring(160, 164)) / IniInfo.PFormat, IniInfo.Pradio));
            oil.put("EmployeeCode", h(cache.substring(164, 166)));
            oil.put("EmployeeID", GetEmpID(oil.str("EmployeeCode")));
            oil.put("VolumeTotal", Conv.round((double) h(cache.substring(166, 174)) / IniInfo.VFormat, IniInfo.Vradio));
            oil.put("PreferentialPrice", oil.get("AMN"));
            oil.put("Discount", 100.0);
            oil.put("TruelyPay", oil.get("AMN"));
            oil.put("Mileage", 0.0);
            oil.put("Coupon", "-");
            oil.put("CouponValue", 0.0);
            oil.put("VerifyNO", ansi(cache.substring(174, 186)));
            oil.put("Shift", "1");
            oil.put("upload", "0");
            oil.put("MAC", cache.substring(196, 204));
            oil.put("Flg", "1");
            oil.put("GTAC", "");
            oil.put("Reserved2", "0");
            oil.put("StationID", IniInfo.StationID);
            if (db().first("OilTransactions", "{GunId} = :g AND {POSTTC} = :t AND {TrueTime} = :tt",
                p("g", oil.get("GunId"), "t", oil.get("POSTTC"), "tt", Conv.toDateTime(trueTime))) == null) {
                oil.put("UploadTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now()));
                db().insert("OilTransactions", oil);
            }
            tx.commit();
            text = BaseFunction.ConverToStr(hex(NozID), 2) + "000200023200";
            VLabelC(NozID);
            if (oil.i("TType") == 8) {
                NozChange(NozID);
            }
        } catch (RuntimeException ex) {
            tx.rollback();
            NozLog.writeLog("Old Oil Insert Error" + ex.getMessage());
            text = BaseFunction.ConverToStr(hex(NozID), 2) + "000200023200";
        }
        return frameEnd(text);
    }

    // ------------------------------------------------------------ fil d'interrogation

    private void SendData() {
        while (IniInfo.ThreadStop) {
            try {
                NozLog.writeLog("OnCommitFlg:" + (OnCommitFlg ? "True" : "False"));
                if (OnCommitFlg) {
                    if (PSStart != PSEnd) {
                        String t = Cmd_GetTransaction().toUpperCase();
                        PSStart++;
                        send(t);
                    } else if (!"-1".equals(LockTime)) {
                        String t = Cmd_LockNoz().toUpperCase();
                        send(t);
                        LockTime = "-1";
                        NozLog.writeLog("MessageL Send:" + t);
                    } else {
                        String t = Cmd_Poll().toUpperCase();
                        send(t);
                        NozLog.writeLog("Message Send:" + t);
                    }
                    if (a != b) {
                        c = 0;
                        b = a;
                    } else {
                        c++;
                        if (c > 5) {
                            LabelC(1);
                            c = 0;
                        }
                    }
                    sleep(IniInfo.NozRunTime);
                } else {
                    CommitFlagFalse++;
                    if (CommitFlagFalse > 5) {
                        CommitFlagFalse = 0;
                        OnCommitFlg = true;
                        Cache = "";
                    }
                    sleep(1000);
                }
            } catch (RuntimeException ex) {
                LabelC(1);
                NozLog.writeLog(String.valueOf(ex.getMessage()));
                if ("InvalidOperationException".equals(ex.getMessage())) {
                    sp.openPort(new String[1]);
                    sleep(1000);
                }
            }
        }
    }

    /** CRC 16 Modbus (polynôme 0xA001), octet de poids fort en premier. */
    private static String CRC(String dt) {
        byte[] array = SerialClass.strToToHexByte(dt);
        if (array.length == 0) {
            return "";
        }
        int crc = 0;
        for (byte x : array) {
            crc ^= x & 0xFF;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xA001 : crc >> 1;
            }
        }
        return SerialClass.byteToHexStr(new byte[] {(byte) ((crc & 0xFF00) >> 8), (byte) (crc & 0xFF)});
    }

    private void FrameAdd() {
        Frame++;
        if (Frame == 128) {
            Frame = 64;
        }
    }

    private int GetEmpID(String empCode) {
        Row e = db().first("Employees", "{Code} = :c", p("c", empCode));
        return e != null ? e.i("EmployeeID") : IniInfo.LoginID;
    }

    /** Fermeture de la station : arrêt du fil et du port série. */
    public void Stop() {
        ThreadStop = false;
        sp.closePort();
    }
}
