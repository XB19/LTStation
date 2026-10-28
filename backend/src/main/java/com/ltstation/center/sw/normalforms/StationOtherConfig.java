package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.AllNozStatue;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.IBoxClientParam;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalNozStatue;
import com.ltstation.center.sw.normal.NozSerial;

/** StationOtherConfig : mode d'identification (0 = normal, 1 = employé obligatoire). */
@SwWindow("NormalForm.StationOtherConfig")
public class StationOtherConfig extends SwForm {

    public void RadioNormal_CheckedChanged() {
        try {
            Row stations = db().first("Stations");
            if (stations == null) {
                throw new IllegalStateException(L("PLSCreateStation"));
            }
            int mode;
            if (sender.equals("RadioNormal")) {
                mode = 0;
            } else if (sender.equals("RadioEmployee")) {
                mode = 1;
            } else {
                return;
            }
            stations.put("IdentifyMode", mode);
            IniInfo.IdentifyMode = mode;
            if (IniInfo.WifiMode == 1) {
                for (IBoxClientParam box : BaseInfo.BoxClientList) {
                    for (AllNozStatue noz : box.NozList) {
                        // comme l'original : 0 pour les boîtiers sans fil, même en mode employé
                        noz.IdentifyMode = 0;
                    }
                }
            } else {
                for (NozSerial port : NormalEngine.Ports) {
                    for (NormalNozStatue n : port.NozAddress) {
                        n.IdentifyMode = mode;
                    }
                }
            }
            db().update("Stations", stations);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationOtherConfig_Load() {
        c(IniInfo.IdentifyMode == 1 ? "RadioEmployee" : "RadioNormal").Checked(true);
    }
}
