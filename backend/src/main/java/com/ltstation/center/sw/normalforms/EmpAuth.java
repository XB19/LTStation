package com.ltstation.center.sw.normalforms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Droits d'un employé du mode Normal (Employees.Auth = « 1,3,… ») : case à cocher de EmpAdd / EmpUpdate ↔ code du menu. */
final class EmpAuth {

    private EmpAuth() {
    }

    static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        String[] names = {"BaseCheck", "Box_Set", "EnergySet", "TankSet", "ShiftSet", "EmployeeLogin", "EmpSet", "ShiftReport", "DayReport", "MonthReport",
            "TransactionQuery", "AuthShift", "CardMakeBtn", "CardUpdateBtn", "BalanceBtn", "CardLostBtn", "CardReplaceBtn", "EmpManagerBtn",
            "UserMessageCheck", "PrintMessageCheck", "HandShiftCheck", "TankImportCheck", "RetailStoreCheck"};
        for (int i = 0; i < names.length; i++) {
            CODES.put(names[i], String.valueOf(i + 1));
        }
    }

    /** Codes des cases cochées, dans l'ordre des contrôles du groupe, chacun suivi d'une virgule. */
    static String authOf(List<String> controls, Predicate<String> checked) {
        StringBuilder text = new StringBuilder();
        for (String name : controls) {
            String code = CODES.get(name);
            if (code != null && checked.test(name)) {
                text.append(code).append(',');
            }
        }
        return text.toString();
    }

    /** Cases à cocher correspondant à un Auth enregistré. */
    static List<String> checksOf(String auth) {
        List<String> out = new ArrayList<>();
        if (auth == null || auth.isEmpty()) {
            return out;
        }
        for (String code : auth.split(",")) {
            CODES.forEach((name, c) -> {
                if (c.equals(code)) {
                    out.add(name);
                }
            });
        }
        return out;
    }
}
