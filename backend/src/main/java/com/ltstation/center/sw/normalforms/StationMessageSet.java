package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;

/** StationMessageSet : en-tête des tickets (StationConfig : nom, adresse, téléphone, société, agence, n° fiscal). */
@SwWindow("NormalForm.StationMessageSet")
public class StationMessageSet extends SwForm {

    public void StationMessageSet_Load() {
        try {
            Row s = db().first("StationConfig");
            if (s != null) {
                c("StationName").Text(s.str("StationName"));
                c("StationAddress").Text(s.str("StationAddress"));
                c("StationTel").Text(s.str("StationTel"));
                c("StationCompany").Text(s.str("StationCompany"));
                c("StationBranch").Text(s.str("StationBranch"));
                c("TaxerBox").Text(s.str("StationTaxer"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            String name = c("StationName").Text();
            String address = c("StationAddress").Text();
            if (name.isEmpty() || address.isEmpty()) {
                throw new IllegalStateException("Empty");
            }
            Row s = db().first("StationConfig");
            if (s == null) {
                db().insert("StationConfig", Row.of("StationName", name, "StationAddress", address, "StationBranch", c("StationBranch").Text(),
                    "StationCompany", c("StationCompany").Text(), "StationTel", c("StationTel").Text(), "StationTaxer", c("TaxerBox").Text()));
            } else {
                // comme l'original : seuls le nom et l'adresse sont modifiés
                s.put("StationName", name);
                s.put("StationAddress", address);
                db().update("StationConfig", s);
            }
            // en-têtes des reçus EFD et des tickets de caisse
            com.ltstation.center.sw.normal.BoxBase.sp.NAME = name;
            com.ltstation.center.sw.normal.BoxBase.sp.CITY = address;
            com.ltstation.center.sw.normal.BoxBase.sc.Tel = c("StationTel").Text();
            com.ltstation.center.sw.normal.BoxBase.sc.StationCompany = c("StationCompany").Text();
            com.ltstation.center.sw.normal.BoxBase.sc.Branch = c("StationBranch").Text();
            com.ltstation.center.sw.normal.BoxBase.sc.StationTax = c("TaxerBox").Text();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
