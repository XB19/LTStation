package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** Register : activation de la licence du poste (adresse MAC chiffrée). */
@SwWindow(value = "Register", setup = true)
public class Register extends SwForm {

    public void Register_Load() {
        List<String> mac = BaseFunction.GetMacAddress();
        if (mac != null && !mac.isEmpty()) {
            c("Info").Text(BaseFunction.EncryptByAes(mac.get(0)));
        } else {
            MessageBox(L("MacError"));
        }
    }

    public void SaveButton_Click() {
        String text = c("Info").Text();
        String text2 = c("ActiveInfo").Text();
        if (BaseFunction.DecryptByAES(text2).equals(text)) {
            var tx = db().begin();
            try {
                db().delete("RegisterInfo", null, null);
                db().insert("RegisterInfo", Row.of("SourceInfo", text, "SecretInfo", text2));
                tx.commit();
                Close("OK");
            } catch (RuntimeException ex) {
                tx.rollback();
            }
            return;
        }
        MessageBox(L("ActiveFailed"));
    }
}
