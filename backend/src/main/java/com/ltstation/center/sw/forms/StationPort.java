package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** StationPort(id) : ports série de la station (port, débit, bits de données, d'arrêt, parité), modifiables dans le tableau. */
@SwWindow(value = "StationPort", setup = true)
public class StationPort extends SwForm {

    private int sid;
    private final List<Row> Buadrates = new ArrayList<>();
    private final List<Row> DataBits = new ArrayList<>();
    private final List<Row> StopBits = new ArrayList<>();
    private final List<Row> Parits = new ArrayList<>();

    @Override
    protected void init() {
        sid = argInt("ID");
        LoadPreData();
        LoadData();
    }

    private void LoadPreData() {
        for (int b : new int[] {110, 300, 600, 1200, 2400, 4800, 9600, 14400, 19200, 38400, 56000, 57600, 115200}) {
            Buadrates.add(Row.of("Data", b, "Display", String.valueOf(b)));
        }
        for (int i = 6; i <= 8; i++) {
            DataBits.add(Row.of("Data", i, "Display", String.valueOf(i)));
        }
        for (int j = 0; j <= 2; j++) {
            StopBits.add(Row.of("Data", j, "Display", String.valueOf(j)));
            Parits.add(Row.of("Data", j, "Display", String.valueOf(j)));
        }
    }

    private void LoadData() {
        try {
            var grid = g("ComConfigView");
            grid.RowsClear();
            if (grid.columns().size() >= 6) {
                grid.ComboColumn(grid.columns().get(2).name(), Buadrates, "Display", "Data");
                grid.ComboColumn(grid.columns().get(3).name(), DataBits, "Display", "Data");
                grid.ComboColumn(grid.columns().get(4).name(), StopBits, "Display", "Data");
                grid.ComboColumn(grid.columns().get(5).name(), Parits, "Display", "Data");
            }
            for (Row item : db().list("ComConfig")) {
                grid.RowsAdd(item.get("CODE"), item.get("PORT"), item.get("BAUDRATE"), item.get("DATABIT"), item.get("STOPBIT"), item.get("PARITY"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Delete_Click() {
        var tx = db().begin();
        try {
            int index = g("ComConfigView").CurrentRowIndex();
            int id = Integer.parseInt(Conv.toStr(g("ComConfigView").Cell(index, "ID")));
            Row d = db().first("ComConfig", "{CODE} = :c", p("c", id));
            db().deleteEntity("ComConfig", d);
            tx.commit();
        } catch (RuntimeException e) {
            tx.rollback();
        }
        LoadData();
    }

    public void Add_Click() {
        var tx = db().begin();
        try {
            Row last = db().first("ComConfig", "ORDER BY {CODE} DESC", null);
            int num = last == null ? 1 : last.i("CODE") + 1;
            db().insert("ComConfig", Row.of("CODE", num, "BAUDRATE", 9600, "DATABIT", 8, "STOPBIT", 0, "PARITY", 0, "PORT", num));
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
        LoadData();
    }

    public void ComConfigView_CellEndEdit() {
        var tx = db().begin();
        try {
            int index = g("ComConfigView").CurrentRowIndex();
            int col = g("ComConfigView").CurrentCellColumnIndex();
            String text = Conv.toStr(g("ComConfigView").Cell(index, col));
            int id = Integer.parseInt(Conv.toStr(g("ComConfigView").Cell(index, "ID")));
            Row com = db().first("ComConfig", "{CODE} = :c", p("c", id));
            if (text.matches("^[0-9]*$")) {
                String field = switch (col) {
                    case 1 -> "PORT";
                    case 2 -> "BAUDRATE";
                    case 3 -> "DATABIT";
                    case 4 -> "STOPBIT";
                    case 5 -> "PARITY";
                    default -> null;
                };
                if (field != null) {
                    com.put(field, Conv.toInt(text));
                }
                db().update("ComConfig", com);
            } else {
                MessageBox(L("NumberOrDecimal"));
                LoadData();
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.rollback();
            LoadData();
        }
    }

    public void NextButton_Click() {
        if (sid == 0) {
            ShowDialog("StationNozzleSet", r -> Close(r.result()), "ID", 0);
        } else {
            Close();
        }
    }

    public void StationPort_Load() {
        c("NextButton").Text(L("Confirm"));
    }
}
