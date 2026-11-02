package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** Deposit_UI : dépôt sur une carte (hors 010001). */
@SwWindow("CardMake.Deposit_UI")
public class Deposit_UI extends CardMakeForm {

    /**
     * Comme l'original, seul le numéro saisi dans CardNumberBox est retenu : le résultat de la lecture du lecteur n'y est jamais utilisé.
     */
    public void CardReadBtn_Click() {
        try {
            if (c("CardNumberBox").Text().isEmpty()) {
                throw new IllegalStateException("Get Card Failed");
            }
            Row d = sendRow("GetCardMessage", Row.of("ASN", c("CardNumberBox").Text(), "CompanyID", CM.CompanyID, "StationID", CM.StationID));
            c("AgentBulkAmountBox").Text(Conv.toStr(d.get("StationAmount")));
            c("CardNumberBox").Text(d.str("Asn"));
            c("UserNameBox").Text(d.str("UserName"));
            c("CardBalanceBox").Text(Conv.toStr(d.get("Balance")));
            c("PhoneNumberBox").Text(d.str("Phone"));
            c("OperateBox").Text(CM.EmpName);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DepositBtn_Click() {
        try {
            if (c("PhoneNumberBox").Text().isEmpty()) {
                throw new IllegalStateException("Phone Number is Must");
            }
            String text = c("CardNumberBox").Text();
            if (text.isEmpty()) {
                throw new IllegalStateException("Card Lost");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("DepositAmountBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Amount Format Error");
            }
            if (result.signum() < 0) {
                throw new IllegalStateException("Amount Format Error");
            }
            if (text.startsWith("010001")) {
                throw new IllegalStateException("This Function is For User Card,your Card can't do this");
            }
            Row r = sendRow("CardBalanceChange", Row.of("Asn", text, "C_Type", 0, "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", CM.EmpID, "CompanyID", CM.CompanyID,
                "StationID", CM.StationID, "LoginType", 0, "Phone", c("PhoneNumberBox").Text()));
            if (r != null) {
                c("TransactionIDBox").Text(Conv.toStr(r.get("AccountLogID")));
                c("DateBox").Text(r.str("Date"));
                c("TimeBox").Text(r.str("Time"));
            }
            c("DepositAmountBox").Text("");
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ViewReportBtn_Click() {
        Open("CardMake.ReportView");
    }
}
