package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** CardReplaceView : remplacement d'une carte après un code SMS valable 5 minutes ; retour à l'ancienne si l'écriture échoue. */
@SwWindow("CardMake.CardReplaceView")
public class CardReplaceView extends CardMakeForm {

    private String ValidCode = "";
    private LocalDateTime ValidDT = LocalDateTime.now();
    private Row card;
    private boolean ValidDeal;

    @Override
    protected void init() {
        c("FactoryBox").SelectedIndex(0);
    }

    public void CheckBtn_Click() {
        try {
            String text = c("LockNumber").Text();
            if (text.isEmpty()) {
                throw new IllegalStateException("Please Write Replace Card Number");
            }
            card = sendRow("CheckLockNumber", Row.of("Asn", text));
            ValidCode = card.str("Valid_Time");
            ValidDT = LocalDateTime.now();
            MessageBox("Please Check your SMS,Write the Valid Code", "Message");
            ValidDeal = false;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardGetBtn_Click() {
        try {
            if (card == null) {
                throw new IllegalStateException("Please Write The Card Number");
            }
            if (!ValidCode.equals(c("ValidBox").Text())) {
                throw new IllegalStateException("Valid Code Error");
            }
            if (Duration.between(ValidDT, LocalDateTime.now()).toMinutes() >= 5) {
                throw new IllegalStateException("Valid Code Out of Time");
            }
            c("CardNumber").Text(card.str("Asn"));
            c("UserName").Text(card.str("UserName"));
            c("PhoneBox").Text(card.str("Phone"));
            c("BalanceBox").Text(Conv.toStr(card.get("Balance")));
            ValidDeal = true;
        } catch (RuntimeException ex) {
            ValidDeal = false;
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            if (!ValidDeal || card == null) {
                return;
            }
            Row o = Row.of("Asn", card.str("Asn"), "StationID", card.get("StationID"), "CarNo", card.str("Car_No"), "Birthday", card.get("Birthday"),
                "Address", card.str("Address"), "DayAmountLimit", card.get("DayAmountLimit"), "DayLimitTime", card.get("DayLimitTime"), "DayVolumeLimit", card.get("DayVolumeLimit"),
                "Discount", 100, "Email", card.str("Email"), "GroupID", card.get("GroupID"), "MonthAmountLimit", card.get("MonthAmountLimit"),
                "MonthVolumeLimit", card.get("MonthAmountLimit"), "Telephone", card.str("Phone"), "CNO", card.str("Certificate"), "Password", card.str("Password"),
                "SpecialPrice", "", "Phone", card.str("Phone"), "PostCode", card.str("PostCode"), "UserName", card.str("UserName"), "ValidTime", card.str("VTime"),
                "Bal", card.get("Balance"));
            String old = o.str("Asn");
            String asn = sendRow("CardReplaceCard", o).str("Asn");
            Row back = Row.of("OldAsn", old, "NewAsn", asn);
            c("NewCardNumber").Text(asn);
            o.put("Asn", asn);
            int factory = c("FactoryBox").SelectedIndex();
            if (!Card_Init(factory, false)) {
                send("CardReplaceBack", back);
                throw new IllegalStateException("Init Error");
            }
            if (!Card_Make(o, factory, false) || !Load(o)) {
                send("CardReplaceBack", back);
                throw new IllegalStateException("Card Make Error");
            }
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Carte 010001 : le solde repart sur la nouvelle puce. */
    private boolean Load(Row o) {
        BigDecimal bal = o.dec("Bal");
        if (!o.str("Asn").startsWith("010001") || bal == null || bal.signum() <= 0) {
            return true;
        }
        try {
            List<String> list = new ArrayList<>();
            if (!CardWrite.ReadKeyFromDBNotChange(list) || list.isEmpty()) {
                throw new IllegalStateException("Key Error");
            }
            if (!CardWrite.WriteOnLoad(o.str("Asn"), bal.toPlainString(), "000000000000", o.str("Password"), list.get(7))) {
                throw new IllegalStateException("Write Error");
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }
}
