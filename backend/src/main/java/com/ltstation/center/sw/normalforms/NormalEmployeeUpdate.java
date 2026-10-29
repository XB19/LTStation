package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** NormalEmployeeUpdate : modification ou suppression d'un employé (argument Code). */
@SwWindow("NormalForm.NormalEmployeeUpdate")
public class NormalEmployeeUpdate extends SwForm {

    private Row employee() {
        Row e = db().first("Employees", "{Code} = :c", p("c", String.valueOf(argInt("Code"))));
        if (e == null) {
            throw new IllegalStateException("Employé introuvable");
        }
        return e;
    }

    public void NormalEmployeeUpdate_Load() {
        try {
            Row e = employee();
            c("EmpCode").Text(e.str("Code"));
            c("EmpName").Text(e.str("Name"));
            c("EmpLoginName").Text(e.str("Memo1"));
            c("EmpPassword").Text(e.str("Memo2"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DelBtn_Click() {
        try {
            db().deleteEntity("Employees", employee());
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            Row e = employee();
            if (db().any("Employees", "{Code} <> :c AND {Memo1} = :l AND {Memo2} = :p",
                p("c", String.valueOf(argInt("Code")), "l", c("EmpLoginName").Text(), "p", c("EmpPassword").Text()))) {
                throw new IllegalStateException("Cet identifiant et ce mot de passe existent déjà");
            }
            e.put("Name", c("EmpName").Text());
            e.put("Memo1", c("EmpLoginName").Text());
            e.put("Memo2", c("EmpPassword").Text());
            db().update("Employees", e);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
