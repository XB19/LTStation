package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.normal.NormalEngine;
import java.util.ArrayList;
import java.util.List;

/** TankSet : cuves (ajout, modification, suppression signalée à Center) et port série de la sonde (NormalTankSet). */
@SwWindow(value = "NormalForm.TankSet", setup = true)
public class TankSet extends SwForm {

    public void TankSet_Load() {
        ShowTankList();
        List<String> list = new ArrayList<>(List.of(SerialClass.serialsIsConnected()));
        for (Row item : db().list("NormalPortSet")) {
            list.remove(item.str("PortID"));
        }
        for (String item : list) {
            c("ComSelect").ItemsAdd(item);
        }
        c("TankImportHandBtn").Visible(IniInfo.SupervisionType == 4);
    }

    private void ShowTankList() {
        g("TankView").RowsClear();
        for (Row item : db().list("NormalTank", "{IsUsing} = true ORDER BY {ID}", null)) {
            Row grade = db().first("NormalGrade", "{ID} = :g", p("g", item.get("GradeID")));
            g("TankView").RowsAdd(item.get("ID"), item.str("TankName"), item.str("TankNo"), grade == null ? "" : grade.str("GradeName"),
                item.get("TankHeight"), item.get("TankVolume"), item.get("OverHeight"), item.get("LowerHeight"), item.get("OverWaterHeight"));
        }
    }

    public void TankAdd_Click() {
        ShowDialog("NormalForm.TankAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowTankList();
                if (IniInfo.SupervisionType == 4) {
                    com.ltstation.center.sw.normal.BoxBase.kenyaDeal.selectItemList(new String[1]);
                }
            }
        });
    }

    private int currentId() {
        return Conv.toInt(g("TankView").Cell(g("TankView").CurrentRowIndex(), 0));
    }

    public void TankDelete_Click() {
        try {
            int id = currentId();
            Row tank = db().first("NormalTank", "{ID} = :i AND {IsUsing} = true", p("i", id));
            if (tank == null) {
                throw new IllegalStateException(L("TankMissing"));
            }
            if (db().any("NormalNoz", "{TankID} = :t", p("t", id))) {
                throw new IllegalStateException(L("IsUsing"));
            }
            if (Confirm(L("DeleteConfirm"))) {
                BaseFunction.post("ServerDeal/NormalTankUpdate", SW.dto().make("StationTankMess", "StationID", IniInfo.StationID, "CompanyID",
                    IniInfo.CompanyID, "UpdateType", "Delete", "OTankNo", Conv.toInt(tank.get("TankNo"))));
                db().deleteEntity("NormalTank", tank);
                NormalEngine.UpdateNozMess(0, "0", "0", 10);
                ShowTankList();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ComSave_Click() {
        String text = c("ComSelect").Text();
        if (!text.isEmpty()) {
            Row set = db().first("NormalTankSet");
            if (set != null) {
                set.put("PortID", text);
                db().update("NormalTankSet", set);
            } else {
                db().insert("NormalTankSet", Row.of("PortID", text));
            }
            MessageBox(L("UpdateSuccess"));
        }
    }

    public void TankView_CellDoubleClick_1() {
        try {
            int id = currentId();
            ShowDialog("NormalForm.TankUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowTankList();
                }
            }, "ID", id);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void TankImportHandBtn_Click() {
        try {
            ShowDialog("TankConfig.TankHandImport", r -> { }, "ID", currentId());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
