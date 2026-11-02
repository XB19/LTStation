package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;
import java.util.LinkedHashMap;
import java.util.Map;

/** EmployeeCardAdd : carte Mifare d'un employé ; annulation sur Center si l'écriture échoue. */
@SwWindow("CardMake.EmployeeCardAdd")
public class EmployeeCardAdd extends CardMakeForm {

    public void EmployeeCardAdd_Load() {
        try {
            LoadStations("StationSelect");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void StationSelect_SelectedIndexChanged() {
        try {
            if (c("StationSelect").SelectedValue() == null) {
                throw new IllegalStateException("Station Lost");
            }
            var list = sendRows("ExtendGetEmployee", Row.of("StationID", Conv.toInt(c("StationSelect").SelectedValue())));
            c("EmpSelect").DataSource(list, "Name", "EmployeeID");
            if (!list.isEmpty()) {
                c("EmpSelect").SelectedIndex(0);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        try {
            if (c("StationSelect").SelectedValue() == null) {
                throw new IllegalStateException("Station Lost");
            }
            if (c("EmpSelect").SelectedValue() == null) {
                throw new IllegalStateException("Select Employee First");
            }
            Row card = sendRow("CardUpdate", Row.of("Password", c("EmpPassword").Text(), "CompanyID", CM.CompanyID, "SpecialPrice", "", "Update_Type", "Add",
                "StationID", Conv.toInt(c("StationSelect").SelectedValue()), "LoginType", 0, "Card_Type", 6,
                "CustomerID", Conv.toInt(c("EmpSelect").SelectedValue()), "OperEmp", CM.EmpID));
            c("CardNoBox").Text(card.str("Asn"));
            c("AccountBox").Text(Conv.toStr(card.get("CustomerAccountID")));
            c("ValidTimeBox").Text(card.str("ValidTime"));
            if (!Mifare_Make(card.str("Asn"))) {
                Map<String, Object> back = new LinkedHashMap<>(card);
                back.put("Update_Type", "Delete");
                send("CardUpdate", back);
                MessageBox("Card RollBack");
            } else {
                MessageBox("CardMakeSuccess");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
