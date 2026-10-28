package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** BoxPortAdd : ajout d'un port d'un boîtier (Box_Set) ; argument PID = boîtier. */
@SwWindow(value = "NormalForm.BoxPortAdd", setup = true)
public class BoxPortAdd extends SwForm {

    public void SaveButton_Click() {
        try {
            int portId = argInt("PID");
            Row set = Row.of("Box_PortID", Integer.parseInt(c("PortSelect").Text().trim()), "Box_Baud", Integer.parseInt(c("BuadSelect").Text().trim()),
                "Box_Stop", Integer.parseInt(c("StopBitSelect").Text().trim()), "Box_Parity", c("ParitySelect").SelectedIndex(),
                "Box_CommunicationTypeID", c("CommunicationSelect").SelectedIndex(), "Box_IntervalTime", Integer.parseInt(c("IntervalTime").Text().trim()),
                "Port_ID", portId, "Box_DeviceType", c("DeviceTypeAdd").SelectedIndex());
            if (db().any("Box_Set", "{Port_ID} = :p AND {Box_PortID} = :b", p("p", portId, "b", set.get("Box_PortID")))) {
                throw new IllegalStateException("端口已设置");
            }
            db().insert("Box_Set", set);
            Close("OK");
        } catch (NumberFormatException ex) {
            MessageBox(L("FormatError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }
}
