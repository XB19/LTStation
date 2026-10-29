package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.Mifare;
import java.math.BigDecimal;

/** Card.Card_Update : lecture d'une carte client Mifare puis modification de ses informations et limites sur Center. */
@SwWindow("Card.Card_Update")
public class Card_Update extends CardFormBase {

    private long icdev;

    public void Card_Update_Load() {
        GetGroup(false);
        GetRule();
        GetGradeList();
        GetStationList();
        c("FactoryBox").SelectedIndex(0);
        c("CardTypeBox").SelectedIndex(0);
        c("GroupBox").SelectedIndex(0);
    }

    public void CardReaderBtn_Click() {
        try {
            icdev = Mifare.rf_init(0, 9600);
            if (icdev <= 0) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            Mifare.Select(icdev);
            if (Mifare.rf_authentication_key(icdev, 0, 20, Mifare.Key()) != 0) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            String asn = "00000000" + Mifare.rf_read_hex(icdev, 20).substring(0, 12);
            Mifare.rf_exit(icdev);
            icdev = 0;
            CustomerRespone r = BaseFunction.post("ExtendUpdate/GetCardMessage", SW.dto().make("UserPublicData", "ASN", asn, "CompanyID", IniInfo.CompanyID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row deal = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            String pa = deal.str("Password");
            if (!pa.isEmpty() && !"9999".equals(pa)) {
                ShowDialog("Card.PasswordInput", res -> {
                    if ("OK".equals(res.result())) {
                        ShowCard(deal);
                    } else {
                        MessageBox("Mot de passe incorrect");
                    }
                }, "PA", pa);
                return;
            }
            ShowCard(deal);
        } catch (RuntimeException ex) {
            // comme l'original : le lecteur n'est pas refermé ici en cas d'erreur d'authentification
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCard(Row d) {
        try {
            c("UserNameBox").Text(d.str("UserName"));
            c("CertificateBox").Text(d.str("Certificate"));
            c("PhoneBox").Text(d.str("Phone"));
            c("Car_NoBox").Text(d.str("Car_No"));
            c("PostCodeBox").Text(d.str("PostCode"));
            c("EmailBox").Text(d.str("Email"));
            c("AddressBox").Text(d.str("Address"));
            c("BirthDayBox").Value(BaseFunction.StampToDateTime(d.str("Birthday")));
            c("PasswordBox").Text(d.str("Password"));
            c("ValidTimeBox").Text(d.str("Valid_Time"));
            c("CardNoBox").Text(d.str("Asn"));
            c("AccountBox").Text(d.str("Account"));
            c("DayLimitTimeBox").Text(d.str("DayLimitTime"));
            c("DayAmountLimitBox").Text(d.dec("DayAmountLimit").toPlainString());
            c("DayVolumeLimitBox").Text(d.dec("DayVolumeLimit").toPlainString());
            c("MonthVolumeLimitBox").Text(d.dec("MonthVolumeLimit").toPlainString());
            c("MonthAmountLimitBox").Text(d.dec("MonthAmountLimit").toPlainString());
            c("GroupBox").SelectedValue(d.get("GroupID"));
            c("RuleSelect").SelectedValue(d.get("ScoreRuleID"));
            c("DiscountBox").Text(d.dec("Discount").toPlainString());
            c("GradeSelectBox").SelectedValue(Conv.toInt(d.get("LimitGrade")));
            if (d.str("LimitStation").contains(String.valueOf(IniInfo.StationID))) {
                c("StationSelectBox").SelectedValue(IniInfo.StationID);
            }
            c("StationSelectBox").Enabled(false);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardCreateBtn_Click() {
        try {
            int result = intLimit("DayLimitTimeBox", "DayLimitTime", false);
            BigDecimal result2 = decLimit("DayAmountLimitBox", "DayAmountLimit", false);
            BigDecimal result3 = decLimit("DayVolumeLimitBox", "DayVolumeLimit", false);
            BigDecimal result4 = decLimit("MonthVolumeLimitBox", "MonthVolumeLimit", false);
            BigDecimal result5 = decLimit("MonthAmountLimitBox", "MonthAmountLimit", false);
            BigDecimal result6 = discount("DiscountBox", "Discount Error");
            CustomerRespone r = BaseFunction.post("ExtendUpdate/CardUpdate", SW.dto().make("Extend_Card_Update",
                "UserName", c("UserNameBox").Text(), "CNO", c("CertificateBox").Text(), "Phone", c("PhoneBox").Text(), "CarNo", c("Car_NoBox").Text(),
                "PostCode", c("PostCodeBox").Text(), "Email", c("EmailBox").Text(), "Address", c("AddressBox").Text(),
                "Birthday", BaseFunction.ConvertDateTimeToInt(c("BirthDayBox").Value()), "Password", c("PasswordBox").Text(),
                "DayLimitTime", result, "DayAmountLimit", result2, "DayVolumeLimit", result3, "MonthAmountLimit", result5, "MonthVolumeLimit", result4,
                "CompanyID", IniInfo.CompanyID, "OperEmp", IniInfo.LoginID, "Discount", result6, "GroupID", Conv.toInt(c("GroupBox").SelectedValue()),
                "SpecialPrice", "", "Telephone", c("PhoneBox").Text(), "Card_Type", c("CardTypeBox").SelectedIndex(), "Update_Type", "Update",
                "Asn", c("CardNoBox").Text(), "CustomerAccountID", Long.parseLong(c("AccountBox").Text().trim()), "ValidTime", c("ValidTimeBox").Text(),
                "LoginType", 1, "RuleID", Conv.toInt(c("RuleSelect").SelectedValue()), "LimitGrade", Conv.toInt(c("GradeSelectBox").SelectedValue()),
                "LimitStation", Conv.toInt(c("StationSelectBox").SelectedValue())));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            MessageBox("Mise à jour effectuée");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
