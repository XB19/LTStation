package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ExtendUpdateController de Center d'origine : API des applications mobiles (connexion d'un
 * employé, stations, boîtiers, pistolets, commandes). Requête {appid, data, ts} chiffrée en
 * AES-CBC (clé AppKey, IV = MD5(ts + en-tête « identity »)[8..24]) et encodée en Base64.
 */
@RestController
@RequestMapping(value = "/ExtendUpdate", produces = ServerDealBase.TEXT)
public class ExtendUpdateController {

    private final Sugar db;
    private final String appKey;
    private final LegacyLog log = new LegacyLog("Web");

    public ExtendUpdateController(@Qualifier("centerSugar") Sugar db, @Value("${app.server-deal.extend-app-key:}") String appKey) {
        this.db = db;
        this.appKey = appKey;
    }

    // ------------------------------------------------------------ chiffrement

    static String md5(String txt) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(txt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** GetTimeStamp : secondes depuis 1970 calculées sur l'heure locale (comme l'original). */
    static String timeStamp() {
        return String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
    }

    private static byte[] pad(String s, int n) {
        return Arrays.copyOf((s + " ".repeat(n)).getBytes(StandardCharsets.UTF_8), n);
    }

    static String aesEncrypt(String input, String key, String iv) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
            new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(c.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    static String aesDecrypt(String input, String key, String iv) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(pad(key, 32), "AES"), new IvParameterSpec(pad(iv, 16)));
        return new String(c.doFinal(Base64.getDecoder().decode(input)), StandardCharsets.UTF_8);
    }

    private String getRequest(String body, HttpServletRequest request, String[] head) {
        try {
            head[0] = request.getHeader("identity");
            log.writeLog("Request Url:" + request.getRequestURL());
            log.writeLog("receive:" + body);
            log.writeLog("Head:" + head[0]);
            Row data = Wire.parseRow(body);
            if (data == null) {
                throw new IllegalStateException("Analysis Error");
            }
            String iv = md5(data.s("ts") + head[0]).toUpperCase().substring(8, 24);
            String plain = aesDecrypt(data.s("data"), appKey, iv);
            log.writeLog(plain);
            return plain;
        } catch (Exception ex) {
            head[0] = "";
            log.writeLog("Analysis Error：" + ex.getMessage());
            return "";
        }
    }

    private String returnMessage(int flag, String data, String head) {
        String ts = timeStamp();
        String iv = md5(ts + head).toUpperCase().substring(8, 24);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("returnCode", flag);
        r.put("returnMsg", "");
        String enc;
        try {
            enc = aesEncrypt(data, appKey, iv);
        } catch (Exception e) {
            enc = null;
        }
        r.put("data", enc);
        r.put("ts", ts);
        return Wire.json(r);
    }

    // ------------------------------------------------------------ actions

    @RequestMapping("/EmpLogin")
    public String EmpLogin(@RequestBody(required = false) String body, HttpServletRequest request) {
        int num = 0;
        try {
            String[] head = {""};
            Row web = Wire.parseRow(getRequest(body, request, head));
            Row emp = db.first("Employees", "{Address} = :u AND {Memo} = :p AND {IsUsing} = true",
                Sugar.p("u", web.str("UserName"), "p", web.str("Password")));
            if (emp != null) {
                Row obj = Row.of("LoginType", 1, "Auth", null, "EmpID", emp.i("EmployeeID"), "AmountUnit", null);
                return returnMessage(num, Wire.json(obj), head[0]);
            }
            num = 10001;
            throw new IllegalStateException("Error");
        } catch (Exception ex) {
            return returnMessage(num == 0 ? 99999 : num, "", "");
        }
    }

    /** DealEmpLogin : stations accessibles à l'employé (toute la société sauf pour une station de type 1). */
    private List<Row> stationsOf(int employeeId, int[] flag) {
        Row emp = db.first("Employees", "{EmployeeID} = :e", Sugar.p("e", employeeId));
        if (emp == null) {
            flag[0] = 10001;
            return null;
        }
        Row st = db.first("Stations", "{StationID} = :s", Sugar.p("s", emp.i("StationID")));
        if (st == null) {
            flag[0] = 10003;
            return null;
        }
        if (st.i("StationTypeID") != 1) {
            return db.list("Stations", "{CompanyID} = :c", Sugar.p("c", st.i("CompanyID")));
        }
        List<Row> l = new ArrayList<>();
        l.add(st);
        return l;
    }

    @RequestMapping("/GetStation")
    public String GetStation(@RequestBody(required = false) String body, HttpServletRequest request) {
        int[] flag = {0};
        try {
            String[] head = {""};
            getRequest(body, request, head);
            List<Row> st = stationsOf(Conv.toInt(head[0]), flag);
            if (st == null) {
                throw new IllegalStateException("Error");
            }
            return returnMessage(flag[0], Wire.json(st), head[0]);
        } catch (Exception ex) {
            return returnMessage(flag[0] == 0 ? 99999 : flag[0], "", "");
        }
    }

    @RequestMapping("/GetDeviceNo")
    public String GetDeviceNo(@RequestBody(required = false) String body, HttpServletRequest request) {
        int[] flag = {0};
        try {
            String[] head = {""};
            Row station = Wire.parseRow(getRequest(body, request, head));
            if (stationsOf(Conv.toInt(head[0]), flag) == null) {
                throw new IllegalStateException("Error");
            }
            List<Row> out = new ArrayList<>();
            for (String no : db.jdbc().queryForList("SELECT DISTINCT device_no FROM air_device WHERE station_id = :s AND is_run = true",
                Sugar.p("s", station.i("StationID")), String.class)) {
                out.add(Row.of("DeviceNo", no));
            }
            return returnMessage(flag[0], Wire.json(out), head[0]);
        } catch (Exception ex) {
            return returnMessage(flag[0] == 0 ? 99999 : flag[0], "", "");
        }
    }

    @RequestMapping("/GetNoz")
    public String GetNoz(@RequestBody(required = false) String body, HttpServletRequest request) {
        int[] flag = {0};
        try {
            String[] head = {""};
            Row data = Wire.parseRow(getRequest(body, request, head));
            if (stationsOf(Conv.toInt(head[0]), flag) == null) {
                throw new IllegalStateException("Error");
            }
            List<Row> l = db.list("AirDevice", "{DeviceNo} = :d AND {IsRun} = true ORDER BY {NozID}", Sugar.p("d", data.str("DeviceNo")));
            return returnMessage(flag[0], Wire.json(l), head[0]);
        } catch (Exception ex) {
            return returnMessage(flag[0] == 0 ? 99999 : flag[0], "", "");
        }
    }

    @RequestMapping("/CreateCmd")
    public String CreateCmd(@RequestBody(required = false) String body, HttpServletRequest request) {
        int[] flag = {0};
        try {
            String[] head = {""};
            Row cmd = Wire.parseRow(getRequest(body, request, head));
            if (stationsOf(Conv.toInt(head[0]), flag) == null) {
                throw new IllegalStateException("Error");
            }
            cmd.put("CreateTime", CenterDeal.now());
            cmd.put("Deal", 0);
            cmd.put("Dowload", false);
            cmd.remove("ID");
            db.insert("CmdList", cmd);
            return returnMessage(flag[0], "", head[0]);
        } catch (Exception ex) {
            return returnMessage(flag[0] == 0 ? 99999 : flag[0], "", "");
        }
    }
}
