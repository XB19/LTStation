package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** GetOiltransactionByHands : relevé manuel des transactions d'un pistolet (numéros de début et de fin). */
@SwWindow("GetOiltransactionByHands")
public class GetOiltransactionByHands extends SwForm {

    private void GetGunCode() {
        try {
            c("GunSelect").DataSource(db().list("GunConfig", "ORDER BY {GUNCODE}", null), "GunCode", "GunCode");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void GetOiltransactionByHands_Load() {
        GetGunCode();
    }

    public void GetTransaction_Click() {
        try {
            if (c("StartPS").Text().isEmpty() || c("EndPS").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsWriteCode"));
            }
            long result;
            long result2;
            try {
                result = Long.parseLong(c("StartPS").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("StartCodeError"));
            }
            try {
                result2 = Long.parseLong(c("EndPS").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(L("EndCodeError"));
            }
            int gun = c("GunSelect").SelectedRow().i("GUNCODE");
            if (((Station) Owner).SetGetOil(gun, result, result2)) {
                Close();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
