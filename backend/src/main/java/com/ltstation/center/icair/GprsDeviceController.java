package com.ltstation.center.icair;

import static com.ltstation.center.icair.AirProto.bytes;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.serverdeal.CenterDeal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /GPRS/Heartbeat et /GPRS/ICCIDCheck : boîtiers GPRS (GPRSController d'origine). Les écrans sont dans web/GprsController.
 * Trame : F5, pistolet, longueur, deux chiffres de chiffrement (décalage de bits, "89" = en clair), ICCID, commande, données, contrôle.
 *
 * Écart : gprs_real_data enregistre le NozID à la création ; sans lui, l'original ajoutait une ligne à chaque trame.
 */
@RestController
public class GprsDeviceController {

    /** Boîtier dont la trame A5 n'a que le volume, l'index et le pourcentage de jauge. */
    private static final String SHORT_ICCID = "200831100000000002";

    private final Sugar db;
    private final LegacyLog log = new LegacyLog("GPRS");

    public GprsDeviceController(CenterDeal deal) {
        this.db = deal.db();
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    private static String stamp() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private static String getStr(byte[] b, int start, int len) {
        return AirProto.str(Arrays.copyOfRange(b, start, start + len));
    }

    private static byte check(byte[] m, int len) {
        int n = len == 0 ? 16 : len;
        byte b = m[0];
        for (int i = 1; i < n; i++) {
            b ^= m[i];
        }
        return (byte) (b & 0x7F);
    }

    private static short doubled(int b) {
        return (short) ((b << 8) | b);
    }

    @RequestMapping("/GPRS/ICCIDCheck")
    public String iccidCheck(@RequestBody(required = false) byte[] body) {
        log.writeLog("ICCIDCheck Receive:" + (body == null ? "" : new String(body, StandardCharsets.UTF_8)));
        return "123";
    }

    @RequestMapping("/GPRS/Heartbeat")
    public void heartbeat(@RequestBody(required = false) byte[] body, HttpServletResponse res) throws IOException {
        byte[] in = body == null ? new byte[0] : body;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            log.writeLog("Receive:" + AirProto.str(in));
            if (in.length == 0 || (in[0] & 0xFF) != 0xF5) {
                throw new IllegalStateException("Données incorrectes");
            }
            byte[] rest = in;
            while (rest.length != 0) {
                if ((rest[0] & 0xFF) != 0xF5) {
                    throw new IllegalStateException("Données incorrectes");
                }
                int len = rest[2] & 0xFF;
                if (check(rest, 24 + len) != rest[24 + len]) {
                    log.writeLog("Format de données incorrect");
                } else {
                    dealCmd(decrypt(rest), out);
                }
                rest = Arrays.copyOfRange(rest, 24 + len + 1, rest.length);
            }
        } catch (RuntimeException ex) {
            log.writeLog(ex.getMessage());
            out.writeBytes("123".getBytes(StandardCharsets.US_ASCII));
        }
        res.getOutputStream().write(out.toByteArray());
    }

    /** DealCmdSecret : déchiffre la trame sauf si elle est en clair ("89"). */
    private byte[] decrypt(byte[] bts) {
        try {
            int n = bts[2] & 0xFF;
            byte[] a = Arrays.copyOf(bts, 24 + n + 1);
            if (bts.length < a.length) {
                throw new IllegalStateException("Trame courte");
            }
            String type = new String(a, 3, 2, AirProto.ANSI);
            if (!"89".equals(type)) {
                int dir = Integer.parseInt(type.substring(0, 1));
                int shift = Integer.parseInt(type.substring(1, 2));
                for (int i = 0; i < 19 + n; i++) {
                    int v = doubled(a[5 + i] & 0xFF);
                    a[5 + i] = dir == 0 ? (byte) ((v << shift) >> 8) : (byte) ((v >> shift) & 0xFF);
                }
            }
            log.writeLog("Real Data:" + AirProto.str(a));
            return a;
        } catch (RuntimeException ex) {
            log.writeLog("Secret Error:" + AirProto.str(bts));
            return bts;
        }
    }

    /** DealCmdEncry. */
    private static String encrypt(String hex, String type) {
        try {
            int dir = Integer.parseInt(type.substring(0, 1));
            int shift = Integer.parseInt(type.substring(1, 2));
            StringBuilder t = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 2) {
                int v = doubled(Integer.parseInt(hex.substring(i, i + 2), 16));
                t.append(String.format("%02X", dir == 0 ? (v >> shift) & 0xFF : ((v << shift) >> 8) & 0xFF));
            }
            return t.toString();
        } catch (RuntimeException ex) {
            return hex;
        }
    }

    private static String asciiHex(String s) {
        return AirProto.str(s.getBytes(StandardCharsets.US_ASCII));
    }

    /** Début de réponse commun : F5, pistolet, longueur, type, ICCID chiffré. */
    private static String head(int noz, String len, String type, String iccid) {
        return "F5" + String.format("%02X", noz) + len + asciiHex(type) + encrypt(asciiHex(iccid), type);
    }

    private void reply(String text, ByteArrayOutputStream out) {
        String t = text + String.format("%02X", check(bytes(text), bytes(text).length) & 0xFF);
        log.writeLog("Return:" + t);
        out.writeBytes(bytes(t));
    }

    private static BigDecimal scaled(long v, int digits) {
        return BigDecimal.valueOf(v * 1.0 / Math.pow(10.0, digits));
    }

    private static long hexLong(String s) {
        return Long.parseLong(s, 16);
    }

    /** Hauteur et volume de la cuve après une opération ; fait partie de la transaction de l'opération. */
    private void updateTank(Row tank, BigDecimal realHeight, BigDecimal level, Row nozzle) {
        tank.set("TankRealHeight", realHeight);
        Map<String, Object> w = p("t", tank.i("ID"), "h", level);
        Row down = db.queryFirst("SELECT height, volume FROM gprs_tank_volume_table WHERE tank_id = :t AND height <= :h ORDER BY height DESC LIMIT 1", w);
        Row up = db.queryFirst("SELECT height, volume FROM gprs_tank_volume_table WHERE tank_id = :t AND height > :h ORDER BY height LIMIT 1", w);
        if (down == null || up == null) {
            tank.set("TankRealVolume", BigDecimal.ZERO);
        } else {
            BigDecimal volume = level.multiply(down.dec("volume").subtract(up.dec("volume")).abs())
                .divide(down.dec("height").subtract(up.dec("height")).abs(), 28, RoundingMode.HALF_EVEN);
            tank.set("TankRealVolume", volume);
            db.exec("INSERT INTO gprs_tank_data (tank_id, user_id, tank_height, tank_volume, create_time) VALUES (:t, :u, :h, :v, :c)",
                p("t", tank.i("ID"), "u", nozzle.i("UserID"), "h", level, "v", volume, "c", LocalDateTime.now()));
        }
        db.update("GPRSTank", tank);
    }

    private void transaction(byte[] bts, boolean shortFrame, String iccid, int noz, Row st, Row nozzle) {
        int vd = st.i("VolumeDigits");
        BigDecimal volume;
        BigDecimal total;
        BigDecimal level;
        BigDecimal price = null;
        BigDecimal amount = null;
        String idCard;
        if (shortFrame) {
            volume = scaled(AirProto.int16(getStr(bts, 24, 4)), vd);
            total = scaled(hexLong(getStr(bts, 28, 4)), vd);
            level = scaled(AirProto.int16(getStr(bts, 32, 4)), 1);
            idCard = getStr(bts, 36, 5);
        } else {
            volume = scaled(AirProto.int16(getStr(bts, 24, 4)), vd);
            amount = scaled(AirProto.int16(getStr(bts, 28, 4)), st.i("AmountDigits"));
            price = scaled(AirProto.int16(getStr(bts, 32, 3)), st.i("PriceDigits"));
            total = scaled(hexLong(getStr(bts, 35, 4)), vd);
            level = scaled(AirProto.int16(getStr(bts, 39, 4)), 1);
            getStr(bts, 43, 4);
            idCard = getStr(bts, 47, 5);
        }
        try (Sugar.Tx tx = db.begin()) {
            Row tank = db.first("GPRSTank", "{ID} = :i", p("i", nozzle.i("TankID")));
            BigDecimal height = level;
            if (shortFrame) {
                price = nozzle.dec("Price");
                amount = volume.multiply(price);
                height = level.divide(BigDecimal.valueOf(100)).multiply(tank.dec("TankHeight"));
            }
            nozzle.set("Totalliser", total).set("UpdateTime", stamp()).set("IsConnect", true);
            if (tank != null) {
                updateTank(tank, height, level, nozzle);
            }
            db.insert("GPRSTransaction", Row.of("StationID", st.i("StationID"), "StationName", st.str("StationName"), "ICCID", iccid, "GunID", noz,
                "OilID", nozzle.i("OilID"), "OilName", nozzle.str("OilName"), "Price", price, "Amount", amount, "Volume", volume, "VolumeTotal", total,
                "CreateTime", stamp(), "Shift", "1", "IDCard", idCard, "VolumeHigh", height, "UserID", st.i("UserID")));
            db.update("GPRSNozzle", nozzle);
            tx.commit();
        } catch (RuntimeException ex) {
            log.writeLog("Opération GPRS : " + ex.getMessage());
        }
    }

    private static boolean parseBool(String v) {
        if ("true".equalsIgnoreCase(v.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(v.trim())) {
            return false;
        }
        throw new IllegalArgumentException("Booléen attendu : " + v);
    }

    private static BigDecimal latLng(byte[] bts, int start) {
        try {
            float f = ByteBuffer.wrap(bts, start, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            BigDecimal n = new BigDecimal((double) f, new MathContext(7));
            BigDecimal whole = n.setScale(0, RoundingMode.DOWN);
            return whole.add(n.subtract(whole).divide(new BigDecimal("0.6"), 28, RoundingMode.HALF_EVEN));
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void position(Row st, BigDecimal lat, BigDecimal lng) {
        if (lat.signum() != 0 && lng.signum() != 0) {
            db.update("GPRSStation", st.set("Latitude", lat).set("Longitude", lng));
        }
    }

    private void command(byte[] bts, String iccid, String text, String type, int noz, Row st, ByteArrayOutputStream out) {
        List<Row> cmds = db.list("GPRSCMD", "{ICCID} = :i AND {Gunid} = :g AND {IsDeal} = false ORDER BY {CreateTime}", p("i", iccid, "g", noz));
        if (cmds.isEmpty()) {
            position(st, latLng(bts, 30), latLng(bts, 25));
            reply(head(noz, "00", type, text) + encrypt("AF", type), out);
            return;
        }
        Row cmd = cmds.get(0);
        String value = cmd.str("CMDValue");
        switch (cmd.i("CMDType")) {
            case 1 -> reply(head(noz, "03", type, text) + encrypt("A1", type)
                + encrypt(AirProto.hex((int) (Double.parseDouble(value) * Math.pow(10.0, st.i("PriceDigits"))), 6), type), out);
            case 2 -> reply(head(noz, "04", type, text) + encrypt("A4", type)
                + encrypt(AirProto.hex((int) (Double.parseDouble(value) * Math.pow(10.0, st.i("VolumeDigits"))), 8), type), out);
            case 3 -> reply(head(noz, "02", type, text) + encrypt("A2", type)
                + encrypt(AirProto.hex((int) AirProto.trunc(new BigDecimal(value)), 4), type), out);
            case 4 -> reply(head(noz, "01", type, text) + encrypt("A3", type) + encrypt(parseBool(value) ? "FF" : "00", type), out);
            case 5 -> reply(head(noz, "01", type, text) + encrypt("A6", type) + encrypt(parseBool(value) ? "FF" : "00", type), out);
            default -> {
                position(st, latLng(bts, 24), latLng(bts, 29));
                reply(head(noz, "00", type, text) + encrypt("AF", type), out);
            }
        }
        db.update("GPRSCMD", cmd.set("IsDeal", true));
    }

    private void dealCmd(byte[] bts, ByteArrayOutputStream out) {
        String text = "";
        try {
            int noz = bts[1] & 0xFF;
            String type = new String(bts, 3, 2, AirProto.ANSI);
            text = new String(bts, 5, 18, AirProto.ANSI);
            int lenType = Integer.parseInt(text.substring(2, 4));
            if ("89".equals(type)) {
                lenType = Integer.parseInt(text.substring(0, 2));
                text = type + text;
            }
            Row rule = db.first("GPRSICCID", "{ICCIDType} = :t", p("t", lenType));
            String iccid = rule == null ? text : text.substring(0, rule.i("ICCIDLength"));
            log.writeLog("ICCID:" + text);
            Row nozzle = db.first("GPRSNozzle", "{ICCID} = :i AND {GunID} = :g", p("i", iccid, "g", noz));
            if (nozzle == null) {
                throw new IllegalStateException("Objet introuvable");
            }
            Row st = db.first("GPRSStation", "{StationID} = :s", p("s", nozzle.i("StationID")));
            db.update("GPRSNozzle", nozzle.set("IsConnect", true).set("UpdateTime", stamp()));
            switch (bts[23] & 0xFF) {
                case 165 -> {
                    transaction(bts, SHORT_ICCID.equals(text), iccid, noz, st, nozzle);
                    reply(head(noz, "00", type, text) + encrypt("A5", type), out);
                }
                case 169 -> {
                    BigDecimal volume = scaled(AirProto.int16(getStr(bts, 24, 4)), st.i("VolumeDigits"));
                    BigDecimal amount = scaled(AirProto.int16(getStr(bts, 28, 4)), st.i("AmountDigits"));
                    Row real = db.queryFirst("SELECT 1 x FROM gprs_real_data WHERE noz_id = :n", p("n", nozzle.i("ID")));
                    if (real != null) {
                        db.exec("UPDATE gprs_real_data SET amount = :a, volume = :v WHERE noz_id = :n", p("a", amount, "v", volume, "n", nozzle.i("ID")));
                    } else {
                        db.exec("INSERT INTO gprs_real_data (amount, volume, noz_id) VALUES (:a, :v, :n)", p("a", amount, "v", volume, "n", nozzle.i("ID")));
                    }
                    // pistolet en décimal et sans contrôle dans cette réponse, comme l'original
                    String t = "F5" + noz + "02" + asciiHex(type) + encrypt(asciiHex(text), type) + "6261";
                    log.writeLog("Return:" + t);
                    out.writeBytes(bytes(t));
                }
                case 175 -> command(bts, iccid, text, type, noz, st, out);
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            log.writeLog(ex.getMessage() + " ICCID:" + text);
        }
    }
}
