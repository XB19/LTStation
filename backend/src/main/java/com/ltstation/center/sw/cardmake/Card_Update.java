package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Card_Update : lecture d'une carte, modification sur Center puis sur la carte (010001) ; retour arrière si l'écriture échoue. */
@SwWindow("CardMake.Card_Update")
public class Card_Update extends CardMakeForm {

    private Row card_Deal;

    public void Card_Update_Load() {
        GetGroup();
        GetRule();
        c("FactoryBox").SelectedIndex(0);
        c("CardTypeBox").SelectedIndex(0);
        c("GroupBox").SelectedIndex(0);
    }

    public void CardReaderBtn_Click() {
        try {
            String asn = ReadAsn(c("FactoryBox").SelectedIndex());
            Row deal = sendRow("GetCardMessage", Row.of("ASN", asn, "CompanyID", CM.CompanyID));
            card_Deal = deal;
            String pa = deal.str("Password");
            if (pa != null && !pa.isEmpty() && !"9999".equals(pa)) {
                ShowDialog("CardMake.PasswordInput", r -> {
                    if (r.ok()) {
                        ShowCard(deal);
                    } else {
                        MessageBox("Password Error");
                    }
                }, "PA", pa);
                return;
            }
            ShowCard(deal);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCard(Row d) {
        c("UserNameBox").Text(d.str("UserName"));
        c("CertificateBox").Text(d.str("Certificate"));
        c("PhoneBox").Text(d.str("Phone"));
        c("Car_NoBox").Text(d.str("Car_No"));
        c("PostCodeBox").Text(d.str("PostCode"));
        c("EmailBox").Text(d.str("Email"));
        c("AddressBox").Text(d.str("Address"));
        c("BirthDayBox").Value(BaseFunction.StampToDateTime(Conv.toStr(d.get("Birthday"))));
        c("PasswordBox").Text(d.str("Password"));
        c("ValidTimeBox").Text(d.str("Valid_Time"));
        c("CardNoBox").Text(d.str("Asn"));
        c("AccountBox").Text(d.str("Account"));
        c("GroupBox").SelectedValue(d.get("GroupID"));
        c("DayLimitTimeBox").Text(Conv.toStr(d.get("DayLimitTime")));
        c("DayAmountLimitBox").Text(Conv.toStr(d.get("DayAmountLimit")));
        c("DayVolumeLimitBox").Text(Conv.toStr(d.get("DayVolumeLimit")));
        c("MonthVolumeLimitBox").Text(Conv.toStr(d.get("MonthVolumeLimit")));
        c("MonthAmountLimitBox").Text(Conv.toStr(d.get("MonthAmountLimit")));
        c("RuleSelect").SelectedValue(d.get("ScoreRuleID"));
        c("DiscountBox").Text(Conv.toStr(d.get("Discount")));
        c("SecondPhone").Text(d.str("SecondPhone"));
        c("CardTypeBox").SelectedIndex(d.i("Card_Type"));
    }

    private static String IsZero(String text) {
        return "0".equals(text) ? "" : text;
    }

    public void CardCreateBtn_Click() {
        try {
            BigDecimal result = positive(c("DayLimitTimeBox").Text(), "DayLimitTime");
            if (result.stripTrailingZeros().scale() > 0) {
                throw new IllegalStateException("DayLimitTime Format Error");
            }
            BigDecimal result2 = positive(c("DayAmountLimitBox").Text(), "DayAmountLimit");
            BigDecimal result3 = positive(c("DayVolumeLimitBox").Text(), "DayVolumeLimit");
            BigDecimal result4 = positive(c("MonthVolumeLimitBox").Text(), "MonthVolumeLimit");
            BigDecimal result5 = positive(c("MonthAmountLimitBox").Text(), "MonthAmountLimit");
            BigDecimal result6 = c("DiscountBox").Text().isEmpty() ? new BigDecimal(100) : discount(c("DiscountBox").Text());
            String password = c("PasswordBox").Text();
            if (!password.isEmpty() && !password.matches("^\\d{4}$")) {
                throw new IllegalStateException("Password Format Error");
            }
            int ruleID = c("RuleSelect").SelectedItem() != null ? Conv.toInt(c("RuleSelect").SelectedValue()) : -1;
            String asn = c("CardNoBox").Text();
            send("CardUpdate", Row.of("UserName", c("UserNameBox").Text(), "CNO", c("CertificateBox").Text(), "Phone", c("PhoneBox").Text(), "CarNo", c("Car_NoBox").Text(),
                "PostCode", c("PostCodeBox").Text(), "Email", c("EmailBox").Text(), "Address", c("AddressBox").Text(),
                "Birthday", BaseFunction.ConvertDateTimeToInt(c("BirthDayBox").Value()), "Password", password, "DayLimitTime", result.intValue(), "DayAmountLimit", result2,
                "DayVolumeLimit", result3, "MonthAmountLimit", result5, "MonthVolumeLimit", result4, "CompanyID", CM.CompanyID, "OperEmp", CM.EmpID, "Discount", result6,
                "GroupID", Conv.toInt(c("GroupBox").SelectedValue()), "SpecialPrice", "", "Telephone", c("PhoneBox").Text(), "Card_Type", c("CardTypeBox").SelectedIndex(),
                "Update_Type", "Update", "Asn", asn, "CustomerAccountID", Conv.toLong(c("AccountBox").Text()), "ValidTime", c("ValidTimeBox").Text(), "LoginType", 0, "RuleID", ruleID));
            if (asn.startsWith("010001")) {
                WriteUserCard(password);
            }
            MessageBox("Update Deal");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void WriteUserCard(String password) {
        List<String> list = new ArrayList<>();
        if (!CardWrite.ReadKeyFromDBNotChange(list) || list.isEmpty()) {
            throw new IllegalStateException("Read Key Error");
        }
        String old = card_Deal.str("Password");
        String need = "9999".equals(old) || old == null || old.isEmpty() ? "0" : "1";
        String[] error = {""};
        if (!CardWrite.ChangeUserCardInfos(c("UserNameBox").Text(), c("CertificateBox").Text(), "1", need, old == null ? "" : old, password, "", "", "",
            IsZero(c("DayVolumeLimitBox").Text()), IsZero(c("DayLimitTimeBox").Text()), IsZero(c("DayAmountLimitBox").Text()), c("Car_NoBox").Text(),
            IsZero(c("MonthVolumeLimitBox").Text()), IsZero(c("MonthAmountLimitBox").Text()), "", "", "", "100", "false", card_Deal.str("Valid_Time"), "", "", "", "", "",
            list.get(1), "", error)) {
            Row d = card_Deal;
            send("CardUpdate", Row.of("UserName", d.str("UserName"), "CNO", d.str("Certificate"), "Phone", d.str("Phone"), "CarNo", d.str("Car_No"), "PostCode", d.str("PostCode"),
                "Email", d.str("Email"), "Address", d.str("Address"), "Birthday", d.get("Birthday"), "Password", d.str("Password"), "DayLimitTime", d.get("DayLimitTime"),
                "DayAmountLimit", d.get("DayAmountLimit"), "DayVolumeLimit", d.get("DayVolumeLimit"), "MonthAmountLimit", d.get("MonthAmountLimit"),
                "MonthVolumeLimit", d.get("MonthVolumeLimit"), "CompanyID", CM.CompanyID, "OperEmp", CM.EmpID, "Discount", 100, "GroupID", d.get("GroupID"), "SpecialPrice", "",
                "Telephone", d.str("Phone"), "Card_Type", d.get("Card_Type"), "Update_Type", "Update", "Asn", d.str("Asn"), "CustomerAccountID", Conv.toLong(d.get("Account")),
                "ValidTime", d.str("Valid_Time"), "LoginType", 0, "RuleID", d.get("ScoreRuleID")));
            throw new IllegalStateException(error[0]);
        }
    }

    public void FactoryBox_SelectedIndexChanged() {
        try {
            c("CardTypeBox").Items(c("FactoryBox").SelectedIndex() == 3 ? List.of("RFID", "Utilisateur", "Points") : List.of("Utilisateur", "VIP"));
            c("CardTypeBox").SelectedIndex(0);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
