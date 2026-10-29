package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** CarGroupUpdate(ID) : fiche d'un groupe de véhicules ; le bouton supprime le groupe et ses véhicules. */
@SwWindow("CarGroupUpdate")
public class CarGroupUpdate extends SwForm {

    private int ID = -1;

    @Override
    protected void init() {
        ID = argInt("ID");
    }

    public void CarGroupUpdate_Load() {
        try {
            Row g = db().first("CarGroup", "{ID} = :id", p("id", ID));
            if (g != null) {
                c("GroupNameBox").Text(g.get("CarGroupName"));
                c("GroupAddressBox").Text(g.get("CarGroupAddress"));
                c("GroupCreditBox").Text(g.get("CarGroupCreditCode"));
                c("GroupPhoneBox").Text(g.get("Phone"));
                return;
            }
            throw new IllegalStateException("Groupe introuvable");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddButton_Click() {
        try {
            Row obj = SW.dto().make("CarGroupRequest", "ID", ID, "UpdateType", "DeleteAll", "EmpID", IniInfo.LoginID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CarGroupUpdate", Wire.json(obj));
            if (r.Result()) {
                Row g = db().first("CarGroup", "{ID} = :id", p("id", ID));
                if (g == null) {
                    throw new IllegalStateException("Groupe introuvable");
                }
                List<Row> list = db().list("CarUser", "{CarGroupID} = :id", p("id", ID));
                List<Integer> ids = list.stream().map(t -> t.i("ID")).toList();
                List<Integer> accounts = list.stream().map(t -> t.i("CustomerAccountID")).toList();
                db().delete("CustomerAccounts", "{CustomerAccountID} IN (:ids)", p("ids", accounts));
                db().delete("CylinderData", "{CarUserID} IN (:ids)", p("ids", ids));
                for (Row u : list) {
                    db().deleteEntity("CarUser", u);
                }
                db().deleteEntity("CarGroup", g);
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
