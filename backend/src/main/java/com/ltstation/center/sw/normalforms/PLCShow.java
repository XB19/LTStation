package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWebForm;
import com.ltstation.center.sw.form.SwWindow;

/** PLCShow : automate (PLC) du compresseur GNV (web/NormalStatue/PLC/PLC.html), intégrée dans la fenêtre principale. */
@SwWindow("NormalForm.PLCShow")
public class PLCShow extends SwWebForm {

    @Override
    protected String Url() {
        return "web/NormalStatue/PLC/PLC.html";
    }

    @Override
    public void LoadHandler_OnLoadEnd() {
        ClearPendingScripts();
        try {
            ExecuteJavascript("UpdateTextLabel('" + L("A_Current_Piston") + "', '" + L("InletPressure") + "', '" + L("MediumPressure") + "', '"
                + L("ProtectivePressure") + "', '" + L("WaterTemperature") + "', '" + L("GasConcentration") + "', '" + L("LowPressure") + "', '"
                + L("B_Current_Piston") + "', '" + L("HighPressure") + "', '" + L("InstrumentAirPressure") + "', '" + L("HydraulicOilTemperature")
                + "', '" + L("GasConcentrationSecond") + "', '" + L("GasConcentrationThird") + "')");
            Row data = db().first("PLCParamData");
            if (data != null) {
                ShowData(data, "0");
            } else {
                UpdateValue("0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0");
                UpdateAlarm("0");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    /** Valeurs et alarmes enregistrées (PLCParamData) : MW28 → 100+i, MB10 → 300+i, MB5 → 200+i. */
    public void ShowData(Row d, String online) {
        UpdateValue(d.str("A_PistonCurrent"), d.str("InTake_Pressure"), d.str("Medium_Pressure"), d.str("Protection_Pressure"),
            d.str("Water_Flow_Temperature"), d.str("Gas_Concentration_Inside_the_Sled_First"), d.str("Low_Pressure"), d.str("B_PistonCurrent"),
            d.str("High_Pressure"), d.str("Instrument_Air_Pressure"), d.str("Hydraulic_Oil_Temperature"), d.str("Gas_Concentration_Second"),
            d.str("Gas_Concentration_Third"), online);
        StringBuilder text = new StringBuilder();
        flags(text, d.str("MW28Data"), 100);
        flags(text, d.str("MB10Data"), 300);
        flags(text, d.str("MB5Data"), 200);
        String alarm = text.length() > 0 ? text.substring(0, text.length() - 1) : "0";
        UpdateAlarm(alarm);
    }

    private static void flags(StringBuilder text, String bits, int base) {
        for (int i = 0; bits != null && i < bits.length(); i++) {
            if (bits.charAt(i) != '0') {
                text.append(base + i).append(',');
            }
        }
    }

    public void UpdateValue(String A_Current_Piston, String InletPressure, String MediumPressure, String ProtectivePressure, String WaterTemperature,
        String GasConcentration, String LowPressure, String B_Current_Piston, String HighPressure, String InstrumentAirPressure,
        String HydraulicOilTemperature, String GasConcentrationSecond, String GasConcentrationThird, String online) {
        String text = "1".equals(online) ? L("Online") : L("Offline");
        ExecuteJavascript("UpdateValue('" + A_Current_Piston + "', '" + InletPressure + "', '" + MediumPressure + "', '" + ProtectivePressure + "', '"
            + WaterTemperature + "', '" + GasConcentration + "', '" + LowPressure + "', '" + B_Current_Piston + "', '" + HighPressure + "', '"
            + InstrumentAirPressure + "', '" + HydraulicOilTemperature + "', '" + GasConcentrationSecond + "', '" + GasConcentrationThird + "','"
            + online + "','" + text + "')");
    }

    public void UpdateAlarm(String Alarm) {
        try {
            if (Alarm.contains(",")) {
                StringBuilder text = new StringBuilder();
                for (String a : Alarm.split(",")) {
                    text.append(L("PLC" + a)).append(',');
                }
                ExecuteJavascript("UpdateAlarm('" + text.substring(0, text.length() - 1) + "')");
            } else if (Alarm.equals("0")) {
                ExecuteJavascript("UpdateAlarm('0')");
            } else {
                ExecuteJavascript("UpdateAlarm('" + L("PLC" + Alarm) + "')");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }
}
