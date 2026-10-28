package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import java.time.LocalDateTime;

/** PortUpdate : modification d'un port série des pistolets (argument ID). */
@SwWindow("NormalForm.PortUpdate")
public class PortUpdate extends SwForm {

    public void PortUpdate_Load() {
        for (String item : SerialClass.serialsIsConnected()) {
            c("PortSelect").ItemsAdd(item);
        }
        ShowCom();
    }

    private void ShowCom() {
        try {
            Row set = db().first("NormalPortSet", "{ID} = :i", p("i", argInt("ID")));
            if (set == null) {
                throw new IllegalStateException("串口信息不存在");
            }
            c("PortSelect").Text(set.str("PortID"));
            c("BuadSelect").Text(set.str("Baud"));
            c("DataBitSelect").Text(set.str("DataBit"));
            c("StopBitSelect").Text(set.str("StopBit"));
            c("ParitySelect").SelectedIndex(set.i("Parity"));
            c("AgreementType").SelectedIndex(set.i("AgreementTypeID"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CanelButton_Click() {
        Close("Cancel");
    }

    private static int parse(String text, String error) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(error);
        }
    }

    public void SaveButton_Click() {
        try {
            int id = argInt("ID");
            String portCom = c("PortSelect").Text();
            int baud = parse(c("BuadSelect").Text(), "波特率选择错误");
            int dataBit = parse(c("DataBitSelect").Text(), "数据位选择错误");
            int stopBit = parse(c("StopBitSelect").Text(), "停止位选择错误");
            Row set = db().first("NormalPortSet", "{ID} = :i", p("i", id));
            if (set == null) {
                throw new IllegalStateException("串口信息不存在");
            }
            if (db().any("NormalPortSet", "{PortID} = :p AND {ID} <> :i", p("p", portCom, "i", id))) {
                throw new IllegalStateException("串口信息已经存在");
            }
            set.put("Parity", c("ParitySelect").SelectedIndex());
            set.put("PortID", portCom);
            set.put("Baud", baud);
            set.put("DataBit", dataBit);
            set.put("StopBit", stopBit);
            set.put("AgreementTypeID", c("AgreementType").SelectedIndex());
            set.put("UpdateTime", LocalDateTime.now().format(Conv.STD));
            db().update("NormalPortSet", set);
            Close("OK");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
