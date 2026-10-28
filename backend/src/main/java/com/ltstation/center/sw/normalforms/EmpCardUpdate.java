package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** Card.EmpCardUpdate : employé et mot de passe d'une carte employé (argument Asn), modifiés sur Center. */
@SwWindow("Card.EmpCardUpdate")
public class EmpCardUpdate extends SwForm {

    public void EmpCardUpdate_Load() {
        try {
            List<Row> list = db().list("Employees");
            c("EmpSelect").DataSource(list, "Name", "EmployeeID");
            if (!list.isEmpty()) {
                c("EmpSelect").SelectedIndex(0);
            }
            Row card = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", argStr("Asn")));
            if (card == null) {
                throw new IllegalStateException(L("CardMiss"));
            }
            c("EmpSelect").SelectedValue(Conv.toInt(card.get("EmployeeCode")));
            c("EmpPassword").Text(card.str("CipherFlg"));
            c("ValidTimeBox").Text(card.dt("ValidDatetime") == null ? "" : card.dt("ValidDatetime").format(Conv.STD));
            c("CardNoBox").Text(card.str("ASN"));
            c("AccountBox").Text(card.str("AccountNo"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void QueryBtn_Click() {
        try {
            Row emp = c("EmpSelect").SelectedRow();
            if (emp == null) {
                throw new IllegalStateException(L("PLSSelectEmp"));
            }
            CustomerRespone r = BaseFunction.post("ExtendUpdate/CardUpdate", SW.dto().make("Extend_Card_Update", "Password", c("EmpPassword").Text(),
                "CompanyID", IniInfo.CompanyID, "OperEmp", IniInfo.LoginID, "SpecialPrice", "", "Update_Type", "Update", "StationID", IniInfo.StationID,
                "LoginType", 1, "Card_Type", 6, "CustomerID", emp.i("EmployeeID"), "Asn", c("CardNoBox").Text()));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
