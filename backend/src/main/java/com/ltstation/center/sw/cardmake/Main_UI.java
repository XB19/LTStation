package com.ltstation.center.sw.cardmake;

import com.ltstation.center.sw.form.SwWindow;
import java.util.Map;

/** Main_UI : barre de boutons de CardMake ; BaseAuth liste les numéros des boutons autorisés. */
@SwWindow("CardMake.Main_UI")
public class Main_UI extends CardMakeForm {

    /** Bouton → fenêtre, noms d'origine (GradeSet ouvre la création de carte, TankSet sa modification). */
    private static final Map<String, String> WINDOWS = Map.ofEntries(Map.entry("StationCreate", "KeyBuild"), Map.entry("CardTransform", "CardTransformTable"),
        Map.entry("CreditUserBtn", "ChargeForm"), Map.entry("GradeSet", "Main_Card"), Map.entry("TankSet", "Card_Update"), Map.entry("Reverse", "ReverseDeal"),
        Map.entry("EmployeeCreate", "Balance_Deal"), Map.entry("CardReplaceBtn", "CardReplaceView"), Map.entry("PasswordChange", "PasswordChange"),
        Map.entry("UserDepositBtn", "UserDepositForm"), Map.entry("DepositBtn", "Balance_Deal"), Map.entry("ViewReport", "ReportView"), Map.entry("EmpManager", "EmpUpdate"),
        Map.entry("CardStatue", "CardStatue"), Map.entry("EmpCardBtn", "EmpForm"), Map.entry("CustomerStatement", "CustomerBill"));
    /** Droits 1 à 12, dans l'ordre. */
    private static final String[] AUTH = {"StationCreate", "GradeSet", "TankSet", "CardTransform", "DepositBtn", "ViewReport", "EmpManager", "Reverse",
        "CardReplaceBtn", "PasswordChange", "CardStatue", "UserDepositBtn"};

    private String CurrentBtn;

    public void Main_UI_Load() {
        try {
            for (String btn : AUTH) {
                c(btn).Visible(false);
            }
            if (CM.BaseAuth == null || CM.BaseAuth.isEmpty()) {
                return;
            }
            for (String a : CM.BaseAuth.split(",")) {
                if (a.matches("[0-9]+") && Integer.parseInt(a) >= 1 && Integer.parseInt(a) <= AUTH.length) {
                    c(AUTH[Integer.parseInt(a) - 1]).Visible(true);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationCreate_Click() {
        try {
            c(CurrentBtn == null ? "StationCreate" : CurrentBtn).Checked(false);
            c(sender).Checked(true);
            CurrentBtn = sender;
            String form = WINDOWS.get(sender);
            if (form != null) {
                Open("CardMake." + form);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
