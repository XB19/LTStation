package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.ExportFile;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.PrintClass;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Deposit : recharge, retrait, consommation, report/transfert et dépôt en ligne d'une carte ; remboursements des crédits. */
@SwWindow("Deposit")
public class Deposit extends SwForm {

    private static final String TID = "000000000000";
    private String PassWord = "";

    public void Deposit_Load() {
        c("PayMent").SelectedIndex(0);
        c("BalanceChange").SelectedIndex(0);
        c("MasterPayMent").SelectedIndex(0);
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private static BigDecimal dec(String s) {
        return s.isEmpty() ? BigDecimal.ZERO : Conv.toDecimal(s);
    }

    private static String str(BigDecimal d) {
        return d.toPlainString();
    }

    private Row userCard(String asn) {
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", asn);
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetDespoitUser", Wire.json(obj));
        return r.Result() ? SW.dto().shape("UserCardInfo", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess()))) : null;
    }

    /** Lecture de la carte (directement ou après PWDInput) ; remplit l'onglet du bouton btnName. */
    public boolean ReLoad(String Pwd, String btnName) {
        try {
            PassWord = Pwd;
            CardWrite.UserPublicData[] user = {null};
            BigDecimal[] bal = {BigDecimal.ZERO};
            boolean num = CardWrite.ReadCardOnLoad(Pwd, user, bal);
            if (user[0] == null) {
                throw new NullPointerException();
            }
            String bin = user[0].ASN.substring(0, 6);
            if (!bin.equals("010006") && !bin.equals("010001") && !bin.equals("010004") && !bin.equals("010014") && !bin.equals("010002")
                && (!IniInfo.CreditPayAdvance || !bin.equals("010011"))) {
                throw new IllegalStateException(L("CardNotSupport"));
            }
            if (!num) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", user[0].ASN);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetDespoitUser", Wire.json(obj));
            if (!r.Result()) {
                throw new IllegalStateException(L("UserInexistence"));
            }
            Row info = SW.dto().shape("UserCardInfo", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            if (info.i("CardStatue") != 0) {
                throw new IllegalStateException(L("CardStatueError"));
            }
            boolean online = bin.equals("010014") || bin.equals("010011");
            String balance = online ? Conv.toStr(info.get("Balance")) : str(bal[0]);
            switch (btnName) {
                case "SaveButton" -> {
                    c("Balance").Text(balance);
                    c("ASN").Text(info.str("Asn"));
                    c("UserName").Text(info.str("Name"));
                }
                case "CardReader" -> {
                    c("DeductionASN").Text(info.str("Asn"));
                    c("DeductionName").Text(info.str("Name"));
                    c("DeductionBalance").Text(balance);
                }
                case "CardConsumeRead" -> {
                    c("ConsumeASN").Text(info.str("Asn"));
                    c("ConsumeName").Text(info.str("Name"));
                    c("ConsumeBalance").Text(balance);
                }
                case "STRead" -> STRead(info, bal[0]);
                case "TransferReader" -> {
                    c("TransferAsn").Text(info.str("Asn"));
                    c("TransferBalance").Text(str(bal[0]));
                    c("TransferName").Text(info.str("Name"));
                    r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardDeposit", Wire.json(obj));
                    if (!r.Result()) {
                        throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                    }
                    c("TransferAmount").Text(BaseFunction.DecryptByAES(r.Mess()));
                }
                default -> {
                }
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    /** Onglet report / transfert : carte de groupe (010002) ou carte utilisateur (010001). */
    private void STRead(Row info, BigDecimal balance) {
        c("STAsn").Text(info.str("Asn"));
        c("STBalance").Text(str(balance));
        String bin = c("STAsn").Text().substring(0, 6);
        if (bin.equals("010002")) {
            c("STTypeSelect").SelectedIndex(0);
            for (String n : new String[] {"label33", "SaveToAsn", "label28", "SaveToName"}) {
                c(n).Visible(true);
            }
            c("STAmount").Enabled(true);
            Row group = db().first("GroupCardInfos", "{Asn} = :a", p("a", c("STAsn").Text()));
            if (group == null) {
                throw new NullPointerException();
            }
            List<Row> list = db().list("CustomerCardInfos", "{GroupID} = :g", p("g", group.get("GroupID")));
            c("SaveToAsn").ItemsClear();
            for (Row x : list) {
                c("SaveToAsn").ItemsAdd(x.str("ASN"));
            }
            if (!list.isEmpty()) {
                c("SaveToAsn").SelectedIndex(0);
            }
        } else if (bin.equals("010001")) {
            c("STTypeSelect").SelectedIndex(1);
            for (String n : new String[] {"label33", "SaveToAsn", "label28", "SaveToName"}) {
                c(n).Visible(false);
            }
            c("STAmount").Enabled(false);
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", c("STAsn").Text());
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetSTranMess", Wire.json(obj));
            if (r.Result()) {
                Object st = Wire.parse(BaseFunction.DecryptByAES(r.Mess()));
                if (st != null) {
                    c("STAmount").Text(Conv.toStr(SW.dto().shape("SaveTransfer", Conv.toRow(st)).get("Amount")));
                }
            }
        }
    }

    public void SaveButton_Click() {
        String name = sender;
        try {
            String[] res = {""};
            if (!CardWrite.ReadPinID(res)) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            if (res[0].equals("01")) {
                ShowDialog("PWDInput", r -> { }, "BtnName", name);
            } else {
                ReLoad("", name);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** CustomerParam renvoyé à BalanceBack quand l'écriture sur la carte échoue après la mise à jour de Center. */
    private void BalanceBack(CustomerRespone r) {
        Row obj = SW.dto().make("CustomerParam", "Mess", BaseFunction.DecryptByAES(r.Mess()), "Code", IniInfo.Code);
        BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BalanceBack", Wire.json(obj));
    }

    private void Ticket(List<String> lines) {
        PrintClass pc = new PrintClass();
        pc.DataList.addAll(lines);
        PrintTicket(pc);
    }

    private void Pdf(List<String> list, String name) {
        String[] out = {""};
        new ExportFile().ExcportPDF(list, name, out);
        DownloadFile(out[0]);
    }

    public void DepositButton_Click() {
        String btn = sender;
        try {
            if (c("ASN").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            if (!Regular(btn)) {
                throw new IllegalStateException(L("InputValidationError"));
            }
            String text = c("ASN").Text();
            BigDecimal num = dec(c("DepositAmount").Text());
            BigDecimal num2 = dec(c("DiscountAmount").Text());
            BigDecimal num3 = dec(c("Balance").Text());
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            Row info = SW.dto().make("UserCardInfo", "Asn", text, "Amount", num, "DisAmount", num2, "flag", true, "PayType", c("PayMent").SelectedIndex(), "DT",
                now(), "Code", IniInfo.Code, "EmpID", IniInfo.LoginID, "UserScore", Conv.toDecimal(c("Score").Text().isEmpty() ? "0" : c("Score").Text()));
            if (text.startsWith("010014")) {
                if (BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/MasterGroupBalanceChange", Wire.json(info)).Result()) {
                    MessageBox(L("DepositSuccess"));
                    ClearAll(btn);
                } else {
                    MessageBox(L("DepositFailed"));
                }
            } else {
                CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BalanceChange", Wire.json(info));
                if (!r.Result()) {
                    ClearAll(btn);
                    throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                }
                if (text.startsWith("010011")) {
                    MessageBox(L("DepositSuccess"));
                    ClearAll(btn);
                    return;
                }
                if (CardWrite.WriteOnLoad(text, str(num.add(num2)), TID, PassWord, list.get(7))) {
                    MessageBox(L("DepositSuccess"));
                    if (text.startsWith("010001")) {
                        Pdf(GetExportList(), L("Deposit"));
                    }
                    ClearAll(btn);
                } else {
                    MessageBox(L("CardWriteError"));
                    BalanceBack(r);
                    ClearAll(btn);
                }
            }
            BigDecimal num4 = num3.add(num).add(num2);
            Ticket(List.of(L("Deposit"), L("CardNo") + "&" + text, L("DepositAmount") + "&" + str(num), L("GiftAmount") + "&" + str(num2),
                L("Balance") + "&" + str(num4), L("DepositTime") + "&" + info.str("DT").replace(" ", ","), L("Operator") + "&" + IniInfo.LoginName));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** DeductionButton (retrait) et ConsumeButton (consommation). */
    public void DeductionButton_Click() {
        String btn = sender;
        try {
            boolean consume = btn.equals("ConsumeButton");
            String text = c(consume ? "ConsumeASN" : "DeductionASN").Text();
            if (text.isEmpty()) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            if (!Regular(btn)) {
                throw new IllegalStateException(L("InputValidationError"));
            }
            BigDecimal num = dec(c(consume ? "ConsumeAmount" : "DeductionAmount").Text());
            BigDecimal num2 = dec(c(consume ? "ConsumeBalance" : "DeductionBalance").Text());
            String item = consume ? L("CardConsume") : L("Deduction");
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            Row info = SW.dto().make("UserCardInfo", "Asn", text, "Amount", num, "flag", false, "PayType", 0, "DT", now(), "Code", IniInfo.Code, "EmpID",
                IniInfo.LoginID, "Name", btn);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/BalanceChange", Wire.json(info));
            if (!r.Result()) {
                ClearAll(btn);
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            if (text.startsWith("010014") || text.startsWith("010011")) {
                MessageBox(L("OperateSuccess"));
                ClearAll(btn);
            } else if (CardWrite.WriteOnUnload(text, str(num), TID, PassWord, list.get(6))) {
                MessageBox(L("OperateSuccess"));
                if (text.startsWith("010001")) {
                    Pdf(GetExportListSaler(btn), consume ? L("CardConsume") : L("Deduction"));
                }
                ClearAll(btn);
                Ticket(List.of(item, L("CardNo") + "&" + text, L("DeductionAmount") + "&" + str(num), L("Balance") + "&" + str(num2.subtract(num)),
                    L("ReprotTime") + "&" + info.str("DT").replace(" ", ","), L("Operator") + "&" + IniInfo.LoginName));
            } else {
                MessageBox(L("CardWriteError"));
                BalanceBack(r);
                ClearAll(btn);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ClearAll(String BtnName) {
        String[] names = switch (BtnName) {
            case "DepositButton" -> new String[] {"ASN", "UserName", "Balance", "DepositAmount", "DiscountAmount", "Score"};
            case "DeductionButton" -> new String[] {"DeductionASN", "DeductionName", "DeductionBalance"};
            case "MasterDeposter" -> new String[] {"AsnSelect", "MasterName", "MasterBalance", "MasterAmn", "MasterDisCount", "MasterScore"};
            default -> new String[0];
        };
        for (String n : names) {
            c(n).Text("");
        }
        if (BtnName.equals("MasterDeposter")) {
            c("AsnSelect").ItemsClear();
        }
    }

    /** Client du compte de la carte (CustomerCardInfos → Customers) ; NullReferenceException si absent. */
    private Row customerOf(String asn) {
        Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", asn));
        if (info == null) {
            throw new NullPointerException();
        }
        Row c = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID")));
        if (c == null) {
            throw new NullPointerException();
        }
        return c;
    }

    private List<String> GetExportListSaler(String BtnName) {
        String Asn = "";
        String item = "";
        String item2 = "";
        String item3 = "";
        String text = L("CardConsume");
        String text2 = L("AfterDeductionBalance");
        List<String> list = new ArrayList<>();
        if (BtnName.equals("DeductionButton")) {
            Asn = c("DeductionASN").Text();
            item = c("DeductionBalance").Text();
            item2 = c("DeductionAmount").Text();
            item3 = str(Conv.toDecimal(item).subtract(Conv.toDecimal(item2)));
            text = L("DeductionAmount");
        } else if (BtnName.equals("ConsumeButton")) {
            Asn = c("ConsumeASN").Text();
            item = c("ConsumeBalance").Text();
            item2 = c("ConsumeAmount").Text();
            item3 = str(Conv.toDecimal(item).subtract(Conv.toDecimal(item2)));
            text2 = L("AfterConsume");
        }
        try {
            Row customers = customerOf(Asn);
            list.addAll(List.of(L("CardNo") + ":", Asn, L("UserName") + ":", customers.str("Name"), L("OldCardBalance") + ":", item, text + ":", item2,
                text2 + ":", item3, "", "", L("PrintTime") + ":", now()));
            return list;
        } catch (RuntimeException e) {
            return list;
        }
    }

    private List<String> GetExportList() {
        try {
            Row customers = customerOf(c("ASN").Text());
            String disc = c("DiscountAmount").Text().isEmpty() ? "0" : c("DiscountAmount").Text();
            return new ArrayList<>(List.of(L("CardNo") + ":", c("ASN").Text(), L("CarNo") + ":", customers.str("VehicleNo"), L("UserName") + ":",
                customers.str("Name"), L("DepositAmount") + ":", c("DepositAmount").Text(), L("GiftAmount") + ":", c("DiscountAmount").Text(),
                L("EndBalance") + ":", str(Conv.toDecimal(c("Balance").Text()).add(Conv.toDecimal(c("DepositAmount").Text())).add(Conv.toDecimal(disc))),
                L("ServiceFee") + ":", "0", L("PayMent") + ":", c("PayMent").Text(), L("CheckNo") + ":", "", L("Operator") + ":", IniInfo.LoginName,
                L("PrintTime") + ":", now(), L("PreferentialStartEnd") + ":", "", L("Memo") + ":", "", L("Sign") + ":", "", L("Score") + ":",
                c("Score").Text().isEmpty() ? "0" : c("Score").Text()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<String> GetExportListTransfer() {
        try {
            Row customers = customerOf(c("TransferAsn").Text());
            return new ArrayList<>(List.of(L("CardNo") + ":", c("TransferAsn").Text(), L("CarNo") + ":", customers.str("VehicleNo"), L("UserName") + ":",
                customers.str("Name"), L("DepositAmount") + ":", c("TransferAmount").Text(), L("EndBalance") + ":",
                str(Conv.toDecimal(c("TransferBalance").Text()).add(Conv.toDecimal(c("TransferAmount").Text()))), L("Operator") + ":", IniInfo.LoginName,
                L("PrintTime") + ":", now(), L("PreferentialStartEnd") + ":", "", L("Memo") + ":", "", L("Sign") + ":", ""));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isDouble(String s) {
        try {
            Double.parseDouble(s.trim());
            return !s.isBlank();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean Regular(String btnname) {
        try {
            switch (btnname) {
                case "DepositButton" -> {
                    if (!isDouble(c("DepositAmount").Text())) {
                        throw new IllegalStateException(L("DepositAmountError"));
                    }
                    if (!c("DiscountAmount").Text().isEmpty() && !isDouble(c("DiscountAmount").Text())) {
                        throw new IllegalStateException(L("GiftAmountError"));
                    }
                    if (!c("Score").Text().isEmpty() && !isInt(c("Score").Text())) {
                        throw new IllegalStateException(L("ScoreError"));
                    }
                }
                case "DeductionButton" -> {
                    if (!isDouble(c("DeductionAmount").Text())) {
                        throw new IllegalStateException(L("DepositAmountError"));
                    }
                }
                case "ConsumeButton" -> {
                    if (!isDouble(c("ConsumeAmount").Text())) {
                        throw new IllegalStateException(L("DepositAmountError"));
                    }
                }
                case "STConfirm" -> {
                    if (c("STAsn").Text().isEmpty()) {
                        throw new IllegalStateException("卡号不能为空");
                    }
                    if (!isDouble(c("STBalance").Text())) {
                        throw new IllegalStateException("余额信息格式错误");
                    }
                    if (!isDouble(c("STAmount").Text())) {
                        throw new IllegalStateException("金额信息错误");
                    }
                }
                default -> {
                }
            }
            return true;
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    public void QueryButton_Click() {
        try {
            int selectedIndex = c("CardType").SelectedIndex();
            String text = c("MasterName").Text();
            String text2 = c("PhoneSearch").Text();
            String text3 = c("CNoSearch").Text();
            String text4 = c("CarNoSearch").Text();
            if (text.isEmpty() && text2.isEmpty() && text3.isEmpty() && text4.isEmpty()) {
                throw new IllegalStateException(L("QueryIsEmpty"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", selectedIndex + "," + text + "," + text2 + "," + text3 + "," + text4);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCardMessage", Wire.json(obj));
            if (r.Result()) {
                List<?> list = (List<?>) Wire.parse(BaseFunction.DecryptByAES(r.Mess()));
                c("AsnSelect").ItemsClear();
                if (!list.isEmpty()) {
                    for (Object o : list) {
                        c("AsnSelect").ItemsAdd(Conv.toStr(o));
                    }
                    c("AsnSelect").SelectedIndex(0);
                } else {
                    c("AsnSelect").Text("");
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void MasterDeposter_Click() {
        try {
            String text = c("AsnSelect").Text();
            if (text.isEmpty()) {
                throw new IllegalStateException(L("NeedCardNo"));
            }
            Row obj = SW.dto().make("UserCardInfo", "Asn", text, "Amount", Conv.toDecimal(c("MasterAmn").Text()), "DisAmount",
                Conv.toDecimal(c("MasterDisCount").Text().isEmpty() ? "0" : c("MasterDisCount").Text()), "flag", true, "PayType",
                c("PayMent").SelectedIndex(), "DT", now(), "Code", IniInfo.Code, "EmpID", IniInfo.LoginID, "UserScore",
                Conv.toDecimal(c("Score").Text().isEmpty() ? "0" : c("Score").Text()));
            boolean ok = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/MasterGroupBalanceChange", Wire.json(obj)).Result();
            MessageBox(ok ? L("DepositSuccess") : L("DepositFailed"));
            c("MasterName").Enabled(true);
            ClearAll("MasterDeposter");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void AsnSelect_SelectedIndexChanged() {
        try {
            String text = c("AsnSelect").Text();
            if (!text.isEmpty()) {
                Row info = userCard(text);
                if (info != null) {
                    c("MasterBalance").Text(Conv.toStr(info.get("Balance")));
                    c("UserNameReturn").Text(info.str("Name"));
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SaveToAsn_SelectedIndexChanged() {
        try {
            Row info = db().first("CustomerCardInfos", "{ASN} = :a", p("a", c("SaveToAsn").Text()));
            if (info != null) {
                Row customers = db().first("Customers", "{CustomerID} = :c", p("c", info.get("CustomerID")));
                if (customers != null) {
                    c("SaveToName").Text(customers.str("Name"));
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void STConfirm_Click() {
        try {
            if (c("STAsn").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            if (!Regular("STConfirm")) {
                throw new IllegalStateException(L("InputValidationError"));
            }
            if (!isDouble(c("STAmount").Text())) {
                throw new IllegalStateException(L("AmountFormatError"));
            }
            double result = Double.parseDouble(c("STAmount").Text().trim());
            int payType = 10;
            switch (c("STTypeSelect").SelectedIndex()) {
                case 0 -> {
                    double result2 = isDouble(c("STBalance").Text()) ? Double.parseDouble(c("STBalance").Text().trim()) : 0;
                    if (result > result2) {
                        throw new IllegalStateException("转储金额过大");
                    }
                }
                case 1 -> payType = 15;
                default -> {
                }
            }
            String text = c("SaveToAsn").Text();
            if (c("STTypeSelect").SelectedIndex() == 0 && text.isEmpty()) {
                throw new IllegalStateException(L("TrasnlateObjectEmpty"));
            }
            Row info = SW.dto().make("UserCardInfo", "Amount", Conv.toDecimal(result), "Asn", c("STAsn").Text(), "PayType", payType, "Balance",
                Conv.toDecimal(c("STBalance").Text()), "SToAsn", text, "EmpID", IniInfo.LoginID);
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/SaveOrTransfer", Wire.json(info));
            if (!r.Result()) {
                throw new IllegalStateException(r.Mess());
            }
            String bin = c("STAsn").Text().substring(0, 6);
            if (bin.equals("010002")) {
                if (CardWrite.WriteOnUnload(info.str("Asn"), Conv.num(result), TID, PassWord, list.get(6))) {
                    MessageBox(L("OperateSuccess"));
                }
            } else if (bin.equals("010001") && CardWrite.WriteOnLoad(info.str("Asn"), Conv.num(result), TID, PassWord, list.get(7))) {
                MessageBox(L("OperateSuccess"));
            }
            ClearBtn();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void ClearBtn() {
        for (String n : new String[] {"STAsn", "STBalance", "STAmount", "SaveToAsn", "SaveToName"}) {
            c(n).Text("");
        }
    }

    public void BalanceChange_SelectedIndexChanged() {
        if (c("BalanceChange").SelectedIndex() == 4) {
            c("CurPage").Text("1");
        }
    }

    private void LoadRepayData(int Page) {
        try {
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", Page);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCreditPay", Wire.json(obj));
            if (!r.Result()) {
                return;
            }
            g("RepayView").RowsClear();
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            c("TotalPageSecond").Text(String.valueOf(r.TotalCount()));
            for (Row x : list) {
                Row item = SW.dto().shape("CreateBillShow", x);
                String color = switch (item.i("Statue")) {
                    case 0 -> "Red";
                    case 1 -> "Purple";
                    case 2 -> "Blue";
                    default -> "Black";
                };
                int i = g("RepayView").RowsAdd(item.get("ID"), item.get("GroupName"), item.get("GroupID"), item.get("Asn"), item.get("BillDate"),
                    item.get("Amount"), item.get("RepayAmount"), BaseFunction.GetRepayStatue(item.i("Statue")));
                g("RepayView").RowForeColor(i, color);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void RepayView_CellDoubleClick() {
        try {
            int id = Conv.toInt(g("RepayView").Cell(e.i("RowIndex"), 0));
            ShowDialog("RepayFrom", r -> {
                if (r.ok()) {
                    LoadRepayData(1);
                }
            }, "ID", id);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CurPage_TextChanged() {
        try {
            LoadRepayData(Conv.toInt(c("CurPage").Text()));
            int num = Conv.toInt(c("CurPage").Text());
            int num2 = Conv.toInt(c("TotalPageSecond").Text());
            for (String b : new String[] {"Cur", "CurEnd", "Next", "NextEnd"}) {
                c(b).Enabled(true);
            }
            if (num == 1) {
                c("Cur").Enabled(false);
                c("CurEnd").Enabled(false);
            }
            if (num == num2) {
                c("Next").Enabled(false);
                c("NextEnd").Enabled(false);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void Next_Click() {
        int num = Conv.toInt(c("CurPage").Text());
        switch (sender) {
            case "Next" -> c("CurPage").Text(String.valueOf(num + 1));
            case "NextEnd" -> c("CurPage").Text(c("TotalPageSecond").Text());
            case "Cur" -> c("CurPage").Text(String.valueOf(num - 1));
            case "CurEnd" -> c("CurPage").Text("1");
            default -> {
            }
        }
    }

    public void TransferBtn_Click() {
        String btn = sender;
        try {
            if (c("TransferAsn").Text().isEmpty()) {
                throw new IllegalStateException(L("PlsReadCard"));
            }
            String text = c("TransferAsn").Text();
            BigDecimal amount = dec(c("TransferAmount").Text());
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            Row obj = SW.dto().make("UserCardInfo", "Asn", text, "Amount", amount, "DisAmount", BigDecimal.ZERO, "flag", true, "PayType", 1, "DT", now(),
                "Code", IniInfo.Code, "EmpID", IniInfo.LoginID, "UserScore", BigDecimal.ZERO);
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/OnlineDepositChange", Wire.json(obj));
            if (!r.Result()) {
                ClearAll(btn);
                throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
            }
            if (CardWrite.WriteOnLoad(text, str(amount), TID, PassWord, list.get(7))) {
                MessageBox(L("DepositSuccess"));
                if (text.startsWith("010001")) {
                    Pdf(GetExportListTransfer(), L("Deposit"));
                }
            } else {
                MessageBox(L("CardWriteError"));
                BalanceBack(r);
            }
            ClearAll(btn);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
