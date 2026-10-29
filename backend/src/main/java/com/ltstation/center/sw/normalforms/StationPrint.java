package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.PrintClass;

/** StationPrint : en-tête et pied du ticket de transaction (StationPrint.PrintData) et imprimante utilisée. */
@SwWindow("NormalForm.StationPrint")
public class StationPrint extends SwForm {

    /** Clé du dictionnaire PrintData → zone de saisie, dans l'ordre de l'original. */
    private static final String[][] KEYS = {
        {"First", "FirstBox"}, {"Second", "SecondBox"}, {"Third", "ThirdBox"}, {"Fouth", "FouthBox"}, {"Fifth", "FifthBox"}, {"Sixth", "SixthBox"},
        {"CarNo", "CarNoBox"}, {"Seven", "SevenBox"}, {"Eight", "EightBox"}, {"Nin", "NineBox"}, {"Ten", "TenBox"}, {"Eleventh", "ElevenBox"},
        {"Twelfth", "TwelveBox"}, {"Thirteenth", "ThirteenBox"}, {"Fourteenth", "FourteenBox"}, {"Fifteenth", "FifteenBox"},
        {"Sixteenth", "SixteenBox"},
    };

    @Override
    protected void init() {
        c("PrintBox").ItemsClear();
        for (String printer : PrintClass.GetLocalPrinters()) {
            c("PrintBox").ItemsAdd(printer);
        }
    }

    public void SettleBtn_Click() {
        try {
            Object machine = c("PrintBox").SelectedItem();
            if (machine == null) {
                throw new IllegalStateException("Object reference not set to an instance of an object.");
            }
            Row data = new Row();
            for (String[] k : KEYS) {
                data.put(k[0], c(k[1]).Text());
            }
            Row sp = db().first("StationPrint");
            if (sp != null) {
                sp.put("PrintData", Wire.json(data));
                sp.put("PrintMachine", machine.toString());
                db().update("StationPrint", sp);
            } else {
                db().insert("StationPrint", Row.of("PrintData", Wire.json(data), "PrintMachine", machine.toString()));
            }
            MessageBox("Enregistrement réussi");
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationPrint_Load() {
        try {
            Row sp = db().first("StationPrint");
            if (sp != null) {
                Row d = Wire.parseRow(sp.str("PrintData"));
                for (String[] k : KEYS) {
                    c(k[1]).Text(d.str(k[0]));
                }
                if (c("PrintBox").Items().contains(sp.str("PrintMachine"))) {
                    c("PrintBox").Text(sp.str("PrintMachine"));
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
