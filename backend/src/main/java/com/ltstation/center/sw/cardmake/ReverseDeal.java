package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.List;

/** ReverseDeal : annulation d'un dépôt choisi dans la liste de la carte. */
@SwWindow("CardMake.ReverseDeal")
public class ReverseDeal extends CardMakeForm {

    /**
     * Comme l'original, seul le numéro saisi dans CardNumberBox est retenu : le résultat de la lecture du lecteur n'y est jamais utilisé.
     */
    public void CardReadBtn_Click() {
        try {
            if (c("CardNumberBox").Text().isEmpty()) {
                throw new IllegalStateException("Get Card Failed");
            }
            Row user = Row.of("ASN", c("CardNumberBox").Text(), "CompanyID", CM.CompanyID, "StationID", CM.StationID);
            Row d = sendRow("GetCardMessage", user);
            c("AgentBulkAmountBox").Text(Conv.toStr(d.get("StationAmount")));
            c("CardNumberBox").Text(d.str("Asn"));
            c("UserNameBox").Text(d.str("UserName"));
            c("CardBalanceBox").Text(Conv.toStr(d.get("Balance")));
            c("PhoneNumberBox").Text(d.str("Phone"));
            c("OperateBox").Text(CM.EmpName);
            List<Row> list = sendRows("GetCardDepositList", user);
            list.forEach(i -> i.put("TransDatetime", dt(i.get("TransDatetime"))));
            c("TransactionList").DataSource(list, "TransDatetime", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ViewReportBtn_Click() {
        Open("CardMake.ReportView");
    }

    public void TransactionList_SelectedIndexChanged() {
        try {
            Row i = c("TransactionList").SelectedRow();
            c("TransactionIDBox").Text(Conv.toStr(i.get("ID")));
            c("DateBox").Text(i.str("TransDatetime"));
            c("DepositAmountBox").Text(Conv.toStr(i.get("Amount")));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DepositBtn_Click() {
        try {
            if (c("PhoneNumberBox").Text().isEmpty()) {
                throw new IllegalStateException("Phone Number is Must");
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
            send("CardBalanceChange", Row.of("Asn", c("CardNumberBox").Text(), "C_Type", 2, "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", CM.EmpID,
                "CompanyID", CM.CompanyID, "StationID", CM.StationID, "LoginType", 0, "Phone", c("PhoneNumberBox").Text(), "Memo", c("ReverMemo").Text(),
                "ReversalID", Conv.toLong(c("TransactionList").SelectedValue())));
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
