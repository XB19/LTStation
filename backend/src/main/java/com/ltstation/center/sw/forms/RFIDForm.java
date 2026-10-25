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
import com.ltstation.center.sw.hw.SerialClass;
import com.ltstation.center.sw.hw.rfid.MessageTran;
import com.ltstation.center.sw.hw.rfid.ReaderMethod;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** RFIDForm : lecture d'un badge RFID de véhicule et création du compte RFID sur Center. */
@SwWindow("RFIDForm")
public class RFIDForm extends SwForm {

    private final ReaderMethod reader = new ReaderMethod();
    private final int TagBit = 5;

    @Override
    protected void init() {
        Poll(500);
    }

    public void RFIDForm_Load() {
        for (String port : SerialClass.serialsIsConnected()) {
            c("PortCoboBox").ItemsAdd(port);
        }
        c("BaudcomboBox").SelectedIndex(1);
        c("BitcomboBox").SelectedIndex(0);
        reader.AnalyCallback = this::AnalyData;
        LoadDiscount();
        LoadOilType();
        c("ConnectBtn").Enabled(true);
        c("DisConnectBtn").Enabled(false);
    }

    private void LoadDiscount() {
        try {
            List<Row> list = new ArrayList<>();
            list.add(Row.of("ID", 0, "Count", BigDecimal.valueOf(100), "Name", L("NoDiscount")));
            list.addAll(db().list("DisCount", "{Count} < 100 ORDER BY {ID}", null));
            list.sort((a, b) -> Conv.toDecimal(b.get("Count")).compareTo(Conv.toDecimal(a.get("Count"))));
            c("RFIDDiscount").DataSource(list, "Name", "Count");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void LoadOilType() {
        try {
            List<Row> list = new ArrayList<>(db().list("GradeSpecifics"));
            list.add(Row.of("Name", L("NoLimit"), "GradeSpecificID", 0));
            list.sort((a, b) -> Integer.compare(a.i("GradeSpecificID"), b.i("GradeSpecificID")));
            c("LimitOilType").DataSource(list, "Name", "GradeSpecificID");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void ConnectBtn_Click() {
        try {
            String[] ex = {""};
            if (reader.OpenCom(c("PortCoboBox").Text(), Conv.toInt(c("BaudcomboBox").Text()), ex) != 0) {
                throw new IllegalStateException(ex[0]);
            }
            AddLog("连接成功");
            c("ConnectBtn").Enabled(false);
            c("DisConnectBtn").Enabled(true);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void AddLog(String mess) {
        c("LogBox").Text(c("LogBox").Text() + mess + "\r\n");
    }

    public void ReadBtn_Click() {
        reader.ReadTag(0, 2, 0, 5, null);
        AddLog("读标签");
    }

    private void AnalyData(MessageTran msgTran) {
        if (msgTran.PacketType() == 160 && msgTran.Cmd() == 129) {
            ProcessReadTag(msgTran);
        }
    }

    /** Badge lu : numéro du badge (Invoke vers la fenêtre). */
    private void ProcessReadTag(MessageTran msgTran) {
        synchronized (this) {
            try {
                byte[] data = msgTran.AryTranData();
                if (data.length != 1) {
                    if (data.length <= 24) {
                        throw new IllegalStateException("请确认卡是否存在");
                    }
                    StringBuilder text = new StringBuilder();
                    for (int j = 23; j < data.length - 4; j++) {
                        text.append(String.format("%02X", data[j] & 0xFF));
                    }
                    c("TagNumber").Text(text.substring(0, TagBit * 4));
                }
            } catch (RuntimeException ex) {
                MessageBox(ex.getMessage());
            }
        }
    }

    /** Badge lu : compte RFID du client sur Center. */
    public void TagNumber_TextChanged() {
        if (c("TagNumber").Text().isEmpty()) {
            return;
        }
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", c("TagNumber").Text());
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetRFIDAccount", Wire.json(obj));
        if (r.Result()) {
            Row pc = Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()));
            int num = Conv.toInt(pc.get("AccountNo"));
            c("RFIDAccount").Text(String.valueOf(num));
            c("UserName").Text(pc.get("Name"));
            c("Phone").Text(pc.get("Telephone"));
            c("Certificate").Text(pc.get("CertificateNo"));
            c("CarNo").Text(pc.get("VehicleNo"));
            c("PostCode").Text(pc.get("PostCode"));
            c("Address").Text(pc.get("Address"));
            c("Email").Text(pc.get("Email"));
            c("RFIDDiscount").SelectedValue(Conv.toDecimal(pc.get("Discount") == null ? "100" : pc.get("Discount")));
            c("LimitOilType").SelectedValue(Conv.toInt(pc.get("LimitOil")));
            c("DayTimeLimit").Text(pc.get("LimitTimeDay"));
            c("RFIDLimitVolume").Text(pc.get("LimitVolume"));
            c("DayAmountLimit").Text(pc.get("LimitAmountDay"));
            c("MonthAmountLimit").Text(pc.get("LimitAmountMonth"));
            c("MonthVolumeLImit").Text(pc.get("LimitVolumeMonth"));
            c("CarNoLimit").Checked(pc.b("isLimitVehicleNo"));
            if (pc.str("Name").isEmpty()) {
                c("RFIDAccount").Text(String.valueOf(num + 1));
            }
        } else {
            MessageBox(BaseFunction.DecryptByAES(r.Mess()));
        }
    }

    private static String zero(String s) {
        return s.isEmpty() ? "0" : s;
    }

    public void CardMake_Click() {
        String[] error = {""};
        if (Regular(error)) {
            Row obj = SW.dto().make("CardMessage", "ASN", c("TagNumber").Text(), "Name", c("UserName").Text(), "Telephone", c("Phone").Text(),
                "CertificateNo", c("Certificate").Text(), "VehicleNo", c("CarNo").Text(), "AccountNo", c("RFIDAccount").Text(), "PostCode",
                c("PostCode").Text(), "Address", c("Address").Text(), "Email", c("Email").Text(), "Discount",
                Conv.toStr(c("RFIDDiscount").SelectedRow().get("Count")), "LimitVolume", zero(c("RFIDLimitVolume").Text()), "LimitTimeDay",
                zero(c("DayTimeLimit").Text()), "LimitAmountDay", zero(c("DayAmountLimit").Text()), "LimitAmountMonth", zero(c("MonthAmountLimit").Text()),
                "LimitVolumeMonth", zero(c("MonthVolumeLImit").Text()), "isLimitVehicleNo", c("CarNoLimit").Checked(), "LimitVehicleNo",
                c("CarNo").Text(), "Code", IniInfo.Code);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateRFIDAccount", Wire.json(obj));
            MessageBox(r.Result() ? "操作完成" : BaseFunction.DecryptByAES(r.Mess()));
        } else {
            MessageBox(error[0]);
        }
    }

    private boolean Regular(String[] error) {
        error[0] = "";
        try {
            dec(zero(c("RFIDLimitVolume").Text()), "单次升数限制格式错误");
            try {
                Integer.parseInt(zero(c("DayTimeLimit").Text()).trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("日次数限制格式错误");
            }
            dec(zero(c("DayAmountLimit").Text()), "日金额限制格式错误");
            dec(zero(c("MonthVolumeLImit").Text()), "月金额限制格式错误");
            dec(zero(c("MonthAmountLimit").Text()), "月升数限制格式错误");
            return true;
        } catch (RuntimeException ex) {
            error[0] = ex.getMessage();
            return false;
        }
    }

    private static void dec(String s, String err) {
        try {
            new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(err);
        }
    }

    public void DisConnectBtn_Click() {
        try {
            reader.CloseCom();
            c("ConnectBtn").Enabled(true);
            c("DisConnectBtn").Enabled(false);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void RFIDForm_FormClosed() {
        reader.CloseCom();
    }
}
