package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.SerialClass;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** KeyMainBoard(id) : graines des clés de cartes (A / B) ; id 1 = assistant d'installation, puis licence. */
@SwWindow(value = "KeyMainBoard", setup = true)
public class KeyMainBoard extends SwForm {

    private int sid;
    private String sSeedAold = "";
    private String sSeedBold = "";

    @Override
    protected void init() {
        sid = argInt("ID");
    }

    public void KeyMainBoard_Load() {
        ReadKeysFromCenter();
        if (sid == 1) {
            c("label5").Text("en-US".equals(IniInfo.Language) ? "Init Card Key,Old Key is 1111111111111111"
                : "请设定您的默认密钥，初始原密码A与原密码B为1111111111111111");
        }
    }

    private void ReadKeysFromCenter() {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetSecretKey", Wire.json(obj));
            if (!r.Result()) {
                return;
            }
            for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                if (item.i("ID") == 21) {
                    sSeedAold = new String(SerialClass.strToToHexByte(BaseFunction.DES3_Decrypt(BaseFunction.CONST_KEY, item.str("D_Data"))),
                        StandardCharsets.US_ASCII);
                }
                if (item.i("ID") == 22) {
                    sSeedBold = new String(SerialClass.strToToHexByte(BaseFunction.DES3_Decrypt(BaseFunction.CONST_KEY, item.str("D_Data"))),
                        StandardCharsets.US_ASCII);
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void SaveButton_Click() {
        try {
            String a = c("SeedAold").Text();
            String b = c("SeedBold").Text();
            String an = c("SeedAnew").Text();
            String bn = c("SeedBnew").Text();
            if (a.length() != 16 || b.length() != 16 || an.length() != 16 || bn.length() != 16) {
                throw new IllegalStateException(L("SecretLength"));
            }
            String digits = "^[0-9]*$";
            if (!(BaseFunction.ValidMessage(a, digits) & BaseFunction.ValidMessage(b, digits) & BaseFunction.ValidMessage(an, digits)
                & BaseFunction.ValidMessage(bn, digits))) {
                throw new IllegalStateException(L("PasswordFormatError"));
            }
            if (!a.equals(sSeedAold) || !b.equals(sSeedBold)) {
                throw new IllegalStateException(L("PassowrdError"));
            }
            List<String> list = new ArrayList<>(List.of(BaseFunction.CreateSecretKey(an, bn)));
            list.add(BaseFunction.DES3_Encrypt(BaseFunction.CONST_KEY, BaseFunction.ConvertToHex(an)));
            list.add(BaseFunction.DES3_Encrypt(BaseFunction.CONST_KEY, BaseFunction.ConvertToHex(bn)));
            Row obj2 = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0, "Mess", BaseFunction.EncryptByAes(Wire.json(list)));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateSecretKey", Wire.json(obj2));
            if (r.Result()) {
                MessageBox(L("UpdateSuccess"));
                if (sid == 1) {
                    // base.DialogResult = register.ShowDialog(); Close();
                    ShowDialog("Register", res -> Close(res.result()));
                    return;
                }
                Close();
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
