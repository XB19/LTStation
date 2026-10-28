package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** TipForm : nouvelle version des boîtiers ; OK = tout mettre à jour, Cancel = arrêter (argument version). */
@SwWindow("NormalForm.TipForm")
public class TipForm extends SwForm {

    public void TipForm_Load() {
        c("label1").Text(argStr("version") + " " + L("VersionUpdate"));
    }

    public void OpenAllPumpBtn_Click() {
        Close("OK");
    }

    public void StopAllPumpBtn_Click() {
        Close("Cancel");
    }
}
