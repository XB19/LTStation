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
import com.ltstation.center.sw.hw.CardIssue;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.Operate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** CardChange : remplacement d'une carte (nouvel ASN sur Center, personnalisation de la nouvelle carte, report du solde). */
@SwWindow("CardChange")
public class CardChange extends SwForm {

    private int Ini_CardSelect;

    @Override
    protected void init() {
        c("CardTypeSelect").SelectedIndex(1);
        c("TypeSelect").SelectedIndex(0);
        c("CardTypeIndex").SelectedIndex(0);
        c("CardTypeIndex").SelectedIndex(1);
    }

    /** Math.Round(decimal, n).ToString() : l'échelle n'est jamais augmentée. */
    private static String round(Object v, int n) {
        BigDecimal d = Conv.toDecimal(v);
        return (d.scale() > n ? d.setScale(n, RoundingMode.HALF_EVEN) : d).toPlainString();
    }

    private boolean DealNewAsn(String Asn) {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", Asn);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetAsnMax", Wire.json(obj));
            if (r.Result()) {
                String value = Conv.toStr(Wire.parse(BaseFunction.DecryptByAES(r.Mess())));
                c("CardAsn").Text(BaseFunction.SetStrLen(String.valueOf(Long.parseLong(value) + 1), 20));
                return true;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void SearchButton_Click() {
        try {
            if (c("TypeNo").Text().isEmpty() && c("UserNameSearch").Text().isEmpty() && c("PhoneSearch").Text().isEmpty()) {
                throw new IllegalStateException(L("NeedQueryCondition"));
            }
            int selectedIndex = c("TypeSelect").SelectedIndex();
            int selectedIndex2 = c("CardTypeIndex").SelectedIndex();
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", selectedIndex, "Mess",
                selectedIndex2 + "|" + c("TypeNo").Text() + "|" + c("UserNameSearch").Text() + "|" + c("PhoneSearch").Text());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardChangeGetMess", Wire.json(obj));
            if (r.Result()) {
                List<?> list = (List<?>) Wire.parse(BaseFunction.DecryptByAES(r.Mess()));
                c("ChangeCardNo").ItemsClear();
                for (Object o : list) {
                    c("ChangeCardNo").ItemsAdd(Conv.toStr(o));
                }
                if (!list.isEmpty()) {
                    c("ChangeCardNo").SelectedIndex(0);
                }
                return;
            }
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ChangeButton_Click() {
        try {
            if (c("OldAsn").Text().isEmpty() || c("CardAsn").Text().isEmpty()) {
                throw new IllegalStateException(L("NeedCardNo"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess",
                c("OldAsn").Text() + "|" + c("CardAsn").Text() + "|" + IniInfo.LoginID);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardChange", Wire.json(obj));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row info = SW.dto().shape("CenterCustomerCardInfos", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            if (!CardMC(info)) {
                r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardReturn", Wire.json(obj));
                if (r.Result()) {
                    MessageBox(L("CardMakeErrorReturn"));
                }
            } else {
                MessageBox(L("CardChangeComplete"));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean CardMC(Row info) {
        Operate operate = new Operate();
        try {
            if (!operate.SCardConnect(CardWrite.READER)) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            String asn = c("CardAsn").Text();
            String sPublicInfo = PrepareBuildInfo(asn, info);
            if (sPublicInfo == null) {
                throw new IllegalStateException(L("CardPublishMessageFailed"));
            }
            List<String> list = new ArrayList<>();
            if (!CardWrite.ReadKeysFromDB(list)) {
                throw new IllegalStateException(L("GetSecretError"));
            }
            for (int i = 0; i < 20; i++) {
                String master = i == 6 || i == 11 ? BaseFunction.fnGetDeriveKey(list.get(i), BaseFunction.CON_BIN) : list.get(i);
                list.set(i, BaseFunction.fnGetDeriveKey(master, asn));
            }
            String[] keys = list.toArray(new String[0]);
            switch (c("CardTypeSelect").SelectedIndex()) {
                case 0 -> {
                    if (!personalize(operate, info, asn, () -> CardIssue.WriteDataToACS(operate, sPublicInfo, keys))) {
                        throw new IllegalStateException(L("CardChangeFailed"));
                    }
                }
                case 1 -> {
                    if (!personalize(operate, info, asn, () -> CardIssue.WriteDataFlying(operate, sPublicInfo, keys))) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                case 2 -> {
                    if (!personalize(operate, info, asn, () -> CardIssue.WriteDataToDHCard(operate, IniInfo.DHTransferKey, sPublicInfo, keys))) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                default -> {
                }
            }
            operate.SCardDisconnect();
            String bin = asn.substring(0, 6);
            if (bin.equals("010001") || bin.equals("010002") || bin.equals("010004")) {
                double amount = Conv.toDouble(c("Balance").Text());
                Conv.toDouble(c("LogBalance").Text());
                if (!DepositAmount(amount, info)) {
                    throw new IllegalStateException(L("BalanceReturnError"));
                }
            }
            return true;
        } catch (RuntimeException ex) {
            operate.SCardDisconnect();
            MessageBox(ex.getMessage());
            return false;
        }
    }

    /** WriteDataToACS / WriteDataFlying / WriteDataToDHCard puis WriteCard ; false en cas d'erreur. */
    private boolean personalize(Operate operate, Row info, String asn, Runnable apdus) {
        try {
            apdus.run();
            if (!WriteCard(operate, asn, info)) {
                throw new IllegalStateException(L("CardWriteError"));
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String PrepareBuildInfo(String Asn, Row info) {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
            return BaseFunction.CON_BIN + "1101" + Asn + Conv.toDateTime(info.get("IssueDatetime")).format(f)
                + Conv.toDateTime(info.get("ValidDatetime")).format(f) + "0100";
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String special(Object v) {
        return v == null ? "" : v.toString().replace(",", "");
    }

    private boolean WriteCard(Operate operate, String NewAsn, Row info) {
        if (info.get("CipherFlg") == null) {
            info.put("CipherFlg", "");
        }
        try {
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            boolean flag = false;
            switch (NewAsn.substring(0, 6)) {
                case "010001", "010014", "010013", "010011" -> {
                    Row obj = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID")));
                    if (obj == null) {
                        throw new IllegalStateException(L("UserInexistence"));
                    }
                    flag = CardWrite.WriteOnNewPersonalCard(operate, obj.str("Name"), obj.str("CertificateNo"), Conv.toStr(obj.get("CertificateTypeID")),
                        info.str("CipherFlg"), info.get("LimitOil") == null ? "" : info.str("LimitOil"), Conv.toStr(info.get("LimitTypeID")),
                        info.str("UseConfine"), Conv.toStr(info.get("LimitVolume")), Conv.toStr(info.get("LimitTime")), Conv.toStr(info.get("LimitAmount")),
                        info.str("LimitVehicleNo"), Conv.toStr(info.get("LimitVolumeMonthly")), Conv.toStr(info.get("LimitAmountMonthly")),
                        Conv.toStr(info.get("LimitMileage")), Conv.toStr(info.get("MileageTolerance")), "", Conv.toStr(info.get("Discount")),
                        Conv.toStr(info.get("isLimitVehicleNo")), Conv.toDateTime(info.get("ValidDatetime")).format(Conv.STD), "", "", "", "", "",
                        list.get(1), special(info.get("SpecialPrice")));
                    if (!flag) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                case "010004" -> {
                    Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", info.get("CustomerID")));
                    if (emp == null) {
                        throw new IllegalStateException(L("UserInexistence"));
                    }
                    Row card = db().first("EmployeeCardInfos", "{EmployeeCode} = :c", p("c", emp.str("EmployeeID")));
                    if (card != null) {
                        flag = CardWrite.WriteOnEmployeeCard(operate, emp.str("Code"), emp.str("Name"),
                            emp.get("CertifyType") == null ? "1" : Conv.toStr(emp.get("CertifyType")), emp.str("CertifyKey"), card.str("CipherFlg"),
                            list.get(1), special(card.get("SpecialPrice")));
                    }
                }
                default -> {
                }
            }
            return flag;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void IniButton_Click() {
        try {
            Ini_CardSelect = c("CardTypeSelect").SelectedIndex();
            Card_Init();
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void Card_Init() {
        Operate operate = new Operate();
        try {
            if (!operate.SCardConnect(CardWrite.READER)) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            switch (Ini_CardSelect) {
                case 1 -> {
                    if (!CardWrite.ExtAuthorizeForFlying(operate)) {
                        throw new IllegalStateException(L("ExternalValidationError"));
                    }
                    if (!CardWrite.DeleteForFlying(operate) && !CardWrite.NewDeleteForFlying(operate)) {
                        throw new IllegalStateException(L("InitializeError"));
                    }
                }
                case 2 -> {
                    if (!CardWrite.DeleteMF(operate, IniInfo.DHTransferKey, 0)) {
                        throw new IllegalStateException(L("InitializeError"));
                    }
                }
                default -> {
                }
            }
            operate.SCardDisconnect();
            MessageBox(L("InitializeSuccess"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private boolean DepositAmount(double Amount, Row info) {
        try {
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            if (!CardWrite.WriteOnLoad(c("CardAsn").Text(), Conv.num(Amount), "000000000000", info.str("CipherFlg"), list.get(7))) {
                throw new IllegalStateException(L("DepositError"));
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void CardTypeSelect_SelectedIndexChanged() {
        boolean show = c("CardTypeSelect").SelectedIndex() != 0;
        c("IniButton").Visible(show);
        c("label11").Visible(show);
    }

    public void ChangeCardNo_SelectedIndexChanged() {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", c("ChangeCardNo").Text());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardChangeGetAsnMess", Wire.json(obj));
            if (!r.Result()) {
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            Row m = SW.dto().shape("CardChangeGetMessage", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            c("UserName").Text(m.str("UserName"));
            c("UserCardName").Text(m.str("UserName"));
            c("UserID").Text(m.str("UserID"));
            c("OldAsn").Text(m.str("Asn"));
            c("Balance").Text(round(m.get("Balance"), IniInfo.Mradio));
            c("LogBalance").Text(round(m.get("TotalAmount"), IniInfo.Mradio));
            DealNewAsn(m.str("Asn"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
