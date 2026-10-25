package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** RepayFrom(ID) : remboursement d'une facture de carte de crédit (groupe). */
@SwWindow("RepayFrom")
public class RepayFrom extends SwForm {

    private int ID;

    @Override
    protected void init() {
        ID = argInt("ID");
    }

    public void skinButton1_Click() {
        Close("Cancel");
    }

    private static BigDecimal dec(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void ReapyBtn_Click() {
        try {
            if (ID == 0 || c("RepayAmount").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLost"));
            }
            int result;
            try {
                result = Integer.parseInt(c("RepayBillID").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("InformationError"));
            }
            BigDecimal result2 = dec(c("RepayAmount").Text());
            if (result2 == null) {
                throw new IllegalStateException(L("PayMentError"));
            }
            BigDecimal result3 = dec(c("Amount").Text());
            if (result2.compareTo(result3 == null ? BigDecimal.ZERO : result3) > 0) {
                throw new IllegalStateException(L("OutOfPayMent"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", result, "Mess", result2.toPlainString());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/DealBillRepay", Wire.json(obj));
            if (r.Result()) {
                MessageBox(L("OperateSuccess"));
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void RepayFrom_Load() {
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", String.valueOf(ID));
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetBillRepay", Wire.json(obj));
        if (r.Result()) {
            Row d = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("RepayBillID").Text(d.str("ID"));
            c("GroupName").Text(d.get("GroupName"));
            c("BillDate").Text(d.get("BillDate"));
            c("Amount").Text(Conv.toDecimal(d.get("Amount")).subtract(Conv.toDecimal(d.get("RepayAmount"))).toPlainString());
            c("ReapyBtn").Enabled(d.i("Statue") != 2);
        }
    }
}
