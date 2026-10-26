package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;

/**
 * NozShow : pistolets du mode Normal (web/NormalStatue/NormalNoz/Noz.html), intégrée dans la fenêtre
 * principale. Les boutons de la page (préréglage, arrêt, mise à jour) remontent par UpdateValueEvent.
 */
@SwWindow("NormalForm.NozShow")
public class NozShow extends SwWebForm {

    /** UpdateSetNozValue(NozID, Value, Statue) */
    public interface UpdateSetNozValue {
        void accept(int NozID, String Value, int Statue);
    }

    public volatile boolean IsReady;
    public volatile UpdateSetNozValue UpdateValueEvent = (a, b, c) -> { };

    /** Largeur d'une carte de pistolet (Size.Width / 3 - 20 de l'original). */
    private static final int WIDTH = 380;

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
        String[] array = JsArg().split("\\|", -1);
        UpdateValueEvent.accept(Conv.toInt(array[0]), array[1], 1281);
    }

    public void ViewUpdate() {
        String[] array = JsArg().split("\\|", -1);
        String str = array[0].substring(3);
        switch (array[1]) {
            case "0" -> ShowDialog("NormalForm.NozFuelSet", r -> { }, "NozID", BaseFunction.SetStrLen(str, 2), "Price", array[2]);
            case "1" -> UpdateValueEvent.accept(Conv.toInt(str), "", 1282);
            case "2" -> {
                Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", Conv.toInt(str)));
                UpdateValueEvent.accept(noz.i("NozID"), noz.str("PortID"), 1283);
            }
            case "3" -> {
                Row noz = db().first("NormalNoz", "{NozID} = :n", p("n", Conv.toInt(str)));
                if (noz == null) {
                    throw new IllegalStateException(L("DeviceNotExist"));
                }
                ShowDialog("NormalForm.BoxNozDetial", r -> { }, "UpdateType", "Update", "ID", noz.i("ID"), "PortID", 1, "Dowload", "Main");
            }
            default -> {
            }
        }
    }

    static String authText(int accredit) {
        return switch (accredit) {
            case 0 -> L("Manualauthorization");
            case 2 -> L("BoxAuth");
            case 3 -> L("Advanceexe");
            default -> L("Automaticallyauthorize");
        };
    }

    private String labels() {
        return ",'" + L("NozID") + "','" + L("Amount") + "','" + L("Volume") + "','" + L("Price") + "','" + L("Authorizationmodel") + "','"
            + L("AmountTotal") + "','" + L("VolumeTotal") + "','" + L("Energy") + "','" + L("PreSet") + "','" + L("MachineStop") + "','"
            + L("MachineUpdate") + "')";
    }

    /**
     * LoadHandler.OnLoadEnd : une carte par pistolet utilisé. L'original ne le fait qu'au premier
     * chargement (IsReady) ; la page web étant rechargée à chaque ouverture, les cartes sont recréées.
     */
    @Override
    public void LoadHandler_OnLoadEnd() {
        ClearPendingScripts();
        for (Row item : db().list("NormalNoz", "{IsUsing} = true ORDER BY {NozID}", null)) {
            Row tank = db().first("NormalTank", "{ID} = :t", p("t", item.get("TankID")));
            if (tank == null) {
                continue;
            }
            Row grade = db().first("NormalGrade", "{ID} = :g", p("g", tank.get("GradeID")));
            if (grade != null) {
                ExecuteJavascript("NozCreate('" + item.str("NozID") + "', '" + grade.str("GradeName") + "', '" + authText(item.i("Accredit")) + "',"
                    + WIDTH + labels());
            }
        }
        IsReady = true;
    }

    private static String num(BigDecimal d) {
        return d.compareTo(BigDecimal.valueOf(-1)) == 0 ? "-1" : d.toPlainString();
    }

    public void Noz_UpdateView(int oldNozID, int NozID, String GradeName, String TankName, BigDecimal Price, BigDecimal Amount, BigDecimal Volume,
        BigDecimal AmountTotal, BigDecimal VolumeTotal, int IsControl, int IsRun, String Auth_Statue, String AuthStatue) {
        ExecuteJavascript("NozViewUpdate('" + oldNozID + "', '" + NozID + "', '" + GradeName + "', '" + TankName + "', '"
            + ShowRadioLen(num(Amount), IniInfo.Mradio) + "', '" + ShowRadioLen(num(Volume), IniInfo.Vradio) + "', '"
            + ShowRadioLen(num(AmountTotal), IniInfo.Mradio) + "', '" + ShowRadioLen(num(VolumeTotal), IniInfo.Vradio) + "', '"
            + ShowRadioLen(num(Price), IniInfo.Pradio) + "', '" + IsRun + "', '" + IsControl + "','" + Auth_Statue + "','" + AuthStatue + "')");
    }

    /** Complète les décimales jusqu'à Len (« -1 » = valeur inchangée). */
    private static String ShowRadioLen(String Value, int Len) {
        try {
            if (Value.equals("-1")) {
                return Value;
            }
            int num = Value.indexOf('.') + 1;
            if (num == 0 && Len != 0) {
                Value += ".";
                num = Value.length();
            }
            StringBuilder sb = new StringBuilder(Value);
            for (int i = Value.length() - num; i < Len; i++) {
                sb.append('0');
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return Value;
        }
    }

    public void Noz_AddView(int NozID, String GradeName, String TankName, int width) {
        ExecuteJavascript("NozCreate('" + NozID + "', '" + GradeName + "', '" + TankName + "'," + WIDTH + labels());
    }

    public void Noz_RemoveView(int NozID) {
        ExecuteJavascript("NozRemove('" + NozID + "')");
    }
}
