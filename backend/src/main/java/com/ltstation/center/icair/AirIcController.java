package com.ltstation.center.icair;

import static com.ltstation.center.icair.AirProto.bytes;
import static com.ltstation.center.icair.AirProto.crc;
import static com.ltstation.center.icair.AirProto.dpow10;
import static com.ltstation.center.icair.AirProto.frameFa;
import static com.ltstation.center.icair.AirProto.hex;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /ICAir/ICCommunication : ancien protocole des terminaux IC (trames FA en binaire), ICAirController d'origine.
 * Requête : numéro d'appareil (10 octets), bloc de jauge, trame ; plusieurs trames ou lignes $GPRMC peuvent se suivre.
 */
@RestController
public class AirIcController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String NO_ASN = "00000000000000000000";

    private final Sugar db;
    private final CenterDeal deal;
    private final AirDeal air;
    private final AirNotify notify;
    private final boolean volumeBySelf;
    private final LegacyLog log = new LegacyLog("ICAir");

    public AirIcController(CenterDeal deal, AirDeal air, AirNotify notify, @Value("${app.server-deal.volume-by-self:0}") int volumeBySelf) {
        this.db = deal.db();
        this.deal = deal;
        this.air = air;
        this.notify = notify;
        this.volumeBySelf = volumeBySelf != 0;
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    /** HttpTestXML : renvoie ce qui est reçu, pour vérifier la liaison d'un appareil. */
    @RequestMapping("/ICAir/HttpTestXML")
    public void httpTest(@RequestBody(required = false) byte[] body, HttpServletResponse res) throws IOException {
        byte[] in = body == null ? new byte[0] : body;
        new LegacyLog("XMLTest").writeLog("Reveive:" + AirProto.str(in));
        res.getOutputStream().write(in);
    }

    @RequestMapping("/ICAir/ICCommunication")
    public void communication(@RequestBody(required = false) byte[] body, HttpServletResponse res) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] in = body == null ? new byte[0] : body;
            log.writeLog("Receive:" + AirProto.str(in));
            exchange(AirProto.str(in), out);
        } catch (RuntimeException ex) {
            String poll = poll(null, log);
            log.writeLog("Send Ex:" + poll);
            out.writeBytes(bytes(poll));
        }
        res.getOutputStream().write(out.toByteArray());
    }

    private void gps(String hex, String no) {
        if (hex.startsWith("2447")) {
            String text = AirProto.ascii(hex);
            String[] f = text.split(",", -1);
            if (text.contains("$GPRMC") && "A".equals(f[2])) {
                air.location(f[5], f[3], no);
            }
        }
    }

    private void exchange(String input, ByteArrayOutputStream out) {
        String rest = input;
        String no = "";
        String send = "";
        int head = 24;
        while (!rest.isEmpty()) {
            if (rest.startsWith("2447")) {
                gps(rest, no);
                rest = AirProto.ascii(rest).contains("$GPRMC") ? rest.substring(rest.indexOf("0D0A") + 4) : rest.substring(50);
                String poll = poll(null, log);
                log.writeLog("Send GPS:" + poll);
                out.writeBytes(bytes(poll));
                continue;
            }
            no = rest.substring(0, 20);
            LegacyLog devLog = new LegacyLog("ICAir/" + no);
            devLog.writeLog("Receive:" + rest);
            String gauge = rest.substring(20, 24);
            String frame = rest.substring(24);
            if ("FFFF".equals(gauge)) {
                gauge = rest.substring(20, 48);
                frame = rest.substring(48);
                head = 48;
            }
            if ("FF02".equals(gauge)) {
                gauge = rest.substring(20, 72);
                frame = rest.substring(72);
                head = 72;
            }
            frame = AirProto.replaceFa(frame);
            int len = Integer.parseInt(frame.substring(8, 12));
            frame = frame.substring(0, 12 + len * 2 + 4);
            rest = rest.substring(head + frame.length());
            String expected = crc(frame.substring(2, frame.length() - 4));
            List<Row> list = db.list("AirDevice", "{DeviceNo} = :n AND {IsRun} = true", p("n", no));
            if (list.isEmpty()) {
                air.dealUnknow(list, no);
                throw new IllegalStateException("Appareil vide");
            }
            Row device = list.get(0);
            air.dealTankHeight(device, gauge);
            byte[] cmd = bytes(frame);
            if (!expected.equals(frame.substring(frame.length() - 4))) {
                if ((cmd[6] & 0xFF) == 50) {
                    throw new IllegalStateException("32");
                }
                gps(rest.substring(44), no);
                throw new IllegalStateException("CRC Error");
            }
            device.set("IsOnline", true).set("ConnecTime", now());
            String next = dispatch(cmd, frame, device, no, devLog);
            send = next == null ? send : next;
            log.writeLog("发送:" + send);
            db.update("AirDevice", device);
            out.writeBytes(bytes(send));
        }
    }

    /** Réponse à une trame ; null si la commande est inconnue (la réponse précédente est alors renvoyée, comme l'original). */
    private String dispatch(byte[] cmd, String frame, Row device, String no, LegacyLog l) {
        switch (cmd[6] & 0xFF) {
            case 48:
                return poll(device, l);
            case 49: {
                readData(frame, no);
                String again = getOld(device);
                String poll = poll(device, l);
                if (!again.isEmpty()) {
                    l.writeLog("Get Old:");
                    return again;
                }
                return poll;
            }
            case 50:
                l.writeLog("Transaction:" + frame);
                return transaction(frame, device, l);
            case 51:
                return download(cmd, device, l);
            case 52: {
                String t = downloadData(cmd, frame.substring(16, 22), device, l);
                return t.isEmpty() ? poll(device, l) : t;
            }
            case 53:
                l.writeLog("Gray:" + frame);
                return gray(frame, device, l);
            case 54:
                return queryWhiteBlack(frame, l);
            case 80:
                return credit(frame, device);
            case 82:
                return app(frame);
            case 86: {
                int v = device.i("ValidFlag");
                device.set("ValidFlag", v == 1 ? 2 : v == 2 ? 3 : v);
                return poll(device, l);
            }
            case 97:
                return rfid(frame, device);
            case 98:
                return poll(device, l);
            default:
                return null;
        }
    }

    private static String checkVolume(BigDecimal v) {
        return v.compareTo(BigDecimal.ONE.negate()) == 0 ? "FFFFFFFF" : pad(Integer.toHexString((int) trunc(v.multiply(BigDecimal.valueOf(100)))), 8);
    }

    private static String checkRVolume(int unit, BigDecimal v) {
        return v.compareTo(BigDecimal.ONE.negate()) == 0 ? "FFFFFFFFFF" : pad(Integer.toHexString((int) trunc(v.multiply(BigDecimal.valueOf(unit)))), 10);
    }

    private static long x100(BigDecimal v) {
        return (int) trunc(v.multiply(BigDecimal.valueOf(100)));
    }

    private String credit(String frame, Row device) {
        String text;
        try {
            AirDeal.Credit c = air.creditCheck(frame, device);
            if (c.suspendType != 0 && c.suspendType != 1) {
                text = "000000000250" + hex(c.suspendType, 2);
            } else {
                text = "000000004550" + hex(c.suspendType, 2) + hex(x100(c.preferentialPrice), 4) + hex(x100(c.discount), 4) + hex(x100(c.balance), 8)
                    + hex(x100(c.maxAmount), 8) + checkVolume(c.maxVolume) + hex((long) (int) trunc(c.mileageLastest), 6) + hex(x100(c.volumeLastest), 6)
                    + hex(x100(c.oilConsumption), 4) + pad(String.valueOf(c.mileageTolerance), 2) + hex(c.limitVehicleNo, 2) + c.vehicleNo + c.pass;
            }
        } catch (RuntimeException ex) {
            text = "00000000025005";
        }
        return frameFa(text);
    }

    private String rfid(String frame, Row device) {
        String text;
        try {
            AirDeal.Credit c = air.creditCheck(frame, device);
            BigDecimal asked = new BigDecimal(frame.substring(38, 46)).divide(BigDecimal.valueOf(10000));
            c.maxAmount = AirDeal.min(c.maxAmount, asked);
            if (c.suspendType != 0 && c.suspendType != 1) {
                text = "000000000261" + hex(c.suspendType, 2);
            } else {
                BigDecimal money = pow10(device.i("AmountDigits"));
                BigDecimal unit = pow10(device.i("VolumeDigits"));
                text = "000000005861" + hex(c.suspendType, 2) + hex((long) (int) trunc(money.multiply(c.preferentialPrice)), 4) + hex(x100(c.discount), 4)
                    + hex(trunc(c.balance.multiply(BigDecimal.valueOf(10000))), 10) + hex((long) (int) trunc(c.maxAmount.multiply(BigDecimal.valueOf(10000))), 10)
                    + checkRVolume(unit.intValue(), c.maxVolume) + hex((long) (int) trunc(c.mileageLastest), 6) + hex((long) (int) trunc(unit.multiply(c.volumeLastest)), 6)
                    + hex((long) (int) trunc(unit.multiply(c.oilConsumption)), 4) + pad(String.valueOf(c.mileageTolerance), 2) + hex(c.limitVehicleNo, 2)
                    + c.vehicleNo + "0000" + c.asn;
            }
        } catch (RuntimeException ex) {
            text = "00000000026105";
        }
        return frameFa(text);
    }

    /** DealApp + réponse 0x52 : code d'application contre autorisation sur le solde de la carte. */
    private String app(String frame) {
        String text;
        try {
            String code = AirProto.appCode(frame.substring(14, 34));
            Row a = db.first("AppCodeUse", "({IsUsing} = 0 OR {IsUsing} = 1) AND ({AppCode} = :c OR {AppCodeShort} = :c)", p("c", code));
            if (a == null) {
                throw new IllegalStateException("Code inconnu");
            }
            CenterDeal.CusCard c = deal.getCusCardInfo(a.s("Asn"));
            db.update("AppCodeUse", a.set("IsUsing", 1));
            if (!c.ok()) {
                throw new IllegalStateException(c.error());
            }
            long bal = BigDecimal.valueOf(c.accounts().d("Balance")).setScale(2, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(10000)).longValue();
            String amount = pad(Long.toHexString(bal), 12);
            text = "000000325200" + pad(a.s("Asn"), 20) + amount + "FFFFFFFFFFFF" + amount
                + hex(x100(BigDecimal.valueOf(c.cardInfos().d("Discount")).setScale(2, RoundingMode.HALF_EVEN)), 4);
        } catch (RuntimeException ex) {
            text = "00000000025201";
        }
        return frameFa(text);
    }

    private void readData(String cache, String no) {
        try {
            Row device = db.first("AirDevice", "{DeviceNo} = :n AND {IsOnline} = true", p("n", no));
            if (cache.length() <= 30) {
                return;
            }
            boolean idle = "01".equals(cache.substring(16, 18));
            int w = "886".equals(device.str("DigitsType")) ? 8 : 6;
            BigDecimal amount = idle ? BigDecimal.ZERO
                : BigDecimal.valueOf(AirProto.int16(cache.substring(22, 22 + w))).divide(pow10(device.i("AmountDigits")));
            BigDecimal volume = idle ? BigDecimal.ZERO
                : BigDecimal.valueOf(AirProto.int16(cache.substring(22 + w, 22 + 2 * w))).divide(pow10(device.i("VolumeDigits")));
            Row real = db.first("NozRealData", "{DeviceNo} = :d AND {StationID} = :s AND {NozID} = :n",
                p("d", device.s("DeviceNo"), "s", device.i("StationID"), "n", device.i("NozID")));
            if (real != null) {
                db.update("NozRealData", real.set("Amount", amount).set("Volume", volume).set("Statue", 1));
            } else {
                db.insert("NozRealData", Row.of("StationID", device.i("StationID"), "NozID", device.i("NozID"), "DeviceNo", device.s("DeviceNo"),
                    "Amount", amount, "Volume", volume, "Statue", 2, "UpdateTime", CenterDeal.now()));
            }
        } catch (RuntimeException ignored) {
            // donnée temps réel perdue : la trame suivante la remplacera
        }
    }

    private String poll(Row device, LegacyLog l) {
        try {
            if (device != null) {
                int valid = device.i("ValidFlag");
                if (valid == 0 || valid == 2) {
                    return lockNoz(device);
                }
                Row cmd = db.first("CmdList", "{CmdPlatType} = 3 AND {DeviceNo} = :d AND {Deal} = 0 AND {NozID} = :n AND {StationID} = :s",
                    p("d", device.s("DeviceNo"), "n", device.i("NozID"), "s", device.i("StationID")));
                if (cmd != null) {
                    return preSet(cmd, device, l);
                }
            }
            Row v = db.first("DataVersions");
            String versions = v == null ? "000000000000000000"
                : hex(v.i("BasicBlack") % 65536, 4) + hex(v.i("AppendBlack") % 256, 2) + hex(v.i("DelBlack") % 256, 2) + hex(v.i("White") % 256, 2)
                + hex((v.i("PPU") + v.i("GradeSpec")) % 256, 2) + hex(v.i("MachineCfg") % 256, 2) + "0000";
            String t = frameFa("010050001730" + LocalDateTime.now().format(STAMP) + versions);
            l.writeLog("Poll Send:" + t);
            return t;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String preSet(Row cmd, Row device, LegacyLog l) {
        try {
            Row v = Wire.parseRow(cmd.s("CmdData"));
            int type = v.i("PreType");
            String t = type == 1 ? "01" : type == 2 ? "02" : "00";
            int digits = type == 1 ? device.i("VolumeDigits") : device.i("AmountDigits");
            int value = type == 2 ? 0 : v.dec("PreValue").multiply(pow10(digits)).setScale(0, RoundingMode.HALF_EVEN).intValue();
            t += hex(value, 8) + hex(cmd.i("ID"), 8);
            db.update("CmdList", cmd.set("Deal", 99));
            String out = frameFa("010050001062" + t);
            l.writeLog("PreSet:" + out);
            return out;
        } catch (RuntimeException ex) {
            l.writeLog("Pre_Set_Error:" + ex.getMessage());
            return "";
        }
    }

    private String lockNoz(Row device) {
        try {
            String t;
            if (device.i("ValidFlag") == 0) {
                t = "0100500006560000000000";
                db.update("AirDevice", device.set("ValidFlag", 1));
            } else {
                t = "010050000656" + Conv.toDateTime(device.str("ValidTime")).format(HOUR);
            }
            return frameFa(t);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /** Cmd_GetTransaction : demande à l'appareil de renvoyer une ancienne opération. */
    private String getOld(Row device) {
        try {
            Row g = db.first("AirGetOlDTransaction", "{DeviceNo} = :d", p("d", device.s("DeviceNo")));
            if (g != null && g.i("PSStart") < g.i("PSEnd")) {
                String t = frameFa("01005000053E" + hex(g.i("PSStart"), 8));
                db.update("AirGetOlDTransaction", g.set("PSStart", g.i("PSStart") + 1));
                return t;
            }
        } catch (RuntimeException ex) {
            log.writeLog("GetOld:" + ex.getMessage());
        }
        return "";
    }

    private String download(byte[] data, Row device, LegacyLog l) {
        try {
            int kind = data[7] & 0xFF;
            long size = switch (kind) {
                case 0 -> 16 + db.count("BasicBlackLists", null, null) * 10;
                case 1 -> 16 + db.count("AppendBlackLists", null, null) * 10;
                case 2 -> 16 + db.count("DeleteBlackLists", null, null) * 10;
                case 3 -> 16 + db.count("EmployeeCardInfos", "{OpenStationID} = :s AND {CardStatusTypeID} = 0", p("s", device.i("StationID"))) * 10;
                case 4 -> 28;
                case 5 -> 14;
                default -> 0;
            };
            String t = frameFa("000050000633" + hex(size, 8) + pad(String.valueOf(kind), 2));
            l.writeLog("CMD_Download:" + t);
            return t;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String page(String text, int seg, int count) {
        String tail = text.substring(seg * 32);
        return tail.length() <= count * 32 ? tail : tail.substring(0, count * 32);
    }

    private String blackList(String table, int version, int versionLen, String segs, int seg, int count, String kind) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter day = DateTimeFormatter.ofPattern("yyyyMMdd");
        List<Row> rows = db.list(table);
        StringBuilder t = new StringBuilder(hex(version, versionLen)).append("00".equals(kind) ? "00000000" : now.format(day))
            .append(now.plusDays(2).format(day)).append("FFFF").append(hex(rows.size(), 8));
        rows.forEach(r -> t.append(r.s("ASN")));
        String text = rows.isEmpty() ? t.toString() : page(t.toString(), seg, count);
        return "000050" + pad(String.valueOf(5 + text.length() / 2), 4) + "34" + kind + segs + text;
    }

    private String downloadData(byte[] data, String segs, Row device, LegacyLog l) {
        try {
            Row v = db.first("DataVersions");
            int seg = Integer.parseInt(segs.substring(0, 4), 16);
            int count = Integer.parseInt(segs.substring(4, 6), 16);
            String t = "";
            switch (data[7] & 0xFF) {
                case 0 -> t = blackList("BasicBlackLists", v.i("BasicBlack") % 65536, 4, segs, seg, count, "00");
                case 1 -> t = blackList("AppendBlackLists", v.i("AppendBlack") % 256, 4, segs, seg, count, "01");
                case 2 -> t = blackList("DeleteBlackLists", v.i("DelBlack") % 256, 4, segs, seg, count, "02");
                case 3 -> {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter day = DateTimeFormatter.ofPattern("yyyyMMdd");
                    List<Row> rows = db.list("EmployeeCardInfos", "{OpenStationID} = :s AND {CardStatusTypeID} = 0", p("s", device.i("StationID")));
                    StringBuilder asn = new StringBuilder();
                    rows.forEach(r -> asn.append(r.s("ASN")));
                    String list = asn.toString();
                    if (!rows.isEmpty()) {
                        list = list.length() <= count * 32 ? list.substring(seg * 32) : list.substring(seg * 32, seg * 32 + count * 32);
                    }
                    String text = pad(String.valueOf(v.i("White") % 256), 4) + now.format(day) + now.plusDays(2).format(day) + "FFFF" + hex(rows.size(), 8) + list;
                    t = "000050" + pad(String.valueOf(5 + text.length() / 2), 4) + "3403" + segs + text;
                }
                case 4 -> t = priceData(v, segs, device);
                case 5 -> {
                    Row st = db.first("Stations", "{StationID} = :s", p("s", device.i("StationID")));
                    Row city = db.first("Cities", "{CityID} = :c", p("c", st.i("CityID")));
                    Row region = db.first("Regions", "{RegionID} = :r", p("r", city.i("RegionID")));
                    if (region == null) {
                        throw new IllegalStateException("Région introuvable");
                    }
                    t = "00005000193405" + segs + hex(v.i("MachineCfg") % 256, 2) + "0101" + pad("0001", 8) + pad(st.s("Code"), 8) + "0001" + hex(device.i("NozID"), 2);
                }
                default -> {
                }
            }
            t = frameFa(t);
            l.writeLog("Dowload_Data:" + t);
            return t;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String priceData(Row v, String segs, Row device) {
        int digits = device.i("PriceDigits");
        int unit = (int) dpow10(digits);
        Row price = db.first("PriceInfos", "{GradeSpecificID} = :g AND {StationID} = :s", p("g", device.i("OilID"), "s", device.i("StationID")));
        if (price == null) {
            throw new IllegalStateException("Prix introuvable");
        }
        Row grade = db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", price.i("GradeSpecificID")));
        if (grade == null) {
            throw new IllegalStateException("Carburant introuvable");
        }
        for (Row s : db.list("SpricePriceSet", "{GradeID} = :g", p("g", price.i("GradeSpecificID")))) {
            if (Conv.toDateTime(s.str("EndTime")).isAfter(LocalDateTime.now())) {
                price.set("CurrentPPU", price.d("NewPPU")).set("ValidateTime", s.str("StartTime"))
                    .set("NewPPU", Conv.round(Conv.toDouble(s.get("SpricePrice")), 2));
                break;
            }
        }
        boolean big = "886".equals(device.str("DigitsType"));
        int w = big ? 6 : 4;
        String noz = hex(device.i("NozID"), 2) + grade.s("Code");
        return (big ? "00005000353404" : "00005000333404") + segs + hex((v.i("PPU") + v.i("GradeSpec")) % 256, 2)
            + Conv.toDateTime(price.get("ValidateTime")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + "01"
            + noz + pad(Conv.num(price.d("CurrentDensity") * 10000.0), 8) + "01" + ppu(price.d("CurrentPPU"), digits, unit, w)
            + noz + pad(Conv.num(price.d("Newdensity") * 10000.0), 8) + "01" + ppu(price.d("NewPPU"), digits, unit, w);
    }

    private static String ppu(double v, int digits, int unit, int w) {
        return hex((int) trunc(BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(unit))), w);
    }

    private String gray(String cache, Row device, LegacyLog l) {
        try {
            List<Row> rows = db.list("OilTransactions", "{Asn} = :a AND {TType} <> 0 AND {TType} <> 5 AND {TType} <> 6 ORDER BY {TrueTime} DESC", p("a", cache.substring(14, 34)));
            if (rows.isEmpty()) {
                throw new IllegalStateException("null");
            }
            Row o = rows.get(0);
            boolean big = "886".equals(device.str("DigitsType"));
            int w = big ? 8 : 6;
            // l'original multiplie par le nombre de décimales, et non par sa puissance de 10
            int d = device.i("AmountDigits");
            String t = o.s("Asn") + hex((int) (o.d("BAL") * d), 8) + hex((int) (o.d("TruelyPay") * d), w) + hex((int) (o.d("PreferentialPrice") * d), 4)
                + hex((int) (o.d("Discount") * 100.0), 4) + hex((int) (o.d("AMN") * d), w) + o.s("CTC") + "00" + Conv.toDateTime(o.get("TrueTime")).format(STAMP)
                + o.s("GMAC") + o.s("PSAMTID") + o.s("PSAMTTC");
            String out = frameFa((big ? "00005000503700" : "00005000503500") + t);
            l.writeLog("GetGray:" + out);
            return out;
        } catch (RuntimeException ex) {
            return frameFa("00005000023501");
        }
    }

    private static int hex16(String s) {
        return AirProto.int16(s);
    }

    private double scaled(String s, int digits) {
        return Conv.round(hex16(s) / dpow10(digits), digits);
    }

    private static String text(String hex) {
        return new String(bytes(hex), AirProto.ANSI).replace("\0", "");
    }

    private int employeeId(String code) {
        Row e = db.first("Employees", "{Code} = :c", p("c", code));
        e = e == null ? db.first("Employees") : e;
        return e == null ? 0 : e.i("EmployeeID");
    }

    /** Cmd_TransactionSix / Cmd_TransactionEight : opération IC, montants sur 6 ou 8 chiffres selon DigitsType. */
    private String transaction(String c, Row device, LegacyLog l) {
        boolean big = "886".equals(device.str("DigitsType"));
        String back = "";
        String noz = hex(device.i("NozID"), 2);
        int am = device.i("AmountDigits");
        int vo = device.i("VolumeDigits");
        int pr = device.i("PriceDigits");
        try {
            int s = big ? 2 : 0;
            int w = big ? 8 : 6;
            String time = c.substring(24, 28) + "-" + c.substring(28, 30) + "-" + c.substring(30, 32) + " " + c.substring(32, 34) + ":" + c.substring(34, 36) + ":" + c.substring(36, 38);
            Row o = Row.of("StationID", device.i("StationID"), "DeviceNo", device.s("DeviceNo"), "POSTTC", c.substring(14, 22),
                "TType", Integer.parseInt(c.substring(22, 24)), "TrueTime", Conv.toDateTime(time).format(Conv.STD), "Asn", c.substring(38, 58),
                "BAL", scaled(c.substring(58, 66), am), "AMN", scaled(c.substring(66, 66 + w), am),
                "CTC", c.substring(72 + s, 76 + s), "TAC", c.substring(76 + s, 84 + s), "GMAC", c.substring(84 + s, 92 + s),
                "PSAMASN", c.substring(100 + s, 120 + s), "PSAMTID", c.substring(120 + s, 132 + s), "PSAMTTC", c.substring(132 + s, 140 + s),
                "PaymentID", hex16(c.substring(142 + s, 144 + s)), "GunId", device.i("NozID"));
            int grade = deal.getGradeID(c.substring(150 + s, 154 + s));
            o.set("GradeSpecificID", grade == 0 ? device.i("OilID") : grade);
            int v = 154 + s;
            int pw = big ? 6 : 4;
            o.set("Volume", scaled(c.substring(v, v + w), vo)).set("PPU", scaled(c.substring(v + w, v + w + pw), pr));
            int e = v + w + pw;
            o.set("EmployeeID", employeeId(String.valueOf(hex16(c.substring(e, e + 2)))));
            if (!volumeBySelf) {
                o.set("VolumeTotal", scaled(c.substring(e + 2, e + 10), vo));
            } else {
                List<Row> last = db.list("OilTransactions", "{StationID} = :s AND {GunId} = :g ORDER BY {VolumeTotal} DESC LIMIT 1", p("s", device.i("StationID"), "g", device.i("NozID")));
                o.set("VolumeTotal", (last.isEmpty() ? 0 : last.get(0).d("VolumeTotal")) + o.d("Volume"));
            }
            o.set("PreferentialPrice", scaled(c.substring(e + 10, e + 14), am));
            o.set("Discount", Conv.round(hex16(c.substring(e + 14, e + 18)) / 100.0, 2));
            o.set("TruelyPay", scaled(c.substring(e + 18, e + 18 + w), am));
            int m = e + 18 + w;
            o.set("Mileage", hex16(c.substring(m, m + 6)));
            if (big) {
                o.set("Coupon", o.i("PaymentID") == 224 ? String.valueOf(hex16(c.substring(m, m + 16))) : text(c.substring(m, m + 40)));
                o.set("CouponValue", hex16(c.substring(242, 248))).set("CarNumber", text(c.substring(248, 280)));
            } else {
                o.set("Coupon", text(c.substring(194, 234))).set("CouponValue", hex16(c.substring(234, 240))).set("CarNumber", text(c.substring(240, 272)));
            }
            o.set("Upload", "1").set("Flg", "1").set("BankSID", "").set("ErrorFlgID", 0).set("GTAC", "");
            String dup = "{StationID} = :s AND {GunId} = :g AND {TrueTime} = :t" + (big ? "" : " AND {POSTTC} = :p");
            Row exists = db.first("OilTransactions", dup, p("s", o.i("StationID"), "g", o.i("GunId"), "t", Conv.toDateTime(o.s("TrueTime")), "p", o.s("POSTTC")));
            if (exists == null) {
                try (Sugar.Tx tx = db.begin()) {
                    db.insert("OilTransactions", o);
                    pushMessage(o);
                    boolean noCard = NO_ASN.equals(o.s("Asn"));
                    // sur 8 chiffres, le débit n'est pas tenté pour une opération sans carte
                    String error = big && noCard ? "" : air.dealCustomer(o);
                    if (!error.isEmpty()) {
                        back = "-1";
                        if (!noCard) {
                            throw new IllegalStateException(error);
                        }
                    }
                    tx.commit();
                }
            } else if (exists.s("DeviceNo").isEmpty()) {
                db.update("OilTransactions", exists.set("DeviceNo", device.s("DeviceNo")));
            }
            back = noz + "000200023200";
        } catch (RuntimeException ex) {
            log.writeLog("Tranasction:" + ex.getMessage());
            l.writeLog("Transaction Error:" + ex.getMessage());
            back = "-1".equals(back) ? noz + "000200023201" : big ? "00000200023201" : "00000200023200";
        }
        String out = frameFa(back);
        l.writeLog("Transaction Deal:" + out);
        return out;
    }

    private void pushMessage(Row oil) {
        CenterDeal.CusCard c = deal.getCusCardInfo(oil.s("Asn"));
        if (c.ok()) {
            notify.wxConsume(c.customers().str("OpendID"), oil.s("Asn"), Conv.num(Conv.round(oil.d("TruelyPay"), 2)), oil.s("TrueTime"),
                deal.getStationName(oil.i("StationID")));
        }
    }

    /** Cmd_QueryWhiteAndBlack : 010004 = liste blanche, 010001 = liste noire. */
    private String queryWhiteBlack(String cache, LegacyLog l) {
        String empty = "0000500012360000000000000000000000";
        try {
            String no = cache.substring(14, 34);
            String t;
            if ("010004".equals(no.substring(0, 6))) {
                t = (db.first("WhiteCardLists", "{ASN} = :a", p("a", no)) != null ? "00005000123601" : "00005000123600") + no;
            } else if ("010001".equals(no.substring(0, 6))) {
                t = (db.first("BasicBlackLists", "{ASN} = :a", p("a", no)) == null ? "00005000123601" : "00005000123600") + no;
            } else {
                t = empty;
            }
            String out = frameFa(t);
            l.writeLog("白名单确认信息:" + out);
            return out;
        } catch (RuntimeException ex) {
            return empty;
        }
    }
}
