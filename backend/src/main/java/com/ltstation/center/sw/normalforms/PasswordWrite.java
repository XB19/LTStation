package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** PasswordWrite : mot de passe de la station (demandé à Center) pour activer les journaux. */
@SwWindow("NormalForm.PasswordWrite")
public class PasswordWrite extends SwForm {

    private String Password = "";

    public void PasswordWrite_Load() {
        CustomerRespone r = BaseFunction.post("ServerDeal/GetStationPassword", SW.dto().make("CustomerParam", "Code", IniInfo.Code));
        if (r.Result()) {
            Password = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())).str("Code");
        } else {
            Close("Cancel");
        }
    }

    public void Confirm_Click() {
        try {
            if (!c("PasswordText").Text().equals(Password)) {
                throw new IllegalStateException("Password Error");
            }
            IniInfo.logwrite = 1;
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
