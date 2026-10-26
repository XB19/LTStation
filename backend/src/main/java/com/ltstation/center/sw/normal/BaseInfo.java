package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ltstation.center.sw.normal.WirelessCmd.CmdList;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPreSet;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPrint;
import com.ltstation.center.sw.normal.WirelessCmd.CmdPrintTransaction;
import com.ltstation.center.sw.normal.WirelessCmd.CmdReplyStatue;
import com.ltstation.center.sw.normal.WirelessCmd.CmdStatue;
import com.ltstation.center.sw.normal.WirelessCmd.UnTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Wireless.BaseInfo : boîtiers sans fil (commandes, chargement, autorisations, transactions, cartes RFID, cuves). */
public final class BaseInfo {

    private BaseInfo() {
    }

    public static final List<IBoxClientParam> BoxClientList = new CopyOnWriteArrayList<>();
    public static final Object TransactionLock = new Object();
    private static final Map<String, LegacyLog> LOGS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter BOX_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE).setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static Sugar db() {
        return SW.db();
    }

    /** DeviceLog.writeLogByPath : journal Log/&lt;path&gt;. */
    public static void log(String text, String path) {
        LOGS.computeIfAbsent(path, LegacyLog::new).writeLog(text);
    }

    public static String json(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String s, Class<T> type) {
        try {
            return JSON.readValue(s, type);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static String SetLength(String str, int len) {
        return NormalFunction.SetLength(str, len);
    }

    private static String setLen(Object o, int len) {
        return SetLength(String.valueOf(o), len);
    }

    public static long ConvertDateTimeToInt(LocalDateTime dt) {
        return BaseFunction.ConvertDateTimeToInt(dt);
    }

    private static long now() {
        return BaseFunction.ConvertDateTimeToInt();
    }

    private static String nowText(int timeZone) {
        LocalDateTime d = LocalDateTime.now();
        return (timeZone != -99 ? d.plusHours(timeZone) : d).format(Conv.STD);
    }

    public static String GetRandom() {
        StringBuilder r = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            r.append(1 + random.nextInt(9));
        }
        return r.toString();
    }

    private static String pad64(String text) {
        String t = BaseFunction.StringConvertToAscii(text == null ? "" : text) + "2020202020202020202020202020202020202020202020202020202020202020";
        return t.substring(0, 64);
    }

    private static String fileName20(String fileName) {
        String t = "20202020202020202020" + BaseFunction.StringConvertToAscii(fileName);
        return t.substring(t.length() - 20);
    }

    /** CreateCmd : trame chiffrée d'une commande pour le boîtier ; null en cas d'erreur. */
    public static byte[] CreateCmd(String cmdHead, int TimeZone, CmdList cmd, IBoxClientParam boxClient) {
        try {
            String Rdom = GetRandom();
            String text = "";
            StringBuilder text2 = new StringBuilder();
            log("Command:" + cmd.CmdType, boxClient.NoHead.toUpperCase());
            log("Command:" + json(cmd), "Command");
            switch (cmd.CmdType == null ? "" : cmd.CmdType) {
                case "DowLoadTankCable" -> {
                    log("Dowload Tank Cable", boxClient.NoHead.toUpperCase());
                    text = "02";
                }
                case "A3" -> {
                    text = "A3";
                    cmdA3(cmd, boxClient, text2);
                }
                case "A2" -> {
                    text = "A2";
                    CmdStatue st = fromJson(cmd.CmdData, CmdStatue.class);
                    text2.append(setLen(st.NozID, 2)).append(setLen(st.PrePrice.multiply(BigDecimal.TEN.pow(st.PriceDigits)).intValue(), 6));
                }
                case "A5", "A1", "A4-Start" -> {
                    text = cmd.CmdType.equals("A5") ? "A5" : "A1";
                    cmdPreSet(cmd, boxClient, text2);
                }
                case "A4-Stop" -> {
                    text = "A4";
                    text2.append(setLen(fromJson(cmd.CmdData, CmdPreSet.class).NozID, 2));
                }
                case "DowLoadProgram" -> {
                    log("DowLoad", boxClient.NoHead);
                    text = "0D";
                    NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(Conv.toInt(cmd.CmdData), 1);
                    text2.append(setLen(String.format("%02x", pg.TotalPage()), 4)).append(setLen("01", 4)).append(pg.ProgramData());
                    boxClient.CurrentProgram = 0;
                    boxClient.ProgramDateTime = now();
                }
                case "12" -> {
                    log("DowLoad Device", boxClient.NoHead.toUpperCase());
                    text = "12";
                    int port = Conv.toInt(cmd.CmdData);
                    Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", boxClient.NoHead));
                    if (box == null) {
                        throw new IllegalStateException("Box Lost");
                    }
                    Row boxPort = db().first("Box_Set", "{Port_ID} = :b AND {Box_PortID} = :p", p("b", box.get("ID"), "p", port));
                    if (boxPort == null) {
                        throw new IllegalStateException("BoxPort Lost");
                    }
                    int programType = 1;
                    int comm = boxPort.i("Box_CommunicationTypeID");
                    if (comm == 1 || comm == 2 || comm == 3 || comm == 4 || comm == 7) {
                        Row noz = db().first("NormalNoz", "{Box_Port_ID} = :b ORDER BY {NozID}", p("b", boxPort.get("ID")));
                        if (noz != null) {
                            String v = noz.str("Version").substring(0, 2);
                            programType = v.equals("06") ? 2 : v.equals("11") ? 3 : programType;
                        }
                    }
                    NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(programType, 1);
                    text2.append(setLen(cmd.NozID, 2)).append(fileName20(pg.FileName())).append(setLen(String.format("%02x", pg.TotalPage()), 4))
                        .append(setLen("01", 4)).append(pg.ProgramData());
                    boxClient.CurrentProgram = 0;
                    boxClient.ProgramDateTime = now();
                }
                case "0E" -> text = "0E";
                case "11" -> {
                    log("GetDeviceVersion", boxClient.NoHead.toUpperCase());
                    text = "11";
                    text2.append(setLen(cmd.NozID, 2));
                }
                case "14" -> {
                    text = "14";
                    CmdPrint c = fromJson(cmd.CmdData, CmdPrint.class);
                    text2.append(setLen(String.format("%02x", c.Count), 2)).append(setLen(String.format("%02x", 32), 2));
                    for (String line : new String[] {c.FirstIndex, c.SecondIndex, c.ThirdIndex, c.FouthIndex, c.FifthIndex, c.SixthIndex, c.NozID, c.Amount,
                        c.Volume, c.Price, c.Seventh, c.TimeIndex, c.EighthIndex, c.NinthIndex, c.TenthIndex}) {
                        text2.append(pad64(line));
                    }
                }
                case "15" -> {
                    text = "15";
                    CmdPrintTransaction c = fromJson(cmd.CmdData, CmdPrintTransaction.class);
                    text2.append(setLen(String.format("%02x", c.NozID), 2)).append(SetLength(c.Amount, 8)).append(SetLength(c.Volume, 8))
                        .append(SetLength(c.Price, 6)).append(c.Time).append(setLen(String.format("%02x", c.TID), 8));
                }
                case "16" -> {
                    log("0x16 Deal", boxClient.NoHead.toUpperCase());
                    text = "16";
                    Row sv = db().first("StationVersion", "{StationID} = :s", p("s", IniInfo.StationID));
                    if (sv != null) {
                        Sugar.Page pg = db().page("EmployeeCardInfos", "{Status} = '0'", null, null, 1, 10);
                        StringBuilder cards = new StringBuilder();
                        for (Row r : pg.rows()) {
                            cards.append(r.str("ASN").substring(8));
                        }
                        int totalPage = pg.total() == 0 ? 1 : pg.totalPage();
                        text2.append(setLen(String.format("%02x", sv.i("EmpVersion")), 2)).append(setLen(String.format("%02x", totalPage), 4)).append("0001")
                            .append(cards);
                    }
                }
                case "18" -> {
                    log("0x18 Deal", boxClient.NoHead.toUpperCase());
                    text = "18";
                    List<Row> list = db().list("NormalPriceInfo", "ORDER BY {ID}", null);
                    for (int i = 0; i < list.size(); i++) {
                        text2.append(setLen(i + 1, 2)).append(setLen(Conv.toDecimal(list.get(i).get("Price")).multiply(BigDecimal.TEN.pow(IniInfo.Pradio)).intValue(), 6))
                            .append(setLen(IniInfo.Pradio, 2));
                    }
                }
                case "ClientReply" -> {
                    CmdReplyStatue r = fromJson(cmd.CmdData, CmdReplyStatue.class);
                    text = r.CmdType;
                    text2.append(r.CmdValue);
                }
                default -> {
                }
            }
            String body = text2.toString();
            log(text + ":" + body, boxClient.NoHead.toUpperCase());
            log("NoHead:" + boxClient.NoHead + "  Command:" + text, "Main");
            String text3 = "F5" + setLen(String.format("%02X", body.length() / 2 + 8), 4) + Rdom + text + body + "0000";
            if ("FA".equals(cmdHead)) {
                text3 = cmdHead + setLen(String.format("%02X", body.length() / 2 + 14), 4) + Rdom + text + body + "0000";
            }
            log("Old Data:" + text3, boxClient.NoHead.toUpperCase());
            log("NoHead:" + boxClient.NoHead + "Old Data:" + text3, "Main");
            String[] outdata = {""};
            boolean ok = BaseFunction.DealCmdEncry(text3, Rdom, outdata);
            String out = outdata[0];
            if ("FA".equals(cmdHead)) {
                out = cmdHead + LocalDateTime.now().plusHours(TimeZone).format(BOX_TIME) + out.substring(2);
            }
            log("DealCmdEncry：" + (ok ? "True" : "False"), boxClient.NoHead.toUpperCase());
            log("DealCmdEncry Data：" + out, boxClient.NoHead.toUpperCase());
            out = out.substring(0, out.length() - 4) + CRC16(SerialClass.strToToHexByte(out));
            if (!ok) {
                throw new IllegalStateException("Encry Error");
            }
            return SerialClass.strToToHexByte(out);
        } catch (RuntimeException ex) {
            log("CreateCmd Error:" + ex.getMessage(), "Main");
            log("CreateCmd Error:" + ex.getMessage(), boxClient.NoHead.toUpperCase());
            return null;
        }
    }

    /** A3 : configuration d'un port du boîtier sans fil. */
    private static void cmdA3(CmdList cmd, IBoxClientParam boxClient, StringBuilder text2) {
        int PortID = cmd.NozID;
        Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", cmd.DeviceNo));
        if (box == null) {
            throw new IllegalStateException("Box Lost");
        }
        Row bp = db().first("Box_Set", "{Port_ID} = :b AND {Box_PortID} = :p", p("b", box.get("ID"), "p", PortID));
        if (bp == null) {
            text2.append(setLen(PortID, 2)).append(setLen("0", 6)).append(setLen("0", 2)).append(setLen("0", 2)).append(setLen("0", 2)).append("000000");
            log("A3 Null:" + text2, boxClient.NoHead.toUpperCase());
            return;
        }
        if (PortID == 5) {
            log("Box_Device", boxClient.NoHead.toUpperCase());
            List<Row> devices = db().list("StationDeviceSet", "{BoxID} = :b", p("b", box.get("ID")));
            text2.append(setLen(PortID, 2)).append(setLen(devices.size(), 2));
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
            return;
        }
        int comm = bp.i("Box_CommunicationTypeID");
        int interval = bp.i("Box_IntervalTime") / 10;
        text2.append(setLen(bp.get("Box_PortID"), 2)).append(setLen(bp.get("Box_Baud"), 6)).append(setLen(bp.get("Box_Stop"), 2))
            .append(setLen(bp.get("Box_Parity"), 2)).append(setLen(comm, 2));
        String[] error = {""};
        if (comm == 6) {
            log("RFID", boxClient.NoHead.toUpperCase());
            List<Row> list3 = db().list("RFIDConfig", "{Box_Port} = :b ORDER BY {RFIDNo}", p("b", bp.get("ID")));
            text2.append(BaseFunction.SetStrLen(String.valueOf(list3.size()), 2)).append(BaseFunction.SetStrLen(String.valueOf(interval), 2))
                .append(BaseFunction.SetStrLen(String.valueOf(bp.i("Box_DeviceType")), 2));
            for (Row r : list3) {
                text2.append(setLen(r.get("RFIDNo"), 2)).append(setLen(r.get("Address"), 2)).append(setLen(r.get("NozID"), 2));
            }
            ReLoadDevice(boxClient.NoHead, 2, bp.i("ID"), error);
        } else if (comm == 5) {
            log("ATG", boxClient.NoHead.toUpperCase());
            if (bp.i("Box_DeviceType") == 0) {
                text2.append("01").append(setLen(interval, 2)).append("0001");
            } else {
                List<Row> tanks = db().list("NormalTank", "{IsUsing} = true ORDER BY {TankNo}", null);
                text2.append(setLen(tanks.size(), 2)).append(setLen(interval, 2)).append(setLen(bp.get("Box_DeviceType"), 2));
                for (Row t : tanks) {
                    text2.append(setLen(t.get("TankNo"), 2)).append(setLen(t.get("TankNo"), 2));
                }
            }
        } else if (comm == 11 || comm == 12) {
            log("CNG PLC || Price Borad", boxClient.NoHead.toUpperCase());
            text2.append("01").append(setLen(interval, 2)).append("0001");
        } else {
            log("Noz", boxClient.NoHead.toUpperCase());
            List<Row> list5 = db().list("NormalNoz", "{Box_Port_ID} = :b ORDER BY {NozID}", p("b", bp.get("ID")));
            text2.append(setLen(list5.size(), 2)).append(setLen(interval, 2)).append(bp.i("Box_DeviceType") != 0 ? "01" : "00");
            for (Row n : list5) {
                text2.append(setLen(n.get("NozID"), 2)).append(setLen(n.get("Address"), 2));
            }
            text2.append("0").append(IniInfo.Vradio).append(IniInfo.Mradio).append(IniInfo.Pradio);
            for (Row n : list5) {
                int acc = n.i("Accredit");
                text2.append(BaseFunction.SetStrLen(acc == 0 ? "1" : acc == 1 ? "0" : String.valueOf(acc), 2));
            }
        }
        // Comme l'original : rechargement des pistolets du port après toute configuration hors port 5
        ReLoadDevice(boxClient.NoHead, 1, bp.i("ID"), error);
    }

    /** A1 / A4-Start / A5 : prédétermination du pistolet. */
    private static void cmdPreSet(CmdList cmd, IBoxClientParam boxClient, StringBuilder text2) {
        CmdPreSet set = fromJson(cmd.CmdData, CmdPreSet.class);
        log("set:" + json(set), boxClient.NoHead.toUpperCase());
        log("0", boxClient.NoHead.toUpperCase());
        Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", cmd.DeviceNo));
        if (box == null) {
            throw new IllegalStateException("Box Lost");
        }
        Row normalNoz = db().first("NormalNoz", "{NozID} = :n AND {PortID} = :p", p("n", set.NozID, "p", box.get("ID")));
        Found found = GetClientNoz(set.NozID, boxClient.NoHead);
        if (normalNoz == null) {
            // Comme l'original : la lecture du prix échoue avant le test « Noz Lost »
            throw new NullPointerException("Object reference not set to an instance of an object.");
        }
        BigDecimal price = Conv.toDecimal(normalNoz.get("Price"));
        if (price.signum() == 0 && found != null) {
            price = found.noz().Price;
        }
        log("Send A1", boxClient.NoHead.toUpperCase());
        log("1", boxClient.NoHead.toUpperCase());
        Row bp = db().first("Box_Set", "{ID} = :b", p("b", normalNoz.get("Box_Port_ID")));
        if (bp == null) {
            throw new IllegalStateException("BoxPort Lost");
        }
        log("2", boxClient.NoHead.toUpperCase());
        boolean big = bp.i("Box_DeviceType") == 1;
        log("3", boxClient.NoHead.toUpperCase());
        BigDecimal preSetValue = set.PreSetValue;
        int radio = set.PreSetType == 0 ? IniInfo.Mradio : IniInfo.Vradio;
        set.PreSetValue = set.PreSetValue.multiply(BigDecimal.TEN.pow(radio)).setScale(0, RoundingMode.HALF_EVEN);
        log("4", boxClient.NoHead);
        BigDecimal high = big ? BigDecimal.valueOf(90000000) : BigDecimal.valueOf(900000);
        if (set.PreSetValue.compareTo(high) > 0) {
            set.PreSetValue = high;
        }
        if (preSetValue.compareTo(BigDecimal.valueOf(-1)) == 0) {
            set.PreSetValue = high;
        }
        log("5", boxClient.NoHead);
        String text4 = SetLength(set.PreSetValue.setScale(0, RoundingMode.HALF_EVEN).toPlainString(), 8);
        log("Price:" + price.toPlainString(), boxClient.NoHead);
        text2.append(setLen(set.NozID, 2)).append(setLen(set.PreSetType, 2)).append(text4)
            .append(SetLength(price.multiply(BigDecimal.TEN.pow(IniInfo.Pradio)).setScale(0, RoundingMode.HALF_EVEN).toPlainString(), 6));
        if (!"".equals(set.DealCode)) {
            long code = set.DealCode == null ? 0L : Long.parseLong(set.DealCode.trim());
            text2.append(SetLength(String.format("%02x", code), 8));
        } else {
            text2.append(SetLength("0", 8));
        }
    }

    /** CRC16 (Modbus) des trames du boîtier. */
    public static String CRC16(byte[] bits) {
        int num = 0xFFFF;
        for (int i = 0; i < bits.length - 2; i++) {
            num ^= bits[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
                num = (num & 1) == 1 ? (num >> 1) ^ 0xA001 : num >> 1;
            }
        }
        return String.format("%04X", num & 0xFFFF);
    }

    /** Pistolet trouvé par GetClientNoz. */
    public record Found(IBoxClientParam box, AllNozStatue noz) {
    }

    private static IBoxClientParam byHead(String NoHead) {
        String h = NoHead == null ? "" : NoHead.trim().toUpperCase();
        for (IBoxClientParam b : BoxClientList) {
            if (b.NoHead != null && b.NoHead.trim().toUpperCase().equals(h)) {
                return b;
            }
        }
        return null;
    }

    /** GetClientNoz : boîtier et pistolet ; null si l'un manque. */
    public static Found GetClientNoz(int NozID, String NoHead) {
        IBoxClientParam box = byHead(NoHead);
        if (box == null) {
            return null;
        }
        AllNozStatue noz = box.NozList.stream().filter(t -> t.NozID == NozID).findFirst().orElse(null);
        return noz == null ? null : new Found(box, noz);
    }

    public static void LoadWireLessDevice() {
        try {
            for (Row b : db().list("BoxList")) {
                DealSetDevice(b.str("BoxDeviceNo"), new String[1]);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static int onDuty() {
        if (IniInfo.NormalNeedDuty == 0) {
            return 1;
        }
        Row h = db().first("HandShiftReport", "ORDER BY {ID} DESC", null);
        return h != null && h.i("DealStatue") == 0 ? 1 : 0;
    }

    /** GetBoxPortID : identifiant du port (0 s'il n'existe pas). */
    public static int GetBoxPortID(int ID) {
        Row bp = db().first("Box_Set", "{ID} = :i", p("i", ID));
        return bp == null ? 0 : bp.i("ID");
    }

    private static AllNozStatue allNoz(Row item, Row box, Row boxSet, int raise, Integer duty) {
        AllNozStatue n = new AllNozStatue();
        n.StationID = IniInfo.StationID;
        n.NozID = item.i("NozID");
        n.Statue = -1;
        n.LastStatue = 0;
        n.UpdateTime = now();
        n.CompanyID = IniInfo.CompanyID;
        n.GradeID = item.i("GradeID");
        n.GradeName = BaseFunction.GetNormalGradeByID(item.i("GradeID"));
        n.DeviceNo = box.str("BoxDeviceNo");
        n.StationType = 0;
        n.AmountDigits = IniInfo.Mradio;
        n.PriceDigits = IniInfo.Pradio;
        n.VolumeDigits = IniInfo.Vradio;
        n.Box_Port = GetBoxPortID(item.i("Box_Port_ID"));
        n.TankID = item.i("TankID");
        n.VRadio = IniInfo.Vradio;
        n.MRadio = IniInfo.Mradio;
        n.PRadio = IniInfo.Pradio;
        n.ConnectFirst = true;
        n.RaiseNoz = raise;
        n.AuthTypeID = item.i("Accredit");
        n.Version = item.str("Version");
        if (duty != null) {
            n.IsOnDuty = duty;
            n.AuthMessage = "";
        }
        n.BoxPortID = item.i("Box_Port_ID");
        n.PUMPID = item.i("PumpID");
        n.Address = -(item.i("Address") + boxSet.i("Box_PortID") * 100 + boxSet.i("Port_ID") * 10000);
        return n;
    }

    private static RFIDRunData rfid(Row c) {
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
        r.UpdateTime = LocalDateTime.now().format(Conv.STD);
        r.UserName = " ";
        r.Version = c.str("Version");
        return r;
    }

    private static TankStatue tank(Row t, boolean current) {
        TankStatue s = new TankStatue();
        s.StationID = IniInfo.StationID;
        s.TankID = Conv.toInt(t.get("TankNo"));
        s.TotalHeight = Conv.toDecimal(t.get("TankHeight"));
        s.TotalVolume = Conv.toDecimal(t.get("TankVolume"));
        s.CompanyID = IniInfo.CompanyID;
        s.TankName = t.str("TankName");
        s.GradeID = t.i("GradeID");
        s.GradeName = BaseFunction.GetNormalGradeByID(t.i("GradeID"));
        s.OverImportLevel = Conv.toDecimal(t.get("OverImportLevel"));
        s.ID = t.i("ID");
        s.TankMac = t.str("TankMac");
        if (current) {
            s.OilHeight = Conv.toDecimal(t.get("CurrentHeight"));
            s.OilVolume = Conv.toDecimal(t.get("CurrentVolume"));
            s.CurrentLevel = Conv.toDecimal(t.get("CurrentVolume"));
        }
        return s;
    }

    private static List<String> macs(List<Row> tanks) {
        return tanks.stream().map(t -> t.str("TankMac")).filter(m -> !m.isEmpty()).distinct().toList();
    }

    /** DealSetDevice : chargement (ou rechargement) d'un boîtier sans fil. */
    public static IBoxClientParam DealSetDevice(String NoHead, String[] Error) {
        Error[0] = "";
        try {
            log(NoHead + " Start", "DeviceThread");
            IBoxClientParam boxClient = byHead(NoHead);
            boolean known = boxClient != null;
            if (!known) {
                log(NoHead + " Create New", "DeviceThread");
                boxClient = new IBoxClientParam();
            } else {
                log(NoHead + " Get", "DeviceThread");
            }
            Row box = db().first("BoxList", "upper({BoxDeviceNo}) = :n", p("n", NoHead.toUpperCase()));
            if (box == null) {
                throw new IllegalStateException("Box Lost");
            }
            int duty = onDuty();
            IniInfo.IsOnDuty = duty;
            List<AllNozStatue> nozs = new ArrayList<>();
            for (Row item : db().list("NormalNoz", "{IsUsing} = true AND {PortID} = :p ORDER BY {NozID}", p("p", box.get("ID")))) {
                Row boxSet = db().first("Box_Set", "{ID} = :b", p("b", item.get("Box_Port_ID")));
                if (boxSet != null) {
                    AllNozStatue n = allNoz(item, box, boxSet, 0, duty);
                    n.IdentifyMode = IniInfo.IdentifyMode;
                    nozs.add(n);
                }
            }
            boxClient.NoHead = NoHead;
            boxClient.TimeZone = IniInfo.TimeZone;
            boxClient.Longitude = (float) Conv.toDouble(box.get("Longitude"));
            boxClient.Latitude = (float) Conv.toDouble(box.get("Latitude"));
            boxClient.NozList = new CopyOnWriteArrayList<>(nozs);
            boxClient.StationID = IniInfo.StationID;
            boxClient.CompanyID = IniInfo.CompanyID;
            boxClient.cmdAuth = new CopyOnWriteArrayList<>();
            boxClient.BoxVersion = box.str("BoxVersion");
            boxClient.ID = box.i("ID");
            List<Row> tanks = db().list("NormalTank", "{IsUsing} = true", null);
            List<TankStatue> list3 = new ArrayList<>();
            for (Row t : tanks) {
                TankStatue s = tank(t, true);
                if (t.get("TankError") != null) {
                    String e = t.str("TankError");
                    for (int i = 1; i < e.length(); i++) {
                        if (e.charAt(i) == '1') {
                            switch (i) {
                                case 7 -> s.TL = 7;
                                case 6 -> s.CG = 6;
                                case 5 -> s.Ttemp = 5;
                                case 4 -> s.Ttemp = 4;
                                case 3 -> s.TLevel = 3;
                                case 2 -> s.TLevel = 2;
                                case 1 -> s.EG = 1;
                                default -> {
                                }
                            }
                        }
                    }
                }
                list3.add(s);
            }
            boxClient.TankList = new CopyOnWriteArrayList<>(list3);
            boxClient.TankMac = macs(tanks);
            List<Integer> ports = db().list("Box_Set", "{Port_ID} = :b", p("b", box.get("ID"))).stream().map(r -> r.i("ID")).toList();
            List<RFIDRunData> rfids = new ArrayList<>();
            if (!ports.isEmpty()) {
                for (Row c : db().list("RFIDConfig", "{Box_Port} IN (:p)", p("p", ports))) {
                    rfids.add(rfid(c));
                }
            }
            boxClient.RFIDList = new CopyOnWriteArrayList<>(rfids);
            PLCParam plc = new PLCParam();
            plc.MW28Data = new int[16];
            plc.MB10Data = new int[16];
            plc.MB5Data = new int[16];
            plc.Error = new int[100];
            plc.Statue = -1;
            Row data = db().first("PLCParamData");
            if (data != null) {
                for (String f : new String[] {"A_PistonCurrent", "B_PistonCurrent", "InTake_Pressure", "Low_Pressure", "Instrument_Air_Pressure",
                    "Medium_Pressure", "Gas_Concentration_Inside_the_Sled_First", "Hydraulic_Oil_Temperature", "High_Pressure", "Protection_Pressure",
                    "Water_Flow_Temperature", "Gas_Concentration_Second", "Gas_Concentration_Third"}) {
                    try {
                        PLCParam.class.getField(f).set(plc, Conv.toDecimal(data.get(f)));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                List<Integer> errors = new ArrayList<>();
                bits(data.str("MW28Data"), plc.MW28Data, 100, errors);
                bits(data.str("MB10Data"), plc.MB10Data, 300, errors);
                bits(data.str("MB5Data"), plc.MB5Data, 200, errors);
                if (!errors.isEmpty()) {
                    plc.Error = errors.stream().mapToInt(Integer::intValue).toArray();
                }
            }
            boxClient.pLCParam = plc;
            boxClient.Online = true;
            if (!known) {
                log(NoHead + " Start Thread", "DeviceThread");
                BoxClientList.add(boxClient);
            } else if (boxClient.CommadnThread == null) {
                log(NoHead + "  Thread Null", "DeviceThread");
                log(NoHead + "  Thread Start", "DeviceThread");
            }
            PriceBorad pb = new PriceBorad();
            pb.Statue = -1;
            boxClient.priceBorad = pb;
            log(NoHead + " End", "DeviceThread");
            return boxClient;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return null;
        }
    }

    private static void bits(String text, int[] target, int base, List<Integer> errors) {
        for (int j = 0; j < text.length() && j < target.length; j++) {
            target[j] = text.charAt(j) - '0';
            if (target[j] != 0) {
                errors.add(base + j);
            }
        }
    }

    public static boolean ResetTank(String[] Error) {
        Error[0] = "";
        try {
            List<Row> tanks = db().list("NormalTank", "{IsUsing} = true", null);
            List<TankStatue> list = new ArrayList<>();
            for (Row t : tanks) {
                list.add(tank(t, false));
            }
            for (IBoxClientParam b : BoxClientList) {
                b.TankList = new CopyOnWriteArrayList<>(list);
                b.TankMac = macs(tanks);
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** ReLoadDevice : pistolets (1) ou lecteurs RFID (2) d'un port du boîtier. */
    public static boolean ReLoadDevice(String NoHead, int ReloadType, int BoxPortID, String[] Error) {
        Error[0] = "";
        try {
            log(NoHead + " Start", "DeviceThread");
            IBoxClientParam boxClient = byHead(NoHead);
            if (boxClient == null) {
                log(NoHead + " Create New", "DeviceThread");
                boxClient = new IBoxClientParam();
                Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead));
                if (box == null) {
                    throw new IllegalStateException("Box Lost");
                }
                boxClient.NoHead = NoHead;
                boxClient.TimeZone = IniInfo.TimeZone;
                boxClient.Longitude = (float) Conv.toDouble(box.get("Longitude"));
                boxClient.Latitude = (float) Conv.toDouble(box.get("Latitude"));
                boxClient.NozList = new CopyOnWriteArrayList<>();
                boxClient.StationID = IniInfo.StationID;
                boxClient.CompanyID = IniInfo.CompanyID;
                boxClient.cmdAuth = new CopyOnWriteArrayList<>();
                boxClient.ID = box.i("ID");
            } else {
                log(NoHead + " Get", "DeviceThread");
            }
            IBoxClientParam b = boxClient;
            if (ReloadType == 1) {
                b.NozList.removeIf(t -> t.Box_Port == BoxPortID);
                Row box = Row.of("BoxDeviceNo", b.NoHead.toUpperCase());
                for (Row item : db().list("NormalNoz", "{IsUsing} = true AND {PortID} = :p AND {Box_Port_ID} = :b ORDER BY {NozID}",
                    p("p", b.ID, "b", BoxPortID))) {
                    Row boxSet = db().first("Box_Set", "{ID} = :i", p("i", item.get("Box_Port_ID")));
                    if (boxSet != null) {
                        b.NozList.add(allNoz(item, box, boxSet, -1, IniInfo.IsOnDuty));
                    }
                }
            } else if (ReloadType == 2) {
                b.RFIDList.removeIf(t -> t.Box_Port == BoxPortID);
                for (Row c : db().list("RFIDConfig", "{Box_Port} = :b", p("b", BoxPortID))) {
                    b.RFIDList.add(rfid(c));
                    AllNozStatue n = b.NozList.stream().filter(t -> t.NozID == c.i("NozID")).findFirst().orElse(null);
                    if (n != null) {
                        n.AuthTypeID = 0;
                        db().exec(db().sql("NormalNoz", "update [NormalNoz] set {Accredit} = 0 where {NozID} = :n"), p("n", c.get("NozID")));
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** DealAuthorize : pistolet décroché ; commande d'autorisation en attente ou autorisation automatique. */
    public static boolean DealAuthorize(BoxTcpServer.Session client, int NozID, String[] Error) {
        Error[0] = "";
        try {
            IBoxClientParam box = BoxClientList.stream().filter(t -> t.NoHead.equalsIgnoreCase(client.Id())).findFirst().orElse(null);
            if (box == null) {
                throw new IllegalStateException("Box Lost");
            }
            log("Deal Auth:", client.Id().toUpperCase());
            AllNozStatue noz = box.NozList.stream().filter(t -> t.NozID == NozID).findFirst().orElse(null);
            if (noz == null) {
                throw new IllegalStateException("Noz Lost");
            }
            CmdList cmd = box.cmdAuth.stream().filter(t -> t.NozID == NozID).min(Comparator.comparingLong(t -> t.CreateTime)).orElse(null);
            if (cmd != null) {
                log("cmdAuth Count:" + box.cmdAuth.size(), client.Id().toUpperCase());
                if (IniInfo.IdentifyMode != 0) {
                    Wire.parseRow(cmd.CmdData);
                }
                box.cmdNormal.add(cmd);
                box.cmdAuth.remove(cmd);
                log("NozID:" + NozID + "  Count:" + box.cmdAuth.size(), client.Id().toUpperCase());
            } else if (noz.AuthTypeID == 1) {
                CmdPreSet set = new CmdPreSet();
                set.DeviceNo = client.Id();
                set.NozID = NozID;
                set.PreSetType = noz.Price.compareTo(BigDecimal.ONE) > 0 ? 0 : 1;
                set.PreSetValue = BigDecimal.valueOf(-1);
                set.PreSetPrice = noz.Price;
                set.AuthType = true;
                CmdList c = new CmdList();
                c.DeviceNo = client.Id();
                c.NozID = NozID;
                c.CmdData = json(set);
                c.CmdType = "A1";
                box.cmdNormal.add(c);
                log("Deal Auth Enqueue", box.NoHead.toUpperCase());
            }
            return true;
        } catch (RuntimeException ex) {
            log("Deal Auth Error:" + ex.getMessage(), client.Id().toUpperCase());
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** DealBoxTranasction : transaction terminée (enregistrée dans UnDealTransaction, sauf doublon). */
    public static boolean DealBoxTranasction(String NoHead, int NozID, String Volume, String Amount, String Price, String AmountTotal, String VolumeTotal,
                                             String order, long NozRunTime, String[] Error) {
        Error[0] = "";
        try {
            synchronized (TransactionLock) {
                Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead));
                if (box == null) {
                    throw new IllegalStateException("Box Lost");
                }
                Row air = db().first("NormalNoz", "{PortID} = :p AND {NozID} = :n", p("p", box.get("ID"), "n", NozID));
                if (air == null) {
                    throw new IllegalStateException("Noz Lost");
                }
                BigDecimal vol = new BigDecimal(Volume);
                BigDecimal volTotal = new BigDecimal(VolumeTotal);
                if (db().first("UnDealTransaction", "{NozID} = :n AND {Volume} = :v AND {VolumeTotal} = :t", p("n", air.get("NozID"), "v", vol, "t", volTotal))
                    != null) {
                    return true;
                }
                db().insert("UnDealTransaction", Row.of("StationID", IniInfo.StationID, "NozID", air.get("NozID"), "TrueTime", LocalDateTime.now().format(Conv.STD),
                    "GradeID", air.get("GradeID"), "Amount", rnd(new BigDecimal(Amount), IniInfo.Mradio), "Price",
                    new BigDecimal(Price), "Volume", vol, "VolumeTotal", volTotal, "TType", 55, "AmountTotal", new BigDecimal(AmountTotal), "AmountDigits",
                    IniInfo.Mradio, "DeviceNo", NoHead, "SeverCreateTime", NozRunTime, "CompanyID", IniInfo.CompanyID, "OrderNumber", order, "PaymentID", 998,
                    "DealStatue", 0));
                return true;
            }
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    /** DealBoxTransaction_BoxOffline : transaction faite hors ligne (heure du boîtier ramenée à l'heure du poste). */
    public static boolean DealBoxTransaction_BoxOffline(String NoHead, int NozID, BigDecimal Volume, BigDecimal Amount, BigDecimal Price, BigDecimal AmountTotal,
                                                        BigDecimal VolumeTotal, int TimeZone, String Time, String order, String cardNo, String IP, String[] Error) {
        Error[0] = "";
        synchronized (TransactionLock) {
            try {
                log("Off Amount:" + Amount.toPlainString(), IP);
                log("Off Volume:" + Volume.toPlainString(), IP);
                log("Off Price:" + Price.toPlainString(), IP);
                log("Off AmountTotal:" + AmountTotal.toPlainString(), IP);
                log("Off VolumeTotal:" + VolumeTotal.toPlainString(), IP);
                LocalDateTime dt = Conv.toDateTime(Time);
                if (TimeZone != -99) {
                    Time = dt.minusHours(TimeZone).format(Conv.STD);
                }
                Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead));
                if (box == null) {
                    throw new IllegalStateException("Box Lost");
                }
                Row noz = db().first("NormalNoz", "{PortID} = :p AND {NozID} = :n", p("p", box.get("ID"), "n", NozID));
                if (noz == null) {
                    throw new IllegalStateException("Noz Lost");
                }
                boolean prepaid = noz.i("Accredit") == 2;
                db().insert("UnDealTransaction", Row.of("StationID", IniInfo.StationID, "NozID", noz.get("NozID"), "TrueTime", Time, "GradeID", noz.get("GradeID"),
                    "Amount", rnd(Amount, IniInfo.Mradio), "Price", Price, "Volume", Volume, "VolumeTotal", VolumeTotal, "TType",
                    prepaid ? 53 : 54, "AmountTotal", AmountTotal, "AmountDigits", IniInfo.Mradio, "DeviceNo", NoHead, "SeverCreateTime", now(), "CompanyID",
                    IniInfo.CompanyID, "OrderNumber", order, "PaymentID", prepaid ? 18 : 19, "DealStatue", 0, "CardNo", "00000000" + cardNo));
                log("Offline Finished", IP);
                return true;
            } catch (RuntimeException ex) {
                Error[0] = ex.getMessage();
                return false;
            }
        }
    }

    /** DealBoxTransaction_BoxOfflineList : lot hors ligne (sans les transactions déjà reçues). */
    public static boolean DealBoxTransaction_BoxOfflineList(String NoHead, List<UnTransaction> unDeals, int TimeZone, String IP, String[] Error) {
        Error[0] = "";
        synchronized (TransactionLock) {
            try {
                List<Row> list = new ArrayList<>();
                for (UnTransaction item : unDeals) {
                    log("Off NozID:" + item.NozID, IP);
                    log("Off Amount:" + item.Amount.toPlainString(), IP);
                    log("Off Volume:" + item.Volume.toPlainString(), IP);
                    log("Off Price:" + item.Price.toPlainString(), IP);
                    log("Off AmountTotal:" + item.AmountTotal.toPlainString(), IP);
                    log("Off VolumeTotal:" + item.VolumeTotal.toPlainString(), IP);
                    Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead));
                    if (box == null) {
                        throw new IllegalStateException("Box Lost");
                    }
                    Row noz = db().first("NormalNoz", "{PortID} = :p AND {NozID} = :n", p("p", box.get("ID"), "n", item.NozID));
                    if (noz != null && db().first("UnDealTransaction", "{NozID} = :n AND {TrueTime} = :t", p("n", item.NozID, "t", item.TimeStr)) == null) {
                        boolean prepaid = noz.i("Accredit") == 2;
                        list.add(Row.of("StationID", IniInfo.StationID, "NozID", noz.get("NozID"), "TrueTime", item.TimeStr, "GradeID", noz.get("GradeID"),
                            "Amount", rnd(item.Amount, IniInfo.Mradio), "Price", item.Price, "Volume", item.Volume, "VolumeTotal",
                            item.VolumeTotal, "TType", prepaid ? 53 : 54, "AmountTotal", item.AmountTotal, "AmountDigits", IniInfo.Mradio, "DeviceNo", NoHead,
                            "SeverCreateTime", now(), "CompanyID", IniInfo.CompanyID, "OrderNumber", item.OrderNumber, "PaymentID", prepaid ? 18 : 999,
                            "DealStatue", 0, "CardNo", "00000000" + item.CardNo));
                    }
                }
                if (!list.isEmpty()) {
                    db().insertAll("UnDealTransaction", list);
                }
                return true;
            } catch (RuntimeException ex) {
                Error[0] = ex.getMessage();
                return false;
            }
        }
    }

    private static SumTransaction sum(String where, Object value, long since) {
        Row r = db().queryFirst(db().sql("select coalesce(sum({NormalTransaction.Amount}), 0) AS SumAmount, coalesce(sum({NormalTransaction.Volume}), 0)"
            + " AS SumVolume, count({NormalTransaction.ID}) AS SumCount from [NormalTransaction] where " + where
            + " and {NormalTransaction.CreateTime} > :s"), p("v", value, "s", since));
        SumTransaction s = new SumTransaction();
        s.SumAmount = Conv.toDecimal(r.get("SumAmount"));
        s.SumVolume = Conv.toDecimal(r.get("SumVolume"));
        s.SumCount = Conv.toInt(r.get("SumCount"));
        return s;
    }

    private static long accountSince(Row customerAccounts) {
        return BaseFunction.ConvertDateTimeToInt(Conv.toDateTime(customerAccounts.get("UpdateDatetime")));
    }

    private static String rfidTime(IBoxClientParam boxClient) {
        return nowText(boxClient.TimeZone);
    }

    private static final class Code extends IllegalStateException {
        Code(String m) {
            super(m);
        }
    }

    /**
     * DealRFIDTag : badge lu par un lecteur RFID sans fil ; renvoie 0 si le pistolet peut être autorisé, sinon le code
     * de refus (returnMessage = réponse au boîtier).
     */
    public static int DealRFIDTag(IBoxClientParam boxClient, int NozID, String Tag, String Password, String mileageStr, int InsertCmdAuth, String oldRFIDTag,
                                  String oldCarNo, String[] returnMessage) {
        int num = 0;
        returnMessage[0] = "";
        try {
            Password = Password.toUpperCase().replace("F", "");
            log("RFID Card Deal:", boxClient.NoHead.toUpperCase());
            RFIDRunData RFID = boxClient.RFIDList.stream().filter(t -> t.RFIDNo == NozID).findFirst().orElse(null);
            if (RFID == null) {
                num = 10;
                returnMessage[0] = "10";
                throw new Code("RFID Lost");
            }
            AllNozStatue Noz = boxClient.NozList.stream().filter(t -> t.NozID == RFID.NozID).findFirst().orElse(null);
            if (Noz == null) {
                num = 20;
                returnMessage[0] = "20";
                throw new Code("Noz Error");
            }
            if (Tag.equals("00000000000000000000")) {
                log("RFID Cancel:", boxClient.NoHead.toUpperCase());
                Row last = db().first("CmdList", "{DeviceNo} = :d AND {NozID} = :n AND {CmdType} = 'A1' ORDER BY {CreateTime} DESC",
                    p("d", boxClient.NoHead, "n", NozID));
                if (last != null) {
                    last.put("Deal", 100);
                    db().update("CmdList", last);
                }
                boxClient.cmdAuth.stream().filter(t -> t.NozID == Noz.NozID && "A1".equals(t.CmdType)).max(Comparator.comparingLong(t -> t.CreateTime))
                    .ifPresent(boxClient.cmdAuth::remove);
                if (boxClient.cmdNormal.stream().anyMatch(t -> t.NozID == Noz.NozID && "A1".equals(t.CmdType))) {
                    boxClient.cmdNormal.stream().filter(t -> t.NozID == NozID && "A1".equals(t.CmdType)).max(Comparator.comparingLong(t -> t.CreateTime))
                        .ifPresent(boxClient.cmdNormal::remove);
                }
                returnMessage[0] = "40";
                RFID.UserName = "";
                RFID.Tag = "";
                RFID.Bal = BigDecimal.ZERO;
                RFID.CarNo = "";
                Noz.CardNo = "";
                Noz.UserName = "";
                Noz.CarNo = "";
                Noz.Bal = BigDecimal.ZERO;
                throw new Code("RFID Cancel");
            }
            String bin9 = Tag.substring(0, 9);
            Row customers = null;
            Row customerCardInfos = null;
            Row customerAccounts = null;
            Row carNumberGroup = null;
            int num2 = 0;
            if (InsertCmdAuth == 1) {
                Holder emp = BaseFunction.GetEmpMess(oldRFIDTag);
                if (!emp.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new Code(emp.error());
                }
                if (!"0".equals(emp.b().str("Status"))) {
                    num = 247;
                    returnMessage[0] = Integer.toHexString(num);
                    throw new Code("Card Statue Error");
                }
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (cus.ok()) {
                    customers = cus.a();
                    customerCardInfos = cus.b();
                    customerAccounts = cus.c();
                }
                if (bin9.equals("000000004")) {
                    carNumberGroup = db().first("CarNumberGroup", "{CarNumber} = :c", p("c", oldCarNo));
                }
            } else if (bin9.equals("000000004")) {
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (!cus.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new Code(cus.error());
                }
                customers = cus.a();
                customerCardInfos = cus.b();
                customerAccounts = cus.c();
                carNumberGroup = db().first("CarNumberGroup", "{CardDigits} = :d", p("d", Password));
                if (carNumberGroup == null) {
                    num = 2;
                    returnMessage[0] = "02";
                    throw new Code("Password Error");
                }
                num2 = 1;
            } else if (!bin9.equals("000000000")) {
                Holder cus = BaseFunction.GetCustomerMess(Tag);
                if (!cus.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new Code(cus.error());
                }
                customers = cus.a();
                customerCardInfos = cus.b();
                customerAccounts = cus.c();
                String cipher = customerCardInfos.str("CipherFlg");
                if (!cipher.isEmpty() && !cipher.equals(Password)) {
                    num = 2;
                    returnMessage[0] = "02";
                    throw new Code("Password Error");
                }
                String oil = customerCardInfos.str("LimitOil");
                if (customerCardInfos.get("LimitOil") != null && !oil.isEmpty() && !oil.equals("0") && Noz.GradeID != Conv.toInt(oil)) {
                    num = 2;
                    returnMessage[0] = "F0";
                    throw new Code("Limit Grade");
                }
                String confine = customerCardInfos.str("UseConfine");
                if (customerCardInfos.get("UseConfine") != null && !confine.isEmpty() && !confine.equals("0")
                    && !confine.contains(String.valueOf(Noz.StationID))) {
                    num = 2;
                    returnMessage[0] = "EF";
                    throw new Code("Station Limit");
                }
                num2 = 1;
            } else {
                Holder emp = BaseFunction.GetEmpMess(Tag);
                if (!emp.ok()) {
                    num = 2;
                    returnMessage[0] = "04";
                    throw new Code(emp.error());
                }
                if (!"0".equals(emp.b().str("Status"))) {
                    num = 247;
                    returnMessage[0] = Integer.toHexString(num);
                    throw new Code("Card Statue Error");
                }
            }
            BigDecimal num3 = BigDecimal.valueOf(-1);
            int num4 = 0;
            SumTransaction sumTransaction = new SumTransaction();
            boolean customerCard = !bin9.equals("000000000") && !bin9.equals("000000004");
            if (num2 == 1) {
                if (customerCardInfos.i("Suspend") != 0) {
                    num = 2;
                    returnMessage[0] = "F7";
                    throw new Code("CardStatus Error");
                }
                if (customerCard) {
                    sumTransaction = sum("{NormalTransaction.CardNo} = :v", Tag, accountSince(customerAccounts));
                    Row group2 = db().first("Group", "{GroupID} = :g", p("g", customerCardInfos.get("GroupID")));
                    if (group2 != null && group2.i("GroupType") == 1) {
                        customerAccounts.put("Balance", Conv.toDouble(group2.get("Balance")));
                        sumTransaction = sum("{NormalTransaction.CardGroup} = :v", group2.get("GroupID"), Conv.toLong(group2.get("UpdateTime")));
                    }
                    NormalNozStatue noz = new NormalNozStatue(0, String.valueOf(Noz.NozID), Noz.TankID, Noz.AuthTypeID, Noz.Price, 0, 1, 1, 0, 0,
                        IniInfo.IdentifyMode);
                    noz.GradeID = Noz.GradeID;
                    NormalFunction.RFIDLimit lim = NormalFunction.GetRFIDLimit(customerCardInfos, customerAccounts, noz, sumTransaction);
                    if (!lim.ok()) {
                        num = Integer.parseInt(lim.res());
                        returnMessage[0] = Integer.toHexString(num);
                        throw new Code("RFID Limit");
                    }
                    num4 = lim.PreSetType();
                    num3 = lim.PreSetValue();
                }
            }
            int preSetType = 0;
            BigDecimal value = BigDecimal.ZERO;
            long num5;
            String text;
            String text2;
            BigDecimal num6 = BigDecimal.valueOf(9000000);
            if (customerCard) {
                sumTransaction = sum("{NormalTransaction.CardNo} = :v", Tag, accountSince(customerAccounts));
                BigDecimal value2 = Conv.toDecimal(customerAccounts.get("Balance")).subtract(sumTransaction.SumAmount);
                Row group = db().first("Group", "{GroupID} = :g", p("g", customerCardInfos.get("GroupID")));
                if (group != null && group.i("GroupType") == 1) {
                    sumTransaction = sum("{NormalTransaction.CardGroup} = :v", group.get("GroupID"), Conv.toLong(group.get("UpdateTime")));
                    value2 = Conv.toDecimal(group.get("Balance")).subtract(sumTransaction.SumAmount);
                }
                num5 = (long) Math.rint(Conv.round(value2.doubleValue(), 3) * Math.pow(10.0, Noz.AmountDigits));
                Row bs = db().first("Box_Set", "{ID} = :i", p("i", Noz.BoxPortID));
                if (bs != null) {
                    long cap = bs.i("Box_DeviceType") == 1 ? 90000000L : 900000L;
                    if (num5 > cap) {
                        num5 = cap;
                    }
                }
                if (num5 <= 0) {
                    num = 8;
                    returnMessage[0] = "08";
                    throw new Code("Bal Lost");
                }
                value = rnd(value2, Noz.MRadio);
                if (num3.compareTo(BigDecimal.valueOf(-1)) != 0) {
                    preSetType = num4;
                    value = num3;
                }
                text = customers.str("VehicleNo");
                text2 = customers.str("Name");
                num6 = Conv.toDecimal(customerAccounts.get("Balance")).subtract(sumTransaction.SumAmount);
            } else {
                num5 = 900000L;
                Row bs = db().first("Box_Set", "{ID} = :i", p("i", Noz.BoxPortID));
                if (bs != null && bs.i("Box_DeviceType") == 1) {
                    num5 = 90000000L;
                }
                text = carNumberGroup == null ? "" : carNumberGroup.str("CarNumber");
                text2 = customers == null ? "" : customers.str("Name");
            }
            String text3 = "20202020202020202020" + BaseFunction.StringConvertToAscii(text);
            String text4 = "202020202020202020202020202020" + BaseFunction.StringConvertToAscii(text2);
            returnMessage[0] = "01" + text4.substring(text4.length() - 30) + text3.substring(text3.length() - 20) + setLen(Noz.AmountDigits, 2)
                + SetLength(String.format("%02X", num5), 10);
            CmdPreSet cmdPreSet = new CmdPreSet();
            cmdPreSet.DeviceNo = boxClient.NoHead.toUpperCase();
            cmdPreSet.NozID = RFID.NozID;
            cmdPreSet.PreSetType = preSetType;
            cmdPreSet.PreSetValue = value;
            cmdPreSet.PreSetPrice = Noz.Price;
            cmdPreSet.CardNo = Tag;
            cmdPreSet.CarNo = text;
            cmdPreSet.Bal = num6;
            cmdPreSet.AuthType = true;
            CmdList cmdList4 = new CmdList();
            cmdList4.DeviceNo = boxClient.NoHead.toUpperCase();
            cmdList4.NozID = RFID.NozID;
            cmdList4.CmdData = json(cmdPreSet);
            cmdList4.CmdType = "A1";
            cmdList4.CmdPlatType = 2;
            cmdList4.CreateTime = now();
            cmdList4.StationID = boxClient.StationID;
            cmdList4.Deal = 0;
            cmdList4.Dowload = true;
            log("Noz.Statue:" + Noz.Statue, boxClient.NoHead.toUpperCase());
            int mil = Integer.parseInt(mileageStr, 16);
            BigDecimal fixedBal = decD(Conv.round((double) num5 / Math.pow(10.0, IniInfo.Mradio), 3));
            if (bin9.equals("000000001") || bin9.equals("000000002")) {
                if (!DealCardNumberRepeat(Tag) && InsertCmdAuth == 0) {
                    num = 2;
                    returnMessage[0] = Integer.toHexString(250);
                    throw new Code("Card Number Repeat");
                }
                if (InsertCmdAuth == 0) {
                    Row order = Row.of("NozID", Noz.NozID, "PreSetPrice", Noz.Price, "PreSetType", preSetType, "PreSetValue", value, "AuthType", true, "Bal", num6,
                        "CardNo", Tag, "CarNo", text, "StationID", IniInfo.StationID, "Mileage", mil);
                    cmdPreSet.DealCode = Conv.toStr(db().insert("CmdOrder", order));
                    cmdList4.CmdData = json(cmdPreSet);
                    queue(boxClient, Noz, cmdList4, IniInfo.IdentifyMode == 1);
                }
                Noz.CardNo = Tag;
                Noz.UserName = customers.str("Name");
                Noz.CarNo = customers.str("VehicleNo");
                Noz.Bal = rnd(num6, 3);
                RFID.UserName = text2;
                RFID.Tag = Tag;
                RFID.Bal = BigDecimal.valueOf(num5);
                RFID.CarNo = text;
                RFID.UpdateTime = rfidTime(boxClient);
            } else if (bin9.equals("000000000")) {
                if (Noz.CardNo == null || Noz.CardNo.isEmpty()) {
                    Noz.CardNo = Tag;
                    Row order = Row.of("NozID", Noz.NozID, "PreSetPrice", Noz.Price, "PreSetType", 0, "PreSetValue", BigDecimal.valueOf(-1), "AuthType", true,
                        "Bal", fixedBal, "CardNo", Tag, "CarNo", text, "StationID", IniInfo.StationID, "Mileage", mil);
                    cmdPreSet.DealCode = Conv.toStr(db().insert("CmdOrder", order));
                    cmdPreSet.PreSetValue = BigDecimal.valueOf(-1);
                    cmdList4.CmdData = json(cmdPreSet);
                    Noz.UserName = text2;
                    Noz.CarNo = "";
                    Noz.Bal = fixedBal;
                    if (Noz.Statue == 1) {
                        boxClient.cmdNormal.add(cmdList4);
                    } else {
                        boxClient.cmdAuth.stream().filter(t -> t.NozID == RFID.NozID && "A1".equals(t.CmdType)).findFirst().ifPresent(boxClient.cmdAuth::remove);
                        boxClient.cmdAuth.add(cmdList4);
                    }
                    RFID.UserName = text2;
                    RFID.Tag = Tag;
                    RFID.Bal = BigDecimal.valueOf(num5);
                    RFID.CarNo = text;
                    RFID.UpdateTime = rfidTime(boxClient);
                }
            } else if (bin9.equals("000000004")) {
                Noz.CardNo = Tag;
                if (InsertCmdAuth == 0) {
                    Row order = Row.of("NozID", Noz.NozID, "PreSetPrice", Noz.Price, "PreSetType", 0, "PreSetValue", BigDecimal.valueOf(-1), "AuthType", true,
                        "Bal", fixedBal, "CardNo", Tag, "CarNo", text, "StationID", IniInfo.StationID, "Mileage", mil);
                    cmdPreSet.DealCode = Conv.toStr(db().insert("CmdOrder", order));
                    cmdPreSet.PreSetValue = BigDecimal.valueOf(-1);
                    cmdList4.CmdData = json(cmdPreSet);
                    Noz.UserName = text2;
                    Noz.CarNo = text;
                    Noz.Bal = fixedBal;
                    queue(boxClient, Noz, cmdList4, IniInfo.IdentifyMode == 1);
                }
                RFID.UserName = text2;
                RFID.Tag = Tag;
                RFID.Bal = BigDecimal.valueOf(num5);
                RFID.CarNo = text;
                RFID.UpdateTime = rfidTime(boxClient);
            } else if (Noz.CardNo == null || Noz.CardNo.isEmpty()) {
                Noz.CardNo = Tag;
                Noz.UserName = text2;
                if (Noz.Statue == 1) {
                    boxClient.cmdNormal.add(cmdList4);
                } else {
                    boxClient.cmdAuth.stream().filter(t -> t.NozID == RFID.NozID && "A1".equals(t.CmdType)).findFirst().ifPresent(boxClient.cmdAuth::remove);
                    boxClient.cmdAuth.add(cmdList4);
                }
            }
            return num;
        } catch (RuntimeException ex) {
            log("Tag Error:" + ex.getMessage(), boxClient.NoHead.toUpperCase().trim());
            if (num == 0) {
                num = 9;
            }
            return num;
        }
    }

    /** Commande A1 : mode d'identification 1 → file RFID ; pistolet décroché → envoi ; sinon en attente du décroché. */
    private static void queue(IBoxClientParam boxClient, AllNozStatue Noz, CmdList cmd, boolean identify) {
        if (identify) {
            boxClient.cmdRFID.removeIf(t -> t.NozID == Noz.NozID);
            boxClient.cmdRFID.add(cmd);
        } else if (Noz.Statue == 1) {
            boxClient.cmdNormal.add(cmd);
        } else {
            boxClient.cmdAuth.add(cmd);
        }
    }

    /** Math.Round(decimal, n) : l'échelle n'est jamais augmentée. */
    private static BigDecimal rnd(BigDecimal d, int n) {
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static BigDecimal decD(double d) {
        return new BigDecimal(Conv.num(d));
    }

    /** DealCardNumberRepeat : la carte est-elle déjà sur un autre pistolet ? (libérée si ce pistolet est au repos). */
    private static boolean DealCardNumberRepeat(String Tag) {
        try {
            for (IBoxClientParam b : BoxClientList) {
                AllNozStatue n = b.NozList.stream().filter(t -> Tag.equals(t.CardNo)).findFirst().orElse(null);
                if (n != null) {
                    if (n.Statue == 0 || n.Statue == -1 || n.Statue == 4) {
                        n.CardNo = "";
                    }
                    log("Repeat:" + json(n), "Repeat");
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** DealScore : points de fidélité de la dernière transaction du pistolet. */
    public static void DealScore(IBoxClientParam boxClient, String RFIDTag, AllNozStatue Noz, String[] Message) {
        Message[0] = "";
        try {
            log("Score", boxClient.NoHead.toUpperCase());
            Holder cus = BaseFunction.GetCustomerMess(RFIDTag);
            if (!cus.ok()) {
                Message[0] = "04";
                throw new IllegalStateException(Message[0]);
            }
            Row card = cus.b();
            if (card.i("ScoreRuleID") == 0) {
                Message[0] = "2000000000";
                return;
            }
            Row tr = db().first("NormalTransaction", "{NozID} = :n ORDER BY {CreateTime} DESC", p("n", Noz.NozID));
            if (tr == null) {
                throw new NullPointerException();
            }
            if (tr.i("ScoreDeal") == 1) {
                Message[0] = "";
                return;
            }
            String score = BaseFunction.SetStrLen(String.format("%02x", Conv.toInt(card.get("Score"))), 8);
            if (tr.i("ScoreDeal") == 2) {
                Message[0] = "20" + score;
                return;
            }
            Row obj = SW.dto().make("StationScoreUpdate", "StationID", IniInfo.StationID, "CardNo", RFIDTag, "GradeID", tr.get("GradeID"), "Volume",
                tr.get("Volume"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateScore", Wire.json(obj));
            if (!r.Result()) {
                Message[0] = "20" + score;
                throw new IllegalStateException("Score Update Error");
            }
            tr.put("ScoreDeal", 1);
            db().update("NormalTransaction", tr);
            Row back = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            card.put("Score", Conv.toInt(back.get("Score")));
            db().update("CustomerCardInfos", card);
            tr.put("ScoreDeal", 2);
            db().update("NormalTransaction", tr);
            Message[0] = "20" + BaseFunction.SetStrLen(String.format("%02x", Conv.toInt(card.get("Score"))), 8);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** DealStationGPS : position du boîtier (latitude, longitude) enregistrée quand elle change. */
    public static void DealStationGPS(String NoHead, byte[] bits) {
        try {
            byte[] a = new byte[4];
            System.arraycopy(bits, 0, a, 0, 4);
            float Latitude = NormalFunction.DealToFloat(a);
            System.arraycopy(bits, 5, a, 0, 4);
            float Longitude = NormalFunction.DealToFloat(a);
            if (Longitude != 0f && Latitude != 0f) {
                IBoxClientParam b = BoxClientList.stream().filter(t -> t.NoHead.equalsIgnoreCase(NoHead)).findFirst().orElse(null);
                if (b != null && Latitude != b.Latitude && Longitude != b.Longitude) {
                    Row box = db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead));
                    if (box != null) {
                        box.put("Latitude", new BigDecimal(Float.toString(Latitude)));
                        box.put("Longitude", new BigDecimal(Float.toString(Longitude)));
                        db().update("BoxList", box);
                    }
                    b.Latitude = Latitude;
                    b.Longitude = Longitude;
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** DealTankRaise : livraison détectée sur une cuve (montée du volume puis stabilité pendant 60 mesures). */
    public static TankStatue DealTankRaise(IBoxClientParam boxClient, TankStatue tank, BigDecimal oHeight, BigDecimal oVolume, BigDecimal wHeight,
                                           BigDecimal Tep) {
        try {
            tank.CurrentLevel = tank.OilVolume;
            tank.OilVolume = oVolume;
            if (tank.RaiseFlag == 0) {
                tank.FirstImport = 0;
                if (tank.OilVolume.compareTo(tank.CurrentLevel) > 0) {
                    tank.RaiseStartVolume = tank.CurrentLevel;
                    tank.RaiseStartTime = now();
                    tank.RaiseFlag = 1;
                    tank.RaiseLevel = tank.RaiseLevel.add(tank.OilVolume.subtract(tank.CurrentLevel));
                    tank.MaxLevel = oVolume;
                    tank.KeepTime = 0;
                }
            } else {
                if (tank.OilVolume.compareTo(tank.MaxLevel) > 0) {
                    tank.RaiseLevel = tank.RaiseLevel.add(tank.OilVolume.subtract(tank.MaxLevel));
                    tank.KeepTime = 0;
                    tank.MaxLevel = oVolume;
                } else {
                    tank.KeepTime++;
                }
                if (tank.KeepTime > 60) {
                    if (tank.RaiseLevel.compareTo(tank.OverImportLevel) > 0) {
                        db().insert("NormalTankStock", Row.of("TankID", tank.ID, "TankName", tank.TankName, "GradeName", tank.GradeName, "StartTime",
                            tank.RaiseStartTime, "EndTime", now(), "StartVolume", tank.RaiseStartVolume, "FinalVolume", oVolume, "StockVolume", tank.RaiseLevel,
                            "CreateTime", now(), "Upload", false));
                    }
                    tank.RaiseLevel = BigDecimal.ZERO;
                    tank.RaiseStartTime = 0L;
                    tank.RaiseFlag = 0;
                    tank.RaiseStartVolume = BigDecimal.ZERO;
                    tank.KeepTime = 0;
                }
            }
            if (tank.FirstImport == 0) {
                if (tank.RaiseLevel.compareTo(tank.OverImportLevel) > 0) {
                    Row obj = SW.dto().make("NormalTankStock", "TankID", tank.ID, "TankName", tank.TankName, "GradeName", tank.GradeName, "StartTime",
                        tank.RaiseStartTime, "StartVolume", tank.RaiseStartVolume, "ID", IniInfo.StationID);
                    BaseFunction.CreateTankCable(tank.ID, new String[1]);
                    BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UploadNormalTankImportStart", Wire.json(obj));
                }
                tank.FirstImport++;
            }
            return tank;
        } catch (RuntimeException e) {
            return tank;
        }
    }

    public static String GetGroupType(int GroupType) {
        return switch (GroupType) {
            case 0 -> MultiLanguage.getText("NormalGroup");
            case 1 -> MultiLanguage.getText("SharedGroup");
            default -> "";
        };
    }
}
