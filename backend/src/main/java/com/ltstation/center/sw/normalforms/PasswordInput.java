package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** Card.PasswordInput : vérification d'un mot de passe passé à l'ouverture (argument PA). */
@SwWindow("Card.PasswordInput")
public class PasswordInput extends SwForm {

    public void Confirm_Click() {
        if (c("PasswordText").Text().equals(argStr("PA"))) {
            Close("OK");
            return;
        }
        MessageBox("Mot de passe incorrect");
    }

    public void Cancle_Click() {
        Close("Cancel");
    }
}
