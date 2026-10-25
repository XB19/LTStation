package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** AddAuthority : nouveau profil de droits (cases cochées → « 1,3,5, »). */
@SwWindow("AddAuthority")
public class AddAuthority extends SwForm {

    public void ShiftClose_Click() {
        Close();
    }

    public void Confirm_Click() {
        String text = c("AuthorityName").Text();
        if (text.isEmpty()) {
            MessageBox(L("PLSWriteName"));
            return;
        }
        StringBuilder text2 = new StringBuilder();
        for (String n : Controls("AuthorithBox")) {
            if (TypeOf(n).endsWith("CheckBox") && c(n).Checked()) {
                text2.append(c(n).Text(), 0, 1).append(',');
            }
        }
        Integer max = db().scalar(db().sql("AuthorityMess", "SELECT MAX({User_IDMess}) FROM [AuthorityMess]"), null, Integer.class);
        if (max == null) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        db().insert("AuthorityMess", Row.of("Name", text, "User_IDMess", max + 1, "AuthorityContent", text2.toString()));
        MessageBox(L("OperateSuccess"));
        Close();
    }
}
