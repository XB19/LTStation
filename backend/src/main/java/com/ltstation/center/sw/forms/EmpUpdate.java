package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** EmpUpdate(id) : modification d'un employé sur Center, puis rechargement des employés de la station. */
@SwWindow("EmpUpdate")
public class EmpUpdate extends SwForm {

    private int sID;

    @Override
    protected void init() {
        GetStation();
        sID = argInt("ID");
    }

    public void EmpUpdate_Load() {
        try {
            c("Authority").DataSource(db().list("AuthorityMess"), "Name", "User_IDMess");
            Row e = db().first("Employees", "{EmployeeID} = :id", p("id", sID));
            if (e != null) {
                c("EmpID").Text(e.str("EmployeeID"));
                c("StationSelect").SelectedIndex(0);
                c("PersonName").Text(e.get("Name"));
                c("LoginName").Text(e.get("Memo1"));
                c("Password").Text(e.get("Memo2"));
                c("Authority").SelectedValue(Conv.toInt(e.get("StationType")));
                c("CenterLogin").Checked(e.b("CenterLogin"));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
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

    public void SaveButton_Click() {
        try {
            if (c("StationSelect").Text().isEmpty() || c("PersonName").Text().isEmpty() || c("LoginName").Text().isEmpty() || c("Password").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            Row e = db().first("Employees", "{EmployeeID} = :id", p("id", Conv.toInt(c("EmpID").Text())));
            if (e != null) {
                Row obj = SW.dto().make("CenterEmployees", "EmployeeID", e.get("EmployeeID"), "StationID", IniInfo.StationID, "PostCode",
                    Conv.toStr(c("Authority").SelectedValue()), "Address", c("LoginName").Text(), "Name", c("PersonName").Text(), "Memo",
                    c("Password").Text(), "CenterLogin", c("CenterLogin").Checked());
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateEmployee", Wire.json(obj));
                if (!r.Result()) {
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                MessageBox(L("OperateSuccess"));
                DowLoadEmp();
                Close("OK");
            } else {
                Close();
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Employés de la station rechargés depuis Center (100 par appel). */
    private void DowLoadEmp() {
        var tx = db().begin();
        try {
            Row customerParam = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetEmployee", Wire.json(customerParam));
            int num = 0;
            if (r.Result()) {
                db().delete("Employees", null, null);
                while (num < r.TotalCount()) {
                    for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                        db().insert("Employees", Row.of("StationID", item.get("StationID"), "EmployeeID", item.i("EmployeeID"), "Code", item.str("CodeInner"),
                            "Name", item.get("Name"), "Memo1", item.get("Address"), "Memo2", "".equals(item.str("Memo")) ? "654789" : item.get("Memo"),
                            "StationType", item.get("PostCode"), "CertifyKey", item.get("CertifyKey"), "Birthday", item.get("Birthday"), "Status", "0",
                            "Gender", item.str("GenderID")));
                    }
                    num += 100;
                    if (num > r.TotalCount()) {
                        break;
                    }
                    customerParam.put("Start", customerParam.i("Start") + 100);
                    r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetEmployee", Wire.json(customerParam));
                }
            }
            tx.commit();
        } catch (RuntimeException e) {
            tx.rollback();
        }
    }
}
