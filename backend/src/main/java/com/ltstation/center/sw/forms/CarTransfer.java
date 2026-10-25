package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** CarTransfer(GroupID, CarUserID) : transfert de solde du groupe vers un véhicule. */
@SwWindow("CarTransfer")
public class CarTransfer extends SwForm {

    private int Gid = -1;
    private int CID = -1;

    @Override
    protected void init() {
        Gid = argInt("GroupID");
        CID = argInt("CarUserID");
        Row g = db().first("CarGroup", "{ID} = :id", p("id", Gid));
        if (g != null) {
            c("GroupName").Text(g.get("CarGroupName"));
        }
        Row u = db().first("CarUser", "{ID} = :id", p("id", CID));
        if (u != null) {
            c("UserName").Text(u.get("UserName"));
            c("CarNo").Text(u.get("PlateNumber"));
            c("Account").Text(u.str("CustomerAccountID"));
        }
    }

    public void AddButton_Click() {
        try {
            if (Gid == -1 || CID == -1) {
                throw new IllegalStateException("转入转出对象错误");
            }
            BigDecimal result;
            try {
                result = new BigDecimal(c("Amount").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("金额格式错误");
            }
            Row obj = SW.dto().make("TransferUser", "GroupID", Gid, "UserID", CID, "EmpID", IniInfo.LoginID, "Amount", result);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarBalanceUpdate", Wire.json(obj));
            if (r.Result()) {
                MessageBox("操作完成");
                Close("OK");
                return;
            }
            throw new IllegalStateException(r.Mess());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
