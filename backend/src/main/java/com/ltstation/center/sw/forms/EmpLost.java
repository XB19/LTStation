package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** EmpLost(Asn) : mise en opposition d'une carte employé. */
@SwWindow("EmpLost")
public class EmpLost extends SwForm {

    private String CardNo = "";

    @Override
    protected void init() {
        String asn = argStr("Asn");
        GetStation();
        CardNo = asn;
        Row emp = db().first("EmployeeCardInfos", "{ASN} = :a", p("a", asn));
        if (emp != null) {
            c("AsnNo").Text(emp.get("ASN"));
            c("StationSelect").SelectedIndex(0);
            c("IssueTime").Text(emp.dt("IssueDatetime").format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            Row employees = db().first("Employees", "{EmployeeID} = :id", p("id", Conv.toInt(emp.get("EmployeeCode"))));
            if (employees != null) {
                c("EmpID").Text(employees.str("EmployeeID"));
                c("EmpName").Text(employees.get("Name"));
            }
        }
    }

    private void GetStation() {
        try {
            c("StationSelect").ItemsClear();
            List<Row> list = db().list("Stations");
            if (list.isEmpty()) {
                throw new IllegalStateException(L("UnvalidatedRequest"));
            }
            c("StationSelect").DataSource(list, "Name", "Code");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    public void LostButton_Click() {
        if (!CardNo.isEmpty()) {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", CardNo);
            if (BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/EmployeeCardLost", Wire.json(obj)).Result()) {
                MessageBox(L("OperateSuccess"));
                Close("OK");
            }
        }
    }
}
