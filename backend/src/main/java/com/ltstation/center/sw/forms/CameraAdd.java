package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** CameraAdd : ajout d'une caméra de surveillance associée à un pistolet. */
@SwWindow("CameraAdd")
public class CameraAdd extends SwForm {

    @Override
    protected void init() {
        LoadNoz();
    }

    private void LoadNoz() {
        try {
            c("NozSelect").DataSource(db().list("GunConfig", "ORDER BY {GUNCODE}", null), "GUNCODE", "GUNCODE");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddButton_Click() {
        try {
            String name = c("NameBox").Text();
            String ip = c("IPBox").Text();
            int num = Conv.toInt(c("NozSelect").SelectedValue());
            if (name.isEmpty() || ip.isEmpty() || num == 0) {
                throw new IllegalStateException("信息缺失");
            }
            if (db().first("CameraSupervise", "{Name} = :n", p("n", name)) != null) {
                throw new IllegalStateException("名称重复");
            }
            db().insert("CameraSupervise", Row.of("Name", name, "IP", ip, "NozID", num));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
