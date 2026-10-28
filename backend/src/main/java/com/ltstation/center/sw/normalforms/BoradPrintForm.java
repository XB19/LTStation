package com.ltstation.center.sw.normalforms;

import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.FrxReport;
import com.ltstation.center.sw.normal.BoxBase;

/** BoxScreenViewV2.BoradPrintForm : aperçu des index des pistolets ou des prix (argument OperateType = bouton de la caisse). */
@SwWindow("BoxScreenViewV2.BoradPrintForm")
public class BoradPrintForm extends SwForm {

    public void BoradPrintForm_Load() {
        String[] error = new String[1];
        FrxReport report = switch (arg("OperateType") == null ? "" : argStr("OperateType")) {
            case "MeterBtn" -> BoxBase.BoxNozPrint(error);
            case "QuickPrintBtn" -> BoxBase.BoxPricePrint(db().list("NormalPriceInfo", "{IsUsing} = true", null), error);
            case "PrinterBtn" -> BoxBase.BoxCurrentPricePrint(db().list("NormalPriceInfo", "{IsUsing} = true", null), error);
            default -> null;
        };
        if (report != null) {
            ShowPrepared(report, "previewControl1");
        }
    }
}
