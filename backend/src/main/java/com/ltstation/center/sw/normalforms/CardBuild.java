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
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Card.CardBuild : création d'une carte client sur Center puis écriture Mifare ; annulation sur Center si l'écriture échoue. */
@SwWindow("Card.CardBuild")
public class CardBuild extends CardFormBase {

    /** CardBuildLog de l'original. */
    private static final Logger CardBuildLog = LoggerFactory.getLogger("CardBuildLog");

    private long icdev;

    public void CardBuild_Load() {
        GetRule();
        GetGroup(true);
        GetGradeList();
        GetStationList();
        c("FactoryBox").SelectedIndex(0);
        c("CardTypeBox").SelectedIndex(0);
        c("DisCountBox").Text("100");
    }

    public void CardCreateBtn_Click() {
        try {
            int result = intLimit("DayLimitTimeBox", "DayLimitTime", true);
            BigDecimal result2 = decLimit("DayAmountLimitBox", "DayAmountLimit", true);
            BigDecimal result3 = decLimit("DayVolumeLimitBox", "DayVolumeLimit", true);
            BigDecimal result4 = decLimit("MonthVolumeLimitBox", "MonthVolumeLimit", true);
            BigDecimal result5 = decLimit("MonthAmountLimitBox", "MonthAmountLimit", true);
            BigDecimal result6 = discount("DisCountBox", "Discount Number Error");
            int card_Type = switch (c("FactoryBox").SelectedIndex()) {
                case 0 -> 2;
                case 1 -> 3;
                case 2 -> 4;
                case 3 -> 7;
                default -> 0;
            };
            CustomerRespone r = BaseFunction.post("ExtendUpdate/CardUpdate", SW.dto().make("Extend_Card_Update",
                "UserName", c("UserNameBox").Text(), "CNO", c("CertificateBox").Text(), "Phone", c("PhoneBox").Text(), "CarNo", c("Car_NoBox").Text(),
                "PostCode", c("PostCodeBox").Text(), "Email", c("EmailBox").Text(), "Address", c("AddressBox").Text(),
                "Birthday", BaseFunction.ConvertDateTimeToInt(c("BirthDayBox").Value()), "Password", c("PasswordBox").Text(),
                "DayLimitTime", result, "DayAmountLimit", result2, "DayVolumeLimit", result3, "MonthAmountLimit", result5, "MonthVolumeLimit", result4,
                "CompanyID", IniInfo.CompanyID, "OperEmp", IniInfo.LoginID, "Discount", result6, "GroupID", Conv.toInt(c("GroupBox").SelectedValue()),
                "SpecialPrice", "", "Telephone", c("PhoneBox").Text(), "Card_Type", card_Type, "Update_Type", "Add", "StationID", IniInfo.StationID,
                "LoginType", 1, "RuleID", Conv.toInt(c("RuleSelect").SelectedValue()), "LimitGrade", Conv.toInt(c("GradeSelectBox").SelectedValue()),
                "LimitStation", Conv.toInt(c("StationSelectBox").SelectedValue())));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row card = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("CardNoBox").Text(card.str("Asn"));
            c("AccountBox").Text(card.str("CustomerAccountID"));
            c("ValidTimeBox").Text(card.str("ValidTime"));
            if (!Card_Deal(card)) {
                Map<String, Object> back = new LinkedHashMap<>(card);
                back.put("Update_Type", "Delete");
                r = BaseFunction.post("ExtendUpdate/CardUpdate", back);
                if (r.Result()) {
                    MessageBox(L("DataRollbackTran"));
                }
            } else {
                MessageBox(L("CardMakeSuccess"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Card_Deal(Row card) {
        try {
            CardBuildLog.info("----Start---");
            icdev = Mifare.rf_init(0, 9600);
            CardBuildLog.info("Init");
            if (icdev <= 0) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            Mifare.Select(icdev);
            int num2 = Mifare.rf_authentication_key(icdev, 0, 4, Mifare.Key());
            CardBuildLog.info("authentication_key:{}", num2);
            if (num2 != 0) {
                throw new IllegalStateException("Card Init Error");
            }
            Mifare.WriteCard(icdev, card.str("Asn"));
            Mifare.rf_exit(icdev);
            icdev = 0;
            return true;
        } catch (RuntimeException ex) {
            Mifare.rf_exit(icdev);
            icdev = 0;
            MessageBox(ex.getMessage());
            CardBuildLog.info("Error:{}", ex.getMessage());
            return false;
        }
    }
}
