package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** PriceDeviceSet : afficheurs de prix / de paiement et leurs ports série. */
@SwWindow("PriceDeviceSet")
public class PriceDeviceSet extends SwForm {

    private final List<String> TypeList = List.of("Price_Normal", "Price_LYX");

    public void PriceDeviceSet_Load() {
        LoadData();
    }

    private void LoadData() {
        try {
            g("PriceConfigView").RowsClear();
            List<Row> ports = db().list("ComConfig", "ORDER BY {PORT}", null);
            for (Row item : db().list("Device")) {
                // Cellule liste déroulante : PORT affiché pour la valeur CODE
                Object port = ports.stream().filter(p -> Conv.toInt(p.get("CODE")) == item.i("Port")).map(p -> p.get("PORT")).findFirst()
                    .orElse(item.get("Port"));
                int type = item.i("DeviceTypeID");
                g("PriceConfigView").RowsAdd(item.get("ID"), port, item.get("Name"), type >= 0 && type < TypeList.size() ? TypeList.get(type) : "");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Add_Click() {
        ShowDialog("DeviceAdd", r -> {
            if (r.ok()) {
                LoadData();
            }
        }, "ID", 0);
    }

    private int currentId() {
        int index = g("PriceConfigView").CurrentRowIndex();
        if (index < 0) {
            throw new IllegalStateException("Object reference not set to an instance of an object.");
        }
        return Integer.parseInt(Conv.toStr(g("PriceConfigView").Cell(index, "ID")));
    }

    public void PriceConfigView_CellDoubleClick() {
        try {
            ShowDialog("DeviceAdd", r -> {
                if (r.ok()) {
                    LoadData();
                }
            }, "ID", currentId());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void DeviceDelete_Click() {
        try {
            int num = currentId();
            Row device = db().first("Device", "{ID} = :id", p("id", num));
            if (device == null) {
                throw new IllegalStateException("设备不存在");
            }
            db().deleteEntity("Device", device);
            LoadData();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
