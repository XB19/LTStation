package com.ltstation.center.legacy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enregistrement au format des modèles C# d'origine (noms de propriétés d'origine, ex. "ASN",
 * "CustomerID"). La recherche des clés ignore la casse, comme le désérialiseur
 * JavaScriptSerializer ; l'ordre et la casse d'écriture sont conservés pour le JSON.
 */
public class Row extends LinkedHashMap<String, Object> {

    private final Map<String, String> index = new HashMap<>();

    public Row() {
    }

    public Row(Map<String, ?> src) {
        if (src != null) {
            src.forEach(this::put);
        }
    }

    /** Construction rapide : Row.of("Code", code, "StationID", 1). */
    public static Row of(Object... kv) {
        Row r = new Row();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    private String actual(Object key) {
        return key == null ? null : index.get(key.toString().toLowerCase(Locale.ROOT));
    }

    @Override
    public Object put(String key, Object value) {
        String a = actual(key);
        if (a != null) {
            return super.put(a, value);
        }
        index.put(key.toLowerCase(Locale.ROOT), key);
        return super.put(key, value);
    }

    @Override
    public Object get(Object key) {
        String a = actual(key);
        return a == null ? null : super.get(a);
    }

    @Override
    public boolean containsKey(Object key) {
        return actual(key) != null;
    }

    @Override
    public Object remove(Object key) {
        String a = actual(key);
        if (a == null) {
            return null;
        }
        index.remove(a.toLowerCase(Locale.ROOT));
        return super.remove(a);
    }

    @Override
    public void clear() {
        index.clear();
        super.clear();
    }

    /** Affectation chaînable (équivalent des initialiseurs d'objet C#). */
    public Row set(String key, Object value) {
        put(key, value);
        return this;
    }

    public Row copy() {
        return new Row(this);
    }

    public String str(String k) {
        return Conv.toStrOrNull(get(k));
    }

    /** Convert.ToString : null devient "". */
    public String s(String k) {
        return Conv.toStr(get(k));
    }

    public int i(String k) {
        return Conv.toInt(get(k));
    }

    public Integer iN(String k) {
        Object v = get(k);
        return v == null ? null : Conv.toInt(v);
    }

    public long l(String k) {
        return Conv.toLong(get(k));
    }

    public double d(String k) {
        return Conv.toDouble(get(k));
    }

    public BigDecimal dec(String k) {
        return Conv.toDecimal(get(k));
    }

    public boolean b(String k) {
        return Conv.toBool(get(k));
    }

    public LocalDateTime dt(String k) {
        return Conv.toDateTime(get(k));
    }

    public Row row(String k) {
        return Conv.toRow(get(k));
    }

    public List<Row> rows(String k) {
        return Conv.toRows(get(k));
    }
}
