package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.util.List;

/** ChargeForm : client à crédit : lecture de la carte, dépôt et historique. */
@SwWindow("CardMake.ChargeForm")
public class ChargeForm extends CardMakeForm {

    private String UserName = "";
    private String Phone = "";
    private String Phone2 = "";

    public void CardReadBtn_Click() {
        try {
            Row d = sendRow("GetCardMessage", Row.of("ASN", ReadAsn(0), "CompanyID", CM.CompanyID, "StationID", CM.StationID));
            String pa = d.str("Password");
            if (pa != null && !pa.isEmpty() && !"9999".equals(pa)) {
                ShowDialog("CardMake.PasswordInput", r -> {
                    if (r.ok()) {
                        ShowCard(d);
                    } else {
                        MessageBox("Password Error");
                    }
                }, "PA", pa);
                return;
            }
            ShowCard(d);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCard(Row d) {
        c("GroupNameBox").Text(Conv.toStr(d.get("GroupID")));
        c("CardNumberBox").Text(d.str("Asn"));
        c("CardUserNameBox").Text(d.str("UserName"));
        c("PhoneNumberBox").Text(d.str("Phone"));
        c("PhoneNumberBoxSecond").Text(d.str("SecondPhone"));
        c("BalanceBox").Text(Conv.toStr(d.get("Balance")));
        c("CardTypeBox").Text(d.i("Card_Type") == 0 ? "Utilisateur" : "VIP");
        c("CardStatueBox").Text(Conv.toStr(d.get("CardStatue")));
        c("OperatorBox").Text(CM.EmpName);
        UserName = d.str("UserName");
        Phone = d.str("Phone");
        Phone2 = d.str("SecondPhone");
        ShowCardLog(1);
    }

    public void CardDeposit_Click() {
        try {
            String text = c("CardNumberBox").Text();
            if (!text.equals(ReadAsn(0))) {
                throw new IllegalStateException("Card No Error");
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
            send("CardBalanceChange", Row.of("Asn", text, "C_Type", 0, "Amount", result, "DisAmount", BigDecimal.ZERO, "EmpID", CM.EmpID, "CompanyID", CM.CompanyID,
                "StationID", CM.StationID, "LoginType", 0, "Memo", c("POBox").Text()));
            MessageBox("Deal Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCardLog(int PageIndex) {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/NPGetUserLog",
                Row.of("PageIndex", PageIndex, "PageSize", 20, "TransactionType", 2, "CardNo", c("CardNumberBox").Text()));
            if (!r.Result()) {
                return;
            }
            g("TransactionView").RowsClear();
            c("PageLabel").Text(PageIndex + "/" + r.TotalPage());
            c("TotalCardNumberBox").Text(String.valueOf(r.TotalCount()));
            c("TotalAmountBox").Text(Conv.toStr(r.TotalAmount()));
            for (Row i : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                g("TransactionView").RowsAdd(i.str("ASN"), UserName, Phone, Phone2, i.get("Balance"), i.str("Memo"), i.get("ID"), i.str("OperatorName"),
                    dt(i.get("TransDatetime")), i.get("Amount"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ClearBtn_Click() {
        for (String n : List.of("GroupNameBox", "CardNumberBox", "CardUserNameBox", "PhoneNumberBox", "PhoneNumberBoxSecond", "BalanceBox", "CardTypeBox", "CardStatueBox",
            "BatchBox", "POBox", "TransactionBox", "OperatorBox", "DateTimeBox", "DepositAmountBox")) {
            c(n).Text("");
        }
    }

    public void NextBtn_Click() {
        try {
            String[] array = c("PageLabel").Text().split("/");
            int num = Conv.toInt(array[0]);
            int num2 = Conv.toInt(array[1]);
            if ("PreBtn".equals(sender)) {
                if (num == 0 || num == 1) {
                    throw new IllegalStateException("Is First Page Now");
                }
                ShowCardLog(num - 1);
            } else if ("NextBtn".equals(sender)) {
                if (num == num2) {
                    throw new IllegalStateException("Is Last Page Now");
                }
                ShowCardLog(num + 1);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
