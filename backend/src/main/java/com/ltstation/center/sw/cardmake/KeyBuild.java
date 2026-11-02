package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.form.SwWindow;
import java.util.ArrayList;
import java.util.List;

/** KeyBuild : remplacement des clés de cartes, autorisé par les deux anciens codes. */
@SwWindow("CardMake.KeyBuild")
public class KeyBuild extends CardMakeForm {

    public void CardCreateBtn_Click() {
        try {
            String text = c("OPA").Text();
            String text2 = c("OPB").Text();
            String text3 = c("NPA").Text();
            String text4 = c("NPB").Text();
            if (text.length() != 16 || text2.length() != 16 || text3.length() != 16 || text4.length() != 16) {
                throw new IllegalStateException("Key Format Error");
            }
            if (!text.matches("^[0-9]*$")) {
                throw new IllegalStateException("Key need number");
            }
            String key = BaseFunction.CONST_KEY;
            List<String> list = new ArrayList<>(List.of(BaseFunction.CreateSecretKey(text3, text4)));
            list.add(BaseFunction.DES3_Encrypt(key, BaseFunction.ConvertToHex(text3)));
            list.add(BaseFunction.DES3_Encrypt(key, BaseFunction.ConvertToHex(text4)));
            send("KeyUpdate", Row.of("OPA", BaseFunction.DES3_Encrypt(key, BaseFunction.ConvertToHex(text)),
                "OPB", BaseFunction.DES3_Encrypt(key, BaseFunction.ConvertToHex(text2)), "CompanyID", CM.CompanyID, "EmpID", CM.EmpID, "m_sSeedKey", list));
            MessageBox("Update Success");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
