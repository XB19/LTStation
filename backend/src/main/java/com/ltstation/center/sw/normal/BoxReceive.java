package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.normal.WirelessCmd.CmdList;
import com.ltstation.center.sw.normal.WirelessCmd.CmdReplyStatue;
import com.ltstation.center.sw.normal.WirelessCmd.UnTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Wireless.BoxReceiveAsyncPlugin : trames reçues des boîtiers sans fil (pistolets, cuves, RFID, PLC, afficheur,
 * transactions hors ligne, versions, téléchargements) et réponses. SendTCPMessage → NormalFunction.MainFormMessage.
 */
public final class BoxReceive {

    private static final Charset DEFAULT = Charset.forName("GBK");
    private static final DateTimeFormatter BOX_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private BoxReceive() {
    }

    private static Sugar db() {
        return SW.db();
    }

    /** Branche le traitement sur le serveur TCP. */
    public static void Install() {
        BoxTcpServer.OnReceived = BoxReceive::OnReceived;
    }

    private static String X2(byte b) {
        return String.format("%02X", b & 0xFF);
    }

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
            sb.append(String.format("%02x", bits[from + i] & 0xFF));
        }
        return sb.toString();
    }

    private static int u(byte b) {
        return b & 0xFF;
    }

    private static String setLen(Object o, int n) {
        return BaseInfo.SetLength(String.valueOf(o), n);
    }

    private static long now() {
        return BaseFunction.ConvertDateTimeToInt();
    }

    private static void log(String text, String path) {
        BaseInfo.log(text, path);
    }

    private static boolean digits(String s) {
        return s.matches("[0-9]+");
    }

    /** decimal.TryParse puis Math.Round(x / 10^n, n) ; null si la valeur n'est pas numérique. */
    private static BigDecimal parsed(String s, int n) {
        if (!digits(s)) {
            return null;
        }
        BigDecimal d = new BigDecimal(s).divide(BigDecimal.TEN.pow(n));
        return d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d;
    }

    private static BigDecimal decD(double d) {
        return new BigDecimal(Conv.num(d));
    }

    private static BigDecimal float1(byte[] bits, int at) {
        byte[] a = new byte[4];
        System.arraycopy(bits, at, a, 0, 4);
        return decD(Conv.round(NormalFunction.DealToFloat(a), 1));
    }

    private static void SendTCPMessage(int Cmd, int F, int S) {
        NormalFunction.MainFormMessage.accept(Cmd, F, S);
    }

    /** DealReceiveData : découpage des trames F5 / FA, CRC, déchiffrement puis CmdDeal. */
    private static void OnReceived(BoxTcpServer.Session client, byte[] data) {
        String text = SerialClass.byteToHexStr(data);
        log("IP:" + client.IP + "  Receive:" + text, "Main");
        try {
            String ReceiveData = text;
            while (ReceiveData.length() > 0) {
                String frame;
                String head = "F5";
                String text3;
                if (ReceiveData.startsWith("F5")) {
                    text3 = ReceiveData.substring(2, 6);
                    int num = Integer.parseInt(text3, 16) * 2 + 4;
                    frame = ReceiveData.substring(0, num);
                    ReceiveData = ReceiveData.substring(num);
                } else if (ReceiveData.startsWith("FA")) {
                    head = "FA";
                    text3 = ReceiveData.substring(14, 18);
                    int num = Integer.parseInt(text3, 16) * 2 + 4;
                    frame = ReceiveData.substring(0, num);
                    ReceiveData = ReceiveData.substring(num);
                } else {
                    break;
                }
                byte[] array = SerialClass.strToToHexByte(frame);
                byte[] crc = {array[array.length - 2], array[array.length - 1]};
                log("Real Data:" + frame, "Main");
                if (BaseInfo.CRC16(array).equals(SerialClass.byteToHexStr(crc))) {
                    if (head.equals("FA")) {
                        text3 = BaseInfo.SetLength(String.format("%02x", Integer.parseInt(text3, 16) - 6), 4);
                        frame = frame.substring(0, 2) + text3 + frame.substring(18);
                        array = SerialClass.strToToHexByte(frame);
                    }
                    log("Real D Data:" + SerialClass.byteToHexStr(array), "Main");
                    byte[] outdata = BaseFunction.DealCmdSecret(array);
                    if (outdata == null) {
                        throw new IllegalStateException("CRC Error");
                    }
                    CmdDeal(head, client, outdata);
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private static IBoxClientParam byHead(String noHead) {
        String h = noHead.trim().toUpperCase();
        for (IBoxClientParam b : BaseInfo.BoxClientList) {
            if (b.NoHead != null && b.NoHead.trim().toUpperCase().equals(h)) {
                return b;
            }
        }
        return null;
    }

    private static boolean CheckBoxValid(String NoHead) {
        return db().first("BoxList", "{BoxDeviceNo} = :n", p("n", NoHead.toUpperCase().trim())) != null;
    }

    /** ClientStartThread : démarre l'envoi des commandes du boîtier ; null s'il n'est pas chargé. */
    private static IBoxClientParam ClientStartThread(String NoHead) {
        IBoxClientParam b = byHead(NoHead);
        if (b != null && !b.IsConnect) {
            b.IsConnect = true;
            b.StartThread();
        }
        return b;
    }

    /** Réponse de la trame : vide = rien à signaler, « Ready » = aucune réponse. */
    private static final class Reply {
        String cmdType = "";
        StringBuilder text = new StringBuilder();
        String valueReturn = "";
    }

    private static void CmdDeal(String cmdHead, BoxTcpServer.Session client, byte[] bits) {
        try {
            Reply reply = new Reply();
            int num = (u(bits[1]) << 8) | u(bits[2]);
            byte[] noHeadBytes = new byte[20];
            System.arraycopy(bits, 7, noHeadBytes, 0, 20);
            String NoHead = new String(noHeadBytes, DEFAULT).replace("\0", "");
            log("NoHead:" + NoHead, "Connection");
            log("NoHead:" + NoHead, "Main");
            IBoxClientParam boxClient;
            if (!client.Id().trim().equalsIgnoreCase(NoHead.trim())) {
                if (!CheckBoxValid(NoHead)) {
                    throw new IllegalStateException("Box Error");
                }
                client.ResetId(NoHead.trim().toUpperCase());
                boxClient = ClientStartThread(NoHead.trim().toUpperCase());
                if (boxClient == null) {
                    throw new IllegalStateException("Box Get Error:boxClient Lost");
                }
                log("NoHead Reset", "Connection");
                boxClient.IP = client.IP;
                boxClient.port = client.Port;
                boxClient.socketClient = client;
            } else {
                boxClient = byHead(NoHead);
                if (boxClient == null) {
                    throw new IllegalStateException("boxClient Get Error");
                }
                if (boxClient.IP == null) {
                    boxClient.IP = client.IP;
                    boxClient.port = client.Port;
                }
                boxClient.socketClient = client;
                boxClient.cmdHead = cmdHead;
            }
            String path = boxClient.NoHead.toUpperCase();
            log("Real Data:" + SerialClass.byteToHexStr(bits), path);
            boxClient.Online = true;
            byte[] gps = new byte[10];
            System.arraycopy(bits, 27, gps, 0, 10);
            BaseInfo.DealStationGPS(NoHead, gps);
            switch (u(bits[37])) {
                case 11 -> {
                    reply.cmdType = "0B";
                    LocalDateTime d = LocalDateTime.now();
                    if (boxClient.TimeZone != -99) {
                        d = d.plusHours(boxClient.TimeZone);
                    }
                    reply.text.append(d.format(BOX_TIME));
                    log("Time", path);
                }
                case 14 -> version(bits, boxClient, NoHead, reply);
                case 13 -> {
                    if (boxClient.CurrentProgram != -1) {
                        reply.cmdType = "0D";
                        int index = (u(bits[38]) << 8) | u(bits[39]);
                        NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(boxClient.CurrentProgram, index);
                        boxClient.ProgramDateTime = now();
                        if (!pg.ok() && pg.Error().equals("Finished")) {
                            reply.valueReturn = "Ready";
                            boxClient.CurrentProgram = -1;
                        }
                        reply.text.append(setLen(String.format("%02x", pg.TotalPage()), 4)).append(setLen(String.format("%02x", index), 4))
                            .append(pg.ProgramData());
                    }
                }
                case 16 -> {
                    reply.cmdType = "10";
                    frame10(bits, num, client, boxClient, reply);
                }
                case 17 -> deviceVersions(bits, num, client, boxClient);
                case 18 -> {
                    if (boxClient.CurrentProgram != -1) {
                        reply.cmdType = "12";
                        program(bits, client, boxClient, reply);
                    }
                }
                case 15 -> {
                    reply.cmdType = "0F";
                    offline(bits, client, boxClient, reply);
                }
                case 22 -> {
                    log("0x16 Deal", path);
                    reply.cmdType = "16";
                    int page = (u(bits[38]) << 8) | u(bits[39]);
                    if (page != 0) {
                        Sugar.Page pg = db().page("EmployeeCardInfos", "{Status} = '0'", null, null, page, 10);
                        StringBuilder cards = new StringBuilder();
                        for (Row r : pg.rows()) {
                            cards.append(r.str("ASN").substring(8));
                        }
                        String version = "00";
                        Row sv = db().first("StationVersion");
                        if (sv != null) {
                            version = setLen(String.format("%02x", sv.i("EmpVersion") % 255), 2);
                        }
                        reply.text.append(version).append(setLen(String.format("%02x", pg.totalPage()), 4)).append(setLen(String.format("%02x", page), 4))
                            .append(cards);
                    }
                }
                case 31 -> {
                    reply.cmdType = "1F";
                    offlineList(bits, client, boxClient, reply);
                }
                default -> {
                }
            }
            if (reply.valueReturn.isEmpty()) {
                CmdReplyStatue obj = new CmdReplyStatue();
                obj.CmdType = reply.cmdType;
                obj.CmdValue = reply.text.toString();
                CmdList cmd = new CmdList();
                cmd.CmdType = "ClientReply";
                cmd.CmdData = BaseInfo.json(obj);
                byte[] out = BaseInfo.CreateCmd(cmdHead, boxClient.TimeZone, cmd, boxClient);
                if (out != null) {
                    client.Send(out);
                }
            } else if (!reply.valueReturn.equals("Ready")) {
                log("Reply Auth To Box: " + reply.valueReturn, path);
            }
            if (boxClient.CurrentProgram != -1 && now() - boxClient.ProgramDateTime > 300000) {
                boxClient.CurrentProgram = 0;
            }
        } catch (RuntimeException ex) {
            log("client Error:" + ex.getMessage(), "Connection");
            log("client Error Data:" + SerialClass.byteToHexStr(bits), "Connection");
            client.Close(ex.getMessage());
        }
    }

    /** Trame 0E : version du boîtier ; demande la liste des cartes employé si elle a changé. */
    private static void version(byte[] bits, IBoxClientParam boxClient, String NoHead, Reply reply) {
        String path = boxClient.NoHead.toUpperCase();
        log("0E", path);
        reply.cmdType = "0E";
        reply.valueReturn = "Ready";
        byte[] array = new byte[10];
        System.arraycopy(bits, 38, array, 0, 10);
        String Version = new String(array, DEFAULT).replace("\0", "");
        db().exec(db().sql("BoxList", "update [BoxList] set {BoxVersion} = :v where {BoxDeviceNo} = :n"), p("v", Version, "n", NoHead));
        boxClient.CurrentProgram = -1;
        boxClient.ProgramDateTime = 0L;
        boxClient.BoxVersion = Version;
        SendTCPMessage(2308, boxClient.ID, 0);
        if (bits.length < 51) {
            return;
        }
        int empVersion = u(bits[48]);
        Row sv = db().first("StationVersion", "{StationID} = :s", p("s", IniInfo.StationID));
        if (sv == null) {
            db().insert("StationVersion", Row.of("StationID", IniInfo.StationID, "StationCode", IniInfo.Code, "EmpVersion", 0));
        } else if (sv.i("EmpVersion") == empVersion) {
            return;
        }
        CmdList item = new CmdList();
        item.DeviceNo = boxClient.NoHead;
        item.CmdType = "16";
        item.NozID = 0;
        item.Deal = 0;
        item.CreateTime = now();
        item.CmdPlatType = 2;
        item.StationID = boxClient.StationID;
        boxClient.cmdNormal.add(item);
    }

    private static void frame10(byte[] bits, int num, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        int off = 0;
        int count = 0;
        int num3 = 0;
        while (off + 40 < num) {
            count++;
            if (count > 40) {
                throw new IllegalStateException("threadCount Broken");
            }
            int type = u(bits[38 + off]);
            int len = (u(bits[39 + off]) << 8) | u(bits[40 + off]);
            switch (type) {
                case 1 -> nozBlock(bits, off, len, client, boxClient, reply);
                case 2 -> num3 += tankBlock(bits, off, len / 18, 18, num3, boxClient, reply, false);
                case 3 -> rfidBlock(bits, off, len, client, boxClient, reply);
                case 4 -> {
                    len = u(bits[39 + off]) - 1;
                    reply.text.append("01");
                }
                case 5, 12 -> {
                    log("Price", boxClient.NoHead.toUpperCase());
                    if (boxClient.priceBorad != null) {
                        int st = (u(bits[43 + off]) & 0x80) >> 7;
                        boxClient.priceBorad.Statue = st;
                        boxClient.priceBorad.UpdateTime = BaseFunction.ConvertDateTimeToInt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                        reply.text.append(st != 1 ? "00" : "01");
                    }
                }
                case 6 -> {
                    log("Port 5 Tank", boxClient.NoHead.toUpperCase());
                    int n = u(bits[39 + off]);
                    int size = u(bits[40 + off]);
                    len = n * size;
                    num3 += tankBlock(bits, off, n, size, num3, boxClient, reply, true);
                }
                case 7 -> len = plcBlock(bits, off, boxClient, reply);
                default -> {
                }
            }
            off = off + len + 3;
        }
    }

    private static void nozBlock(byte[] bits, int off, int len, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        String path = boxClient.NoHead.toUpperCase();
        int k = 0;
        int shortLen = u(bits[41 + off + k]);
        int longLen = u(bits[42 + off + k]);
        k += 2;
        while (k < len) {
            int nozId = u(bits[41 + k + off]);
            log("Noz" + nozId, path);
            BaseInfo.Found found = BaseInfo.GetClientNoz(nozId, client.Id());
            int st = u(bits[42 + k + off]);
            log("Statue:" + Integer.toBinaryString(st), path);
            int fueling = (st & 2) >> 1;
            int pause = (st & 4) >> 2;
            int done = (st & 8) >> 3;
            int online = (st & 0x80) >> 7;
            int up = st & 1;
            int locked = (st & 0x20) >> 5;
            if (found == null) {
                k += fueling != 1 && done != 1 && pause != 1 ? shortLen : longLen;
                continue;
            }
            AllNozStatue Noz3 = found.noz();
            log("Statue:" + Integer.toBinaryString(st), path);
            boolean changed = false;
            int old = Noz3.Statue;
            if (online == 0) {
                Noz3.Statue = -1;
                changed = true;
            } else if (fueling == 1) {
                Noz3.Statue = 3;
                changed = true;
            } else if (locked == 1) {
                Noz3.Statue = 2;
                changed = true;
            } else if (up == 1) {
                Noz3.Statue = 1;
                changed = true;
            } else if (Noz3.Statue != 0) {
                Noz3.Statue = 0;
                changed = true;
            }
            if (!changed) {
                Noz3.Statue = 0;
            }
            if (Noz3.Statue != old) {
                Noz3.LastStatue = old;
            }
            boolean values = false;
            boolean ok = true;
            String vol = "0";
            String amt = "0";
            String price = "0";
            String volTotal = "0";
            String amtTotal = "0";
            String order2 = "";
            if (fueling == 1 || done == 1 || pause == 1) {
                values = true;
                int at = 43 + k + off;
                order2 = hex(bits, at, 4);
                BigDecimal v = parsed(hex(bits, at + 4, 4), Noz3.VRadio);
                BigDecimal a = parsed(hex(bits, at + 8, 4), Noz3.MRadio);
                BigDecimal pr = parsed(hex(bits, at + 12, 3), Noz3.PRadio);
                BigDecimal vt = parsed(hex(bits, at + 15, 6), Noz3.VRadio);
                BigDecimal at2 = parsed(hex(bits, at + 21, 6), Noz3.MRadio);
                if (v != null) {
                    vol = v.toPlainString();
                    Noz3.Volume = v;
                }
                if (a != null) {
                    amt = a.toPlainString();
                    Noz3.Amount = a;
                }
                if (pr != null) {
                    price = pr.toPlainString();
                    Noz3.Price = pr;
                }
                if (vt != null) {
                    volTotal = vt.toPlainString();
                    Noz3.VolumeTotal = vt;
                }
                if (at2 != null) {
                    amtTotal = at2.toPlainString();
                    Noz3.AmountTotal = at2;
                }
                ok = v != null && a != null && pr != null && vt != null && at2 != null;
                k += longLen;
            } else {
                k += shortLen;
            }
            if (changed || Noz3.ConnectFirst) {
                Noz3.ConnectFirst = false;
            }
            log("bit3:" + done, path);
            if (Noz3.RaiseNoz == 0 && (st & 1) == 1 && done != 1 && fueling != 1 && Noz3.AuthTypeID != 2 && online != 0 && Noz3.IsOnDuty == 1) {
                BaseInfo.DealAuthorize(client, nozId, new String[1]);
                SendTCPMessage(2310, boxClient.ID, nozId);
            }
            Noz3.LastRaiseNoz = Noz3.RaiseNoz;
            log("RasizeNoz B:" + Noz3.LastRaiseNoz, path);
            log("RasizeNoz Now:" + (st & 1), path);
            Noz3.RaiseNoz = online == 1 ? st & 1 : -1;
            int code = st;
            if (done == 0) {
                Noz3.RecordUpload = 0;
            } else if (ok) {
                log("Deal Transaction", path);
                long nozRunTime = Noz3.NozRunTime;
                Noz3.RecordUpload = 1;
                log("Volume:" + vol, path);
                log("Amount:" + amt, path);
                log("Price:" + price, path);
                log("AmountTotal:" + amtTotal, path);
                log("VolumeTotal:" + volTotal, path);
                if (BaseInfo.DealBoxTranasction(client.Id(), nozId, vol, amt, price, amtTotal, volTotal, order2, nozRunTime, new String[1])) {
                    code = 2;
                    Noz3.NozRunTime = 0L;
                    Noz3.CardNo = "";
                    Noz3.UserName = "";
                    Noz3.CarNo = "";
                    Noz3.Bal = BigDecimal.ZERO;
                    Noz3.AuthMessage = "";
                }
            } else {
                code = 2;
            }
            if (ok) {
                if (pause == 1) {
                    code = 1;
                } else if (code != 2) {
                    code = 0;
                }
                Noz3.ErrorCode = "0";
            } else {
                Noz3.ErrorCode = "100001";
                log("Flag Error", path);
            }
            if (Noz3.Statue == 3 && Noz3.NozRunTime == 0L) {
                Noz3.NozRunTime = now();
            }
            if (values && ok) {
                Noz3.CurrentAmount = new BigDecimal(amt);
                Noz3.CurrentVolume = new BigDecimal(vol);
                Noz3.AmountTotal = new BigDecimal(amtTotal);
                Noz3.VolumeTotal = new BigDecimal(volTotal);
                Noz3.Price = new BigDecimal(price);
            }
            Noz3.UpdateTime = now();
            if (Noz3.LastRaiseNoz != 0 && Noz3.LastRaiseNoz != -1 && (Noz3.RaiseNoz == 0 || Noz3.RaiseNoz == -1)) {
                log("Noz Statue 0", path);
                Noz3.CardNo = "";
                Noz3.Bal = BigDecimal.ZERO;
                Noz3.UserName = "";
            }
            reply.text.append(BaseInfo.SetLength(String.format("%02x", code), 2));
            if (Noz3.Statue == -1) {
                Noz3.Statue = 4;
            }
            SendTCPMessage(2305, boxClient.ID, nozId);
        }
    }

    private static int[] alarms(String error) {
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

    /** Blocs 2 (cuves) et 6 (cuves du port 5) ; renvoie le nombre de cuves du bloc. */
    private static int tankBlock(byte[] bits, int off, int count, int size, int num3, IBoxClientParam boxClient, Reply reply, boolean port5) {
        String path = boxClient.NoHead.toUpperCase();
        boxClient.TankRun = 1;
        if (!port5) {
            log("Tank", path);
        }
        for (int i = 0; i < count; i++) {
            int base = 41 + off + i * size;
            int TankID = u(bits[base]) + num3;
            if (i >= boxClient.TankList.size()) {
                continue;
            }
            String TankError = BaseInfo.SetLength(Integer.toBinaryString(u(bits[base + 1])), 8);
            log("TankError:" + TankError, path);
            int[] e = alarms(TankError);
            log("TL:" + e[0], path);
            log("CG:" + e[1], path);
            log("Ttemp:" + e[2], path);
            log("TLevel:" + e[3], path);
            log("EG:" + e[4], path);
            BigDecimal h = float1(bits, base + 2);
            BigDecimal v = float1(bits, base + 6);
            BigDecimal w = float1(bits, base + 10);
            BigDecimal t = float1(bits, base + 14);
            log("oHeight:" + h.toPlainString(), path);
            log("oVolume:" + v.toPlainString(), path);
            log("wHeight:" + w.toPlainString(), path);
            if (h.signum() == 0 && v.signum() == 0 && w.signum() == 0 && t.signum() == 0) {
                continue;
            }
            TankStatue tank = boxClient.TankList.stream().filter(x -> x.TankID == TankID).findFirst().orElse(null);
            if (tank == null) {
                continue;
            }
            if (tank.OverImportLevel.signum() != 0) {
                BaseInfo.DealTankRaise(boxClient, tank, h, v, w, t);
            }
            if (port5) {
                log("get", path);
            }
            boolean changed = tank.OilHeight.compareTo(h) != 0 || tank.OilVolume.compareTo(v) != 0 || tank.WaterHeight.compareTo(w) != 0
                || tank.Temp.compareTo(t) != 0;
            if (tank.TL != e[0] || tank.EG != e[4] || tank.CG != e[1] || tank.Ttemp != e[2] || tank.TLevel != e[3]) {
                RealTankChange c = new RealTankChange();
                c.TankID = tank.ID;
                c.StationID = tank.StationID;
                c.CompanyID = boxClient.CompanyID;
                c.TankName = tank.TankName;
                c.UpdateTime = now();
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
                db().exec(db().sql("NormalTank", "update [NormalTank] set {TankError} = :e where {ID} = :i"), p("e", TankError, "i", tank.ID));
            }
            if (!port5) {
                log("get", path);
            }
            tank.OilHeight = h;
            tank.OilVolume = v;
            tank.WaterHeight = w;
            tank.Temp = t;
            tank.UpdateTime = now();
            if (changed) {
                db().insert("NormalTankHistory", Row.of("TankID", tank.ID, "TankNo", String.valueOf(tank.TankID), "TankName", tank.TankName, "CreateTime",
                    tank.UpdateTime, "Upload", 0, "CurrentHeight", tank.OilHeight, "CurrentVolume", tank.OilVolume, "CurrentTemp", tank.Temp,
                    "CurrentWaterHeight", tank.WaterHeight));
            }
            if (port5) {
                SendTCPMessage(2307, boxClient.ID, TankID);
                if (changed) {
                    SendTCPMessage(2311, boxClient.ID, tank.ID);
                }
            }
        }
        reply.text.append("01");
        return count;
    }

    private static String nowText(IBoxClientParam boxClient) {
        LocalDateTime d = LocalDateTime.now();
        return (boxClient.TimeZone != -99 ? d.plusHours(boxClient.TimeZone) : d).format(Conv.STD);
    }

    private static void rfidBlock(byte[] bits, int off, int len, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        String path = boxClient.NoHead.toUpperCase();
        int k = 0;
        int shortLen = u(bits[41 + off + k]);
        int longLen = u(bits[42 + off + k]);
        k += 2;
        while (k < len) {
            int RFIDNo = u(bits[41 + off + k]);
            int st = u(bits[42 + off + k]);
            int online = (st & 0x80) >> 7;
            int left = (st & 4) >> 2;
            int read = (st & 8) >> 3;
            int skip = read != 1 ? shortLen : longLen;
            RFIDRunData rfid = boxClient.RFIDList.stream().filter(t -> t.RFIDNo == RFIDNo).findFirst().orElse(null);
            if (rfid == null) {
                reply.text.append("10");
                k += skip;
                continue;
            }
            rfid.IsOnline = online != 0;
            BaseInfo.Found found = BaseInfo.GetClientNoz(rfid.NozID, client.Id());
            if (found == null) {
                reply.text.append("20");
                k += skip;
                continue;
            }
            AllNozStatue noz = found.noz();
            if (read == 1 || left == 1) {
                int at = 43 + off + k;
                String tag = hexLower(bits, at, 10);
                String password = hexLower(bits, at + 10, 2);
                String mileage = "000000";
                if (longLen == 17) {
                    mileage = hexLower(bits, at + 12, 3);
                }
                String[] returnMessage = {""};
                log("RFID No:" + tag, path);
                if (noz.IsOnDuty == 1) {
                    if (tag.startsWith("000000003")) {
                        BaseInfo.DealScore(boxClient, tag, noz, returnMessage);
                    } else {
                        String oldTag = tag;
                        String oldCarNo = "";
                        int insert = 0;
                        if (IniInfo.IdentifyMode == 1) {
                            CmdList c = boxClient.cmdRFID.stream().filter(t -> t.NozID == noz.NozID && "A1".equals(t.CmdType)).findFirst().orElse(null);
                            if (c != null && c.CmdData != null && !c.CmdData.isEmpty()) {
                                Row order = Wire.parseRow(c.CmdData);
                                if (!order.str("CardNo").startsWith("000000000") && tag.startsWith("000000000") && !tag.equals("00000000000000000000")) {
                                    insert = 1;
                                    tag = order.str("CardNo");
                                    oldCarNo = order.str("CarNo");
                                }
                            }
                        }
                        if (BaseInfo.DealRFIDTag(boxClient, RFIDNo, tag, password, mileage, insert, oldTag, oldCarNo, returnMessage) == 0) {
                            SendTCPMessage(2310, boxClient.ID, noz.NozID);
                            if (insert == 1) {
                                CmdList c2 = boxClient.cmdRFID.stream().filter(t -> t.NozID == noz.NozID && "A1".equals(t.CmdType)).findFirst().orElse(null);
                                if (c2 != null) {
                                    if (noz.Statue == 1) {
                                        boxClient.cmdNormal.add(c2);
                                    } else {
                                        boxClient.cmdAuth.add(c2);
                                    }
                                    boxClient.cmdRFID.remove(c2);
                                }
                            }
                        }
                    }
                } else {
                    returnMessage[0] = "F9";
                }
                reply.text.append(returnMessage[0]);
                k += longLen;
            } else {
                log("RFID Message:", path);
                if (noz.Statue == 3) {
                    BigDecimal rest = noz.Bal.subtract(noz.CurrentAmount);
                    rest = rest.scale() > 3 ? rest.setScale(3, RoundingMode.HALF_EVEN) : rest;
                    long num61 = rest.multiply(BigDecimal.TEN.pow(noz.AmountDigits)).setScale(0, RoundingMode.HALF_EVEN).longValue();
                    if (num61 < 0) {
                        num61 = 0L;
                    }
                    reply.text.append("10").append(setLen(noz.AmountDigits, 2)).append(BaseInfo.SetLength(String.format("%02X", num61), 10));
                    rfid.Bal = noz.Bal.subtract(noz.CurrentAmount);
                } else {
                    reply.text.append("00");
                    rfid.Bal = BigDecimal.ZERO;
                }
                rfid.Tag = noz.CardNo;
                rfid.UserName = noz.UserName;
                rfid.CarNo = noz.CarNo;
                rfid.UpdateTime = nowText(boxClient);
                k += shortLen;
            }
            SendTCPMessage(2306, boxClient.ID, RFIDNo);
        }
    }

    /** Bloc 7 : automate (PLC) ; renvoie la longueur du bloc. */
    private static int plcBlock(byte[] bits, int off, IBoxClientParam boxClient, Reply reply) {
        String path = boxClient.NoHead.toUpperCase();
        log("PLC", path);
        int blockLen = u(bits[40 + off]);
        int st = u(bits[41 + off]);
        boolean changed = false;
        log("PLCStatue:" + st, path);
        PLCParam plc = boxClient.pLCParam;
        plc.Statue = (st & 0x80) >> 7;
        BigDecimal[] v = new BigDecimal[13];
        for (int k = 0; k < 13; k++) {
            v[k] = float1(bits, 42 + off + k * 4);
        }
        BigDecimal[] old = {plc.A_PistonCurrent, plc.B_PistonCurrent, plc.InTake_Pressure, plc.Low_Pressure, plc.Instrument_Air_Pressure, plc.Medium_Pressure,
            plc.Gas_Concentration_Inside_the_Sled_First, plc.Hydraulic_Oil_Temperature, plc.High_Pressure, plc.Protection_Pressure, plc.Water_Flow_Temperature,
            plc.Gas_Concentration_Second, plc.Gas_Concentration_Third};
        for (int k = 0; k < 13; k++) {
            if (old[k].compareTo(v[k]) != 0) {
                changed = true;
            }
        }
        plc.A_PistonCurrent = v[0];
        plc.B_PistonCurrent = v[1];
        plc.InTake_Pressure = v[2];
        plc.Low_Pressure = v[3];
        plc.Instrument_Air_Pressure = v[4];
        plc.Medium_Pressure = v[5];
        plc.Gas_Concentration_Inside_the_Sled_First = v[6];
        plc.Hydraulic_Oil_Temperature = v[7];
        plc.High_Pressure = v[8];
        plc.Protection_Pressure = v[9];
        plc.Water_Flow_Temperature = v[10];
        plc.Gas_Concentration_Second = v[11];
        plc.Gas_Concentration_Third = v[12];
        int base = 42 + off;
        String t5 = BaseInfo.SetLength(Integer.toBinaryString((u(bits[base + 53]) << 8) | u(bits[base + 52])), 16);
        String t6 = BaseInfo.SetLength(Integer.toBinaryString((u(bits[base + 55]) << 8) | u(bits[base + 54])), 16);
        String t7 = BaseInfo.SetLength(Integer.toBinaryString((u(bits[base + 57]) << 8) | u(bits[base + 56])), 16);
        changed = alarm(boxClient, "MW28", t5, plc.MW28Data) || changed;
        changed = alarm(boxClient, "MB5", t6, plc.MB5Data) || changed;
        changed = alarm(boxClient, "MB10", t7, plc.MB10Data) || changed;
        if (changed) {
            List<Integer> errors = new ArrayList<>();
            read(t5, plc.MW28Data, 100, errors);
            read(t7, plc.MB10Data, 300, errors);
            read(t6, plc.MB5Data, 200, errors);
            plc.Error = errors.isEmpty() ? new int[100] : errors.stream().mapToInt(Integer::intValue).toArray();
            Row data = db().first("PLCParamData", "{StationID} = :s AND {CompanyID} = :c", p("s", boxClient.StationID, "c", boxClient.CompanyID));
            Row row = data != null ? data : new Row();
            String[] names = {"A_PistonCurrent", "B_PistonCurrent", "InTake_Pressure", "Low_Pressure", "Instrument_Air_Pressure", "Medium_Pressure",
                "Gas_Concentration_Inside_the_Sled_First", "Hydraulic_Oil_Temperature", "High_Pressure", "Protection_Pressure", "Water_Flow_Temperature",
                "Gas_Concentration_Second", "Gas_Concentration_Third"};
            for (int k = 0; k < names.length; k++) {
                row.put(names[k], v[k]);
            }
            row.put("MW28Data", BaseInfo.SetLength(t5, 16));
            row.put("MB5Data", BaseInfo.SetLength(t6, 16));
            row.put("MB10Data", BaseInfo.SetLength(t7, 16));
            row.put("UpdateTime", BaseFunction.ConvertDateTimeToInt(LocalDateTime.now(java.time.ZoneOffset.UTC)));
            row.put("StationID", IniInfo.StationID);
            row.put("CompanyID", IniInfo.CompanyID);
            if (data != null) {
                db().update("PLCParamData", row);
            } else {
                db().insert("PLCParamData", row);
            }
            SendTCPMessage(2312, boxClient.ID, 0);
        }
        reply.text.append("01");
        return blockLen;
    }

    private static boolean alarm(IBoxClientParam boxClient, String region, String text, int[] current) {
        boolean changed = false;
        for (int k = 0; k < text.length(); k++) {
            int bit = text.charAt(k) - '0';
            if (bit != current[k]) {
                NormalFunction.DealPLCAlarm(boxClient.StationID, boxClient.CompanyID, region, k, bit, new String[1]);
                current[k] = bit;
                changed = true;
            }
        }
        return changed;
    }

    private static void read(String text, int[] target, int base, List<Integer> errors) {
        for (int k = 0; k < text.length(); k++) {
            target[k] = text.charAt(k) - '0';
            if (target[k] != 0) {
                errors.add(base + k);
            }
        }
    }

    private static Row boxOf(BoxTcpServer.Session client) {
        return db().first("BoxList", "{BoxDeviceNo} = :n", p("n", client.Id()));
    }

    /** Trame 11 : versions des appareils d'un port (lecteurs RFID ou pistolets). */
    private static void deviceVersions(byte[] bits, int num, BoxTcpServer.Session client, IBoxClientParam boxClient) {
        int port = u(bits[38]);
        Row box = boxOf(client);
        if (box == null) {
            return;
        }
        Row bp = db().first("Box_Set", "{Port_ID} = :b AND {Box_PortID} = :p", p("b", box.get("ID"), "p", port));
        log("Read Version" + SerialClass.byteToHexStr(bits), boxClient.NoHead.toUpperCase());
        if (bp == null) {
            throw new NullPointerException("Object reference not set to an instance of an object.");
        }
        int comm = bp.i("Box_CommunicationTypeID");
        if (comm == 6 || comm == 1) {
            int k = 0;
            while (39 + k < num) {
                int deviceId = u(bits[39 + k]);
                k++;
                byte[] array = new byte[10];
                System.arraycopy(bits, 39 + k, array, 0, 10);
                k += 10;
                String version = new String(array, DEFAULT).replace("\0", "");
                if (comm == 6) {
                    Row c = db().first("RFIDConfig", "{Box_Port} = :b AND {RFIDNo} = :d", p("b", bp.get("ID"), "d", deviceId));
                    if (c != null) {
                        c.put("Version", version);
                        db().update("RFIDConfig", c);
                    }
                    RFIDRunData r = boxClient.RFIDList.stream().filter(t -> t.RFIDNo == deviceId).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Sequence contains no matching element"));
                    r.Version = version;
                } else {
                    version = version.stripLeading();
                    log("Version" + version, boxClient.NoHead.toUpperCase());
                    Row n = db().first("NormalNoz", "{PortID} = :b AND {NozID} = :d", p("b", box.get("ID"), "d", deviceId));
                    if (n != null) {
                        n.put("Version", version);
                        db().update("NormalNoz", n);
                    }
                    AllNozStatue a = boxClient.NozList.stream().filter(t -> t.NozID == deviceId).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Sequence contains no matching element"));
                    a.Version = version;
                }
            }
        }
        SendTCPMessage(2309, boxClient.ID, port);
    }

    private static String fileName20(String fileName) {
        String t = "20202020202020202020" + BaseFunction.StringConvertToAscii(fileName);
        return t.substring(t.length() - 20);
    }

    /** Trame 12 : page suivante du programme d'un appareil du boîtier. */
    private static void program(byte[] bits, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        int port = u(bits[38]);
        int index = (u(bits[39]) << 8) | u(bits[40]);
        Row box = boxOf(client);
        if (box == null) {
            throw new IllegalStateException("Box Lost");
        }
        Row bp = db().first("Box_Set", "{Port_ID} = :b AND {Box_PortID} = :p", p("b", box.get("ID"), "p", port));
        if (bp == null) {
            throw new NullPointerException("Object reference not set to an instance of an object.");
        }
        int comm = bp.i("Box_CommunicationTypeID");
        if (comm == 6) {
            NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(1, index);
            if (!pg.ok()) {
                if (!pg.Error().equals("Finished")) {
                    throw new IllegalStateException("Dowload Program Error：" + pg.Error());
                }
                reply.valueReturn = "Ready";
                boxClient.CurrentProgram = -1;
            }
            reply.text.append(setLen(port, 2)).append(fileName20(pg.FileName())).append(setLen(String.format("%02x", pg.TotalPage()), 4))
                .append(setLen(String.format("%02x", index), 4)).append(pg.ProgramData());
        } else if (comm == 1 || comm == 2 || comm == 3 || comm == 4 || comm == 5 || comm == 7) {
            AllNozStatue n = boxClient.NozList.stream().filter(t -> t.Box_Port == bp.i("ID")).min((a, b) -> Integer.compare(a.NozID, b.NozID)).orElse(null);
            if (n != null) {
                int type = 0;
                if (n.Version != null && n.Version.length() > 2) {
                    String v = n.Version.substring(0, 2);
                    type = v.equals("06") ? 2 : v.equals("11") ? 3 : 0;
                }
                if (type != 0) {
                    NormalFunction.ProgramPage pg = NormalFunction.GetProgramDataVersion(type, index);
                    if (pg.ok()) {
                        reply.text.append(setLen(port, 2)).append(fileName20(pg.FileName())).append(setLen(String.format("%02x", pg.TotalPage()), 4))
                            .append(setLen(String.format("%02x", index), 4)).append(pg.ProgramData());
                    }
                }
            }
        }
    }

    private static String trueTime(byte[] bits, int at) {
        return "20" + X2(bits[at]) + "-" + X2(bits[at + 1]) + "-" + X2(bits[at + 2]) + " " + X2(bits[at + 3]) + ":" + X2(bits[at + 4]) + ":" + X2(bits[at + 5]);
    }

    /** Trame 0F : transactions hors ligne ; la réponse reprend toujours le numéro du lot. */
    private static void offline(byte[] bits, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        int batch = u(bits[38]);
        int count = u(bits[39]);
        int size = u(bits[40]);
        for (int j = 0; j < count; j++) {
            int base = j * size;
            int nozID = u(bits[41 + base]);
            BaseInfo.Found f = BaseInfo.GetClientNoz(nozID, client.Id());
            if (f == null) {
                log("Get Noz ID Falied:" + nozID, "Main");
                throw new IllegalStateException("Get Noz Error");
            }
            AllNozStatue noz = f.noz();
            BigDecimal vol = parsed(hex(bits, 42 + base, 4), noz.VolumeDigits);
            BigDecimal amt = parsed(hex(bits, 46 + base, 4), noz.AmountDigits);
            BigDecimal price = parsed(hex(bits, 50 + base, 3), noz.PriceDigits);
            BigDecimal volTotal = parsed(hex(bits, 53 + base, 6), noz.VolumeDigits);
            BigDecimal amtTotal = parsed(hex(bits, 59 + base, 6), noz.AmountDigits);
            String time = trueTime(bits, 65 + base);
            String order = size > 30 ? hex(bits, 71 + base, 4) : "00000000";
            String card = size > 34 ? hex(bits, 75 + base, 6) : "000000000000";
            if (vol != null && amt != null && price != null && volTotal != null && amtTotal != null) {
                BaseInfo.DealBoxTransaction_BoxOffline(client.Id(), nozID, vol, amt, price, amtTotal, volTotal, boxClient.TimeZone, time, order, card,
                    boxClient.NoHead.toUpperCase(), new String[1]);
            }
        }
        reply.text.append(BaseInfo.SetLength(String.format("%02X", batch), 2));
    }

    /** Trame 1F : lot de transactions hors ligne ; la réponse reprend toujours le numéro du lot. */
    private static void offlineList(byte[] bits, BoxTcpServer.Session client, IBoxClientParam boxClient, Reply reply) {
        int batch = (u(bits[38]) << 8) | u(bits[39]);
        int count = u(bits[40]);
        int size = u(bits[41]);
        List<UnTransaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int base = i * size;
            int nozID = u(bits[42 + base]);
            BaseInfo.Found f = BaseInfo.GetClientNoz(nozID, client.Id());
            if (f == null) {
                log("Get Noz ID Falied:" + nozID, "Main");
                continue;
            }
            AllNozStatue noz = f.noz();
            BigDecimal vol = parsed(hex(bits, 43 + base, 4), noz.VolumeDigits);
            BigDecimal amt = parsed(hex(bits, 47 + base, 4), noz.AmountDigits);
            BigDecimal price = parsed(hex(bits, 51 + base, 3), noz.PriceDigits);
            BigDecimal volTotal = parsed(hex(bits, 54 + base, 6), noz.VolumeDigits);
            BigDecimal amtTotal = parsed(hex(bits, 60 + base, 6), noz.AmountDigits);
            String time = trueTime(bits, 66 + base);
            String order = size > 30 ? hex(bits, 72 + base, 4) : "00000000";
            String card = size > 34 ? hex(bits, 76 + base, 6) : "000000000000";
            if (vol != null && amt != null && price != null && volTotal != null && amtTotal != null) {
                UnTransaction u = new UnTransaction();
                u.NozID = nozID;
                u.Volume = vol;
                u.Price = price;
                u.Amount = amt;
                u.AmountTotal = amtTotal;
                u.VolumeTotal = volTotal;
                u.TimeStr = time;
                u.CardNo = card;
                u.OrderNumber = order;
                list.add(u);
            }
        }
        BaseInfo.DealBoxTransaction_BoxOfflineList(client.Id(), list, boxClient.TimeZone, boxClient.NoHead.toUpperCase(), new String[1]);
        reply.text.append(BaseInfo.SetLength(String.format("%02X", batch), 4));
    }
}
