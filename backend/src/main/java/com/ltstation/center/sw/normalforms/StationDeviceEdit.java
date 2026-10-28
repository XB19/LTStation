package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** StationDeviceEdit : ajout (argument ID = -1) ou modification d'un appareil de paiement (StationDevice). */
@SwWindow("NormalForm.StationDeviceEdit")
public class StationDeviceEdit extends SwForm {

    private static long utcNow() {
        // ConvertDateTimeToInt(DateTime.UtcNow) : l'heure UTC traitée comme une heure locale
        return Conv.toMillis(LocalDateTime.now(ZoneOffset.UTC));
    }

    public void Confirm_Click() {
        try {
            int tid = argInt("ID");
            Row v = Row.of("DeviceName", c("DeviceNameBox").Text(), "DeviceType", c("DeviceTypeBox").SelectedIndex(), "PayMent", c("PayMentBox").SelectedIndex(),
                "BindUrl", c("BindAddressBox").Text(), "BindNoz", c("BindNozBox").Text(), "BindPort", c("PortSelectBox").Text(),
                "UserName", c("UserNameBox").Text(), "Password", c("PasswordBox").Text(), "Pin", c("PINBox").Text(), "UpdateTime", utcNow());
            if (tid != -1) {
                Row d = db().first("StationDevice", "{ID} = :i", p("i", tid));
                if (d != null) {
                    d.putAll(v);
                    db().update("StationDevice", d);
                }
            } else {
                db().insert("StationDevice", v);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationDeviceEdit_Load() {
        for (String item : SerialClass.serialsIsConnected()) {
            c("PortSelectBox").ItemsAdd(item);
        }
        int tid = argInt("ID");
        if (tid != -1) {
            Row d = db().first("StationDevice", "{ID} = :i", p("i", tid));
            if (d != null) {
                c("DeviceNameBox").Text(d.str("DeviceName"));
                c("DeviceTypeBox").SelectedIndex(d.i("DeviceType"));
                c("PayMentBox").SelectedIndex(d.i("PayMent"));
                c("BindAddressBox").Text(d.str("BindUrl"));
                c("BindNozBox").Text(d.str("BindNoz"));
                c("PortSelectBox").Text(d.str("BindPort"));
            }
        }
        DeviceTypeBox_SelectedIndexChanged();
    }

    public void DeviceTypeBox_SelectedIndexChanged() {
        boolean pos = c("DeviceTypeBox").SelectedIndex() == 0;
        c("UserNameBox").Enabled(pos);
        c("PasswordBox").Enabled(pos);
    }
}
