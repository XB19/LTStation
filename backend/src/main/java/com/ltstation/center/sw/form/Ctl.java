package com.ltstation.center.sw.form;

import com.ltstation.center.legacy.Conv;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôle WinForms côté serveur (TextBox, ComboBox, CheckBox, DateTimePicker, Label, Button…) :
 * mêmes propriétés que l'original (Text, Checked, Enabled, Visible, ReadOnly, Items,
 * SelectedIndex, SelectedValue, Value). Seules les propriétés modifiées sont renvoyées au client.
 */
public class Ctl {

    private final String name;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final List<Object> values = new ArrayList<>();
    private final List<Map<String, ?>> bound = new ArrayList<>();
    private boolean dirty;
    /** Fenêtre propriétaire : événements déclenchés par une modification depuis le code (comme WinForms). */
    SwForm owner;

    Ctl(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    private static final Map<String, String[]> EVENTS = Map.of(
        "Text", new String[] {"TextChanged"},
        "Checked", new String[] {"CheckedChanged", "CheckStateChanged"},
        "SelectedIndex", new String[] {"SelectedIndexChanged", "SelectedValueChanged"},
        "Value", new String[] {"ValueChanged"});

    private Ctl set(String k, Object v) {
        Object old = props.get(k);
        props.put(k, v);
        dirty = true;
        boolean changed = old == null ? v != null : !old.equals(v);
        if (changed && owner != null) {
            if ("Checked".equals(k) && Boolean.TRUE.equals(v)) {
                owner.radioChecked(name);
            }
            String[] ev = EVENTS.get(k);
            if (ev != null) {
                owner.raise(name, ev);
            }
        }
        return this;
    }

    Map<String, Object> props() {
        return props;
    }

    boolean dirty() {
        return dirty;
    }

    void clean() {
        dirty = false;
    }

    /** Valeur envoyée par le client (sans marquer le contrôle comme modifié). */
    void fromClient(String k, Object v) {
        props.put(k, v);
    }

    // ------------------------------------------------------------ Text

    public String Text() {
        Object v = props.get("Text");
        if (v == null && props.containsKey("SelectedIndex")) {
            int i = SelectedIndex();
            List<Object> items = Items();
            return i >= 0 && i < items.size() ? Conv.toStr(items.get(i)) : "";
        }
        return v == null ? "" : Conv.toStr(v);
    }

    public Ctl Text(Object v) {
        return set("Text", v == null ? "" : Conv.toStr(v));
    }

    // ------------------------------------------------------------ états

    public boolean Checked() {
        return Conv.toBool(props.getOrDefault("Checked", false));
    }

    public Ctl Checked(boolean v) {
        return set("Checked", v);
    }

    public boolean Enabled() {
        return Conv.toBool(props.getOrDefault("Enabled", true));
    }

    public Ctl Enabled(boolean v) {
        return set("Enabled", v);
    }

    public boolean Visible() {
        return Conv.toBool(props.getOrDefault("Visible", true));
    }

    public Ctl Visible(boolean v) {
        return set("Visible", v);
    }

    public boolean ReadOnly() {
        return Conv.toBool(props.getOrDefault("ReadOnly", false));
    }

    public Ctl ReadOnly(boolean v) {
        return set("ReadOnly", v);
    }

    public Ctl Focus() {
        return set("Focus", System.nanoTime());
    }

    public Object Tag() {
        return props.get("Tag");
    }

    public Ctl Tag(Object v) {
        props.put("Tag", v);
        return this;
    }

    /** Couleur du texte (ForeColor) : nom CSS. */
    public Ctl ForeColor(String color) {
        return set("ForeColor", color);
    }

    public Ctl BackColor(String color) {
        return set("BackColor", color);
    }

    // ------------------------------------------------------------ listes (ComboBox / ListBox)

    @SuppressWarnings("unchecked")
    public List<Object> Items() {
        Object v = props.get("Items");
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /** Items.Clear() puis Items.AddRange(…). */
    public Ctl Items(List<?> items) {
        values.clear();
        bound.clear();
        set("Items", new ArrayList<>(items == null ? List.of() : items));
        return SelectedIndex(-1);
    }

    public Ctl ItemsAdd(Object item) {
        List<Object> l = new ArrayList<>(Items());
        l.add(item == null ? "" : item);
        return set("Items", l);
    }

    public Ctl ItemsClear() {
        values.clear();
        bound.clear();
        set("Items", new ArrayList<>());
        return SelectedIndex(-1);
    }

    /** DataSource + DisplayMember + ValueMember. */
    public Ctl DataSource(List<? extends Map<String, ?>> rows, String display, String value) {
        List<Object> items = new ArrayList<>();
        values.clear();
        bound.clear();
        bound.addAll(rows);
        for (Map<String, ?> r : rows) {
            items.add(Conv.toStr(r.get(display)));
            values.add(r.get(value));
        }
        set("Items", items);
        return SelectedIndex(items.isEmpty() ? -1 : 0);
    }

    public int SelectedIndex() {
        Object v = props.get("SelectedIndex");
        return v == null ? -1 : Conv.toInt(v);
    }

    public Ctl SelectedIndex(int i) {
        return set("SelectedIndex", i);
    }

    /** SelectedValue (DataSource) ou SelectedItem. */
    public Object SelectedValue() {
        int i = SelectedIndex();
        if (i < 0) {
            return null;
        }
        if (i < values.size()) {
            return values.get(i);
        }
        List<Object> items = Items();
        return i < items.size() ? items.get(i) : null;
    }

    public Ctl SelectedValue(Object v) {
        for (int i = 0; i < values.size(); i++) {
            if (String.valueOf(values.get(i)).equals(String.valueOf(v))) {
                return SelectedIndex(i);
            }
        }
        return this;
    }

    /** SelectedItem d'une liste liée (DataSource) : l'enregistrement choisi. */
    public com.ltstation.center.legacy.Row SelectedRow() {
        int i = SelectedIndex();
        if (i < 0 || i >= bound.size()) {
            return null;
        }
        Map<String, ?> r = bound.get(i);
        return r instanceof com.ltstation.center.legacy.Row row ? row : new com.ltstation.center.legacy.Row(r);
    }

    public Object SelectedItem() {
        int i = SelectedIndex();
        List<Object> items = Items();
        return i >= 0 && i < items.size() ? items.get(i) : null;
    }

    // ------------------------------------------------------------ DateTimePicker / NumericUpDown

    public LocalDateTime Value() {
        Object v = props.get("Value");
        return v == null ? LocalDateTime.now() : Conv.toDateTime(v);
    }

    public Ctl Value(LocalDateTime v) {
        return set("Value", v == null ? null : v.format(Conv.STD));
    }

    public Ctl Minimum(Object v) {
        return set("Minimum", v);
    }

    public Ctl Maximum(Object v) {
        return set("Maximum", v);
    }

    /** Valeur décimale d'un NumericUpDown. */
    public java.math.BigDecimal DecValue() {
        String t = Text();
        return t.isBlank() ? java.math.BigDecimal.ZERO : Conv.toDecimal(t);
    }
}
