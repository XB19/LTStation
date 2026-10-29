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

/** CarBalanceChange(GroupID) : solde d'un groupe de véhicules (dépôt, correction, répartition) et ses véhicules. */
@SwWindow("CarBalanceChange")
public class CarBalanceChange extends SwForm {

    private int Gid = -1;

    @Override
    protected void init() {
        LoadGroupSelect();
        Gid = argInt("GroupID");
        ShowCarList();
        GetBalance();
        c("GroupSelect").SelectedValue(Gid);
    }

    private void GetBalance() {
        Row obj = SW.dto().make("CarGroupRequest", "UpdateType", "Get", "EmpID", IniInfo.LoginID, "ID", Gid);
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarGroupUpdate", Wire.json(obj));
        if (r.Result()) {
            c("BalanceBox").Text(Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())).str("Balance"));
        }
    }

    private void LoadGroupSelect() {
        try {
            c("GroupSelect").DataSource(db().list("CarGroup"), "CarGroupName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ShowCarList() {
        try {
            g("CardUserView").RowsClear();
            for (Row item : db().list("CarUser", "{CarGroupID} = :g", p("g", Gid))) {
                g("CardUserView").RowsAdd(item.get("ID"), item.get("UserName"), item.get("PlateNumber"), item.get("VehicleType"), item.get("Balance"),
                    item.get("CarGroupID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void change(String updateType, BigDecimal amount) {
        Row obj = SW.dto().make("GDAmount", "GroupID", Gid, "UpdateType", updateType, "EmpID", IniInfo.LoginID);
        if (amount != null) {
            obj.put("Amount", amount);
        }
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GroupBalanceChange", Wire.json(obj));
        if (r.Result()) {
            GetBalance();
            return;
        }
        throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
    }

    private static BigDecimal amount(String text, String error) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void DepositButton_Click() {
        try {
            change("Deposit", amount(c("DepositAmount").Text(), "Format du montant rechargé incorrect"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DebitBtn_Click() {
        try {
            change("Red", amount(c("DeBox").Text(), "Format du montant contre-passé incorrect"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void TrasnlateBtn_Click() {
        try {
            change("AVG", null);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardUserView_CellDoubleClick() {
        try {
            int index = g("CardUserView").CurrentRowIndex();
            if (index < 0) {
                throw new IllegalStateException("Object reference not set to an instance of an object.");
            }
            int carUserId = Conv.toInt(g("CardUserView").Cell(index, "ID"));
            ShowDialog("CarTransfer", r -> {
                if (r.ok()) {
                    GetBalance();
                }
            }, "GroupID", Gid, "CarUserID", carUserId);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
