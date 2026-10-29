package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.normal.NormalFunction;
import java.time.LocalDateTime;

/** PortAdd : nouveau port série des pistolets (NormalPortSet), signalé à la fenêtre principale (message 1026). */
@SwWindow("NormalForm.PortAdd")
public class PortAdd extends SwForm {

    public void PortAdd_Load() {
        for (String item : SerialClass.serialsIsConnected()) {
            c("PortSelect").ItemsAdd(item);
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    public void SaveButton_Click() {
        try {
            String port = c("PortSelect").Text();
            int baud = Integer.parseInt(c("BuadSelect").Text().trim());
            int dataBit = Integer.parseInt(c("DataBitSelect").Text().trim());
            int stopBit = Integer.parseInt(c("StopBitSelect").Text().trim());
            if (db().any("NormalPortSet", "{PortID} = :p", p("p", port))) {
                throw new IllegalStateException("Ce port est déjà utilisé");
            }
            String now = LocalDateTime.now().format(Conv.STD);
            int id = Conv.toInt(db().insert("NormalPortSet", Row.of("Baud", baud, "CreateTime", now, "Parity", c("ParitySelect").SelectedIndex(),
                "DataBit", dataBit, "PortID", port, "StopBit", stopBit, "UpdateTime", now, "AgreementTypeID", c("AgreementType").SelectedIndex())));
            NormalFunction.MainFormMessage.accept(1026, id, 0);
            Close("OK");
        } catch (NumberFormatException ex) {
            MessageBox(L("FormatError"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
