package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** CardMainBoard : menu de fabrication des cartes (clés, cartes, PSAM). */
@SwWindow("CardMainBoard")
public class CardMainBoard extends SwForm {

    public void CreateKey_Click() {
        ShowDialog("KeyMainBoard", r -> { }, "ID", 0);
    }

    public void CreateCard_Click() {
        ShowDialog("CardMake", r -> { }, "IsCreate", true);
    }

    public void CreatePSAM_Click() {
        ShowDialog("CreatePSAM", r -> { });
    }

    public void Update_Click() {
        ShowDialog("CardMake", r -> { }, "IsCreate", false);
    }
}
