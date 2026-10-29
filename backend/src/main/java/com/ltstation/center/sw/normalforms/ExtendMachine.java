package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;

/** ExtendMachine : port série d'un appareil externe (ExtendPort : 0 = tiroir-caisse…). */
@SwWindow("NormalForm.ExtendMachine")
public class ExtendMachine extends SwForm {

    public void Save_Click() {
        try {
            int machineType = c("MachineTypeCombox").SelectedIndex();
            String text = c("SerialPortCombox").Text();
            if (machineType == -1 || text.isEmpty()) {
                throw new IllegalStateException("Type d'appareil ou port série manquant");
            }
            Row ext = db().first("ExtendPort", "{MachineType} = :m", p("m", machineType));
            if (ext == null) {
                db().insert("ExtendPort", Row.of("MachineType", machineType, "Port", text));
            } else {
                ext.put("MachineType", machineType);
                ext.put("Port", text);
                db().update("ExtendPort", ext);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ExtendMachine_Load() {
        try {
            for (String item : SerialClass.serialsIsConnected()) {
                c("SerialPortCombox").ItemsAdd(item);
            }
            Row ext = db().first("ExtendPort");
            if (ext != null) {
                c("MachineTypeCombox").SelectedIndex(ext.i("MachineType"));
                c("SerialPortCombox").Text(ext.str("Port"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
