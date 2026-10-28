package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** OverCountForm : nombre d'échecs réseau tolérés (IniInfo.OverCount). */
@SwWindow("NormalForm.OverCountForm")
public class OverCountForm extends SwForm {

    public void Confirm_Click() {
        try {
            int result;
            try {
                result = Integer.parseInt(c("OverCountText").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("FormatError"));
            }
            IniInfo.OverCount = result;
            // comme l'original : c'est SupervisionType qui est enregistré (OverCount reste en mémoire)
            BaseFunction.SetConfigValue("SupervisionType", String.valueOf(IniInfo.SupervisionType));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
