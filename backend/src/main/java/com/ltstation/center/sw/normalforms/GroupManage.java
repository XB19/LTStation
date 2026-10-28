package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;

/** Card.GroupManage : groupes de clients (liste locale, puis liste de Center après un ajout ou une modification). */
@SwWindow("Card.GroupManage")
public class GroupManage extends SwForm {

    private void GetGroup() {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetGroup",
                SW.dto().make("Card_Login_Return", "CompanyID", IniInfo.CompanyID, "EmpID", IniInfo.LoginID));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            g("Box_Device").RowsClear();
            for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                g("Box_Device").RowsAdd(item.get("GroupID"), item.str("Name"), BaseInfo.GetGroupType(item.i("GroupType")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void GroupShow() {
        try {
            g("Box_Device").RowsClear();
            for (Row item : db().list("Group", "{GroupID} <> 100000001", null)) {
                g("Box_Device").RowsAdd(item.get("GroupID"), item.str("Name"), BaseInfo.GetGroupType(item.i("GroupType")));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void GroupManage_Load() {
        GroupShow();
    }

    public void AddItem_Click() {
        ShowDialog("Card.GroupItem", r -> {
            if ("OK".equals(r.result())) {
                GetGroup();
            }
        }, "GroupID", -1);
    }

    public void Box_Device_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("Box_Device").Cell(g("Box_Device").CurrentRowIndex(), 0));
            ShowDialog("Card.GroupItem", r -> {
                if ("OK".equals(r.result())) {
                    GetGroup();
                }
            }, "GroupID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void GroupManage_FormClosed() {
        // DialogResult = OK : la fenêtre appelante recharge ses groupes
    }
}
