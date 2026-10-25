package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** ShiftItemForm : postes de travail (nom, heure de début / de fin, intervalle), modifiables dans le tableau. */
@SwWindow("ShiftItemForm")
public class ShiftItemForm extends SwForm {

    public void ShiftItemForm_Load() {
        Reload();
    }

    private void Reload() {
        ShowData(db().list("ShiftConfig", "ORDER BY {ID}", null));
    }

    private void ShowData(List<Row> list) {
        try {
            g("ShiftItemView").RowsClear();
            for (Row item : list) {
                g("ShiftItemView").RowsAdd(item.get("ID"), item.get("Name"), item.get("StartTime"), item.get("EndTime"), item.get("Scope"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private Integer maxId() {
        return db().scalar(db().sql("ShiftConfig", "SELECT MAX({ID}) FROM [ShiftConfig]"), null, Integer.class);
    }

    public void AddShift_Click() {
        var tx = db().begin();
        try {
            Integer max = maxId();
            if (max == null) {
                throw new IllegalStateException("Sequence contains no elements");
            }
            int num = max + 1;
            db().insert("ShiftConfig", Row.of("ID", num, "Name", String.valueOf(num), "StartTime", 8, "EndTime", 8, "Scope", 30));
            tx.commit();
            Reload();
        } catch (RuntimeException e) {
            tx.rollback();
        }
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void ShiftItemView_CellEndEdit() {
        var tx = db().begin();
        try {
            int index = g("ShiftItemView").CurrentRowIndex();
            int col = g("ShiftItemView").CurrentCellColumnIndex();
            String text = Conv.toStr(g("ShiftItemView").Cell(index, col));
            int id = Integer.parseInt(Conv.toStr(g("ShiftItemView").Cell(index, "ID")));
            Row shift = db().first("ShiftConfig", "{ID} = :id", p("id", id));
            switch (col) {
                case 1 -> {
                    Integer v = tryInt(text);
                    if (v == null) {
                        throw new IllegalStateException("duty Name Should be Number");
                    }
                    if (v <= 0 || v >= 100) {
                        throw new IllegalStateException("duty Name Number Error");
                    }
                    shift.put("Name", text);
                }
                case 2, 3 -> {
                    Integer v = tryInt(text);
                    if (v == null || v > 24 || v < 0) {
                        throw new IllegalStateException(L("TimeError"));
                    }
                    shift.put(col == 2 ? "StartTime" : "EndTime", v);
                }
                case 4 -> {
                    Integer v = tryInt(text);
                    if (v != null) {
                        shift.put("Scope", v);
                    }
                }
                default -> {
                }
            }
            db().update("ShiftConfig", shift);
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
        Reload();
    }

    public void Delete_Click() {
        try {
            Integer num = maxId();
            if (num == null) {
                throw new IllegalStateException("Sequence contains no elements");
            }
            if (num == 0) {
                throw new IllegalStateException(L("NoShift"));
            }
            db().delete("ShiftConfig", "{ID} = :id", p("id", num));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
        Reload();
    }
}
