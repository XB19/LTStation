package com.ltstation.center.icair;

import static com.ltstation.center.icair.AirProto.bytes;
import static com.ltstation.center.icair.AirProto.crc;
import static com.ltstation.center.icair.AirProto.dpow10;
import static com.ltstation.center.icair.AirProto.hex;
import static com.ltstation.center.icair.AirProto.int16;
import static com.ltstation.center.icair.AirProto.pad;
import static com.ltstation.center.icair.AirProto.pow10;
import static com.ltstation.center.icair.AirProto.trunc;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.serverdeal.CenterDeal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /ICAir/BLSDeviceCommunication : protocole des boîtiers connectés (trames F9), ICAirController d'origine.
 * Requête : numéro d'appareil (10 octets) puis FC (battement) ou F9 nn E fff llll cc ... crc.
 *
 * Écarts avec l'original :
 *  - les remboursements WeChat (scan-pay) ne sont pas émis : la commande est marquée et attend le traitement,
 *    car le paiement WeChat n'est pas repris dans ce projet ;
 *  - URLPoint vide ou absent est traité de la même façon partout ; l'original soustrayait deux fois le stock
 *    d'une cuve si URLPoint était absent ;
 *  - le programme de l'appareil vient du dossier app.air.program-dir : 123.bin et atomizer.bin
 *    ne sont pas dans les sources reçues, D5 répond vide tant qu'ils n'y sont pas.
 */
@RestController
public class AirBlsController {

    private static final String ZEROS = "000000000000000000000000000000000000000000000000000000";

    private static final AtomicInteger FRAME = new AtomicInteger();

    private final Sugar db;
    private final CenterDeal deal;
    private final AirDeal air;
    private final AirNotify notify;
    private final Path programDir;
    private final LegacyLog mainLog = new LegacyLog("NewCommunication");
    private final LegacyLog scanLog = new LegacyLog("scanPayLog");

    public AirBlsController(CenterDeal deal, AirDeal air, AirNotify notify, @Value("${app.air.program-dir:./Dowload}") String programDir) {
        this.db = deal.db();
        this.deal = deal;
        this.air = air;
        this.notify = notify;
        this.programDir = Path.of(programDir);
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    private static boolean filled(String v) {
        return v != null && !v.isEmpty();
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    @RequestMapping("/ICAir/BLSDeviceCommunication")
    public void communication(@RequestBody(required = false) byte[] body, HttpServletResponse res) throws IOException {
        byte[] answer = new byte[0];
        LegacyLog log = mainLog;
        try {
            String rest = AirProto.str(body == null ? new byte[0] : body);
            mainLog.writeLog("物联网电控 收到 Send:" + rest);
            String out = "";
            String no = "";
            String loaded = "";
            List<Row> list = null;
            int turn = 0;
            while (!rest.isEmpty()) {
                boolean gps = rest.startsWith("2447");
                if (!gps) {
                    no = rest.substring(0, 20);
                }
                if (!loaded.equals(no)) {
                    loaded = no;
                    list = db.list("AirDevice", "{DeviceNo} = :n AND {IsRun} = true ORDER BY {NozID}", p("n", no));
                    air.dealUnknow(list, no);
                }
                log = new LegacyLog("NewCommunication/" + no);
                if (!gps) {
                    rest = rest.substring(20);
                }
                turn++;
                log.writeLog("物联网电控 心跳包前 Receive" + turn + ":" + rest);
                if (rest.startsWith("FC")) {
                    String[] cmd = {""};
                    updateDevice(cmd, list);
                    out = cmd[0];
                    rest = rest.substring(4);
                    continue;
                }
                if (rest.startsWith("2447")) {
                    String text = AirProto.ascii(rest);
                    if (text.contains("$GPRMC")) {
                        String[] f = text.split(",", -1);
                        if ("A".equals(f[2])) {
                            air.location(f[5], f[3], no);
                        }
                        rest = rest.substring(rest.indexOf("0D0A") + 4);
                    } else {
                        rest = rest.substring(50);
                    }
                    continue;
                }
                int len = int16(rest.substring(8, 12));
                int noz = Integer.parseInt(rest.substring(2, 4));
                int frame = int16(rest.substring(5, 8));
                String whole = rest.substring(0, len * 2 + 16);
                if (!crc(whole.substring(2, whole.length() - 4)).equals(whole.substring(whole.length() - 4))) {
                    log.writeLog("物联网电控 新命令处理 错误 Error:CRC Error");
                    throw new IllegalStateException("CRC Error");
                }
                out = answer(bytes(whole), noz, frame, list, log);
                rest = rest.substring(len * 2 + 16);
                if (turn > 2) {
                    break;
                }
            }
            String send = out.isEmpty() ? "FC" : out;
            answer = bytes(send.toUpperCase());
            log.writeLog("物联网电控 发送 Send:" + send.toUpperCase());
        } catch (RuntimeException ex) {
            mainLog.writeLog("物联网电控 错误 Error:" + ex.getMessage());
        }
        res.getOutputStream().write(answer);
    }

    private static int nozId(List<Row> list, Row cmd) {
        int n = 1;
        List<Row> sorted = new ArrayList<>(list);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).i("NozID") == cmd.i("NozID")) {
                n = i + 1;
            }
        }
        return n;
    }

    /** BLSCMDDealReturn. */
    private String answer(byte[] cmd, int noz, int frame, List<Row> list, LegacyLog log) {
        try {
            Row device;
            if (noz > list.size()) {
                device = list.get(0);
            } else if (noz == 0) {
                for (Row r : list) {
                    r.set("IsOnline", true).set("ConnecTime", now());
                    db.update("AirDevice", r);
                }
                device = list.get(0);
            } else {
                device = list.get(noz - 1);
            }
            String hexCmd = AirProto.str(cmd);
            int code = cmd[6] & 0xFF;
            String out = "";
            switch (code) {
                case 161 -> out = poll(device);
                case 209 -> out = downloadMachine(list);
                case 210 -> out = downloadBlack(int16(hexCmd.substring(14, 18)));
                case 211 -> out = downloadPrice(list);
                case 212 -> out = downloadPluse(list);
                case 213 -> out = downloadProgram(int16(hexCmd.substring(14, 18)), device);
                case 214 -> out = downloadAppendBlack();
                case 215 -> out = downloadWhite(Integer.parseInt(cmd[7] + "" + cmd[8], 16), list);
                case 193 -> out = realData(list, noz, hexCmd);
                case 194 -> out = transaction(hexCmd, list, noz, log);
                case 195 -> out = appCode(hexCmd, noz, list, log);
                case 196 -> out = rfid(hexCmd, noz, list);
                case 197 -> {
                    if (expired(device)) {
                        log.writeLog("Appareil expiré, date : " + device.str("ValidTime"));
                        out = "C507" + ZEROS;
                    } else {
                        String asn = hexCmd.substring(14, 34);
                        int type;
                        try {
                            type = air.accountType(asn);
                        } catch (RuntimeException ex) {
                            type = 0;
                        }
                        out = cardQuery(hexCmd, noz, list, type == 2);
                    }
                }
                case 225, 226, 227, 228, 229, 230, 231 -> {
                    return "";
                }
                case 234 -> {
                    scanResult(hexCmd);
                    return "";
                }
                case 232 -> device.set("Pluse", Integer.parseInt(hexCmd.substring(14, 22)));
                case 177 -> out = updateLevel(list, noz, hexCmd);
                default -> {
                }
            }
            if (code == 193) {
                String[] extra = {""};
                if (updateDevice(extra, list) && !extra[0].isEmpty()) {
                    return extra[0];
                }
            }
            if (noz != 0) {
                db.update("AirDevice", device.set("IsOnline", true).set("ConnecTime", now()));
            }
            String body = pad(String.valueOf(noz), 2) + "E" + hex(frame, 3) + hex(out.length() / 2, 4) + out;
            return "F9" + body + crc(body);
        } catch (RuntimeException ex) {
            log.writeLog("物联网电控 新命令处理 错误：" + ex.getMessage());
            return "";
        }
    }

    /** BaseBusinessMethod.judgeEquipMaturity : date de validité de l'appareil dépassée. */
    private static boolean expired(Row device) {
        String v = device.str("ValidTime");
        try {
            return filled(v) && !Conv.toDateTime(v).isAfter(LocalDateTime.now());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Row versions() {
        Row v = db.first("DataVersions");
        if (v == null) {
            throw new IllegalStateException("Version Error");
        }
        return v;
    }

    private String poll(Row a) {
        try {
            Row v = db.first("DataVersions");
            if (v == null) {
                db.insert("DataVersions", new Row());
                v = db.first("DataVersions");
            }
            String out = "A1" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + hex(v.i("MachineCfg") % 256, 2) + hex(v.i("BasicBlack") % 256, 2) + hex(v.i("White") % 256, 2) + hex(v.i("PPU") % 256, 2)
                + hex(v.i("TransportInfoes") % 256, 2) + hex(v.i("DistributionPoints") % 256, 2);
            long now = CenterDeal.now();
            for (Row r : db.list("NozRealData", "{DeviceNo} = :d AND {StationID} = :s AND {Statue} <> 1", p("d", a.s("DeviceNo"), "s", a.i("StationID")))) {
                if (r.i("Statue") == 2 && now - r.l("UpdateTime") > 10000) {
                    db.update("NozRealData", r.set("Statue", 1));
                }
            }
            if (filled(a.str("URLPoint"))) {
                notify.partner(a.s("URLPoint"), "/oil/nozStatue", Row.of("Statue", 0, "Amount", "0", "Volume", "0", "device_sn", a.s("DeviceNo"),
                    "NozID", a.i("NozID"), "lng", a.str("lng"), "lat", a.str("lat")));
            }
            return out;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadMachine(List<Row> list) {
        try {
            if (list.isEmpty()) {
                return "";
            }
            Row v = versions();
            Row st = db.first("Stations", "{StationID} = :s", p("s", list.get(0).i("StationID")));
            return "D1" + hex(v.i("MachineCfg") % 256, 2) + "000000" + pad(st.s("Code"), 8);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadAppendBlack() {
        try {
            List<Row> rows = db.list("AppendBlackLists", "{Deal} = false ORDER BY {Time}", null);
            if (rows.isEmpty()) {
                return "";
            }
            Row a = rows.get(0);
            db.update("AppendBlackLists", a.set("Deal", true));
            return "D6" + a.s("ASN");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static long check(byte[] data) {
        long sum = 0;
        for (byte b : data) {
            sum += b & 0xFF;
        }
        return sum;
    }

    private static String pagedAsn(List<Row> rows, int index) {
        int pages = rows.size() / 200 + 1;
        long sum = 0;
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i >= (index - 1) * 200 && i < index * 200) {
                t.append(rows.get(i).s("ASN"));
            }
            if (pages == index) {
                sum += check(bytes(rows.get(i).s("ASN")));
            }
        }
        if (pages == index) {
            t.append(hex(sum, 20));
        }
        return hex(pages, 4) + hex(index, 4) + t;
    }

    private String downloadBlack(int index) {
        try {
            return "D2" + hex(versions().i("BasicBlack") % 256, 2) + pagedAsn(db.list("BasicBlackLists", "ORDER BY {Time}", null), index);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadWhite(int index, List<Row> list) {
        try {
            Row v = versions();
            return "D7" + hex(v.i("White") % 256, 2)
                + pagedAsn(db.list("WhiteCardLists", "{StationID} = :s ORDER BY {ValidateTime}", p("s", list.get(0).i("StationID"))), index);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadPrice(List<Row> list) {
        try {
            Row v = versions();
            if (list.isEmpty()) {
                return "";
            }
            StringBuilder t = new StringBuilder();
            int n = 1;
            for (Row item : list) {
                Row tank = air.tankOf(item);
                Row grade = tank == null ? null : db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", tank.i("GradeID")));
                Row price = grade == null ? null : db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g",
                    p("s", item.i("StationID"), "g", grade.i("GradeSpecificID")));
                if (price != null) {
                    double unit = dpow10(item.i("PriceDigits"));
                    t.append(hex(n, 2)).append(grade.s("Code")).append("00000000").append(hex((int) (price.d("CurrentPPU") * unit), 8))
                        .append(hex((int) (price.d("NewPPU") * unit), 8))
                        .append(Conv.toDateTime(price.get("ValidateTime")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
                    n++;
                }
            }
            return "D3" + hex(v.i("PPU") % 256, 2) + hex(list.size(), 2) + t;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadPluse(List<Row> list) {
        try {
            Row v = versions();
            return list.isEmpty() ? "" : "D4" + hex(v.i("TransportInfoes") % 256, 2) + hex(2500, 12);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String downloadProgram(int index, Row a) {
        try {
            Row v = versions();
            byte[] all = Files.readAllBytes(programDir.resolve("3".equals(a.str("DigitsType")) ? "atomizer.bin" : "123.bin"));
            int pages = all.length / 2048 + 1;
            int from = (index - 1) * 2048;
            String data = AirProto.str(Arrays.copyOfRange(all, from, index == pages ? all.length : from + 2048));
            if (index == pages) {
                data += hex(check(all), 20);
            }
            return "D5" + hex(v.i("DistributionPoints") % 256, 2) + hex(pages, 4) + hex(index, 4) + data;
        } catch (IOException | RuntimeException ex) {
            mainLog.writeLog("程序下载 错误：" + ex.getMessage());
            return "";
        }
    }

    private static Row pick(List<Row> list, int index) {
        return index > list.size() ? list.get(0) : list.get(index - 1);
    }

    private static BigDecimal value(String hexValue, int digits) {
        return BigDecimal.valueOf(int16(hexValue) / dpow10(digits));
    }

    private String realData(List<Row> list, int index, String in) {
        try {
            if (list.isEmpty()) {
                throw new IllegalStateException("AirDevice Error");
            }
            Row a = pick(list, index);
            String state = in.substring(14, 16);
            Row real = db.first("NozRealData", "{DeviceNo} = :d AND {StationID} = :s AND {NozID} = :n",
                p("d", a.s("DeviceNo"), "s", a.i("StationID"), "n", a.i("NozID")));
            if ("01".equals(state)) {
                if (real == null) {
                    db.insert("NozRealData", Row.of("DeviceNo", a.s("DeviceNo"), "StationID", a.i("StationID"), "NozID", a.i("NozID"),
                        "Statue", 1, "UpdateTime", CenterDeal.now()));
                } else if (real.i("Statue") != 1) {
                    db.update("NozRealData", real.set("Statue", 1).set("UpdateTime", CenterDeal.now()));
                }
                return "C100";
            }
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal volume = BigDecimal.ZERO;
            if ("02".equals(state)) {
                amount = value(in.substring(16, 26), a.i("AmountDigits"));
                volume = value(in.substring(26, 36), a.i("VolumeDigits"));
                if (real == null) {
                    db.insert("NozRealData", Row.of("DeviceNo", a.s("DeviceNo"), "StationID", a.i("StationID"), "NozID", a.i("NozID"), "Statue", 2,
                        "UpdateTime", CenterDeal.now(), "Amount", amount, "Volume", volume));
                } else {
                    db.update("NozRealData", real.set("Statue", 2).set("Amount", amount).set("Volume", volume).set("UpdateTime", CenterDeal.now()));
                }
            }
            if (filled(a.str("URLPoint"))) {
                notify.partner(a.s("URLPoint"), "/oil/nozStatue", Row.of("Statue", Integer.parseInt(state), "Amount", amount.toPlainString(),
                    "Volume", volume.toPlainString(), "device_sn", a.s("DeviceNo"), "NozID", a.i("NozID"), "lng", a.str("lng"), "lat", a.str("lat")));
            }
            return "C100";
        } catch (RuntimeException ex) {
            return "C101";
        }
    }

    /** BLSCom_DealTransaction : opération de la pompe, débit de la carte et notifications. */
    private String transaction(String in, List<Row> list, int index, LegacyLog log) {
        try {
            if (list.isEmpty()) {
                throw new IllegalStateException("AirDevice Error");
            }
            Row a = pick(list, index);
            int am = a.i("AmountDigits");
            int vo = a.i("VolumeDigits");
            int pr = a.i("PriceDigits");
            String asn = in.substring(30, 50);
            double amn = int16(in.substring(50, 60)) / dpow10(am);
            double vol = int16(in.substring(60, 70)) / dpow10(vo);
            double bal = int16(in.substring(70, 80)) / dpow10(am);
            double total = int16(in.substring(80, 96)) / dpow10(vo);
            double ppu = int16(in.substring(96, 104)) / dpow10(pr);
            // division entière, comme l'original
            int discount = int16(in.substring(104, 108)) / 100;
            double pay = int16(in.substring(112, 122)) / dpow10(am);
            String t = in.substring(122, 136);
            String coupon = AirProto.ascii(in.substring(136, 156)).replace("\0", "");
            String posttc = in.substring(18, 26);
            Row oil = Row.of("TType", int16(in.substring(14, 16)), "PaymentID", Integer.parseInt(in.substring(16, 18)), "POSTTC", posttc,
                "GunId", a.i("NozID"), "Asn", asn, "AMN", Conv.round(amn, am), "Volume", Conv.round(vol, vo), "BAL", Conv.round(bal, am),
                "VolumeTotal", Conv.round(total, vo), "PPU", Conv.round(ppu, pr), "Discount", (double) discount, "EmployeeID", 1,
                "TruelyPay", Conv.round(pay, am),
                "TrueTime", t.substring(0, 4) + "-" + t.substring(4, 6) + "-" + t.substring(6, 8) + " " + t.substring(8, 10) + ":" + t.substring(10, 12) + ":" + t.substring(12, 14),
                "Coupon", coupon, "ErrorFlgID", Integer.parseInt(in.substring(156, 160)), "GradeSpecificID", deal.getGradeID(in.substring(26, 30)),
                "StationID", a.i("StationID"), "CTC", "C", "TAC", "T", "GMAC", "G", "GTAC", "GC", "PSAMTID", "PS", "PSAMTTC", "PTTC",
                "Upload", "1", "Flg", "0", "DeviceNo", a.s("DeviceNo"), "CompanyID", a.i("CompanyID"));
            CenterDeal.CusCard card = deal.getCusCardInfo(asn);
            if (card.ok()) {
                oil.set("CarNumber", card.customers().str("VehicleNo"));
            }
            if (db.first("OilTransactions", "{StationID} = :s AND {GunId} = :g AND {TrueTime} = :t",
                p("s", oil.i("StationID"), "g", oil.i("GunId"), "t", Conv.toDateTime(oil.s("TrueTime")))) != null) {
                log.writeLog("Transaction Exist");
                return "C2" + posttc;
            }
            long id;
            try (Sugar.Tx tx = db.begin()) {
                id = Conv.toLong(db.insert("OilTransactions", oil));
                if (asn.startsWith("FFFFFF")) {
                    try {
                        Row driver = db.first("JoyFill_fleetDriver", "{asn} = :a", p("a", asn));
                        if (driver != null) {
                            air.fleetRecord(oil, driver);
                        }
                    } catch (RuntimeException ex) {
                        log.writeLog("同步到车队卡消费记录，失败！" + ex.getMessage());
                    }
                }
                if (oil.i("TType") == 10) {
                    scanRefuelOver(in, oil);
                }
                String error = air.dealCustomer(oil);
                if (!error.isEmpty()) {
                    db.exec(db.sql("OilTransactions", "UPDATE [OilTransactions] SET {Flg} = '0' WHERE {ID} = :i"), p("i", id));
                    log.writeLog("Transaction Reback");
                } else {
                    air.dealTankVolume(a, oil);
                }
                tx.commit();
            }
            partnerAfter(a, oil, coupon, log);
            if (notify.monitored(a.i("StationID"))) {
                notify.order(oil, id, BigDecimal.valueOf(ppu), BigDecimal.valueOf(amn), BigDecimal.valueOf(vol));
            }
            return "C2" + posttc;
        } catch (Exception ex) {
            log.writeLog("交易记录 错误:" + ex.getMessage());
            return "C2000000";
        }
    }

    private void partnerAfter(Row a, Row oil, String coupon, LegacyLog log) {
        if (!filled(a.str("URLPoint"))) {
            return;
        }
        Row tank = db.first("AirTank", "{ID} = :i AND {IsRun} = true", p("i", Conv.toInt(a.str("TankNo"))));
        String sn = a.s("DeviceNo");
        String volume = Conv.num(oil.d("Volume"));
        if (a.i("DeviceTypeID") == 1) {
            if (tank != null) {
                db.update("AirTank", tank.set("CurrentOil", tank.dec("CurrentOil").subtract(BigDecimal.valueOf(oil.d("Volume")))));
            }
            Row code = db.first("AppCodeUse", "{AppType} = 2 AND {DeviceNo} = :d AND {AppCodeShort} = :c", p("d", sn, "c", oil.s("Coupon")));
            String refuel = code == null ? volume : code.dec("Volume").toPlainString();
            Row notice = Row.of("code", coupon, "device_sn", sn, "oil_output", volume, "refuel_num", refuel);
            notify.partner(a.s("URLPoint"), "/oil/oilingNotice", notice);
            notify.partner(a.s("URLPoint"), "/oil/closeDoorNotice", notice);
        } else if (a.i("DeviceTypeID") == -1 && tank != null) {
            db.update("AirTank", tank.set("CurrentOil", tank.dec("CurrentOil").add(BigDecimal.valueOf(oil.d("Volume")))));
            notify.partner(a.s("URLPoint"), "/oil/supplementOilNotice", Row.of("capacity", volume, "meter_capacity", volume, "device_sn", sn));
        }
        log.writeLog("Partenaire notifié");
    }

    private void refundLater(Row order) {
        scanLog.writeLog("Remboursement WeChat à traiter, commande " + order.i("id"));
    }

    private void scanRefuelOver(String in, Row oil) {
        try {
            int id = int16(in.substring(136, 156));
            if (id <= 0) {
                return;
            }
            Row o = db.first("JoyFill_scanPayOrder", "{id} = :i", p("i", id));
            BigDecimal real = BigDecimal.valueOf(oil.d("AMN"));
            BigDecimal amount = o.dec("amount");
            o.set("realRefuelAmount", real).set("realRefuelLiters", BigDecimal.valueOf(oil.d("Volume"))).set("paymentState", 2);
            if (real.compareTo(amount) >= 0) {
                db.update("JoyFill_scanPayOrder", o.set("needRefund", 0));
            } else {
                db.update("JoyFill_scanPayOrder", o.set("needRefund", 1).set("refundBalance", amount.subtract(real)).set("refundTime", now()));
                refundLater(o);
            }
        } catch (RuntimeException ex) {
            scanLog.writeLog("Fin du plein scan-pay : " + ex.getMessage());
        }
    }

    /** BLSCom_EA : résultat du préréglage d'une commande scan-pay. */
    private void scanResult(String in) {
        try {
            int id = int16(in.substring(16, 36));
            if (id <= 0) {
                return;
            }
            Row o = db.first("JoyFill_scanPayOrder", "{id} = :i", p("i", id));
            if (o == null) {
                scanLog.writeLog("Commande introuvable : " + id);
                return;
            }
            String r = in.substring(14, 16);
            switch (r) {
                case "01", "02", "04" -> {
                    if (o.i("finishState") != 3) {
                        o.set("realRefuelAmount", BigDecimal.ZERO);
                        if ("02".equals(r)) {
                            o.set("needRefund", 2);
                        } else if ("04".equals(r)) {
                            o.set("needRefund", 3);
                        }
                        db.update("JoyFill_scanPayOrder", o.set("paymentState", 2).set("finishState", 3).set("refundBalance", o.dec("amount")).set("refundTime", now()));
                        refundLater(o);
                    }
                }
                case "00" -> {
                    if (o.i("finishState") != 2) {
                        db.update("JoyFill_scanPayOrder", o.set("finishState", 1));
                    }
                }
                case "03" -> db.update("JoyFill_scanPayOrder", o.set("finishState", 2));
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            scanLog.writeLog("Préréglage scan-pay : " + ex.getMessage());
        }
    }

    private Row oilGrade(Row a) {
        Row tank = air.tankOf(a);
        if (tank == null) {
            throw new IllegalStateException("Cuve introuvable");
        }
        Row g = db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", tank.i("GradeID")));
        if (g == null) {
            throw new IllegalStateException("Carburant introuvable");
        }
        return g;
    }

    private String appCode(String in, int index, List<Row> list, LegacyLog log) {
        int result = 0;
        try {
            Row a = list.get(index - 1);
            int n = Integer.parseInt(in.substring(14, 16));
            String code = in.substring(in.length() - 4 - n, in.length() - 4);
            if (!filled(a.str("URLPoint"))) {
                Row use = db.first("AppCodeUse", "({IsUsing} = 0 OR {IsUsing} = 1) AND ({AppCodeShort} = :c OR {AppCode} = :c) ORDER BY {CreateTime}", p("c", code));
                if (use == null) {
                    throw new IllegalStateException("Error Code");
                }
                AirDeal.Query q = air.cardQuery(use.s("Asn"), oilGrade(a).s("Code"), a, false);
                result = q.result;
                if (result != 0) {
                    throw new IllegalStateException("Carte refusée");
                }
                BigDecimal money = pow10(a.i("AmountDigits"));
                BigDecimal unit = pow10(a.i("VolumeDigits"));
                BigDecimal maxVol = q.maxVolume.signum() < 0 ? BigDecimal.ZERO : q.maxVolume;
                String pass = filled(q.pass) ? q.pass : "9999";
                db.update("AppCodeUse", use.set("IsUsing", 1));
                return "C3" + hex(q.result, 2) + pad(use.s("Asn"), 20) + hex((int) trunc(q.maxAmount.multiply(money)), 10)
                    + hex((int) trunc(maxVol.multiply(unit)), 10) + hex((int) trunc(q.balance.multiply(money)), 10) + pad(pass, 4);
            }
            String reply = notify.post(a.s("URLPoint") + "/oil/verifyCode", Row.of("token", notify.token(), "code", code, "device_sn", a.s("DeviceNo")));
            Row r = Wire.parseRow(reply);
            if (r == null || !r.b("success")) {
                throw new IllegalStateException(r == null ? "Sans réponse" : r.s("message"));
            }
            Row v = r.row("data");
            BigDecimal litres = new BigDecimal(v.s("refuel_num"));
            db.insert("AppCodeUse", Row.of("DeviceNo", v.s("device_sn"), "Volume", litres, "AppCodeShort", v.s("oil_code"), "AppType", 2, "IsUsing", 1));
            BigDecimal price = BigDecimal.valueOf(9999);
            Row info = db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g", p("s", a.i("StationID"), "g", a.i("OilID")));
            if (info != null) {
                price = BigDecimal.valueOf(Conv.round(litres.doubleValue() * info.d("NewPPU"), 2));
            }
            String amount = hex(price.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN).intValue(), 10);
            return "C3" + hex(0, 2) + "99999999999999999999" + amount
                + hex(litres.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN).intValue(), 10) + amount + "9999";
        } catch (RuntimeException ex) {
            log.writeLog("App ex Error:" + ex.getMessage());
            return "C309" + "00000000000000000000000000000000000000000000000000" + "9999";
        }
    }

    private String rfid(String in, int index, List<Row> list) {
        int result = 0;
        try {
            if (list.isEmpty() || index > list.size()) {
                result = 10;
                throw new IllegalStateException("Appareil");
            }
            Row a = list.get(index - 1);
            String code;
            try {
                code = oilGrade(a).s("Code");
            } catch (RuntimeException ex) {
                result = 10;
                throw ex;
            }
            AirDeal.Query q = air.cardQuery(in.substring(14, 34), code, a, false);
            result = q.result;
            if (result != 0) {
                throw new IllegalStateException("Carte refusée");
            }
            return "C4" + hex(q.result, 2) + pad(q.asn, 20) + pad(q.maxAmount.toPlainString(), 10) + pad(q.maxVolume.toPlainString(), 10)
                + pad(q.balance.toPlainString(), 10) + pad(q.pass, 4);
        } catch (RuntimeException ex) {
            return "C4" + hex(result, 2) + ZEROS;
        }
    }

    private static String x8(BigDecimal v) {
        return pad(String.format("%08x", v.setScale(0, RoundingMode.HALF_EVEN).intValue()), 10);
    }

    /** BLSCom_CardQuery_amount (C5) et BLSCom_CardQuery_liters (C6). */
    private String cardQuery(String in, int index, List<Row> list, boolean liters) {
        String tag = liters ? "C6" : "C5";
        int result = 0;
        try {
            if (list.isEmpty() || index > list.size()) {
                result = 10;
                throw new IllegalStateException("Appareil 1");
            }
            Row a = list.get(index - 1);
            Row noz = db.queryFirst("SELECT oil_id FROM noz_real_date_view WHERE device_no = :d AND noz_id = :n", p("d", a.s("DeviceNo"), "n", a.i("NozID")));
            Row grade = noz == null ? null : db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", noz.i("oil_id")));
            if (grade == null) {
                result = 10;
                throw new IllegalStateException("Appareil 2");
            }
            AirDeal.Query q = air.cardQuery(in.substring(14, 34), grade.s("Code"), a, liters);
            result = q.result;
            if (result != 0) {
                throw new IllegalStateException("Carte refusée");
            }
            BigDecimal money = pow10(a.i("AmountDigits"));
            BigDecimal unit = pow10(a.i("VolumeDigits"));
            return tag + hex(q.result, 2) + pad(q.asn, 20) + x8(q.maxAmount.multiply(money)) + x8(q.maxVolume.multiply(unit)) + x8(q.balance.multiply(money)) + pad(q.pass, 4);
        } catch (RuntimeException ex) {
            mainLog.writeLog("Contrôle de carte : " + ex.getMessage());
            return tag + hex(result, 2) + ZEROS;
        }
    }

    private static String wrap(int noz, String data) {
        String body = pad(String.valueOf(noz), 2) + "F" + hex(FRAME.get(), 3) + hex(data.length() / 2, 4) + data;
        return "F9" + body + crc(body);
    }

    /** BLSCom_UpdateDevice : prochaine commande en attente pour ce boîtier. Le résultat va dans out[0]. */
    private boolean updateDevice(String[] out, List<Row> list) {
        out[0] = "";
        try {
            if (list.isEmpty()) {
                throw new IllegalStateException("AirDevice Error");
            }
            List<Row> cmds = db.list("CmdList", "{DeviceNo} = :d AND {Deal} <> 99 AND {CmdPlatType} = 1 ORDER BY {CreateTime}", p("d", list.get(0).s("DeviceNo")));
            if (cmds.isEmpty()) {
                return true;
            }
            Row cmd = cmds.get(0);
            Row device = null;
            for (Row r : list) {
                if (r.i("NozID") == cmd.i("NozID")) {
                    device = r;
                    break;
                }
            }
            if (device == null) {
                db.update("CmdList", cmd.set("Deal", 99));
                return false;
            }
            FRAME.incrementAndGet();
            int noz = 0;
            String data = cmd.str("CmdData");
            String t = "";
            switch (cmd.s("CmdType")) {
                case "E1" -> t = "E1" + Conv.toDateTime(data).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
                case "E1-1" -> t = "E10000000000";
                case "E2" -> {
                    Row m = Wire.parseRow(data);
                    String head = m.s("Head");
                    String foot = m.s("Foot");
                    t = "E2" + hex(head.length() * 2, 2) + AirProto.str(head.getBytes(AirProto.ANSI)) + hex(foot.length() * 2, 2) + AirProto.str(foot.getBytes(AirProto.ANSI));
                }
                case "E3" -> {
                    Row m = Wire.parseRow(data);
                    long v = trunc(m.dec("UpdateVolume").multiply(pow10(device.i("VolumeDigits"))));
                    noz = m.i("NozAddress");
                    t = "E3" + pad(m.s("UpdateType"), 2) + hex(v, 16);
                }
                case "E4" -> {
                    Row m = Wire.parseRow(data);
                    int flag = m.i("PreFlag");
                    long v = trunc(m.dec("PreAmount").multiply(pow10(flag != 0 ? device.i("VolumeDigits") : device.i("AmountDigits"))));
                    noz = nozId(list, cmd);
                    t = "E4" + hex(flag, 2) + hex(v, 10);
                }
                case "EA" -> {
                    Row m = Wire.parseRow(data);
                    int flag = m.i("PreFlag");
                    long v = trunc(m.dec("PreAmount").multiply(pow10(flag != 0 ? device.i("VolumeDigits") : device.i("AmountDigits"))));
                    noz = nozId(list, cmd);
                    t = "EA" + hex(flag, 2) + hex(v, 10) + hex(m.i("scanPayOrderId"), 20);
                }
                case "E5" -> {
                    noz = cmd.i("NozID");
                    t = "E5";
                }
                case "E6" -> {
                    noz = Conv.toInt(data);
                    t = "E6";
                }
                case "E7" -> t = "E7";
                case "E8" -> {
                    noz = Wire.parseRow(data).i("NozIndex") + 1;
                    t = "E8";
                }
                case "D4" -> {
                    Row m = Wire.parseRow(data);
                    noz = m.i("NozIndex") + 1;
                    t = "D4" + hex(52, 2) + hex(Conv.toInt(m.get("Pluse")), 12);
                }
                case "E9" -> t = "E9";
                case "C7" -> {
                    noz = nozId(list, cmd);
                    t = "C7" + data;
                }
                default -> {
                }
            }
            db.update("CmdList", cmd.set("Deal", 99));
            out[0] = wrap(noz, t);
            return true;
        } catch (RuntimeException ex) {
            out[0] = "E301";
            return false;
        }
    }

    private String updateLevel(List<Row> list, int index, String text) {
        try {
            if (index > list.size() || list.isEmpty()) {
                throw new IllegalStateException("Appareil introuvable");
            }
            Row a = list.get(index - 1);
            List<Row> tanks = db.list("AirTank", "{StationID} = :s AND {IsRun} = true", p("s", a.i("StationID")));
            BigDecimal height;
            if (text.length() > 27) {
                height = AirProto.asciiFloat(text.substring(14, 30));
                air.dealTankAlarm(tanks.get(0), height, text.substring(14));
                if (text.length() >= 66) {
                    String second = text.substring(38, 54);
                    if (!"0000000000000000".equals(second)) {
                        height = AirProto.asciiFloat(second);
                        if (tanks.size() >= 2) {
                            air.dealTankAlarm(tanks.get(1), height, text.substring(38));
                        }
                    }
                }
            } else {
                height = BigDecimal.valueOf(int16(text.substring(14, 18)));
                int decimals = Integer.parseInt(text.substring(20, 22));
                if (decimals > 0) {
                    height = height.divide(pow10(decimals)).setScale(decimals, RoundingMode.HALF_EVEN);
                }
                air.dealTankAlarm(tanks.get(0), height, "000000000000000000");
            }
            if (filled(a.str("URLPoint"))) {
                notify.partner(a.s("URLPoint"), "/oil/tankStatue", Row.of("device_sn", a.s("DeviceNo"), "Current_OilHeight", height.toPlainString(),
                    "Error", "0", "Current_OilVol", "0"));
            }
            return "B100";
        } catch (RuntimeException ex) {
            return "B101";
        }
    }
}
