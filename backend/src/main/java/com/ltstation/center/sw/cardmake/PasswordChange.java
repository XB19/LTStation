package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/**
 * PasswordChange : nouveau mot de passe après un code reçu par SMS. Cartes 010001 refusées à la lecture :
 * la mise à jour de la puce de l'original, réservée à ces cartes, n'est donc jamais atteinte.
 */
@SwWindow("CardMake.PasswordChange")
public class PasswordChange extends CardMakeForm {

    private String ValidCode = "";
    private Row card;

    public void SendBtn_Click() {
        try {
            String asn = ReadAsn(0);
            c("LockNumber").Text(asn);
            if (asn == null || asn.isEmpty()) {
                throw new IllegalStateException("Please Write Query Card Number");
            }
            if (asn.startsWith("010001")) {
                throw new IllegalStateException("This Function is For User Card,your Card can't do this");
            }
            card = sendRow("CheckLockNumber", Row.of("Asn", asn, "NPType", "Password"));
            ValidCode = card.str("Valid_Time");
            c("CardNumber").Text(card.str("Asn"));
            c("UserName").Text(card.str("UserName"));
            c("PhoneBox").Text(card.str("Phone"));
            c("BalanceBox").Text(Conv.toStr(card.get("Balance")));
            MessageBox("Please Check your SMS,Write the Valid Code", "Message");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancelBtn_Click() {
        Close("Cancel");
    }

    public void ConfirmBtn_Click() {
        try {
            String code = c("ValidBox").Text();
            if (code.isEmpty()) {
                throw new IllegalStateException("Please Write your SMS Code First");
            }
            if (!code.equals(ValidCode)) {
                throw new IllegalStateException("SMS Code Error");
            }
            String password = c("NewPasswordBox").Text();
            if (!password.isEmpty() && !password.matches("^\\d{4}$")) {
                throw new IllegalStateException("Password Format Error");
            }
            send("CardPasswordUpdte", Row.of("Asn", card.str("Asn"), "Password", password));
            MessageBox("Deal Success");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
