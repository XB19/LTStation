package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.Profil;
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
            c("BaseAuthority").Enabled(false);
        }
        List<Row> dataSource = db().list("AuthorityMess");
        // Assistant : le premier employé est l'administrateur général ; ensuite, seul lui peut donner un profil d'administrateur
        c("BaseAuthority").DataSource(ID == 0 ? Profil.firstEmployeeProfiles(dataSource) : Profil.assignable(dataSource), "Name", "ID");
        if (ID != 0 && !Profil.currentIsGeneral()) {
            // L'accès au Center est réservé à l'administrateur général
            c("CenterLogin").Checked(false);
            c("CenterLogin").Enabled(false);
        }
    }

    private void DowLoadEmp() {
        try {
            downloadEmployees();
        } catch (RuntimeException ex) {
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

    /** Recopie les employés de la station depuis Center (ServerDeal/GetEmployee) ; utilisé aussi par l'import Excel. */
    static void downloadEmployees() {
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
            throw ex;
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
            if (c("StationSelect").Text().isEmpty() || c("PersonName").Text().isEmpty() || c("LoginName").Text().isEmpty()
                || c("Password").Text().isEmpty()) {
                throw new IllegalStateException(L("InformationLoss"));
            }
            boolean checked = c("CenterLogin").Checked();
            boolean general = CID == 0 || Profil.currentIsGeneral();
            if (!general && Profil.adminProfile(c("BaseAuthority").SelectedRow().str("User_IDMess"))) {
                throw new IllegalStateException(Profil.ONLY_GENERAL + " : création d'un administrateur");
            }
            Row employees = SW.dto().make("CenterEmployees",
                "StationID", c("StationSelect").SelectedRow().get("StationID"),
                "Memo", c("Password").Text(),
                "PostCode", c("BaseAuthority").SelectedRow().str("User_IDMess"),
                "Address", c("LoginName").Text(),
                "Name", c("PersonName").Text(),
                "CenterLogin", general && checked, "KeepCenterLogin", !general);
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
