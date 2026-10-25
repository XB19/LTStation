package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.util.ArrayList;
import java.util.List;

/** PasswordLost : réinitialisation du code PIN d'une carte (Center puis carte). */
@SwWindow("PasswordLost")
public class PasswordLost extends SwForm {

    public void ReadButton_Click() {
        try {
            CardWrite.UserPublicData[] user = {null};
            boolean num = CardWrite.ReadCardNotVerify(user);
            MessageBox(user[0].ASN);
            if (num) {
                BaseFunction.CusMess m = BaseFunction.GetCusMess(user[0].ASN);
                if (m.ok()) {
                    c("CardNo").Text(user[0].ASN);
                    c("PersonName").Text(m.name());
                    c("CustomerID").Text(String.valueOf(m.cusId()));
                    c("CertificateNo").Text(m.certificateNo());
                    return;
                }
                throw new IllegalStateException(m.error());
            }
            throw new IllegalStateException(L("ReadCardFailed"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ShiftClose_Click() {
        Close();
    }

    public void Confirm_Click() {
        try {
            if (Regular()) {
                String text = c("NewPassWord").Text();
                List<String> list = new ArrayList<>();
                if (!CardWrite.ReadKeyFromDBNotChange(list)) {
                    throw new IllegalStateException(L("GetSecretError"));
                }
                Row info = SW.dto().make("CenterCustomerCardInfos", "ASN", c("CardNo").Text(), "CipherFlg", text);
                Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", Wire.json(info));
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ReloadPwd", Wire.json(obj));
                if (!r.Result()) {
                    throw new IllegalStateException("中心验证错误");
                }
                if (!CardWrite.ReloadPin(text, list.get(5), list.get(1))) {
                    // Comme l'original : la requête de rétablissement renvoie le même objet (CipherFlg non resérialisé)
                    info.put("CipherFlg", r.Mess());
                    BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/ReloadPwd", Wire.json(obj));
                    throw new IllegalStateException(L("InitPasswordFailed"));
                }
                MessageBox(L("OperateSuccess"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean Regular() {
        try {
            if (!c("NewPassWord").Text().isEmpty() && !BaseFunction.ValidMessage(c("NewPassWord").Text(), "^\\d{4}$")) {
                throw new IllegalStateException(L("PasswordFormatError"));
            }
            if (c("CardNo").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            if (c("PersonName").Text().isEmpty() || c("CustomerID").Text().isEmpty()) {
                throw new IllegalStateException(L("InvalidInformation"));
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }
}
