package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** AuthShiftBox : rapports de poste automatiques autorisés ou non (AuthShift). */
@SwWindow("NormalForm.AuthShiftBox")
public class AuthShiftBox extends SwForm {

    @Override
    protected void init() {
        c(IniInfo.AuthShift ? "radioButtonAllow" : "radioButtonForbid").Checked(true);
    }

    public void radioButtonAllow_CheckedChanged() {
        try {
            if (sender.equals("radioButtonAllow")) {
                IniInfo.AuthShift = true;
                BaseFunction.SetConfigValue("AuthShift", "1");
            } else if (sender.equals("radioButtonForbid")) {
                IniInfo.AuthShift = false;
                BaseFunction.SetConfigValue("AuthShift", "0");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
