package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.FrxReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NormalModel.KenyaDeal : eTIMS (KRA, Kenya). Les identifiants (tin, bhfId, cmcKey) viennent de KenyaInitData et
 * l'adresse du service de Supervision (type 4) ; les listes de référence sont copiées dans les tables Kenya*.
 */
public class KenyaDeal {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public String tin;
    public String bhfId;
    public String dvcSrlNo;
    public String BaseUrl;
    public String dvcId;
    private String cmcKey;

    private static Sugar db() {
        return SW.db();
    }

    private static String error(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** InitData : identifiants enregistrés, puis mise à jour des codes, classes d'articles et articles. */
    public boolean InitData(String[] Error) {
        Error[0] = "";
        try {
            Row init = db().first("KenyaInitData");
            if (init != null) {
                tin = init.str("tin");
                bhfId = init.str("bhfId");
                cmcKey = init.str("cmcKey");
            }
            Row sup = db().first("Supervision", "{SupervisionType} = 4", null);
            if (sup != null) {
                dvcSrlNo = sup.str("dvcSrlNo");
                BaseUrl = sup.str("BaseUrl");
            }
            selectCodeList(Error);
            selectItemClsList(Error);
            selectItemList(Error);
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    /** EtimsHttpPost : POST JSON vers BaseUrl + action ; renvoie la réponse, ou null (message dans out[0]). */
    private String EtimsHttpPost(String url, Object content, Map<String, String> headers, String[] out) {
        try {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("Adresse de requête vide");
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BaseUrl + url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(content instanceof String s ? s : Wire.json(content), StandardCharsets.UTF_8));
            if (headers != null) {
                headers.forEach((k, v) -> b.header(k, v == null ? "" : v));
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) {
                throw new IllegalStateException("Response status code does not indicate success: " + r.statusCode());
            }
            return r.body();
        } catch (Exception e) {
            out[0] = error(e);
            return null;
        }
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("tin", tin);
        h.put("bhfId", bhfId);
        h.put("cmcKey", cmcKey);
        return h;
    }

    private Row codeRequest(String lastReqDt) {
        return SW.dto().make("KenyaCodeRequest", "tin", tin, "bhfId", bhfId, "lastReqDt", lastReqDt);
    }

    private String maxResultDt(String table) {
        return db().scalar(db().sql(table, "select MAX({resultDt}) from [" + table + "]"), null, String.class);
    }

    /** Envoi simple : erreur si resultCd ≠ 000 (sans réponse HTTP : succès, comme l'original). */
    private boolean send(String action, Object body, String[] Error) {
        Error[0] = "";
        try {
            String[] out = {""};
            String ret = EtimsHttpPost(action, body, headers(), out);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if (!"000".equals(r.str("resultCd"))) {
                    throw new IllegalStateException(r.str("resultMsg"));
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    /** Initialisation : informations de l'appareil (selectInitOsdcInfo) enregistrées dans KenyaInitData. */
    public boolean Initialisation(String[] Error) {
        Error[0] = "";
        try {
            String[] out = {""};
            String ret = EtimsHttpPost("selectInitOsdcInfo", SW.dto().make("KenyaInitDataRequest", "tin", tin, "bhfId", bhfId, "dvcSrlNo", dvcSrlNo), null,
                out);
            if (ret == null) {
                throw new IllegalStateException("Request Error");
            }
            Row root = Wire.parseRow(ret);
            if ("000".equals(root.str("resultCd"))) {
                Row info = root.row("data").row("info");
                cmcKey = info.str("cmcKey");
                dvcId = info.str("dvcId");
                db().delete("KenyaInitData", null, null);
                db().insert("KenyaInitData", info);
                return true;
            }
            if ("902".equals(root.str("resultCd"))) {
                if (!InitData(Error)) {
                    throw new IllegalStateException("Échec de l'initialisation");
                }
                return true;
            }
            throw new IllegalStateException(root.str("resultMsg"));
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean selectCodeList(String[] Error) {
        Error[0] = "";
        try {
            String last = maxResultDt("KenyaclsList");
            String ret = EtimsHttpPost("selectCodeList", codeRequest(last != null ? last : "20230101000000"), headers(), new String[1]);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if ("000".equals(r.str("resultCd")) && r.get("data") != null) {
                    for (Row cls : r.row("data").rows("clsList")) {
                        Row c = new Row(cls);
                        c.put("resultDt", r.get("resultDt"));
                        int id = Conv.toInt(db().insert("KenyaclsList", c));
                        if (cls.get("dtlList") == null) {
                            continue;
                        }
                        for (Row dtl : cls.rows("dtlList")) {
                            Row d = new Row(dtl);
                            d.put("KenyaclsListID", id);
                            db().insert("KenyadtlList", d);
                        }
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean selectItemClsList(String[] Error) {
        Error[0] = "";
        try {
            String last = maxResultDt("KenyaselectItemCls");
            String ret = EtimsHttpPost("selectItemClsList", codeRequest(last != null ? last : "20180523000000"), headers(), new String[1]);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if ("000".equals(r.str("resultCd")) && r.get("data") != null) {
                    for (Row item : r.row("data").rows("itemClsList")) {
                        Row c = new Row(item);
                        c.put("resultDt", r.get("resultDt"));
                        db().insert("KenyaselectItemCls", c);
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean selectBhfList(String[] Error) {
        Error[0] = "";
        try {
            String last = maxResultDt("KenyaSelectBhf");
            String ret = EtimsHttpPost("selectBhfList", codeRequest(last != null ? last : "20180520000000"), headers(), new String[1]);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if ("000".equals(r.str("resultCd")) && r.get("data") != null) {
                    for (Row bhf : r.row("data").rows("bhfList")) {
                        Row c = new Row(bhf);
                        c.put("resultDt", r.get("resultDt"));
                        db().insert("KenyaSelectBhf", c);
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean saveBhfCustomer(Object kenyaCustomer, String[] Error) {
        return send("saveBhfCustomer", kenyaCustomer, Error);
    }

    public boolean saveBhfUser(Object kenyaBhf, String[] Error) {
        return send("saveBhfUser", kenyaBhf, Error);
    }

    public boolean saveBhfInsurance(Object insurance, String[] Error) {
        return send("saveBhfInsurance", insurance, Error);
    }

    public boolean selectImportItemList(String[] Error) {
        Error[0] = "";
        try {
            String ret = EtimsHttpPost("selectImportItemList", codeRequest("20180524000000"), headers(), new String[1]);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if (!"000".equals(r.str("resultCd"))) {
                    throw new IllegalStateException(r.str("resultMsg"));
                }
                if (r.get("data") != null) {
                    for (Row item : r.row("data").rows("itemList")) {
                        Row c = new Row(item);
                        c.put("resultDt", r.get("resultDt"));
                        db().insert("KenyaImportItemList", c);
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean updateImportItem(Object updateImportItem, String[] Error) {
        return send("updateImportItem", updateImportItem, Error);
    }

    /** saveItem : sans réponse HTTP, échec (contrairement aux autres envois). */
    public boolean saveItem(Object saveitem, String[] Error) {
        Error[0] = "";
        try {
            String[] out = {""};
            String ret = EtimsHttpPost("saveItem", saveitem, headers(), out);
            if (ret == null) {
                return false;
            }
            Row r = Wire.parseRow(ret);
            if (!"000".equals(r.str("resultCd"))) {
                throw new IllegalStateException(r.str("resultMsg"));
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    /** selectItemList : articles ajoutés ou mis à jour (par itemCd). */
    public boolean selectItemList(String[] Error) {
        Error[0] = "";
        try {
            String last = maxResultDt("KenyaSelectItemList");
            String ret = EtimsHttpPost("selectItemList", codeRequest(last != null && !last.isEmpty() ? last : "20160523000000"), headers(), new String[1]);
            if (ret == null) {
                throw new IllegalStateException("Error");
            }
            Row r = Wire.parseRow(ret);
            if (!"000".equals(r.str("resultCd"))) {
                throw new IllegalStateException(r.str("resultMsg"));
            }
            if (r.get("data") != null) {
                for (Row item : r.row("data").rows("itemList")) {
                    Row existing = db().first("KenyaSelectItemList", "{itemCd} = :c", p("c", item.str("itemCd")));
                    Row c = existing != null ? existing : new Row();
                    c.putAll(item);
                    c.put("resultDt", r.get("resultDt"));
                    if (existing != null) {
                        c.put("ID", existing.get("ID"));
                        db().update("KenyaSelectItemList", c);
                    } else {
                        db().insert("KenyaSelectItemList", c);
                    }
                }
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean saveItemComposition(Object composition, String[] Error) {
        return send("saveItemComposition", composition, Error);
    }

    public boolean selectTrnsPurchaseSalesList(String[] Error) {
        return check("selectTrnsPurchaseSalesList", "20190524000000", "Http Error", Error);
    }

    public boolean selectStockMoveList(String[] Error) {
        return check("selectStockMoveList", "20180524000000", "HTTP Error", Error);
    }

    private boolean check(String action, String lastReqDt, String httpError, String[] Error) {
        Error[0] = "";
        try {
            String ret = EtimsHttpPost(action, codeRequest(lastReqDt), headers(), new String[1]);
            if (ret == null) {
                throw new IllegalStateException(httpError);
            }
            Row r = Wire.parseRow(ret);
            if (!"000".equals(r.str("resultCd"))) {
                throw new IllegalStateException(r.str("resultMsg"));
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    /** insertTrnsPurchase : comme l'original, renvoie vrai même en cas d'erreur (message dans Error). */
    public boolean insertTrnsPurchase(Object purchase, String[] Error) {
        send("insertTrnsPurchase", purchase, Error);
        return true;
    }

    /** saveTrnsSalesOsdc : vente déclarée ; les données du reçu (signature, compteur) sont enregistrées dans KenyaOilPrint. */
    public boolean saveTrnsSalesOsdc(Row sales, Row[] print, String[] Error) {
        Error[0] = "";
        print[0] = null;
        try {
            Map<String, String> h = headers();
            h.put("dvcId", dvcId);
            String ret = EtimsHttpPost("saveTrnsSalesOsdc", sales, h, new String[1]);
            if (ret != null) {
                Row r = Wire.parseRow(ret);
                if (!"000".equals(r.str("resultCd"))) {
                    throw new IllegalStateException(r.str("resultMsg"));
                }
                Row d = r.row("data");
                Row p = Row.of("TransactionID", sales.get("invcNo"), "curRcptNo", d.get("curRcptNo"), "totRcptNo", d.get("totRcptNo"),
                    "intrlData", d.get("intrlData"), "rcptSign", d.get("rcptSign"), "sdcDateTime", d.get("sdcDateTime"), "IsPrint", 0);
                db().insert("KenyaOilPrint", p);
                print[0] = p;
            }
            return true;
        } catch (RuntimeException ex) {
            Error[0] = error(ex);
            return false;
        }
    }

    public boolean insertStockIO(Object stockIO, String[] Error) {
        return send("insertStockIO", stockIO, Error);
    }

    public boolean saveStockMaster(Object stockMaster, String[] Error) {
        return send("saveStockMaster", stockMaster, Error);
    }

    private static BigDecimal r2(BigDecimal d) {
        return Conv.netRound(d, 2);
    }

    private static BigDecimal tax16(BigDecimal amount) {
        return r2(amount.multiply(BigDecimal.valueOf(16)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN).stripTrailingZeros());
    }

    /** KenyaTransactionSend : première transaction non déclarée envoyée à eTIMS, reçu imprimé, sortie de stock déclarée. */
    public void KenyaTransactionSend() {
        try {
            Row t = db().first("NormalTransaction", "{ApiSend} = false", null);
            if (t == null) {
                return;
            }
            LocalDateTime created = BaseFunction.StampToDateTime(t.str("CreateTime"));
            BigDecimal real = t.dec("RealAmount");
            Row sales = SW.dto().make("KenyasaveTrnsSalesOsdcRequest", "trdInvcNo",
                "BLY" + t.str("CreateTime") + BaseFunction.SetStrLen(t.str("NozID"), 2) + IniInfo.Code, "orgInvcNo", 0, "salesTyCd", "N", "rcptTyCd", "S",
                "pmtTyCd", "01", "salesSttsCd", "02", "cfmDt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                "salesDt", created.format(DateTimeFormatter.BASIC_ISO_DATE), "totItemCnt", 1, "taxblAmtB", r2(real), "taxRtB", BigDecimal.valueOf(16),
                "taxAmtB", tax16(real), "regrId", IniInfo.LoginName, "regrNm", IniInfo.LoginName, "modrId", IniInfo.LoginName,
                "modrNm", IniInfo.LoginName, "invcNo", t.i("ID"), "prchrAcptcYn", "N", "receipt",
                SW.dto().make("KenyasaveTrnsSalesOsdcReceipt", "rcptPbctDt", created.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), "prchrAcptcYn", "N"));
            Row tank = db().first("NormalTank", "{ID} = :i", p("i", t.get("TankID")));
            if (tank == null) {
                throw new IllegalStateException("Lost Code");
            }
            Row item = db().first("KenyaSelectItemList", "{itemCd} = :c", p("c", tank.str("KenyaCode")));
            if (item == null) {
                throw new IllegalStateException("Lost ClsCd");
            }
            List<Row> items = new ArrayList<>();
            items.add(SW.dto().make("KenyasaveTrnsSalesOsdcItemListItem", "itemSeq", 1, "itemCd", tank.str("KenyaCode"), "itemClsCd", item.str("itemClsCd"),
                "itemNm", t.str("GradeName"), "qty", r2(t.dec("Volume")), "prc", Conv.netRound(t.dec("Price"), 0), "splyAmt", r2(real), "taxTyCd", "B",
                "taxblAmt", r2(real), "taxAmt", tax16(real), "pkgUnitCd", "NT", "qtyUnitCd", "U"));
            sales.put("itemList", items);
            Row[] print = {null};
            String[] error = {""};
            if (!saveTrnsSalesOsdc(sales, print, error)) {
                return;
            }
            t.put("ApiSend", true);
            db().update("NormalTransaction", t);
            BigDecimal amount = t.dec("Amount");
            if (print[0] != null) {
                BigDecimal total = r2(amount);
                BigDecimal net = r2(amount.divide(new BigDecimal("1.16"), 10, RoundingMode.HALF_EVEN));
                BigDecimal vat = tax16(net);
                String sdc = print[0].str("sdcDateTime");
                KenyaTransactionPrint(IniInfo.StationName, IniInfo.StationAddress, r2(t.dec("Price")).toPlainString(), r2(t.dec("Volume")).toPlainString(),
                    net.toPlainString(), "0", vat.toPlainString(), total.toPlainString(),
                    sdc.substring(0, 4) + "-" + sdc.substring(4, 6) + "-" + sdc.substring(6, 8),
                    sdc.substring(8, 10) + ":" + sdc.substring(10, 12) + ":" + sdc.substring(12), tank.str("KenyaCode"),
                    tank.str("KenyaCode") + "/" + t.i("ID"), print[0].str("intrlData"), print[0].str("rcptSign"));
            }
            int sarNo = Conv.toInt(db().insert("NormalTankImportHand", Row.of("TankID", tank.i("ID"), "Volume", t.get("Volume"), "Price", t.get("Price"),
                "Amount", amount, "CreateTime", BaseFunction.ConvertDateTimeToInt(), "ImportType", 1)));
            BigDecimal taxAmt = tax16(amount);
            BigDecimal totAmt = r2(amount.add(amount.multiply(BigDecimal.valueOf(16)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_EVEN)));
            Row io = SW.dto().make("KenyainsertStockIOResponse", "sarNo", sarNo, "orgSarNo", 0, "regTyCd", "M", "sarTyCd", "11",
                "ocrnDt", LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE), "totItemCnt", 1, "totTaxblAmt", r2(amount), "totTaxAmt", taxAmt,
                "totAmt", totAmt, "regrId", IniInfo.LoginName, "regrNm", IniInfo.LoginName, "modrId", IniInfo.LoginName, "modrNm", IniInfo.LoginName);
            io.put("itemList", List.of(SW.dto().make("KenyainsertStockIOItemListItem", "itemSeq", 1, "itemCd", tank.str("KenyaCode"),
                "itemClsCd", item.str("itemClsCd"), "itemNm", item.str("itemNm"), "pkgUnitCd", item.str("pkgUnitCd"), "pkg", r2(t.dec("Volume")),
                "qtyUnitCd", item.str("qtyUnitCd"), "qty", r2(t.dec("Volume")), "prc", r2(t.dec("Price")), "splyAmt", r2(amount), "taxblAmt", r2(amount),
                "taxTyCd", "B", "taxAmt", taxAmt, "totAmt", totAmt)));
            BoxBase.kenyaDeal.insertStockIO(io, new String[1]);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Reçu eTIMS (KenyaTransaction.frx) avec QR de vérification KRA. */
    public void KenyaTransactionPrint(String StationName, String Address, String Price, String Volume, String Amount, String TotalDiscount,
        String VatValue, String Total, String OilDate, String OilTime, String SCUID, String CUINVOICENO, String InternalData, String Signature) {
        try {
            String url = "https://etims.kra.go.ke/common/link/etims/receipt/indexEtimsReceptData?Data=" + tin + bhfId + Signature;
            new FrxReport().Load("KenyaTransaction.frx").SetParameterValue("StationName", StationName).SetParameterValue("Address", Address)
                .SetParameterValue("Pin", tin).SetParameterValue("Price", Price).SetParameterValue("Volume", Volume).SetParameterValue("Amount", Amount)
                .SetParameterValue("BarImage", url).SetParameterValue("TotalAmount", Amount).SetParameterValue("TotalDiscount", TotalDiscount)
                .SetParameterValue("TotalReal", Amount).SetParameterValue("VatValue", VatValue).SetParameterValue("Total", Total)
                .SetParameterValue("OilDate", OilDate).SetParameterValue("OilTime", OilTime).SetParameterValue("SCUID", SCUID)
                .SetParameterValue("CUINVOICENO", CUINVOICENO).SetParameterValue("InternalData", group4(InternalData))
                .SetParameterValue("Signature", group4(Signature)).Print();
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Regex.Replace(texte, ".{4}", "$0-").TrimEnd('-') */
    private static String group4(String s) {
        return s == null ? "" : s.replaceAll(".{4}", "$0-").replaceAll("-+$", "");
    }
}
