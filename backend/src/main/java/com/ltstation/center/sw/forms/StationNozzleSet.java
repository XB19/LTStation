package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** StationNozzleSet(id) : pistolets (port série et cuve de chaque pistolet), modifiables dans le tableau. */
@SwWindow(value = "StationNozzleSet", setup = true)
public class StationNozzleSet extends SwForm {

    private int sID;

    @Override
    protected void init() {
        sID = argInt("ID");
        if (sID == 1) {
            c("NextButton").Text("zh-CN".equals(IniInfo.Language) ? "确定" : "Valider");
        }
        TankLoadData();
        LoadData();
    }

    private List<Row> tanks() {
        String sql = "SELECT {tankConfig.ID} AS ID, {GradeSpecifics.Name} AS Name, {PriceInfos.NewPPU} AS Price FROM [tankConfig]"
            + " JOIN [GradeSpecifics] ON CAST({tankConfig.OilCode} AS varchar) = {GradeSpecifics.Code}"
            + " JOIN [PriceInfos] ON {GradeSpecifics.Code} = {PriceInfos.Code}";
        return db().query(sql, null);
    }

    private void TankLoadData() {
        try {
            g("TankView").RowsClear();
            List<Row> list = tanks();
            if (list.isEmpty()) {
                throw new IllegalStateException(L("LostTankOrPrice"));
            }
            for (Row item : list) {
                g("TankView").RowsAdd(item.get("ID"), item.get("Name"), item.get("Price"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadData() {
        try {
            var grid = g("NozzleConfigView");
            grid.RowsClear();
            List<Row> ports = db().list("ComConfig", "ORDER BY {PORT}", null);
            List<Row> list2 = tanks();
            if (grid.columns().size() > 2) {
                grid.ComboColumn(grid.columns().get(1).name(), ports, "CODE", "CODE");
                grid.ComboColumn(grid.columns().get(2).name(), list2, "Name", "ID");
            }
            if (list2.isEmpty()) {
                throw new IllegalStateException(L("LostTank"));
            }
            for (Row gun : db().list("GunConfig")) {
                boolean portOk = ports.stream().anyMatch(p -> p.i("CODE") == gun.i("PORTCODE"));
                boolean tankOk = list2.stream().anyMatch(t -> t.i("ID") == gun.i("TANKCODE"));
                grid.RowsAdd(gun.get("GUNCODE"), portOk ? gun.get("PORTCODE") : null, tankOk ? gun.get("TANKCODE") : null, "", "", "");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NozzleConfigView_CellEndEdit() {
        var tx = db().begin();
        try {
            int index = g("NozzleConfigView").CurrentRowIndex();
            int col = g("NozzleConfigView").CurrentCellColumnIndex();
            String text = Conv.toStr(g("NozzleConfigView").Cell(index, col));
            int id = Integer.parseInt(Conv.toStr(g("NozzleConfigView").Cell(index, "ID")));
            Row gun = db().first("GunConfig", "{GUNCODE} = :g", p("g", id));
            if (text.matches("^[0-9]*$")) {
                switch (col) {
                    case 0 -> gun.put("GUNCODE", Conv.toInt(text));
                    case 1 -> gun.put("PORTCODE", Conv.toInt(text));
                    case 2 -> gun.put("TANKCODE", Conv.toInt(text));
                    default -> {
                    }
                }
                db().update("GunConfig", gun);
                tx.commit();
                return;
            }
            throw new IllegalStateException(L("NumberOrDecimal"));
        } catch (RuntimeException e) {
            tx.rollback();
            LoadData();
        }
    }

    public void Add_Click() {
        var tx = db().begin();
        try {
            int code = 1;
            List<Row> guns = db().list("GunConfig", "ORDER BY {GUNCODE} DESC", null);
            List<Row> ports = db().list("ComConfig");
            if (ports.isEmpty()) {
                throw new IllegalStateException(L("PlsSetSerial"));
            }
            List<Row> tanks = db().list("tankConfig");
            if (tanks.isEmpty()) {
                throw new IllegalStateException(L("PlsSetTank"));
            }
            if (!guns.isEmpty()) {
                code = guns.get(0).i("GUNCODE") + 1;
            }
            db().insert("GunConfig", Row.of("GUNCODE", code, "TANKCODE", tanks.get(0).get("ID"), "PORTCODE", ports.get(0).get("CODE")));
            tx.commit();
            LoadData();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void Delete_Click() {
        var tx = db().begin();
        try {
            int index = g("NozzleConfigView").CurrentRowIndex();
            int id = Integer.parseInt(Conv.toStr(g("NozzleConfigView").Cell(index, "ID")));
            Row gun = db().first("GunConfig", "{GUNCODE} = :g", p("g", id));
            if (gun != null) {
                db().deleteEntity("GunConfig", gun);
            }
            tx.commit();
            LoadData();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    public void NextButton_Click() {
        try {
            if ("NextButton".equals(sender) && sID == 0) {
                ShowDialog("CreateEmployee", r -> Close(r.result()), "ID", 1);
                return;
            }
            BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpDateVersion", Wire.json(SW.dto().make("CustomerParam", "Code", IniInfo.Code)));
            Close();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
