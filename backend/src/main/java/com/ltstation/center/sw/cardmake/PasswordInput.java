package com.ltstation.center.sw.cardmake;

import com.ltstation.center.sw.form.SwWindow;

/** PasswordInput : le mot de passe attendu (PA) est connu de la fenêtre appelante. */
@SwWindow("CardMake.PasswordInput")
public class PasswordInput extends CardMakeForm {

    public void Cancle_Click() {
        Close("Cancel");
    }

    public void Confirm_Click() {
        if (c("PasswordText").Text().equals(argStr("PA"))) {
            Close("OK");
        } else {
            MessageBox("Password Error");
        }
    }
}
