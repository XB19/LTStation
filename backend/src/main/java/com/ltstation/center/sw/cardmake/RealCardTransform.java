package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** RealCardTransform : écriture de la carte réelle d'une carte NP, puis enregistrement sur Center. */
@SwWindow("CardMake.RealCardTransform")
public class RealCardTransform extends CardMakeForm {

    public void RealCardTransform_Load() {
        c("CardNumberBox").Text(argStr("Asn"));
        c("CardAccountBox").Text(argStr("CustomerID"));
        c("CardUserNameBox").Text(argStr("CustomerName"));
        c("PhoneBox").Text(argStr("Phone"));
        c("CardValidTimeBox").Text(argStr("ValidTime"));
        c("BalanceBox").Text(argStr("Balance"));
    }

    public void ConfirmBtn_Click() {
        try {
            if (Card_Deal()) {
                send("UpdateNPCardTransform", Row.of("Asn", argStr("Asn"), "CustomerID", argInt("CustomerID"), "CustomerName", argStr("CustomerName"), "Phone", argStr("Phone"),
                    "ValidTime", argStr("ValidTime"), "Balance", argStr("Balance")));
                MessageBox("Transform Success");
                Close("OK");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Card_Deal() {
        try {
            String asn = argStr("Asn");
            if (asn.length() != 20) {
                throw new IllegalStateException("Car No Error");
            }
            int factory = c("FactoryBox").SelectedIndex();
            if (!Card_Init(factory, false)) {
                throw new IllegalStateException("Init Error");
            }
            if (!Card_Make(Row.of("Asn", asn, "ValidTime", argStr("ValidTime")), factory, true)) {
                throw new IllegalStateException("Card Make Error");
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }
}
