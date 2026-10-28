package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/**
 * Port.BoxDeviceSet : appareil du port 5 d'un boîtier (imprimante, sonde, afficheur de prix) ; arguments UpdateType
 * (« Add » : ID = boîtier ; « Update » : ID = appareil).
 */
@SwWindow(value = "Port.BoxDeviceSet", setup = true)
public class BoxDeviceSet extends SwForm {

    private boolean update() {
        return "Update".equals(argStr("UpdateType"));
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            int type = c("DeviceTypeSelect").SelectedIndex();
            int address;
            try {
                address = Integer.parseInt(c("DeviceAddress").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("FormatError"));
            }
            int baud = c("DeviceBuadSelect").SelectedIndex();
            int interval = c("DeviceIntervalSelect").SelectedIndex();
            int stop = c("DeviceStopSelect").SelectedIndex();
            int parity = c("DeviceParitySelect").SelectedIndex();
            if (type == -1 || baud == -1 || interval == -1 || stop == -1 || parity == -1) {
                throw new IllegalStateException(L("ParamError"));
            }
            Row values = Row.of("StationDeviceType", type, "DeviceStop", stop, "DeviceAddress", address, "DeviceBuad", baud, "DeviceInterval", interval,
                "DeviceParity", parity, "CreateTime", BaseFunction.ConvertDateTimeToInt());
            if (!update()) {
                if (db().any("StationDeviceSet", "{DeviceAddress} = :a AND {BoxID} = :b", p("a", address, "b", id))) {
                    throw new IllegalStateException(L("AddressRepeat"));
                }
                values.put("BoxID", id);
                db().insert("StationDeviceSet", values);
                Close("OK");
                return;
            }
            // comme l'original : l'appareil sert à la fois d'identifiant et de boîtier dans le contrôle d'adresse
            if (db().any("StationDeviceSet", "{DeviceAddress} = :a AND {BoxID} = :b AND {ID} <> :b", p("a", address, "b", id))) {
                throw new IllegalStateException(L("AddressRepeat"));
            }
            Row device = db().first("StationDeviceSet", "{ID} = :i", p("i", id));
            if (device == null) {
                throw new IllegalStateException(L("DeviceNotExist"));
            }
            device.putAll(values);
            db().update("StationDeviceSet", device);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BoxDeviceSet_Load() {
        try {
            if (update()) {
                Row d = db().first("StationDeviceSet", "{ID} = :i", p("i", argInt("ID")));
                if (d != null) {
                    c("DeviceTypeSelect").SelectedIndex(d.i("StationDeviceType"));
                    c("DeviceAddress").Text(d.str("DeviceAddress"));
                    c("DeviceBuadSelect").SelectedIndex(d.i("DeviceBuad"));
                    c("DeviceIntervalSelect").SelectedIndex(d.i("DeviceInterval"));
                    c("DeviceStopSelect").SelectedIndex(d.i("DeviceStop"));
                    c("DeviceParitySelect").SelectedIndex(d.i("DeviceParity"));
                }
                c("DeleteBtn").Visible(true);
                c("DeleteBtn").Text(L("DeleteBtnShow"));
            } else {
                c("DeleteBtn").Visible(false);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeleteBtn_Click() {
        try {
            Row d = db().first("StationDeviceSet", "{ID} = :i", p("i", argInt("ID")));
            if (d != null) {
                db().deleteEntity("StationDeviceSet", d);
                Close("OK");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
