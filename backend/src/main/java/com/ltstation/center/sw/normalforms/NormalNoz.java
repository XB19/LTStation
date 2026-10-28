package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/**
 * NormalNoz : carte d'un seul pistolet (web/NormalStatue/NormalNoz/Noz.html, argument ID). Fenêtre de l'original
 * non ouverte par la fenêtre principale (qui utilise NozShow pour tous les pistolets).
 */
@SwWindow("NormalForm.NormalNoz")
public class NormalNoz extends SwWebForm {

    /** UpdateSetNozValue(NozID, Value, Statue) */
    public interface UpdateSetNozValue {
        void accept(int NozID, String Value, int Statue);
    }

    public volatile UpdateSetNozValue UpdateValueEvent = (a, b, c) -> { };
    public volatile boolean IsReady;
    public int NozID = -1;
    private static final BigDecimal M1 = BigDecimal.valueOf(-1);
    private BigDecimal Price = BigDecimal.ZERO;
    private BigDecimal Amount = M1;
    private BigDecimal Volume = M1;
    private BigDecimal AmountTotal = M1;
    private BigDecimal VolumeTotal = M1;
    private int IsRun = -1;
    private String GradeName = "-1";
    private String TankName = "-1";
    private String IsControl = "-1";

    @Override
    protected String Url() {
        return "web/NormalStatue/NormalNoz/Noz.html";
    }

    @Override
    protected void init() {
        JsFunction("ControlLoad", "ControlLoad");
        JsFunction("ViewUpdate", "ViewUpdate");
    }

    public void ControlLoad() {
        UpdateValueEvent.accept(NozID, JsArg(), 1281);
    }

    public void ViewUpdate() {
        switch (JsArg()) {
            case "Fueling" -> ShowDialog("NormalForm.NozFuelSet", r -> { }, "NozID", BaseFunction.SetStrLen(String.valueOf(NozID), 2), "Price", "0");
            case "Start" -> UpdateValueEvent.accept(NozID, "", 1282);
            case "Stop" -> UpdateValueEvent.accept(NozID, "", 1283);
            case "UpdateMessage" -> {
                // comme l'original : le pistolet est cherché par numéro égal à l'identifiant de la carte
                Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", argInt("ID")));
                if (noz != null) {
                    ShowDialog("NormalForm.BoxNozDetial", r -> { }, "UpdateType", "Update", "ID", noz.i("ID"), "PortID", 1, "Dowload", "Main");
                } else {
                    MessageBox(L("NozError"));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        ClearPendingScripts();
        Row noz = db().first("NormalNoz", "{ID} = :i", p("i", argInt("ID")));
        if (noz == null) {
            return;
        }
        NozID = noz.i("NozID");
        String grade = "";
        String tank = "罐";
        Row g = db().first("NormalGrade", "{ID} = :g", p("g", noz.get("GradeID")));
        if (g != null) {
            grade = g.str("GradeName");
        }
        Row t = db().first("NormalTank", "{ID} = :t", p("t", noz.get("TankID")));
        if (t != null) {
            tank = t.str("TankName");
        }
        ExecuteJavascript("NozSet('" + NozID + "', '" + grade + "', '0', '0', '" + tank + "', '0', '0', '" + noz.dec("Price").toPlainString() + "')");
        IsReady = true;
    }

    private static boolean changed(BigDecimal v, BigDecimal old) {
        return v.compareTo(M1) != 0 && v.compareTo(old) != 0;
    }

    /** Seules les valeurs modifiées sont envoyées à la page (« -1 » = inchangé). */
    public void UpdateView(int nozId, BigDecimal price, BigDecimal amountTotal, BigDecimal volumeTotal, String gradeName, BigDecimal amount,
        BigDecimal volume, String tankName, int isRun, String isControl) {
        if (nozId != -1 && NozID != nozId) {
            NozID = nozId;
        } else {
            nozId = -1;
        }
        price = changed(price, Price) ? price : M1;
        amountTotal = changed(amountTotal, AmountTotal) ? amountTotal : M1;
        volumeTotal = changed(volumeTotal, VolumeTotal) ? volumeTotal : M1;
        if (!gradeName.equals("-1") && !gradeName.equals(GradeName)) {
            GradeName = gradeName;
        } else {
            gradeName = "-1";
        }
        if (changed(amount, Amount)) {
            Amount = amount;
        } else {
            amount = M1;
        }
        if (changed(volume, Volume)) {
            Volume = volume;
        } else {
            volume = M1;
        }
        if (!tankName.equals("-1") && !tankName.equals(TankName)) {
            TankName = tankName;
        } else {
            tankName = "-1";
        }
        if (isRun != -1 && IsRun != isRun) {
            IsRun = isRun;
        } else {
            isRun = -1;
        }
        if (!isControl.equals("-1") && !isControl.equals(IsControl)) {
            IsControl = isControl;
        } else {
            isControl = "-1";
        }
        ExecuteJavascript("NozUpdate('" + nozId + "', '" + gradeName + "', '" + ShowRadioLen(amount, IniInfo.MoneyFormat) + "', '"
            + ShowRadioLen(volume, IniInfo.VolumeFormat) + "', '" + tankName + "', '" + ShowRadioLen(amountTotal, IniInfo.MoneyFormat) + "', '"
            + ShowRadioLen(volumeTotal, IniInfo.VolumeFormat) + "', '" + ShowRadioLen(price, IniInfo.PriceFormat) + "', '" + isRun + "','" + isControl + "')");
        if (price.compareTo(M1) != 0) {
            Price = price;
        }
        if (amountTotal.compareTo(M1) != 0) {
            AmountTotal = amountTotal;
        }
        if (volumeTotal.compareTo(M1) != 0) {
            VolumeTotal = volumeTotal;
        }
    }

    private static String ShowRadioLen(BigDecimal v, Object format) {
        String value = v.toPlainString();
        int len = com.ltstation.center.legacy.Conv.toInt(format);
        int num = value.indexOf('.') + 1;
        if (num == 0 && len != 0) {
            value += ".";
            num = value.length();
        }
        StringBuilder sb = new StringBuilder(value);
        for (int i = value.length() - num; i < len; i++) {
            sb.append('0');
        }
        return sb.toString();
    }

    public void UpdateValue(BigDecimal CloseValue, BigDecimal DutyAmountTotal, BigDecimal DutyVolumeTotal) {
        // valeurs conservées par l'original sans être affichées
    }
}
