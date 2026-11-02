package com.ltstation.center.sw.cardmake;

import com.ltstation.center.sw.form.SwWindow;

/** PasswordConfirm : mot de passe de la carte, saisi avant une écriture. */
@SwWindow("CardMake.PasswordConfirm")
public class PasswordConfirm extends CardMakeForm {

    public void Confirm_Click() {
        CM.CardPassword = c("PasswordText").Text();
        Close("OK");
    }

    public void Cancle_Click() {
        Close("Cancel");
    }
}
