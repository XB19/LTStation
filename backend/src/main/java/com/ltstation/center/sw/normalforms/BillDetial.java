package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.Map;

/** BoxScreenViewV2.BillDetial : relevé d'un client à crédit (argument Bill = CenterPersonalCreditBill), réglé sur Center. */
@SwWindow("BoxScreenViewV2.BillDetial")
public class BillDetial extends SwForm {

    @SuppressWarnings("unchecked")
    private Row bill() {
        return arg("Bill") instanceof Map<?, ?> m ? new Row((Map<String, ?>) m) : null;
    }

    public void BillDetial_Load() {
        try {
            Row creditBill = bill();
            if (creditBill != null) {
                c("AccountNameBox").Text(creditBill.str("AccountName"));
                c("AmountBox").Text(creditBill.dec("SumAmount").toPlainString());
                c("BillDateBox").Text(creditBill.str("BillDate"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            Row creditBill = bill();
            CustomerRespone r = BaseFunction.post("Station/DealPersonalCreditBill", SW.dto().make("PersonalCreditBillRequest",
                "ID", creditBill.get("ID"), "PersonalCreditID", creditBill.get("PersonalCreditID")));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
