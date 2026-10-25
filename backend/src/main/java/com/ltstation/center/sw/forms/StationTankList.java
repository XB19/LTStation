package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** StationTankList(id) : cuves de la station (carburant, dimensions, seuils d'alarme), modifiables dans le tableau. */
@SwWindow(value = "StationTankList", setup = true)
public class StationTankList extends SwForm {

    private int sID;
    private final Row CustomerParam = SW.dto().make("CustomerParam");
    private List<Row> gradeSpecifics;

    @Override
    protected void init() {
        gradeSpecifics = db().list("GradeSpecifics");
        sID = argInt("ID");
        if (sID == 1) {
            c("NextStep").Text(L("Confirm"));
        }
        DowOilType();
        LoadData();
        if (IniInfo.NormalType == 2) {
            for (String col : List.of("HighOil", "LowOil", "HighWater", "Overflow", "LowTemp", "Monitor", "TransportationTime", "AlarmStock")) {
                g("TankConfigView").ColumnVisible(col, false);
            }
        }
    }

    private void DowOilType() {
        Row stations = db().first("Stations");
        if (stations == null) {
            MessageBox(L("PLSCreateStation"));
            return;
        }
        var tx = db().begin();
        try {
            CustomerParam.put("Code", stations.get("Code"));
            CustomerParam.put("Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetOilType", Wire.json(CustomerParam));
            if (r.Result()) {
                db().delete("GradeSpecifics", null, null);
                for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                    db().insert("GradeSpecifics", item);
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.commit();
        }
    }

    private void LoadData() {
        var grid = g("TankConfigView");
        grid.RowsClear();
        if (grid.columns().size() > 1) {
            grid.ComboColumn(grid.columns().get(1).name(), gradeSpecifics, "Name", "Code");
        }
        for (Row t : db().list("tankConfig")) {
            int oil = t.i("OilCode");
            grid.RowsAdd(t.get("ID"), BaseFunction.isExit(gradeSpecifics, oil) ? String.valueOf(oil) : null, t.get("Height"), t.get("Vol"),
                t.get("OilUpper"), t.get("OilLower"), t.get("WaterUpper"), t.get("OilOverFlow"), t.get("LowerTemp"), t.get("MonitorID"),
                t.get("TransportationTime"), t.get("OrderLimit"));
        }
    }

    public void TankConfigView_CellEndEdit() {
        var tx = db().begin();
        try {
            int index = g("TankConfigView").CurrentRowIndex();
            int col = g("TankConfigView").CurrentCellColumnIndex();
            String text = Conv.toStr(g("TankConfigView").Cell(index, col));
            int tankId = Integer.parseInt(Conv.toStr(g("TankConfigView").Cell(index, "TankID")));
            Row tank = db().first("tankConfig", "{ID} = :id", p("id", tankId));
            if (text.matches("^-?\\d+$|^(-?\\d+)(\\.\\d+)?$")) {
                switch (col) {
                    case 1 -> tank.put("OilCode", Conv.toInt(text));
                    case 2 -> tank.put("Height", Conv.toDouble(text));
                    case 3 -> tank.put("Vol", Conv.toDouble(text));
                    case 4 -> tank.put("OilUpper", Conv.toDouble(text));
                    case 5 -> tank.put("OilLower", Conv.toDouble(text));
                    case 6 -> tank.put("WaterUpper", Conv.toDouble(text));
                    case 7 -> tank.put("OilOverFlow", Conv.toDouble(text));
                    case 8 -> tank.put("LowerTemp", Conv.toDouble(text));
                    case 9 -> tank.put("MonitorID", Conv.toInt(text));
                    default -> {
                    }
                }
                db().update("tankConfig", tank);
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

    public void AddTank_Click() {
        var tx = db().begin();
        try {
            Integer max = db().scalar(db().sql("tankConfig", "SELECT MAX({ID}) FROM [tankConfig]"), null, Integer.class);
            if (max == null) {
                throw new IllegalStateException("Sequence contains no elements");
            }
            db().insert("tankConfig", Row.of("ID", max + 1, "OilCode", Conv.toInt(gradeSpecifics.get(0).get("Code")), "Height", 0.0, "Vol", 0.0,
                "OilUpper", 0.0, "OilLower", 0.0, "WaterUpper", 0.0, "LowerTemp", 0.0, "OilOverFlow", 0.0, "MonitorID", 1, "OutLimit", 0.0,
                "OrderLimit", 0.0, "TransportationTime", 0.0));
            tx.commit();
            LoadData();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            tx.rollback();
        }
    }

    public void TankDelete_Click() {
        int index = g("TankConfigView").CurrentRowIndex();
        if (index < 0) {
            LoadData();
            return;
        }
        if (Confirm(L("OperationExecution"))) {
            var tx = db().begin();
            try {
                int tankId = Integer.parseInt(Conv.toStr(g("TankConfigView").Cell(index, "TankID")));
                Row d = db().first("tankConfig", "{ID} = :id", p("id", tankId));
                db().deleteEntity("tankConfig", d);
                tx.commit();
            } catch (RuntimeException e) {
                tx.rollback();
            }
        }
        LoadData();
    }

    public void NextStep_Click() {
        if (sID == 0) {
            ShowDialog("StationPort", r -> Close(r.result()), "ID", 0);
        } else {
            Close();
        }
    }
}
