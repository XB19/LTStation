package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** EmpUpdate : modification (« Update ») ou suppression (« Delete ») d'un employé (argument ID), d'abord sur Center. */
@SwWindow(value = "NormalForm.EmpUpdate", setup = true)
public class EmpUpdate extends SwForm {

    private Row employee() {
        Row e = db().first("Employees", "{EmployeeID} = :e", p("e", argInt("ID")));
        if (e == null) {
            throw new IllegalStateException(L("EmpEmpty"));
        }
        return e;
    }

    private static Object wire(Row emp) {
        return SW.dto().has("Employees") ? SW.dto().shape("Employees", emp) : emp;
    }

    public void EmpUpdate_Load() {
        try {
            Row e = employee();
            c("LoginName").Text(e.str("Name"));
            c("Password").Text(e.str("Memo2"));
            c("Phone").Text(e.str("Telephone"));
            for (String name : EmpAuth.checksOf(e.str("Auth"))) {
                c(name).Checked(true);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            String name = c("LoginName").Text();
            String password = c("Password").Text();
            String phone = c("Phone").Text();
            if (name.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                throw new IllegalStateException(L("InformationLost"));
            }
            if (db().any("Employees", "{EmployeeID} <> :e AND ({Name} = :n OR {Telephone} = :t)", p("e", argInt("ID"), "n", name, "t", phone))) {
                throw new IllegalStateException(L("EmpRepeat"));
            }
            Row e = employee();
            e.put("Name", name);
            e.put("Telephone", phone);
            e.put("Memo1", name);
            e.put("Memo2", password);
            e.put("Auth", EmpAuth.authOf(Controls("groupBox4"), n -> c(n).Checked()));
            Object stationType = e.get("StationType");
            e.put("StationType", "Update");
            CustomerRespone r = BaseFunction.post("ServerDeal/NormalEmpUpload", wire(e));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            e.put("StationType", stationType);
            db().update("Employees", e);
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cancel_Click() {
        try {
            Row e = employee();
            e.put("StationType", "Delete");
            CustomerRespone r = BaseFunction.post("ServerDeal/NormalEmpUpload", wire(e));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            db().delete("Employees", "{EmployeeID} = :e", p("e", argInt("ID")));
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
