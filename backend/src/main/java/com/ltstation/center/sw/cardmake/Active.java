package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwWindow;

/** Active : activation du poste (code fourni pour la valeur affichée dans ValidBox). */
@SwWindow(value = "CardMake.Active", anonymous = true)
public class Active extends CardMakeForm {

    private String M = "";

    @Override
    protected void init() {
        M = argStr("MacD");
        c("ValidBox").Text(M);
    }

    public void ActiveBtn_Click() {
        try {
            String text = c("StationNameBox").Text();
            String text2 = c("LoginNameBox").Text();
            String text3 = c("LoginPasswordBox").Text();
            String text4 = c("ActiveBox").Text();
            if (!M.equals(BaseFunction.DecryptByAES(text4))) {
                throw new IllegalStateException("Valid Message Error");
            }
            if (text.isEmpty() || text2.isEmpty() || text3.isEmpty() || text4.isEmpty()) {
                throw new IllegalStateException("MessageLost");
            }
            send("CreateStationPreCard", Row.of("StationName", text, "LoginName", text2, "LoginPassword", text3, "Active", text4,
                "PlatformActive", c("PlatformBox").Text()));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
