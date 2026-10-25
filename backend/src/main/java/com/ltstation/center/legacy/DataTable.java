package com.ltstation.center.legacy;

import java.util.ArrayList;
import java.util.List;

/** System.Data.DataTable : colonnes nommées dans l'ordre et lignes de valeurs (rapports, exports). */
public class DataTable {

    public final List<String> Columns = new ArrayList<>();
    public final List<Object[]> Rows = new ArrayList<>();

    public DataTable addColumn(String name) {
        Columns.add(name);
        return this;
    }

    /** Rows.Add(v1, v2…) dans l'ordre des colonnes. */
    public DataTable addRow(Object... values) {
        Object[] r = new Object[Columns.size()];
        System.arraycopy(values, 0, r, 0, Math.min(values.length, r.length));
        Rows.add(r);
        return this;
    }

    public Object get(int row, int col) {
        return Rows.get(row)[col];
    }

    public Object get(int row, String col) {
        return Rows.get(row)[Columns.indexOf(col)];
    }

    /** dataRow[col].ToString() : chaîne vide pour DBNull. */
    public String str(int row, int col) {
        Object v = get(row, col);
        return v == null ? "" : Conv.toStr(v);
    }
}
