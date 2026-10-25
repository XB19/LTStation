package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;

/** Login : page de connexion d'origine (web/login/login.html) ; identifiant = Memo1, mot de passe = Memo2. */
@SwWindow(value = "Login", anonymous = true)
public class Login extends SwWebForm {

    @Override
    protected String Url() {
        return "web/login/login.html";
    }

    @Override
    protected void init() {
        JsFunction("LoginLoad", "LoginLoad");
        JsFunction("LanguageLoad", "LanguageLoad");
    }

    public void LoginLoad() {
        String[] array = JsArg().split("\\|", -1);
        String userName = array[0];
        String pass = array.length > 1 ? array[1] : "";
        Row employees = db().first("Employees", "{Memo1} = :u AND {Memo2} = :p", p("u", userName, "p", pass));
        if (employees != null) {
            if ("0".equals(employees.str("StationType"))) {
                MessageBox(L("AuthorityProblem"));
            } else {
                IniInfo.LoginID = employees.i("EmployeeID");
                IniInfo.LoginName = employees.s("Name");
                IniInfo.LoginType = Conv.toInt(employees.get("StationType"));
                IniInfo.Auth = employees.s("Auth");
                IniInfo.LoginPassword = pass;
                SessionLogin();
                Close("OK");
            }
        } else {
            MessageBox(L("LoginFailed"));
        }
    }

    public void LanguageLoad() {
        IniInfo.Language = JsArg();
        MultiLanguage.languageChange(IniInfo.Language);
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        if (IniInfo.NormalType != 2) {
            String text = IniInfo.Center + "GPRSModal/images/JoyFel.jpg";
            ExecuteJavascript("UpdateLogin('" + IniInfo.Language + "','" + text + "')");
        }
    }
}
