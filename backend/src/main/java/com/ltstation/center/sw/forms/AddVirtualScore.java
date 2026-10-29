package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** AddVirtualScore : création d'un compte de points virtuel sur Center. */
@SwWindow("AddVirtualScore")
public class AddVirtualScore extends SwForm {

    public void QueryButton_Click() {
        try {
            String text = c("UserName").Text();
            String text2 = c("UserPhone").Text();
            if (text.isEmpty() || text2.isEmpty()) {
                throw new IllegalStateException("Veuillez compléter les informations");
            }
            Row add = SW.dto().make("CenterVirtualUserAdd", "UserName", text, "Phone", text2, "EID", IniInfo.LoginID,
                "CompanyID", IniInfo.CompanyID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/AddVirtualScore", Wire.json(add));
            if (r.Result()) {
                MessageBox("Opération réussie");
                Close("OK");
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
