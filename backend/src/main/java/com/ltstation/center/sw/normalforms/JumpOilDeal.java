package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/** BoxScreenViewV2.JumpOilDeal : transaction (paiement 55) pour une partie d'un saut d'index (argument ID de PumpJumpOil). */
@SwWindow("BoxScreenViewV2.JumpOilDeal")
public class JumpOilDeal extends SwForm {

    private Row oil() {
        return db().first("PumpJumpOil", "{ID} = :i", p("i", argInt("ID")));
    }

    public void JumpOilDeal_Load() {
        try {
            Row oil = arg("ID") == null ? null : oil();
            if (oil != null) {
                c("NozIDBox").Text(oil.str("NozID"));
                c("VolumeBox").Text(oil.dec("Volume").toPlainString());
                c("AmountBox").Text(oil.dec("Amount").toPlainString());
                c("PriceBox").Text(oil.dec("Price").toPlainString());
                c("VolumeTotalBox").Text(oil.dec("VolumeTotal").toPlainString());
                c("TimeBox").Text(oil.str("TrueTime"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ConfirmBtn_Click() {
        try {
            BigDecimal result;
            try {
                result = new BigDecimal(c("DealVolumeBox").Text().trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("DealVolume value Error");
            }
            Row oil = oil();
            if (result.compareTo(oil.dec("Volume")) > 0) {
                throw new IllegalStateException("Volume is Large");
            }
            // Heure à la seconde, comme ToString("yyyy-MM-dd HH:mm:ss") puis Convert.ToDateTime
            long time = BaseFunction.ConvertDateTimeToInt(c("RealTimeBox").Value().withNano(0));
            BigDecimal price = oil.dec("Price");
            BigDecimal amount = result.multiply(price);
            db().insert("NormalTransaction", Row.of("CreateTime", time, "NozID", oil.get("NozID"), "Price", price, "Volume", result,
                "VolumeTotal", oil.dec("VolumeTotal").subtract(oil.dec("Volume")).add(result), "EmployeeID", IniInfo.LoginID, "StationID", IniInfo.StationID,
                "OrderNo", "", "Upload", false, "PayMent", 55, "AmountDigits", IniInfo.Mradio, "CardNo", "", "Mileage", BigDecimal.ZERO,
                "DiscountValue", new BigDecimal(100), "Amount", amount, "RealAmount", amount));
            oil.put("Volume", oil.dec("Volume").subtract(result));
            db().update("PumpJumpOil", oil);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
