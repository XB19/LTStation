package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** StationDeviceConfig : appareils de paiement de la station (StationDevice). */
@SwWindow("NormalForm.StationDeviceConfig")
public class StationDeviceConfig extends SwForm {

    private static final String[] DeviceTypeShow = {"YMNY POS", "YMNY ID+", "YMNY AutoCard", "YMNY QRCode"};
    private static final String[] PayMentShow = {"POS", "ID+", "AutoCard", "QRCode"};

    public void DeviceDataView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("DeviceDataView").Cell(g("DeviceDataView").CurrentRowIndex(), 0));
            edit(id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AddItemBtn_Click() {
        edit(-1);
    }

    private void edit(int id) {
        ShowDialog("NormalForm.StationDeviceEdit", r -> {
            if ("OK".equals(r.result())) {
                ShowData();
            }
        }, "ID", id);
    }

    public void StationDeviceConfig_Load() {
        ShowData();
    }

    private void ShowData() {
        try {
            g("DeviceDataView").RowsClear();
            for (Row item : db().list("StationDevice")) {
                g("DeviceDataView").RowsAdd(item.get("ID"), DeviceTypeShow[item.i("DeviceType")], item.str("DeviceName"), PayMentShow[item.i("PayMent")],
                    item.str("BindUrl"), item.str("BindPort"), item.str("BindNoz"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
