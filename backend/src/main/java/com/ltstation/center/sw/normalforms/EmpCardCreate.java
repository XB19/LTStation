package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.Mifare;
import java.util.List;
import java.util.Map;

/** Card.EmpCardCreate : création d'une carte employé sur Center puis écriture Mifare ; annulation sur Center si l'écriture échoue. */
@SwWindow("Card.EmpCardCreate")
public class EmpCardCreate extends SwForm {

    private long icdev;

    public void EmpCardCreate_Load() {
        try {
            List<Row> list = db().list("Employees");
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
            Row emp = c("EmpSelect").SelectedRow();
            if (emp == null) {
                throw new IllegalStateException(L("PLSSelectEmp"));
            }
            CustomerRespone r = BaseFunction.post("ExtendUpdate/CardUpdate", SW.dto().make("Extend_Card_Update", "Password", c("EmpPassword").Text(),
                "CompanyID", IniInfo.CompanyID, "OperEmp", IniInfo.LoginID, "SpecialPrice", "", "Update_Type", "Add", "StationID", IniInfo.StationID,
                "LoginType", 1, "Card_Type", 6, "CustomerID", emp.i("EmployeeID")));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row card = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            c("CardNoBox").Text(card.str("Asn"));
            c("AccountBox").Text(card.str("CustomerAccountID"));
            c("ValidTimeBox").Text(card.str("ValidTime"));
            if (!Card_Deal(card)) {
                Map<String, Object> back = new java.util.LinkedHashMap<>(card);
                back.put("Update_Type", "Delete");
                r = BaseFunction.post("ExtendUpdate/CardUpdate", back);
                if (r.Result()) {
                    MessageBox(L("DataRollbackTran"));
                }
            } else {
                MessageBox(L("CardMakeSuccess"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Card_Deal(Row card) {
        try {
            icdev = Mifare.rf_init(0, 9600);
            if (icdev <= 0) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            Mifare.Select(icdev);
            if (Mifare.rf_authentication_key(icdev, 0, 4, Mifare.Key()) != 0) {
                throw new IllegalStateException("Card Init Error");
            }
            Mifare.WriteCard(icdev, card.str("Asn"));
            Mifare.rf_exit(icdev);
            icdev = 0;
            return true;
        } catch (RuntimeException ex) {
            Mifare.rf_exit(icdev);
            icdev = 0;
            MessageBox(ex.getMessage());
            return false;
        }
    }
}
