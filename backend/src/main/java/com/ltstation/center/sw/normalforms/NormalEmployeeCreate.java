package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** NormalEmployeeCreate : liste des employés avec identifiant et mot de passe (ajout, modification). */
@SwWindow("NormalForm.NormalEmployeeCreate")
public class NormalEmployeeCreate extends SwForm {

    public void NormalEmployeeCreate_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("EmpView").RowsClear();
            for (Row item : db().list("Employees", "ORDER BY {EmployeeID}", null)) {
                g("EmpView").RowsAdd(item.get("EmployeeID"), item.str("Name"), item.str("Memo1"), item.str("Memo2"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        ShowDialog("NormalForm.NormalEmployeeAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    public void EmpView_CellDoubleClick() {
        try {
            int code = Conv.toInt(g("EmpView").Cell(g("EmpView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.NormalEmployeeUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "Code", code);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
