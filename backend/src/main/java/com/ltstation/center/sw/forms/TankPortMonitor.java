package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** TankPortMonitor : port série de la sonde des cuves (ATG). */
@SwWindow("TankPortMonitor")
public class TankPortMonitor extends SwForm {

    public void TankPortMonitor_Load() {
        c("TankPort").DataSource(db().list("ComConfig", "ORDER BY {CODE}", null), "CODE", "CODE");
        Row config = db().first("MonitorConfig");
        if (config != null) {
            Row com = db().first("ComConfig", "{PORT} = :p", p("p", config.get("PORTCODE")));
            if (com != null) {
                c("TankPort").SelectedValue(com.get("CODE"));
            }
        }
    }

    public void SaveBtn_Click() {
        try {
            int port = c("TankPort").SelectedRow().i("PORT");
            Row m = db().first("MonitorConfig");
            if (m == null) {
                db().insert("MonitorConfig", Row.of("CODE", 1, "CalcVol", 1.0, "NAME", "ATG", "PORTCODE", port));
            } else {
                m.put("PORTCODE", port);
                db().update("MonitorConfig", m);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
