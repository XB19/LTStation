package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import com.ltstation.center.sw.normal.NormalFunction;
import java.math.BigDecimal;

/** BoxScreenView.BoxPriceUpdate : nouveau prix et coûts d'un produit (argument ID) ; envoyé aux pistolets s'il est déjà en vigueur. */
@SwWindow("BoxScreenView.BoxPriceUpdate")
public class BoxPriceUpdate extends SwForm {

    public void BoxPriceUpdate_Load() {
        try {
            Row info = db().first("NormalPriceInfo", "{ID} = :i", p("i", argInt("ID")));
            if (info != null) {
                c("ProductNameBox").Text(BaseFunction.GetNormalGradeByID(info.i("GradeID")));
                c("CurrentPriceBox").Text(info.dec("Price").toPlainString());
                c("ValidTimeBox").Value(BaseFunction.StampToDateTime(info.str("ValidTime")));
                c("DiscountBox").Text(info.dec("Discount").toPlainString());
                c("AnPriceBox").Text(info.dec("AnnouncementPrice").toPlainString());
                c("RateBox").Text(info.dec("RateOfChange").toPlainString());
                c("TaxBox").Text(info.dec("TaxCost").toPlainString());
                c("ShipBox").Text(info.dec("ShippingCost").toPlainString());
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private BigDecimal dec(String box, boolean emptyZero, String error) {
        String t = c(box).Text();
        try {
            return new BigDecimal(emptyZero && t.isEmpty() ? "0" : t.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void ConfirmBtn_Click() {
        try {
            int id = argInt("ID");
            BigDecimal result = dec("NewPriceBox", false, "New Price Format Error");
            BigDecimal result2 = dec("DiscountBox", true, "Discount Format Error");
            BigDecimal result3 = dec("AnPriceBox", true, "Announcement price Format Error");
            BigDecimal result4 = dec("RateBox", true, "Rate of change Format Error");
            BigDecimal result5 = dec("TaxBox", true, "Tax cost Format Error");
            BigDecimal result6 = dec("ShipBox", true, "Shipping cost Format Error");
            Row info = db().first("NormalPriceInfo", "{ID} = :i", p("i", id));
            if (info == null) {
                return;
            }
            info.put("Discount", result2);
            info.put("AnnouncementPrice", result3);
            info.put("RateOfChange", result4);
            info.put("TaxCost", result5);
            info.put("ShippingCost", result6);
            long num = BaseFunction.ConvertDateTimeToInt();
            long num2 = BaseFunction.ConvertDateTimeToInt(c("ValidTimeBox").Value());
            BigDecimal price = info.dec("Price");
            info.put("Price", result);
            info.put("ValidTime", num2);
            db().update("NormalPriceInfo", info);
            BaseFunction.post("ServerDeal/UpdateNormalPrice", SW.dto().make("PriceDataShow", "StationID", IniInfo.StationID, "NewPPU", result,
                "CompanyID", IniInfo.CompanyID, "GradeSpecificID", info.get("GradeID"), "ID", id, "ValidateTime", String.valueOf(num2)));
            if (num >= num2 && price.compareTo(result) != 0) {
                // FindWindow(« NormalSystem ») : sans moteur Normal démarré, la fenêtre reste ouverte comme dans l'original
                if (!NormalEngine.IsStarted()) {
                    return;
                }
                NormalFunction.MainFormMessage.accept(1027, id, 0);
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
