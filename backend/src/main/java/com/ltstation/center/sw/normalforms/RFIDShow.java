package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RFIDShow : lecteurs RFID du mode Normal (web/NormalStatue/RFID/RFID.html), intégrée dans la fenêtre principale. */
@SwWindow("NormalForm.RFIDShow")
public class RFIDShow extends SwWebForm {

    public volatile boolean IsReady;

    @Override
    protected String Url() {
        return "web/NormalStatue/RFID/RFID.html";
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    /** LoadHandler.OnLoadEnd : un lecteur par RFIDConfig (recréés à chaque chargement de la page). */
    @Override
    public void LoadHandler_OnLoadEnd() {
        ClearPendingScripts();
        try {
            IsReady = true;
            for (Row item : db().list("RFIDConfig")) {
                CreateOne(item.i("ID"), item.i("RFIDNo"), item.i("Address"), item.i("NozID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void RemoveRFID(int ID) {
        ExecuteJavascript("DeleteRFID(" + ID + ")");
    }

    public void CreateOne(int ID, int RFIDNo, int Address, int NozID) {
        ExecuteJavascript("CreateRFID(" + ID + ", '" + IniInfo.StationName + "', " + RFIDNo + ", false, '" + now() + "', '" + L("Address") + "', "
            + Address + ", '" + L("BindNoz") + "', " + NozID + ", '" + L("Tag") + "', '', '" + L("UserName") + "', '', '" + L("CarNo") + "', '', '"
            + L("Balance") + "', '', false, '" + L("Free") + "')");
    }

    public void UpdateOne(int ID, int RFIDNo, int Address, int NozID, String Tag, String UserName, String CarNo, BigDecimal Bal, boolean isOnline,
        boolean isWork) {
        String text9 = isWork ? L("Work") : L("Free");
        ExecuteJavascript("UpdateRFID(" + ID + ", '" + IniInfo.StationName + "', " + RFIDNo + ", " + isOnline + ", '" + now() + "', '" + L("Address")
            + "', " + Address + ", '" + L("BindNoz") + "', " + NozID + ", '" + L("Tag") + "', '" + Tag + "', '" + L("UserName") + "', '" + UserName
            + "', '" + L("CarNo") + "', '" + CarNo + "', '" + L("Balance") + "', " + Bal.toPlainString() + ", " + isWork + ", '" + text9 + "')");
    }
}
