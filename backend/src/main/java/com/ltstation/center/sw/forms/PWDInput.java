package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.lang.reflect.InvocationTargetException;

/** PWDInput : saisie du code PIN (4 chiffres) d'une carte, transmis à la fenêtre propriétaire selon BtnName. */
@SwWindow("PWDInput")
public class PWDInput extends SwForm {

    public String BtnName = "";

    @Override
    protected void init() {
        BtnName = argStr("BtnName");
        On("PWD", "TextChanged", "SkinTxt_TextChanged");
    }

    public void SkinTxt_TextChanged() {
        String text = c("PWD").Text();
        if (text.isEmpty()) {
            return;
        }
        try {
            if (!BaseFunction.ValidMessage(text, "^[0-9]*$")) {
                throw new IllegalStateException(L("FormatError"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            c("PWD").Text(text.substring(0, text.length() - 1));
        }
    }

    /** ((CardUpDate)/(Deposit))base.Owner).ReLoad(pwd, BtnName). */
    private boolean ReLoad(String pwd) {
        try {
            return (Boolean) Owner.getClass().getMethod("ReLoad", String.class, String.class).invoke(Owner, pwd, BtnName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof Error er) {
                throw er;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new ClassCastException("Unable to cast object of type '" + Owner.getClass().getSimpleName() + "'.");
        }
    }

    public void SureButton_Click() {
        try {
            String pwd = c("PWD").Text();
            if (pwd.length() != 4) {
                throw new IllegalStateException(L("NeedPasswordMaxLength"));
            }
            boolean ok;
            switch (BtnName) {
                case "CardNoSearch" -> ok = ((CardLog) Owner).Card_Reload(pwd, BtnName);
                case "ReadBtn" -> {
                    ((RelieveCard) Owner).Pwd = pwd;
                    ok = true;
                }
                default -> ok = ReLoad(pwd);
            }
            if (!ok) {
                throw new IllegalStateException(L("PassowrdError"));
            }
            Close();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CancleButton_Click() {
        Close();
    }
}
