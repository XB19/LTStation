package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** GroupManage : liste des groupes de clients. */
@SwWindow("CardMake.GroupManage")
public class GroupManage extends CardMakeForm {

    public void GroupManage_Load() {
        LoadGroups();
    }

    private void LoadGroups() {
        try {
            g("Box_Device").RowsClear();
            for (Row item : sendRows("ExtendGetGroup", Row.of("CompanyID", CM.CompanyID, "EmpID", CM.EmpID))) {
                g("Box_Device").RowsAdd(item.get("GroupID"), item.str("Name"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void Edit(int id) {
        ShowDialog("CardMake.GroupItem", r -> {
            if (r.ok()) {
                LoadGroups();
            }
        }, "GroupID", id);
    }

    public void AddItem_Click() {
        Edit(-1);
    }

    public void Box_Device_CellDoubleClick() {
        try {
            Edit(Conv.toInt(g("Box_Device").Cell(g("Box_Device").CurrentRowIndex(), 0)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void GroupManage_FormClosed() {
        Close("OK");
    }
}
