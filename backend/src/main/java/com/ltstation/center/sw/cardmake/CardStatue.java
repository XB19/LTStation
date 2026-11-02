package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** CardStatue : blocage ou déblocage d'une carte (StatueComBox : 0 = en service, 1 = bloquée). */
@SwWindow("CardMake.CardStatue")
public class CardStatue extends CardMakeForm {

    private Row card;

    public void QueryBtn_Click() {
        try {
            String text = c("LockNumber").Text();
            if (text.isEmpty()) {
                throw new IllegalStateException("Please Write Query Card Number");
            }
            card = sendRow("CheckLockNumber", Row.of("Asn", text));
            c("CardNumber").Text(card.str("Asn"));
            c("UserName").Text(card.str("UserName"));
            c("PhoneBox").Text(card.str("Phone"));
            c("BalanceBox").Text(Conv.toStr(card.get("Balance")));
            c("StatueComBox").SelectedIndex(card.i("CardStatue") != 0 ? 1 : 0);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            if (card == null) {
                throw new IllegalStateException("Please Query First");
            }
            card.put("CardStatue", c("StatueComBox").SelectedIndex());
            send("CardStatueUpdate", card);
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
