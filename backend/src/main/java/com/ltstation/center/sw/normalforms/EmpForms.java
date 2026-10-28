package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** EmpForms : liste des employés (ajout, modification par double-clic). */
@SwWindow("NormalForm.EmpForms")
public class EmpForms extends SwForm {

    @Override
    protected void init() {
        ShowData();
    }

    public void EmpAdd_Click() {
        ShowDialog("NormalForm.EmpAdd", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        });
    }

    private void ShowData() {
        try {
            g("EmpView").RowsClear();
            for (Row item : db().list("Employees")) {
                g("EmpView").RowsAdd(item.get("EmployeeID"), item.str("Name"), item.str("Telephone"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EmpView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("EmpView").Cell(g("EmpView").CurrentRowIndex(), 0));
            ShowDialog("NormalForm.EmpUpdate", r -> {
                if ("OK".equals(r.result())) {
                    ShowData();
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
