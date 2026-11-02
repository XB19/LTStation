package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main_Card : création d'une carte sur Center puis écriture ; annulation sur Center si l'écriture échoue. */
@SwWindow("CardMake.Main_Card")
public class Main_Card extends CardMakeForm {

    public void Main_Card_Load() {
        try {
            GetGroup();
            GetRule();
            c("FactoryBox").SelectedIndex(0);
            c("CardTypeBox").SelectedIndex(0);
            c("GroupBox").SelectedIndex(0);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private BigDecimal limit(String box, String name) {
        return c(box).Text().isEmpty() ? BigDecimal.ZERO : positive(c(box).Text(), name);
    }

    public void CardCreateBtn_Click() {
        try {
            BigDecimal result = limit("DayLimitTimeBox", "DayLimitTime");
            if (result.stripTrailingZeros().scale() > 0) {
                throw new IllegalStateException("DayLimitTime Format Error");
            }
            BigDecimal result2 = limit("DayAmountLimitBox", "DayAmountLimit");
            BigDecimal result3 = limit("DayVolumeLimitBox", "DayVolumeLimit");
            BigDecimal result4 = limit("MonthVolumeLimitBox", "MonthVolumeLimit");
            BigDecimal result5 = limit("MonthAmountLimitBox", "MonthAmountLimit");
            if (!c("PasswordBox").Text().isEmpty() && !c("PasswordBox").Text().matches("^\\d{4}$")) {
                throw new IllegalStateException("Password Format Error");
            }
            BigDecimal result6 = c("DiscountBox").Text().isEmpty() ? new BigDecimal(100) : discount(c("DiscountBox").Text());
            int type = c("CardTypeBox").SelectedIndex();
            int card_Type = 0;
            if (c("FactoryBox").SelectedIndex() == 3) {
                card_Type = type != 3 ? type + 2 : 7;
            } else if (type == 1) {
                card_Type = 5;
            }
            int ruleID = c("RuleSelect").SelectedItem() != null ? Conv.toInt(c("RuleSelect").SelectedValue()) : -1;
            Row card = sendRow("CardUpdate", Row.of("UserName", c("UserNameBox").Text(), "CNO", c("CertificateBox").Text(), "Phone", c("PhoneBox").Text(),
                "CarNo", c("Car_NoBox").Text(), "PostCode", c("PostCodeBox").Text(), "Email", c("EmailBox").Text(), "Address", c("AddressBox").Text(),
                "Birthday", BaseFunction.ConvertDateTimeToInt(c("BirthDayBox").Value()), "Password", c("PasswordBox").Text(), "DayLimitTime", result.intValue(),
                "DayAmountLimit", result2, "DayVolumeLimit", result3, "MonthAmountLimit", result5, "MonthVolumeLimit", result4, "CompanyID", CM.CompanyID,
                "OperEmp", CM.EmpID, "Discount", result6, "GroupID", Conv.toInt(c("GroupBox").SelectedValue()), "SpecialPrice", "", "Telephone", c("PhoneBox").Text(),
                "Card_Type", card_Type, "Update_Type", "Add", "StationID", CM.StationID, "LoginType", 0, "RuleID", ruleID, "SecondPhone", c("SecondPhone").Text()));
            c("CardNoBox").Text(card.str("Asn"));
            c("AccountBox").Text(Conv.toStr(card.get("CustomerAccountID")));
            c("ValidTimeBox").Text(card.str("ValidTime"));
            if (!Card_Deal(card)) {
                Map<String, Object> back = new LinkedHashMap<>(card);
                back.put("Update_Type", "Delete");
                send("CardUpdate", back);
                MessageBox("RollbackTran Deal");
            } else {
                MessageBox("Card Make Success");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Card_Deal(Row card) {
        int factory = c("FactoryBox").SelectedIndex();
        if (factory > 2) {
            return Mifare_Make(card.str("Asn"));
        }
        try {
            String asn = card.str("Asn");
            if (asn == null || asn.length() != 20) {
                throw new IllegalStateException("Car No Error");
            }
            if (!Card_Init(factory, true)) {
                throw new IllegalStateException("Init Error");
            }
            if (!Card_Make(card, factory, false)) {
                throw new IllegalStateException("Card Make Error");
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void CardViewBtn_Click() {
        Open("CardMake.Card_View");
    }

    public void FactoryBox_SelectedIndexChanged() {
        c("CardTypeBox").Items(c("FactoryBox").SelectedIndex() == 3 ? List.of("RFID", "Utilisateur", "Points", "Facture") : List.of("Utilisateur", "VIP"));
        c("CardTypeBox").SelectedIndex(0);
    }

    public void ClearBtn_Click() {
        for (String n : List.of("UserNameBox", "CertificateBox", "PhoneBox", "Car_NoBox", "PostCodeBox", "EmailBox", "AddressBox", "PasswordBox", "ValidTimeBox",
            "CardNoBox", "AccountBox", "DayLimitTimeBox", "DayAmountLimitBox", "DayVolumeLimitBox", "MonthVolumeLimitBox", "MonthAmountLimitBox")) {
            c(n).Text("");
        }
    }
}
