package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** Login de CardMake : ExtendGetLogin, puis Main_UI. */
@SwWindow(value = "CardMake.Login", anonymous = true)
public class Login extends CardMakeForm {

    public void CardCreateBtn_Click() {
        try {
            String text = c("LoginBox").Text();
            String text2 = c("PasswordBox").Text();
            if (text.isEmpty() || text2.isEmpty()) {
                throw new IllegalStateException("LoginName or Password Lost");
            }
            Row r = sendRow("ExtendGetLogin", Row.of("LoginName", text, "Password", text2));
            CM.EmpID = r.i("EmpID");
            CM.CompanyID = r.i("CompanyID");
            CM.EmpName = r.str("Name");
            CM.BaseAuth = r.str("BaseAuth");
            SessionLogin(CM.EmpID);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
