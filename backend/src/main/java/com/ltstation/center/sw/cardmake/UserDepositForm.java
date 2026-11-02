package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** UserDepositForm : dépôt sur une carte 010001 : Center, puis puce, retour sur Center si la puce échoue, puis SMS. */
@SwWindow("CardMake.UserDepositForm")
public class UserDepositForm extends CardMakeForm {

    private static final String VIP_ONLY = "This Function is For VIP Card,your Card can't do this";

    @Override
    protected void init() {
        CM.CardPassword = "";
        c("OperateBox").Text(CM.EmpName);
    }

    public void CardReadBtn_Click() {
        try {
            String[] res = {""};
            if (!CardWrite.ReadPinID(res)) {
                throw new IllegalStateException("Card Reader Disconnect");
            }
            if ("01".equals(res[0])) {
                ShowDialog("CardMake.PasswordConfirm", r -> {
                    if (r.ok()) {
                        Reload(CM.CardPassword);
                    } else {
                        MessageBox("Password Cancel");
                    }
                });
            } else {
                Reload("");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void Reload(String Pwd) {
        try {
            CardWrite.UserPublicData[] user = {null};
            if (!CardWrite.ReadCardOnLoad(Pwd, user, new BigDecimal[1])) {
                throw new IllegalStateException("Card Read Failed");
            }
            CustomerRespone r = BaseFunction.post("ServerDeal/GetUserMessage", Row.of("Code", CM.StationCode, "Mess", user[0].ASN));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            if (!user[0].ASN.startsWith("010001")) {
                throw new IllegalStateException(VIP_ONLY);
            }
            Row p = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("CardNumberBox").Text(p.str("Asn"));
            c("UserNameBox").Text(p.str("Name"));
            c("CardBalanceBox").Text(Conv.netRound(p.dec("Bal"), IniInfo.Mradio).toPlainString());
            c("PhoneNumberBox").Text(p.str("Telephone"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DepositBtn_Click() {
        try {
            List<String> list = new ArrayList<>();
            if (!CardWrite.ReadKeyFromDBNotChange(list) || list.isEmpty()) {
                throw new IllegalStateException("Key Error");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("DepositAmountBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Deposit Amount Error");
            }
            if (result.signum() < 0) {
                throw new IllegalStateException("Deposit Amount Can't less than 0");
            }
            if (result.compareTo(new BigDecimal(10000000)) > 0) {
                throw new IllegalStateException("Deposit Amount is Large");
            }
            String asn = c("CardNumberBox").Text();
            if (!asn.startsWith("010001")) {
                throw new IllegalStateException(VIP_ONLY);
            }
            Row back = sendRow("CardBalanceChange", Row.of("Asn", asn, "C_Type", 4, "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", CM.EmpID, "CompanyID", CM.CompanyID,
                "StationID", CM.StationID, "LoginType", 0, "Phone", c("PhoneNumberBox").Text()));
            if (!CardWrite.WriteOnLoad(asn, result.toPlainString(), "000000000000", CM.CardPassword, list.get(7))) {
                try {
                    send("CardBalanceReturn", back);
                    MessageBox("RollbackTran Deal");
                } catch (RuntimeException ex) {
                    MessageBox("RollbackTran Failed,Please Ask Deveploer to deal it");
                }
                return;
            }
            String logId = Conv.toStr(back.get("AccountLogID"));
            c("TransactionIDBox").Text(logId);
            c("DateBox").Text(back.str("Date"));
            c("TimeBox").Text(back.str("Time"));
            c("DepositAmountBox").Text("");
            String before = c("CardBalanceBox").Text();
            String text = "Dear customer, your NP Smart Card has been credited\n" + "TNS ID: " + logId + "\n" + "Agent Name: " + CM.StationName + "\n"
                + "Date and Time: " + back.str("Date") + " " + back.str("Time") + "\n" + "Amount Deposited: " + result.toPlainString() + "\n" + "Card No: " + asn + "\n"
                + "User name: " + c("UserNameBox").Text() + "\n" + "PREVIOUS AMNT: " + before + "\n"
                + "CRNT bal: " + new BigDecimal(before).add(result).toPlainString() + "\n";
            BaseFunction.post("ExtendUpdate/MessageSend", Row.of("Asn", asn, "Content", text));
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ViewReportBtn_Click() {
        Open("CardMake.ReportView");
    }
}
