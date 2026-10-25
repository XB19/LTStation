package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** EmpForm : liste des employés de la station. */
@SwWindow("EmpForm")
public class EmpForm extends SwForm {

    public void EmpForm_Load() {
        ViewShow(db().list("Employees"));
    }

    private void ViewShow(List<Row> list) {
        try {
            g("EmpView").RowsClear();
            for (Row item : list) {
                g("EmpView").RowsAdd(item.get("EmployeeID"), item.get("Name"), item.get("Memo1"), GetType(Conv.toInt(item.get("StationType"))));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String GetType(int id) {
        return switch (id) {
            case 4 -> L("FuelEmployee");
            case 1 -> L("Administrators");
            case 2 -> L("Accounting");
            case 3 -> L("Cashier");
            default -> {
                Row a = db().first("AuthorityMess", "{User_IDMess} = :id", p("id", id));
                yield a != null ? a.str("Name") : "";
            }
        };
    }

    public void EmpView_CellDoubleClick() {
        ShowDialog("EmpUpdate", r -> {
            if (r.ok()) {
                ViewShow(db().list("Employees"));
            }
        }, "ID", Conv.toInt(g("EmpView").Cell(e.i("RowIndex"), 0)));
    }

    public void AddEmp_Click() {
        ShowDialog("CreateEmployee", r -> {
            if (r.ok()) {
                ViewShow(db().list("Employees"));
            }
        }, "ID", 2);
    }

    public void Confirm_Click() {
        Close();
    }
}
