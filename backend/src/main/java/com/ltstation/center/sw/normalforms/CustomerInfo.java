package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxScreenView.CustomerInfo : premier client à crédit et ses coordonnées. */
@SwWindow("BoxScreenView.CustomerInfo")
public class CustomerInfo extends SwForm {

    public void CustomerInfo_Load() {
        try {
            Row credit = db().first("PersonalCredit");
            if (credit != null) {
                ShowData(credit);
                ShowPersonalCredit(credit);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowData(Row personal) {
        g("OilDataView").RowsClear();
        g("OilDataView").RowsAdd(personal.get("ID"), personal.str("AccountName"), personal.str("Phone"));
    }

    private void ShowPersonalCredit(Row credit) {
        c("CardNumberBox").Text(credit.str("CardNumber"));
        c("CarNumberBox").Text(credit.str("CarLicense"));
        c("AccountNameBox").Text(credit.str("AccountName"));
        c("PhoneBox").Text(credit.str("Phone"));
    }

    public void CancelBtn_Click() {
        Close("Cancel");
    }

    public void ConfirmBtn_Click() {
        Close("OK");
    }

    public void OilDataView_CellClick() {
        try {
            int index = g("OilDataView").CurrentRowIndex();
            Row credit = db().first("PersonalCredit", "{ID} = :i", p("i", Integer.parseInt(String.valueOf(g("OilDataView").Cell(index, 0)))));
            if (credit != null) {
                ShowPersonalCredit(credit);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
