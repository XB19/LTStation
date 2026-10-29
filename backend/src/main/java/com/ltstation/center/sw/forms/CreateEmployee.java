package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import java.util.List;

/** CreateEmployee(int ID) : création d'un employé sur Center (ID 1 = assistant d'installation). */
@SwWindow(value = "CreateEmployee", setup = true)
public class CreateEmployee extends SwForm {

    private int CID = 1;

    @Override
    protected void init() {
        int ID = argInt("ID");
        GetStation();
        CID = ID;
        if (ID == 0) {
            Row employees = db().first("Employees", "{StationType} = '1'", null);
            if (employees != null) {
                c("PersonName").Text(employees.s("Address"));
                c("LoginName").Text(employees.s("Name"));
                c("Password").Text(employees.s("CertifyKey"));
            }
            c("BaseAuthority").SelectedIndex(1);
            c("BaseAuthority").Enabled(false);
        }
        List<Row> dataSource = db().list("AuthorityMess");
        c("BaseAuthority").DataSource(dataSource, "Name", "ID");
    }

    private void DowLoadEmp() {
        var tx = db().begin();
        try {
            Row stations = db().first("Stations");
            if (stations == null) {
                throw new IllegalStateException("Informations de la station manquantes");
            }
            IniInfo.Code = stations.s("Code");
            Row customerParam = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
            CustomerRespone customerRespone = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetEmployee", Wire.json(customerParam));
            int num = 0;
            if (customerRespone.Result()) {
                db().delete("Employees", null, null);
                while (num < customerRespone.TotalCount()) {
                    for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(customerRespone.Mess()))) {
                        db().insert("Employees", Row.of("StationID", item.get("StationID"), "EmployeeID", item.i("EmployeeID"),
                            "Code", item.str("CodeInner"), "Name", item.get("Name"), "Memo1", item.get("Address"),
                            "Memo2", "".equals(item.str("Memo")) ? "654789" : item.get("Memo"), "StationType", item.get("PostCode"),
                            "CertifyKey", item.get("CertifyKey"), "Birthday", item.get("Birthday"), "Status", "0",
                            "Gender", item.str("GenderID")));
                    }
                    num++;
                    if (num > customerRespone.TotalCount()) {
                        break;
                    }
                    customerParam.put("Start", customerParam.i("Start") + 1);
                    customerRespone = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetEmployee", Wire.json(customerParam));
                }
            }
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
        if (CID == 1) {
            Row employees = db().first("Employees", "{Memo1} = :u AND {Memo2} = :p", p("u", c("LoginName").Text(), "p", c("Password").Text()));
            IniInfo.LoginName = employees.s("Name");
            IniInfo.LoginID = employees.i("EmployeeID");
            IniInfo.LoginType = 1;
        }
        Close();
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
            if (c("StationSelect").Text().isEmpty() || c("PersonName").Text().isEmpty() || c("LoginName").Text().isEmpty()
                || c("Password").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            boolean checked = c("CenterLogin").Checked();
            Row employees = SW.dto().make("CenterEmployees",
                "StationID", c("StationSelect").SelectedRow().get("StationID"),
                "Memo", c("Password").Text(),
                "PostCode", c("BaseAuthority").SelectedRow().str("User_IDMess"),
                "Address", c("LoginName").Text(),
                "Name", c("PersonName").Text(),
                "CenterLogin", checked);
            if (db().first("Employees", "{Name} = :n AND {Memo2} = :m", p("n", employees.get("Address"), "m", employees.get("Memo"))) != null) {
                throw new IllegalStateException(L("DuplicateInformation"));
            }
            CustomerRespone customerRespone = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateEmployee", Wire.json(employees));
            if (customerRespone.Result()) {
                DowLoadEmp();
                MessageBox(L("OperateSuccess"));
                if (CID == 1) {
                    // base.DialogResult = keyMainBoard.ShowDialog()
                    CancelClose();
                    ShowDialog("KeyMainBoard", r -> Close(r.result()), "ID", 1);
                } else {
                    Close("OK");
                }
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(customerRespone.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
