package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;

/** PortStatueChange : marche / arrêt des ports série des boîtiers. */
@SwWindow("NormalForm.PortStatueChange")
public class PortStatueChange extends SwForm {

    public void PortStatueChange_Load() {
        boolean run = NormalEngine.Ports.stream().anyMatch(p -> p.PortRun);
        c(run ? "StartBtn" : "CloseBtn").Checked(true);
    }

    public void CloseBtn_Click() {
        try {
            if (sender.equals("StartBtn")) {
                if (NormalEngine.Ports.isEmpty()) {
                    // SendMessage(FindWindow(« NormalSystem »), 1028, 0, 0)
                    NormalFunction.MainFormMessage.accept(1028, 0, 0);
                } else {
                    NormalEngine.PortRunStatueChange(true);
                }
            } else if (sender.equals("CloseBtn")) {
                NormalEngine.PortRunStatueChange(false);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
