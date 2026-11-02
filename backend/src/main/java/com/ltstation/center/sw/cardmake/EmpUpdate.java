package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** EmpUpdate : employés de CardMake. */
@SwWindow("CardMake.EmpUpdate")
public class EmpUpdate extends CardMakeForm {

    public void EmpUpdate_Load() {
        LoadEmp();
    }

    private void LoadEmp() {
        try {
            g("EmpView").RowsClear();
            for (Row item : sendRows("ExtendGetEmployee", loginData())) {
                g("EmpView").RowsAdd(item.get("EmployeeID"), item.get("StationID"), item.str("Password"), item.str("Name"), item.str("StationName"),
                    item.str("Phone"), item.str("IssueAuth"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddBtn_Click() {
        ShowDialog("CardMake.EmpFormUpdate", r -> {
            if (r.ok()) {
                LoadEmp();
            }
        });
    }

    private String cell(int i, int col) {
        Object v = g("EmpView").Cell(i, col);
        return v == null ? "" : v.toString();
    }

    public void EmpView_CellDoubleClick() {
        try {
            int i = g("EmpView").CurrentRowIndex();
            ShowDialog("CardMake.EmpFormUpdate", r -> {
                if (r.ok()) {
                    LoadEmp();
                }
            }, "EmployeeID", Conv.toInt(g("EmpView").Cell(i, 0)), "StationID", Conv.toInt(g("EmpView").Cell(i, 1)), "Password", cell(i, 2), "Name", cell(i, 3),
                "StationName", cell(i, 4), "Phone", cell(i, 5), "IssueAuth", cell(i, 6));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
