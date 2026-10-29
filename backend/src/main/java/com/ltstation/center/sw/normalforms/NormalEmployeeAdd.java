package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** NormalEmployeeAdd : nouvel employé (code suivant, identifiant de connexion Memo1, mot de passe Memo2). */
@SwWindow("NormalForm.NormalEmployeeAdd")
public class NormalEmployeeAdd extends SwForm {

    public void NormalEmployeeAdd_Load() {
        try {
            String text = db().scalar(db().sql("Employees", "select MAX({Code}) from [Employees]"), null, String.class);
            // Convert.ToInt32(null) = 0 : sans employé, le premier code est 1
            text = "".equals(text) ? "1" : String.valueOf((text == null ? 0 : Integer.parseInt(text.trim())) + 1);
            c("EmpoyeeCode").Text(text);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            if (db().any("Employees", "{Name} = :n", p("n", c("EmployeeName").Text()))) {
                throw new IllegalStateException("Ce nom d'employé existe déjà");
            }
            if (db().any("Employees", "{Memo1} = :l AND {Memo2} = :p", p("l", c("LoginName").Text(), "p", c("Password").Text()))) {
                throw new IllegalStateException("Cet identifiant et ce mot de passe existent déjà");
            }
            String code = c("EmpoyeeCode").Text();
            db().insert("Employees", Row.of("Code", code, "Name", c("EmployeeName").Text(), "Memo1", c("LoginName").Text(), "Memo2", c("Password").Text(),
                "EmployeeID", Integer.parseInt(code.trim()), "StationID", IniInfo.StationID));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void skinButton1_Click() {
        Close("Cancel");
    }
}
