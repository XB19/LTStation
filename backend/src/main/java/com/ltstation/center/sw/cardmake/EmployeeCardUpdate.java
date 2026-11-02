package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwWindow;

/** EmployeeCardUpdate : station, employé et mot de passe d'une carte employé (sur Center seulement). */
@SwWindow("CardMake.EmployeeCardUpdate")
public class EmployeeCardUpdate extends CardMakeForm {

    private Row EmployeeCard;

    @Override
    protected void init() {
        try {
            EmployeeCard = sendRow("ExtendGetSingleEmployeeCard", Row.of("Asn", argStr("ASN")));
            c("EmpPassword").Text(EmployeeCard.str("CipherFlg"));
            c("ValidTimeBox").Text(dt(EmployeeCard.get("ValidDatetime")));
            c("CardNoBox").Text(EmployeeCard.str("ASN"));
            c("AccountBox").Text(Conv.toStr(EmployeeCard.get("CustomerAccountID")));
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
            if (EmployeeCard != null) {
                c("EmpSelect").SelectedValue(EmployeeCard.get("EmployeeID"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void EmployeeCardUpdate_Load() {
        try {
            LoadStations("StationSelect");
            if (EmployeeCard != null) {
                c("StationSelect").SelectedValue(EmployeeCard.get("StationID"));
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
            send("CardUpdate", Row.of("Password", c("EmpPassword").Text(), "CompanyID", CM.CompanyID, "SpecialPrice", "", "Update_Type", "Update",
                "StationID", Conv.toInt(c("StationSelect").SelectedValue()), "LoginType", 0, "Card_Type", 6, "CustomerID", Conv.toInt(c("EmpSelect").SelectedValue()),
                "OperEmp", CM.EmpID, "Asn", c("CardNoBox").Text()));
            MessageBox("CardMakeSuccess");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
