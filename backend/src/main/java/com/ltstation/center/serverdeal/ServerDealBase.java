package com.ltstation.center.serverdeal;

import com.ltstation.center.crypto.LegacyAes;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base des contrôleurs /ServerDeal/* (ServerDealController de Center d'origine), appelés par
 * les logiciels Station, CardMake et les terminaux. Requête : {"Result":true,"Mess":hex(AES(json))} ;
 * réponse : {"Result":bool,"Mess":hex(AES(...)),"TotalCount":n} (ServerReturnMessage).
 */
public abstract class ServerDealBase {

    protected static final String TEXT = "text/html;charset=utf-8";

    protected final Sugar db;
    protected final CenterDeal deal;
    protected final LegacyLog log = new LegacyLog("ServerDeal");

    protected ServerDealBase(Sugar db, CenterDeal deal) {
        this.db = db;
        this.deal = deal;
    }

    /** Exception métier (throw new Exception("...") de l'original). */
    public static class Fail extends RuntimeException {
        public Fail(String message) {
            super(message);
        }
    }

    protected static Fail fail(String message) {
        return new Fail(message);
    }

    /** GetRequest() puis DecryptByAES : texte JSON envoyé par la station. */
    protected static String reqText(String body) {
        Row r = Wire.parseRow(body);
        if (r == null) {
            throw new Fail("Requête vide");
        }
        return LegacyAes.decrypt(r.s("Mess"));
    }

    protected static Row req(String body) {
        Row r = Wire.parseRow(reqText(body));
        return r == null ? new Row() : r;
    }

    protected static List<Row> reqList(String body) {
        return Wire.parseRows(reqText(body));
    }

    protected static String enc(String s) {
        return LegacyAes.encrypt(s == null ? "" : s);
    }

    protected static String ret(boolean result, String mess, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Result", result);
        m.put("Mess", mess);
        m.put("TotalCount", total);
        return Wire.json(m);
    }

    /** Result=true, Mess=EncryptByAes(json.Serialize(obj)). */
    protected static String okJson(Object obj) {
        return ret(true, enc(Wire.json(obj)), 0);
    }

    protected static String okJson(Object obj, int total) {
        return ret(true, enc(Wire.json(obj)), total);
    }

    /** Result=true, Mess=EncryptByAes(texte). */
    protected static String okText(String text) {
        return ret(true, enc(text), 0);
    }

    /** Result=true, Mess="". */
    protected static String okEmpty() {
        return ret(true, "", 0);
    }

    /** ReturnError(ex) : Result=false, Mess=EncryptByAes(ex.Message). */
    protected String err(Exception ex) {
        String m = ex.getMessage();
        if (!(ex instanceof Fail)) {
            log.writeLog("Error:" + ex);
        }
        return ret(false, enc(m == null ? ex.getClass().getSimpleName() : m), 0);
    }

    /** Liste d'entités sérialisée comme la classe Modal.&lt;Classe&gt; d'origine (toutes ses propriétés). */
    protected List<Row> ent(String cls, List<Row> rows) {
        String full = "Modal." + cls;
        return deal.dto().has(full) ? deal.dto().shapeAll(full, rows) : rows;
    }

    protected Row ent(String cls, Row row) {
        String full = "Modal." + cls;
        return row == null || !deal.dto().has(full) ? row : deal.dto().shape(full, row);
    }

    /** Station désignée par son code ; sinon « requête illégale » comme l'original. */
    protected Row stationByCode(String code) {
        Row st = db.first("Stations", "{Code} = :c", p("c", code));
        if (st == null) {
            throw fail("Requête illégale : la station n'existe pas");
        }
        return st;
    }

    protected static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }
}
