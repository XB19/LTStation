package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** EmpPassword : vérification du mot de passe passé à l'ouverture (argument Password). */
@SwWindow("NormalForm.EmpPassword")
public class EmpPassword extends SwForm {

    public void Confirm_Click() {
        try {
            if (c("PasswordText").Text().equals(argStr("Password"))) {
                Close("OK");
                return;
            }
            throw new IllegalStateException(L("PasswordError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cancle_Click() {
        Close("Cancel");
    }
}
