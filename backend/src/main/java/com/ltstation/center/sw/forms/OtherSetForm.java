package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** OtherSetForm : vitesse de rafraîchissement de l'écran principal. */
@SwWindow("OtherSetForm")
public class OtherSetForm extends SwForm {

    public void Confirm_Click() {
        try {
            IniInfo.SpeedRun = Conv.toInt(Conv.toStr(c("SpeedSelectBox").SelectedItem()));
            BaseFunction.SetConfigValue("Speed", String.valueOf(IniInfo.SpeedRun));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
