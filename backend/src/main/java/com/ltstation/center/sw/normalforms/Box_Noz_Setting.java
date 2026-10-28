package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** Box_Noz_Setting : pistolets d'un port de boîtier (arguments ComPortID, Box_Port). */
@SwWindow("NormalForm.Box_Noz_Setting")
public class Box_Noz_Setting extends SwForm {

    public void Box_Noz_Setting_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("BoxPortView").RowsClear();
            for (Row item : db().list("NormalNoz", "{PortID} = :p AND {Box_Port_ID} = :b", p("p", argInt("ComPortID"), "b", argInt("Box_Port")))) {
                Row tank = db().first("NormalTank", "{ID} = :t", p("t", item.get("TankID")));
                if (tank == null) {
                    continue;
                }
                Row grade = db().first("NormalGrade", "{ID} = :g", p("g", item.get("GradeID")));
                if (grade != null) {
                    g("BoxPortView").RowsAdd(item.get("ID"), item.get("PortID"), item.get("Box_Port_ID"), item.get("NozID"), tank.str("TankName"),
                        grade.str("GradeName"), item.get("Price"));
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BoxPortView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("BoxPortView").Cell(g("BoxPortView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.Box_Noz_Update", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        ShowDialog("NormalForm.Box_Noz_Add", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        }, "CPort", argInt("ComPortID"), "BPort", argInt("Box_Port"));
    }
}
