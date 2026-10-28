package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** StationPassword : mot de passe vérifié auprès de Center (action Center passée à l'ouverture : argument url). */
@SwWindow("NormalForm.StationPassword")
public class StationPassword extends SwForm {

    public void StationPassword_Load() {
    }

    public void Confirm_Click() {
        try {
            CustomerRespone r = BaseFunction.post(argStr("url"), SW.dto().make("CustomerParam", "Code", IniInfo.Code));
            if (r.Result()) {
                if (!Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())).str("Code").equals(c("PasswordText").Text())) {
                    throw new IllegalStateException("Password Error");
                }
                Close("OK");
            } else {
                Close("Cancel");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cancle_Click() {
        Close("Cancel");
    }
}
