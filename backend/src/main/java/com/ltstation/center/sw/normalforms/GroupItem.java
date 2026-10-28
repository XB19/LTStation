package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** Card.GroupItem : création (argument GroupID = -1) ou modification d'un groupe sur Center. */
@SwWindow("Card.GroupItem")
public class GroupItem extends SwForm {

    private int groupId() {
        return arg("GroupID") == null ? -1 : argInt("GroupID");
    }

    @Override
    protected void init() {
        if (groupId() != -1) {
            GetGroup();
        }
    }

    private void GetGroup() {
        try {
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendGetSingleGroup", SW.dto().make("Card_Group_Deal", "GroupID", groupId()));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row g = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("GroupNameBox").Text(g.str("Name"));
            c("GroupAddressBox").Text(g.str("Address"));
            c("GroupPhoneBox").Text(g.str("Telephone"));
            c("GroupEmailBox").Text(g.str("Email"));
            c("GroupCNoBox").Text(g.str("CertificateNo"));
            c("GroupType").SelectedIndex(g.i("GroupType"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            if (c("GroupNameBox").Text().isEmpty()) {
                throw new IllegalStateException("Name is necessary");
            }
            CustomerRespone r = BaseFunction.post("ExtendUpdate/ExtendUpdateGroup", SW.dto().make("Groups", "GroupID", groupId(), "Name",
                c("GroupNameBox").Text(), "Address", c("GroupAddressBox").Text(), "Telephone", c("GroupPhoneBox").Text(), "Email", c("GroupEmailBox").Text(),
                "CertificateNo", c("GroupCNoBox").Text(), "UpdateType", groupId() == -1 ? "Add" : "Update", "CompanyID", IniInfo.CompanyID,
                "GroupType", c("GroupType").SelectedIndex()));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            MessageBox("Deal Success");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
