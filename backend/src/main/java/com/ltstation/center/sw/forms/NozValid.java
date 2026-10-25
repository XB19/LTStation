package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** NozValid : blocage / déblocage des pistolets (code de maintenance du jour et de l'heure). */
@SwWindow("NozValid")
public class NozValid extends SwForm {

    private String DTS = "";

    @Override
    protected void init() {
        DTS = "8" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyyHH")) + "666";
    }

    public void Save_Click() {
        try {
            if (!c("Password").Text().equals(DTS)) {
                MessageBox(L("ValidError"));
                return;
            }
            String text = c("Radio1").Checked() ? c("DTime").Value().format(DateTimeFormatter.ofPattern("yyyyMMddHH")) : "";
            Station.LockMachine(text);
            Close("OK");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
