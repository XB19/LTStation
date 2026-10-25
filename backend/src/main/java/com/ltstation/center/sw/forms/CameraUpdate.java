package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** CameraUpdate(ID) : modification / suppression d'une caméra de surveillance. */
@SwWindow("CameraUpdate")
public class CameraUpdate extends SwForm {

    private int ID = -1;

    @Override
    protected void init() {
        ID = argInt("ID");
    }

    public void AddButton_Click() {
        try {
            String name = c("NameBox").Text();
            String ip = c("IPBox").Text();
            int num = Conv.toInt(c("NozSelect").SelectedValue());
            if (name.isEmpty() || ip.isEmpty() || num == 0) {
                throw new IllegalStateException("信息缺失");
            }
            if (db().first("CameraSupervise", "{Name} = :n AND {ID} <> :id", p("n", name, "id", ID)) != null) {
                throw new IllegalStateException("当前设备已配置");
            }
            Row cam = db().first("CameraSupervise", "{ID} = :id", p("id", ID));
            if (cam == null) {
                throw new IllegalStateException("设备不存在");
            }
            cam.put("Name", name);
            cam.put("IP", ip);
            cam.put("NozID", num);
            db().update("CameraSupervise", cam);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CameraUpdate_Load() {
        LoadNoz();
        LoadData();
    }

    private void LoadData() {
        try {
            Row cam = db().first("CameraSupervise", "{ID} = :id", p("id", ID));
            if (cam == null) {
                throw new IllegalStateException("信息不存在");
            }
            c("NameBox").Text(cam.get("Name"));
            c("IPBox").Text(cam.get("IP"));
            c("NozSelect").SelectedValue(cam.get("NozID"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadNoz() {
        try {
            c("NozSelect").DataSource(db().list("GunConfig", "ORDER BY {GUNCODE}", null), "GUNCODE", "GUNCODE");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DelBtn_Click() {
        try {
            db().delete("CameraSupervise", "{ID} = :id", p("id", ID));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
