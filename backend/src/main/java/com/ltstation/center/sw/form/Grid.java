package com.ltstation.center.sw.form;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DataGridView côté serveur : lignes liées (DataSource, colonnes lues par DataPropertyName) ou
 * ajoutées une à une (Rows.Add, valeurs dans l'ordre des colonnes), ligne courante choisie par
 * le client, colonnes masquées et en-têtes modifiés.
 */
public class Grid {

    private final String name;
    private final List<Layouts.Column> columns;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Boolean> visible = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    /** Colonnes à liste déroulante (DataGridViewComboBoxCell) : valeur → libellé. */
    private final Map<String, List<Map<String, Object>>> options = new LinkedHashMap<>();
    private static final String FORE = "__fore";
    private int current = -1;
    private int currentColumn = -1;
    private boolean dirty;

    Grid(String name, List<Layouts.Column> columns) {
        this.name = name;
        this.columns = columns == null ? List.of() : columns;
    }

    public String name() {
        return name;
    }

    public List<Layouts.Column> columns() {
        return columns;
    }

    boolean dirty() {
        return dirty;
    }

    void clean() {
        dirty = false;
    }

    void setCurrent(int i) {
        current = i;
    }

    void setCurrentColumn(int c) {
        currentColumn = c;
    }

    /** CurrentCell.ColumnIndex (-1 si aucune). */
    public int CurrentCellColumnIndex() {
        return currentColumn;
    }

    /** Valeur saisie dans une cellule (CellEndEdit) : sans marquer le tableau comme modifié. */
    void fromClient(int row, String col, Object value) {
        Row r = Row(row);
        if (r != null) {
            r.put(key(col), value);
        }
    }

    /** DataSource = liste ; null vide le tableau. */
    public Grid DataSource(List<? extends Map<String, ?>> list) {
        rows.clear();
        if (list != null) {
            for (Map<String, ?> m : list) {
                rows.add(m instanceof Row r ? r : new Row(m));
            }
        }
        current = rows.isEmpty() ? -1 : 0;
        dirty = true;
        return this;
    }

    /** Rows.Clear() */
    public Grid RowsClear() {
        return DataSource(null);
    }

    /** Rows.Add(v1, v2…) : valeurs dans l'ordre des colonnes. */
    public int RowsAdd(Object... values) {
        Row r = new Row();
        for (int i = 0; i < values.length && i < columns.size(); i++) {
            Layouts.Column c = columns.get(i);
            r.put(c.prop() != null ? c.prop() : c.name(), values[i]);
        }
        rows.add(r);
        dirty = true;
        return rows.size() - 1;
    }

    /** Couleur du texte de toutes les cellules d'une ligne (Cell.Style.ForeColor) : nom CSS. */
    public Grid RowForeColor(int i, String color) {
        Row r = Row(i);
        if (r != null) {
            r.put(FORE, color);
            dirty = true;
        }
        return this;
    }

    public int RowCount() {
        return rows.size();
    }

    public List<Row> Rows() {
        return rows;
    }

    /** Ligne liée (DataBoundItem). */
    public Row Row(int i) {
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    private String key(String col) {
        for (Layouts.Column c : columns) {
            if (c.name().equalsIgnoreCase(col)) {
                return c.prop() != null ? c.prop() : c.name();
            }
        }
        return col;
    }

    /** Rows[i].Cells["col"].Value */
    public Object Cell(int i, String col) {
        Row r = Row(i);
        return r == null ? null : r.get(key(col));
    }

    /** Rows[i].Cells[index].Value */
    public Object Cell(int i, int colIndex) {
        return colIndex >= 0 && colIndex < columns.size() ? Cell(i, columns.get(colIndex).name()) : null;
    }

    public Grid Cell(int i, String col, Object v) {
        Row r = Row(i);
        if (r != null) {
            r.put(key(col), v);
            dirty = true;
        }
        return this;
    }

    /** CurrentRow.Index (-1 si aucune). */
    public int CurrentRowIndex() {
        return current >= 0 && current < rows.size() ? current : -1;
    }

    public Row CurrentRow() {
        return Row(CurrentRowIndex());
    }

    public Object CurrentCell(String col) {
        return Cell(CurrentRowIndex(), col);
    }

    /** Cellules à liste déroulante d'une colonne : DataSource, DisplayMember, ValueMember. */
    public Grid ComboColumn(String col, List<? extends Map<String, ?>> source, String display, String value) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Map<String, ?> r : source) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("value", Conv.toStr(r.get(value)));
            o.put("label", Conv.toStr(r.get(display)));
            l.add(o);
        }
        options.put(col, l);
        dirty = true;
        return this;
    }

    public Grid ColumnVisible(String col, boolean v) {
        visible.put(col, v);
        dirty = true;
        return this;
    }

    public Grid HeaderText(String col, String text) {
        headers.put(col, text);
        dirty = true;
        return this;
    }

    private static Object display(Object v) {
        if (v instanceof LocalDateTime dt) {
            return dt.format(Conv.STD);
        }
        if (v instanceof Double || v instanceof Float) {
            return Conv.num(((Number) v).doubleValue());
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        return v;
    }

    /** Lignes envoyées au client : une valeur par nom de colonne d'origine. */
    Map<String, Object> toClient() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("__i", i);
            if (r.get(FORE) != null) {
                m.put(FORE, r.get(FORE));
            }
            if (columns.isEmpty()) {
                r.forEach((k, v) -> {
                    if (!FORE.equals(k)) {
                        m.put(k, display(v));
                    }
                });
            }
            for (Layouts.Column c : columns) {
                m.put(c.name(), display(r.get(c.prop() != null ? c.prop() : c.name())));
            }
            out.add(m);
        }
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("rows", out);
        g.put("current", CurrentRowIndex());
        if (!visible.isEmpty()) {
            g.put("visible", visible);
        }
        if (!headers.isEmpty()) {
            g.put("headers", headers);
        }
        if (!options.isEmpty()) {
            g.put("options", options);
        }
        return g;
    }
}
