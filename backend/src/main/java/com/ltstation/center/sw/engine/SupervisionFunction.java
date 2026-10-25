package com.ltstation.center.sw.engine;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plateformes externes de supervision du logiciel Station (BaseFunction) :
 * FoRan (supervision des bouteilles de gaz, SupervisionType = 2) et NP (envoi des
 * transactions démarrées à distance, ExtendUrl). Les identifiants, écrits en dur dans
 * l'original, se règlent dans StationSoftware.properties (NPApiKey, NPUser, NPPassword).
 */
public final class SupervisionFunction {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private SupervisionFunction() {
    }

    /** Réponse FoRan {Code, msg, data}. */
    public record FoRan(int code, String msg, Object data) {
    }

    private static FoRan parse(String body) {
        Row r = Wire.parseRow(body);
        return r == null ? new FoRan(-1, "Réponse vide", "") : new FoRan(r.i("Code"), r.s("msg"), r.get("data"));
    }

    private static String multipart(String url, Map<String, String> headers, Map<String, String> fields) throws Exception {
        String boundary = "---------------------------" + Long.toHexString(System.nanoTime());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append("\r\n--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(e.getKey()).append("\"\r\n\r\n").append(e.getValue());
        }
        sb.append("\r\n--").append(boundary).append("--\r\n");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8));
        headers.forEach(b::header);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    // ------------------------------------------------------------ FoRan

    /** GetToken : jeton FoRan, renouvelé toutes les 5 minutes. */
    public static String GetToken() {
        try {
            if (IniInfo.FoRanToken.isEmpty() || BaseFunction.ConvertDateTimeToInt() - IniInfo.FoRanOutTime > 300000) {
                Row obj = Row.of("uid", IniInfo.uid, "clientKey", IniInfo.clientKey);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("data", BaseFunction.AesEncrypt(Wire.json(obj), IniInfo.clientSecret));
                FoRan r = parse(multipart(IniInfo.FoRanUrl + "/frplatform-sj/auth/v1/accessToken", Map.of("uid", IniInfo.uid), fields));
                if (r.code() == 0) {
                    IniInfo.FoRanToken = Conv.toStr(r.data());
                    IniInfo.FoRanOutTime = BaseFunction.ConvertDateTimeToInt();
                    return IniInfo.FoRanToken;
                }
                throw new IllegalStateException("Error");
            }
            return IniInfo.FoRanToken;
        } catch (Exception e) {
            return "";
        }
    }

    public static FoRan PostJson(String url, String postData) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json;charset=UTF-8").header("Accept", "application/json").header("token", GetToken())
                .POST(HttpRequest.BodyPublishers.ofString(postData, StandardCharsets.UTF_8)).build();
            return parse(HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body());
        } catch (Exception e) {
            return new FoRan(-1, e.getMessage(), "");
        }
    }

    public static FoRan GetJson(String url, Map<String, String> dic) {
        try {
            StringBuilder sb = new StringBuilder(url);
            if (!dic.isEmpty()) {
                sb.append('?');
                int n = 0;
                for (Map.Entry<String, String> e : dic.entrySet()) {
                    sb.append(n++ > 0 ? "&" : "").append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                }
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(sb.toString())).header("token", GetToken()).GET().build();
            return parse(HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body());
        } catch (Exception e) {
            return new FoRan(-1, e.getMessage(), "");
        }
    }

    /** FoRanCheckUp : contrôle avant remplissage d'un véhicule (bouteilles en règle). */
    public static boolean FoRanCheckUp(String carNo, Row start, String[] error) {
        error[0] = "";
        try {
            Row car = SW.db().first("CarUser", "{PlateNumber} = :p", Sugar.p("p", carNo));
            if (car == null) {
                error[0] = "Immatriculation inconnue";
                throw new IllegalStateException("523");
            }
            Row cyl = new Row();
            if (IniInfo.SupervisionType == 2) {
                cyl = SW.db().first("CylinderData", "{CarUserID} = :c", Sugar.p("c", car.i("ID")));
                if (cyl == null) {
                    error[0] = "Informations de bouteille absentes";
                    throw new IllegalStateException("325");
                }
            }
            Row obj = Row.of("stationCode", "44060401276", "operationTime", LocalDateTime.now().format(Conv.STD), "quantity", 9999,
                "cylinderState", 0, "fillingType", 3, "isDeleted", 0, "barcode", cyl.get("ProductNo"));
            FoRan r = IniInfo.SupervisionType == 2 ? PostJson(IniInfo.FoRanUrl + "/sj-business/sjcherkfront/v1/data", Wire.json(obj))
                : new FoRan(0, "", "");
            if (r.code() == 0) {
                start.put("Amount", car.get("Balance"));
                start.put("Asn", car.get("Asn"));
                start.put("Balance", car.get("Balance"));
                start.put("PlNumber", carNo);
                start.put("Code", 0);
                return true;
            }
            start.put("Code", r.code());
            throw new IllegalStateException(r.msg());
        } catch (RuntimeException e) {
            start.put("PlNumber", carNo);
            try {
                start.put("Code", Integer.parseInt(e.getMessage()));
            } catch (NumberFormatException nfe) {
                start.put("Code", -1);
            }
            error[0] = e.getMessage();
            return false;
        }
    }

    /** FoRanTransactionUp : déclaration d'un remplissage et des contrôles des bouteilles. */
    public static boolean FoRanTransactionUp(Row oil, String[] error) {
        error[0] = "";
        try {
            Row car = SW.db().first("CarUser", "{PlateNumber} = :p", Sugar.p("p", oil.str("CarNumber")));
            if (car == null) {
                throw new IllegalStateException("L'utilisateur n'existe pas");
            }
            String begin = Conv.toDateTime(oil.get("TrueTime")).format(Conv.STD);
            String end = Conv.toDateTime(oil.get("TrueTime")).plusMinutes(10).format(Conv.STD);
            for (Row item : SW.db().list("CylinderData", "{CarUserID} = :c", Sugar.p("c", car.i("ID")))) {
                Row fill = Row.of("fillingType", 3, "stationCode", "44060401276", "operatorName", IniInfo.LoginName, "barcode", item.get("ProductNo"),
                    "cylinderId", item.get("CylinderId"), "mediumId", item.get("MediumId"), "cylinderState", 0, "balance", oil.i("GunId"),
                    "modes", 1, "vehicleId", item.get("VehicleID"), "vehicleTerritory", 0, "plateNumber", oil.get("CarNumber"),
                    "vehicleState", 1, "fillingVerifyType", 1, "isDeleted", 0, "beginDateTime", begin, "endDateTime", end,
                    "fillingWeight", Conv.toDecimal(oil.d("Volume")));
                FoRan r = PostJson(IniInfo.FoRanUrl + "/sj-business/sjfillingdetail/v1/data", Wire.json(fill));
                if (r.code() != 0) {
                    throw new IllegalStateException(r.msg());
                }
                PostJson(IniInfo.FoRanUrl + "/sj-business/sjcheckback/v1/data", Wire.json(Row.of("stationCode", "44060401276", "fillingType", 3,
                    "operationTime", begin, "operatorId", "nz0000000001", "operatorNum", 1, "barcode", item.get("ProductNo"),
                    "cylinderState", 0, "isDeleted", 0, "pressure", 28)));
                Row after = Row.of("stationCode", "44060401276", "operationTime", begin, "operatorId", "nz0000000001", "operatorNum", 1,
                    "barcode", item.get("ProductNo"));
                for (String k : new String[] {"appearanceCheck", "soundCheck", "insideCheck", "waterCheck", "airtightCheck", "thicknessCheck",
                    "cylinderValveCheck", "windingLayerCheck", "metalCheck", "threadCheck", "undamagedCheck"}) {
                    after.put(k, 1);
                }
                after.put("isDeleted", 0);
                after.put("result", 1);
                after.put("cylinderId", item.get("CylinderId"));
                after.put("mjDate", item.get("MjDate"));
                after.put("xjDate", item.get("XjDate"));
                after.put("inspectionId", "r0cBHOgZ2D3CQk5iB0m93w==");
                PostJson(IniInfo.FoRanUrl + "/sj-business/sjcylinderinspect/v1/data", Wire.json(after));
            }
            return true;
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    private static List<Row> dataList(FoRan r) {
        return Wire.parseRows(Wire.json(r.data()));
    }

    public static boolean LoadCarGroupHttp(String applicantUserId, List<Row> out, String[] error) {
        try {
            FoRan r = GetJson(IniInfo.FoRanUrl + "/sj-business/sjuseunitinfo/all", Map.of("applicantUserId", applicantUserId));
            if (r.code() != 0) {
                throw new IllegalStateException(r.msg());
            }
            List<Row> l = dataList(r);
            if (l.isEmpty()) {
                throw new IllegalStateException("Liste vide");
            }
            l.stream().filter(t -> applicantUserId.equals(t.str("applicantUserId"))).forEach(out::add);
            return !out.isEmpty();
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    public static boolean LoadCarUserHttp(String plateNumber, List<Row> out, String[] error) {
        try {
            FoRan r = GetJson(IniInfo.FoRanUrl + "/sj-business/sjvehicleinfo/all", Map.of("plateNumber", plateNumber));
            if (r.code() != 0) {
                throw new IllegalStateException(r.msg());
            }
            out.addAll(dataList(r));
            if (out.isEmpty()) {
                throw new IllegalStateException("Liste vide");
            }
            return true;
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    public static boolean LoadCylinderHttp(String vid, String vidid, List<Row> out, String[] error) {
        try {
            FoRan r = GetJson(IniInfo.FoRanUrl + "/sj-business/sjcylinderinfo/all", Map.of("plateNumber", vid));
            if (r.code() != 0) {
                throw new IllegalStateException(r.msg());
            }
            List<Row> l = dataList(r);
            if (l.isEmpty()) {
                throw new IllegalStateException("Liste vide");
            }
            l.stream().filter(t -> vidid.equals(t.str("vehicleId"))).forEach(out::add);
            return !out.isEmpty();
        } catch (RuntimeException e) {
            error[0] = e.getMessage();
            return false;
        }
    }

    // ------------------------------------------------------------ NP (transactions démarrées à distance)

    /** HttpPostFormData : formulaire envoyé à l'adresse ExtendUrl avec la clé et le compte NP. */
    public static String HttpPostFormData(Map<String, String> kv) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            String apiKey = IniInfo.setting("NPApiKey");
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.put("apikey", apiKey);
            }
            String user = IniInfo.setting("NPUser");
            String pass = IniInfo.setting("NPPassword");
            if (user != null && !user.isEmpty()) {
                headers.put("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((user + ":" + (pass == null ? "" : pass)).getBytes(StandardCharsets.US_ASCII)));
            }
            return multipart(IniInfo.ExtendUrlRequest, headers, kv);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public static boolean NPSendExtendTransation(Row oil) {
        try {
            BaseFunction.BaseLog.writeLog("NP Start:");
            Row ds = SW.db().first("DealStart", "{MarkPoint} = :m AND {Deal} = 0", Sugar.p("m", oil.str("Coupon")));
            if (ds == null) {
                throw new IllegalStateException("DealStart Lost");
            }
            Map<String, String> kv = new LinkedHashMap<>();
            kv.put("transaction_id", ds.s("transaction_id"));
            kv.put("station_id", String.valueOf(oil.i("StationID")));
            kv.put("nozzle_id", String.valueOf(oil.i("GunId")));
            kv.put("amount", Conv.toStr(oil.get("TruelyPay")));
            kv.put("volume", Conv.toStr(oil.get("Volume")));
            kv.put("account_number", ds.s("Account_number"));
            kv.put("customer_id", Conv.toStr(ds.get("customer_id")));
            kv.put("txntype", ds.s("txntype"));
            BaseFunction.BaseLog.writeLog("Send:" + Wire.json(kv));
            String text = HttpPostFormData(kv);
            BaseFunction.BaseLog.writeLog("NP return:" + text);
            Row ret = Wire.parseRow(text);
            if (ret != null && ret.i("Statue") == 1) {
                ds.put("Deal", 1);
                SW.db().update("DealStart", ds);
            }
            return true;
        } catch (RuntimeException e) {
            BaseFunction.BaseLog.writeLog("NP Error:" + e.getMessage());
            return false;
        }
    }

    static List<Row> none() {
        return new ArrayList<>();
    }
}
