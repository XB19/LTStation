package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** LogShow : écriture des journaux activée ou non (LogWrite). */
@SwWindow("NormalForm.LogShow")
public class LogShow extends SwForm {

    public void LogShow_Load() {
        c(IniInfo.logwrite == 0 ? "radioButtonForbid" : "radioButtonAllow").Checked(true);
    }

    public void radioButtonAllow_CheckedChanged() {
        try {
            if (sender.equals("radioButtonAllow")) {
                IniInfo.logwrite = 1;
                BaseFunction.SetConfigValue("LogWrite", "1");
            } else if (sender.equals("radioButtonForbid")) {
                IniInfo.logwrite = 0;
                BaseFunction.SetConfigValue("LogWrite", "0");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
