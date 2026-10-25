package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** DeviceAdd(ID) : afficheur de prix / de paiement relié à un port série (0 = nouveau). */
@SwWindow("DeviceAdd")
public class DeviceAdd extends SwForm {

    private int ID;

    @Override
    protected void init() {
        ID = argInt("ID");
    }

    public void PriceDeviceAdd_Load() {
        LoadPort();
        if (ID != 0) {
            Row device = db().first("Device", "{ID} = :id", p("id", ID));
            if (device != null) {
                c("PortSelect").SelectedValue(device.get("Port"));
                c("DeviceName").Text(device.get("Name"));
                c("DeviceType").SelectedIndex(device.i("DeviceTypeID"));
            }
        }
    }

    private void LoadPort() {
        try {
            c("PortSelect").DataSource(db().list("ComConfig", "ORDER BY {PORT}", null), "PORT", "CODE");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Confirm_Click() {
        try {
            int code = c("PortSelect").SelectedRow().i("CODE");
            String name = c("DeviceName").Text();
            int selectedIndex = c("DeviceType").SelectedIndex();
            if (db().first("Device", "{Port} = :p AND {ID} <> :id", p("p", code, "id", ID)) != null) {
                throw new IllegalStateException("端口已经被占用");
            }
            if (ID != 0) {
                Row device = db().first("Device", "{ID} = :id", p("id", ID));
                device.put("Name", name);
                device.put("Port", code);
                device.put("DeviceTypeID", selectedIndex);
                db().update("Device", device);
            } else {
                db().insert("Device", Row.of("Port", code, "Name", name, "DeviceTypeID", selectedIndex));
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Cancle_Click() {
        Close("Cancel");
    }
}
