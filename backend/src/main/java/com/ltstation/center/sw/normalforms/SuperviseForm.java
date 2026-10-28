package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BoxBase;
import com.ltstation.center.sw.normal.NormalEngine;
import java.time.LocalDateTime;

/** Station.SuperviseForm : supervision fiscale (aucune, EFD Tanzanie, eTIMS Kenya), enregistrement et démarrage des envois. */
@SwWindow("Station.SuperviseForm")
public class SuperviseForm extends SwForm {

    private static final LegacyLog SuperviseLog = new LegacyLog("SuperviseLog");

    public void SaveButton_Click() {
        try {
            if (c("NoneBtn").Checked()) {
                IniInfo.SupervisionType = 0;
            }
            if (c("TanSanBtn").Checked()) {
                IniInfo.SupervisionType = 3;
            }
            if (c("KenyaBtn").Checked()) {
                IniInfo.SupervisionType = 4;
            }
            Row sup = db().first("Supervision");
            if (sup == null) {
                sup = new Row();
            }
            sup.put("SupervisionType", IniInfo.SupervisionType);
            if (IniInfo.SupervisionType == 3) {
                sup.put("UserName", c("UserNameBox").Text());
                sup.put("Password", c("PasswordBox").Text());
                replace(sup);
                BoxBase.EFDPFXNumber = java.util.Base64.getEncoder()
                    .encodeToString(BoxBase.ReadPFXNumber().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                SuperviseLog.writeLog("TRAReg:" + sup.str("UserName"));
                if (BoxBase.EFDRegistration()) {
                    BoxBase.EFDTokenTime = LocalDateTime.MIN;
                    NormalEngine.StartEFD();
                }
            } else if (IniInfo.SupervisionType == 4) {
                sup.put("UserName", c("TinBox").Text());
                sup.put("Password", c("BhfIdBox").Text());
                sup.put("dvcSrlNo", c("DvcSrlNoBox").Text());
                sup.put("BaseUrl", c("UrlBox").Text());
                replace(sup);
                BoxBase.kenyaDeal.tin = sup.str("UserName");
                BoxBase.kenyaDeal.bhfId = sup.str("Password");
                BoxBase.kenyaDeal.dvcSrlNo = sup.str("dvcSrlNo");
                BoxBase.kenyaDeal.BaseUrl = sup.str("BaseUrl");
                String[] error = {""};
                if (!BoxBase.kenyaDeal.Initialisation(error)) {
                    throw new IllegalStateException(error[0]);
                }
                BoxBase.kenyaDeal.selectCodeList(error);
                BoxBase.kenyaDeal.selectItemClsList(error);
                BoxBase.kenyaDeal.selectBhfList(error);
            } else {
                BoxBase.EFDDealRun = false;
            }
            BaseFunction.SetConfigValue("SupervisionType", String.valueOf(IniInfo.SupervisionType));
            MessageBox(L("OperateSuccess"));
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Une seule ligne de supervision : l'ancienne est supprimée avant l'enregistrement. */
    private void replace(Row sup) {
        sup.remove("ID");
        db().delete("Supervision", null, null);
        db().insert("Supervision", sup);
    }

    private void showEfd(boolean editable) {
        c("UserNameBox").ReadOnly(!editable);
        c("PasswordBox").ReadOnly(!editable);
    }

    private void fill(int type) {
        Row sup = db().first("Supervision", "{SupervisionType} = :t", p("t", type));
        if (sup == null) {
            return;
        }
        if (type == 3) {
            c("UserNameBox").Text(sup.str("UserName"));
            c("PasswordBox").Text(sup.str("Password"));
        } else {
            c("TinBox").Text(sup.str("UserName"));
            c("BhfIdBox").Text(sup.str("Password"));
            c("DvcSrlNoBox").Text(sup.str("dvcSrlNo"));
            c("UrlBox").Text(sup.str("BaseUrl"));
        }
    }

    public void SuperviseForm_Load() {
        try {
            c("UserNameBox").Text("");
            c("PasswordBox").Text("");
            showEfd(false);
            switch (IniInfo.SupervisionType) {
                case 0 -> c("NoneBtn").Checked(true);
                case 3 -> {
                    c("TanSanBtn").Checked(true);
                    Row sup = db().first("Supervision");
                    if (sup != null) {
                        c("UserNameBox").Text(sup.str("UserName"));
                        c("PasswordBox").Text(sup.str("Password"));
                    }
                    showEfd(true);
                    c("GunAndTankTab").SelectedIndex(1);
                }
                case 4 -> {
                    c("KenyaBtn").Checked(true);
                    Row sup = db().first("Supervision");
                    if (sup != null) {
                        c("TinBox").Text(sup.str("UserName"));
                        c("BhfIdBox").Text(sup.str("Password"));
                        c("DvcSrlNoBox").Text(sup.str("dvcSrlNo"));
                        c("UrlBox").Text(sup.str("BaseUrl"));
                    }
                    c("GunAndTankTab").SelectedIndex(2);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void NoneBtn_CheckedChanged() {
        try {
            switch (sender) {
                case "NoneBtn" -> {
                    c("UserNameBox").Text("");
                    c("PasswordBox").Text("");
                    showEfd(false);
                    c("GunAndTankTab").SelectedIndex(0);
                }
                case "TanSanBtn" -> {
                    showEfd(true);
                    c("GunAndTankTab").SelectedIndex(1);
                    fill(3);
                }
                case "KenyaBtn" -> {
                    c("GunAndTankTab").SelectedIndex(2);
                    fill(4);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
