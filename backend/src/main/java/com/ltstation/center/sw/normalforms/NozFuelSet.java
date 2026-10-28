package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.NormalEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** NozFuelSet : préréglage d'un pistolet en montant ou en volume (arguments NozID, Price). */
@SwWindow("NormalForm.NozFuelSet")
public class NozFuelSet extends SwForm {

    private Row SNoz;

    private static final BigDecimal M1 = BigDecimal.valueOf(-1);

    @Override
    protected void init() {
        c("NozLabel").Text(argStr("NozID"));
    }

    private static BigDecimal tryDec(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void AmountRadio_CheckedChanged() {
        if (sender.equals("AmountRadio")) {
            c("SetLable").Text(L("AmountPreset"));
        } else if (sender.equals("VolumeRadio")) {
            c("SetLable").Text(L("VolumePreset"));
        }
    }

    public void SaveButton_Click() {
        try {
            if (IniInfo.IdentifyMode == 1) {
                throw new IllegalStateException(L("NeedEmployeeCertification"));
            }
            if (IniInfo.IsOnDuty == 0) {
                throw new IllegalStateException(L("NeedOnDuty"));
            }
            int setType = 0;
            String text = c("PreAmount").Text();
            if (SNoz == null) {
                throw new IllegalStateException(L("NozError"));
            }
            BigDecimal price = tryDec(c("SetPrice").Text());
            if (price == null) {
                throw new IllegalStateException(L("PreSetPriceError"));
            }
            String nozId = c("NozLabel").Text();
            if (text.isEmpty() || c("SetVolume").Text().isEmpty() || c("SetPrice").Text().isEmpty()) {
                // plein (sans valeur) : volume si le prix est inférieur à 1
                if (price.compareTo(BigDecimal.ONE) < 0) {
                    setType = 1;
                }
                if (IniInfo.WifiMode == 1) {
                    NormalEngine.SetAndFuelWifi(SNoz, M1, setType, M1);
                } else {
                    NormalEngine.SetAndFuel(SNoz.i("PortID"), nozId, M1, setType, M1);
                }
            } else {
                BigDecimal value = tryDec(text);
                if (value == null) {
                    throw new IllegalStateException(L("PreSetValueError"));
                }
                if (IniInfo.WifiMode == 1) {
                    NormalEngine.SetAndFuelWifi(SNoz, value, setType, price);
                } else {
                    NormalEngine.SetAndFuel(SNoz.i("PortID"), nozId, value, setType, price);
                }
            }
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NozFuelSet_Load() {
        SNoz = db().first("NormalNoz", "{NozID} = :n", p("n", Conv.toInt(c("NozLabel").Text())));
        BigDecimal nozPrice = tryDec(argStr("Price"));
        if (nozPrice == null || nozPrice.signum() == 0) {
            if (SNoz != null) {
                Row info = db().first("NormalPriceInfo", "{GradeID} = :g AND {IsUsing} = true", p("g", SNoz.get("GradeID")));
                if (info != null) {
                    c("SetPrice").Text(info.dec("Price").toPlainString());
                }
            }
        } else {
            c("SetPrice").Text(nozPrice.toPlainString());
        }
    }

    /** Montant ↔ volume recalculé avec le prix (3 décimales). */
    public void PreAmount_Validated() {
        try {
            BigDecimal value = tryDec(c(sender).Text());
            if (value == null || value.signum() <= 0) {
                c(sender).Text("");
                return;
            }
            BigDecimal price = tryDec(c("SetPrice").Text());
            if (price == null) {
                price = BigDecimal.ZERO;
            }
            if (sender.equals("PreAmount")) {
                c("SetVolume").Text(Conv.netRound(value.divide(price, 20, RoundingMode.HALF_EVEN).stripTrailingZeros(), 3).toPlainString());
            } else if (sender.equals("SetVolume")) {
                c("PreAmount").Text(Conv.netRound(value.multiply(price), 3).toPlainString());
            }
        } catch (RuntimeException ex) {
            c(sender).Text("");
            MessageBox(ex.getMessage());
        }
    }
}
