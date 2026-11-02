package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** Balance_Deal : lecture de la carte puis crédit ou débit du solde sur Center. */
@SwWindow("CardMake.Balance_Deal")
public class Balance_Deal extends CardMakeForm {

    public void CardReadBtn_Click() {
        try {
            String asn = ReadAsn(c("FactoryBox").SelectedIndex());
            Row deal = sendRow("GetCardMessage", Row.of("ASN", asn, "CompanyID", CM.CompanyID, "StationID", CM.StationID));
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
        c("AgentBox").Text(Conv.toStr(d.get("StationAmount")));
        c("CardNoBox").Text(d.str("Asn"));
        c("UserNameBox").Text(d.str("UserName"));
        c("BalanceBox").Text(Conv.toStr(d.get("Balance")));
    }

    public void ConfirmBtn_Click() {
        try {
            String text = c("CardNoBox").Text();
            int factory = c("FactoryBox").SelectedIndex();
            if (factory <= 2 && !text.equals(ReadAsn(factory))) {
                throw new IllegalStateException("Card No Error");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("AmountBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Amount Format Error");
            }
            if (result.signum() < 0) {
                throw new IllegalStateException("Amount Format Error");
            }
            send("CardBalanceChange", Row.of("Asn", text, "C_Type", c("OperateBox").SelectedIndex(), "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", CM.EmpID,
                "CompanyID", CM.CompanyID, "StationID", CM.StationID, "LoginType", 0));
            c("CardNoBox").Text("");
            c("UserNameBox").Text("");
            c("BalanceBox").Text("");
            c("AmountBox").Text("");
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
