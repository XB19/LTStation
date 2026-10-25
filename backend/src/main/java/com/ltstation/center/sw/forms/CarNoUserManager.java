package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** CarNoUserManager : groupes de véhicules, véhicules d'un groupe, recherche par plaque. */
@SwWindow("CarNoUserManager")
public class CarNoUserManager extends SwForm {

    public void AddButton_Click() {
        ShowDialog("CarGroupManager", r -> LoadGroupSelect());
    }

    private void LoadGroupSelect() {
        try {
            c("GroupSelect").DataSource(db().list("CarGroup"), "CarGroupName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void addRow(Row item) {
        g("CardUserView").RowsAdd(item.get("ID"), item.get("UserName"), item.get("PlateNumber"), item.get("VehicleType"), item.get("Balance"),
            BaseFunction.GetGroupName(item.i("CarGroupID")));
    }

    private void ShowCarList(int groupId) {
        try {
            g("CardUserView").RowsClear();
            for (Row item : db().list("CarUser", "{CarGroupID} = :g", p("g", groupId))) {
                addRow(item);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CarNoUserManager_Load() {
        IniInfo.VIDDO = true;
        LoadGroupSelect();
    }

    public void GroupSelect_SelectedIndexChanged() {
        try {
            Row g = db().first("CarGroup", "{ID} = :id", p("id", Conv.toInt(c("GroupSelect").SelectedValue())));
            if (g != null) {
                c("GroupAddress").Text(g.get("CarGroupAddress"));
                c("GroupPhone").Text(g.get("Phone"));
                c("GroupBalance").Text(g.str("Balance"));
                ShowCarList(g.i("ID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Add_Child_Click() {
        int group = Conv.toInt(c("GroupSelect").SelectedValue());
        ShowDialog("CarAdd", r -> {
            if (r.ok()) {
                ShowCarList(Conv.toInt(c("GroupSelect").SelectedValue()));
            }
            LoadGroupSelect();
        }, "GroupID", group);
    }

    public void BalBtn_Click() {
        ShowDialog("CarBalanceChange", r -> { }, "GroupID", Conv.toInt(c("GroupSelect").SelectedValue()));
    }

    public void CardUserView_CellDoubleClick() {
        try {
            ShowDialog("CarUpdateMess", r -> {
                if (r.ok()) {
                    ShowCarList(Conv.toInt(c("GroupSelect").SelectedValue()));
                }
            }, "ID", Conv.toInt(g("CardUserView").Cell(e.i("RowIndex"), 0)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CarNoUserManager_FormClosing() {
        IniInfo.VIDDO = false;
    }

    /** Recherche par plaque quand le pointeur quitte le champ. */
    public void textBox1_MouseLeave() {
        try {
            Row car = db().first("CarUser", "{PlateNumber} = :p", p("p", c("textBox1").Text()));
            if (car == null) {
                throw new IllegalStateException("车牌不存在");
            }
            g("CardUserView").RowsClear();
            addRow(car);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
