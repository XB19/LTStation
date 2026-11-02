package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** GroupItem : création (GroupID = -1), modification ou suppression d'un groupe. */
@SwWindow("CardMake.GroupItem")
public class GroupItem extends CardMakeForm {

    private int GroupID = -1;

    @Override
    protected void init() {
        GroupID = argInt("GroupID");
        if (GroupID != -1) {
            LoadGroup();
        }
    }

    private void LoadGroup() {
        try {
            Row groups = sendRow("ExtendGetSingleGroup", Row.of("GroupID", GroupID));
            c("GroupNameBox").Text(groups.str("Name"));
            c("GroupAddressBox").Text(groups.str("Address"));
            c("GroupPhoneBox").Text(groups.str("Telephone"));
            c("GroupEmailBox").Text(groups.str("Email"));
            c("GroupCNoBox").Text(groups.str("CertificateNo"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            if (c("GroupNameBox").Text().isEmpty()) {
                throw new IllegalStateException("Name is necessary");
            }
            send("ExtendUpdateGroup", Row.of("GroupID", GroupID, "Name", c("GroupNameBox").Text(), "Address", c("GroupAddressBox").Text(),
                "Telephone", c("GroupPhoneBox").Text(), "Email", c("GroupEmailBox").Text(), "CertificateNo", c("GroupCNoBox").Text(),
                "UpdateType", GroupID == -1 ? "Add" : "Update", "CompanyID", CM.CompanyID));
            MessageBox("Deal Success");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeleteBtn_Click() {
        try {
            send("ExtendUpdateGroup", Row.of("GroupID", GroupID, "UpdateType", "Delete", "CompanyID", CM.CompanyID));
            MessageBox("Deal Success");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
