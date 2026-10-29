package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CardUpDate : modification des cartes (utilisateur, employé, groupe, crédit, enfant) sur la carte et sur Center. */
@SwWindow("CardUpDate")
public class CardUpDate extends CardFormBase {

    private static final String CAR = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领A-Z]{1}[A-Z]{1}[A-Z0-9]{4}[A-Z0-9挂学警港澳]{1}$";
    private static final String MAIL = "^[\\w-]+@[\\w-]+\\.(com|net|org|edu|mil|tv|biz|info)$";
    private static final String NUM = "^[0-9]*$";

    private String ID_NeedPwdBefore = "";
    private String PwdBefore = "";
    public final LegacyLog StationLog = new LegacyLog("CardUpdate");

    @Override
    protected void init() {
        PreaperLoadFirst();
        PreaperLoadOilType();
        PreaperLoadGroup();
        PreaperLoadDisCount();
        GetStation();
        c("CardTool").SelectedIndex(0);
    }

    private List<Row> grades() {
        return new ArrayList<>(db().list("GradeSpecifics"));
    }

    private void PreaperLoadFirst() {
        try {
            List<Row> list = grades();
            list.add(Row.of("Name", L("NoLimit"), "GradeSpecificID", 0));
            list.sort(Comparator.comparingInt(r -> r.i("GradeSpecificID")));
            for (String box : new String[] {"GradePrice", "EGradePrice"}) {
                for (String n : Controls(box)) {
                    if (TypeOf(n).endsWith("ComboBox")) {
                        c(n).DataSource(new ArrayList<>(list), "Name", "Code");
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void GetStation() {
        try {
            c("StationSelect").ItemsClear();
            c("EmployeeSelect").ItemsClear();
            List<Row> list = db().list("Stations");
            if (list.isEmpty()) {
                throw new IllegalStateException(L("UnvalidatedRequest"));
            }
            c("StationSelect").DataSource(list, "Name", "StationID");
            List<Row> list2 = new ArrayList<>(db().list("Employees"));
            list2.sort((a, b) -> Integer.compare(b.i("EmployeeID"), a.i("EmployeeID")));
            c("EmployeeSelect").DataSource(list2, "Name", "EmployeeID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    private void PreaperLoadOilType() {
        List<Row> list = grades();
        list.add(Row.of("Name", L("NoLimit"), "Code", ""));
        list.sort(Comparator.comparingInt(r -> r.i("GradeSpecificID")));
        for (String n : new String[] {"PersonLimitOilType", "GroupLimitOil", "CreditLimitOilType", "ChildLimitOil"}) {
            c(n).DataSource(list, "Name", "Code");
        }
    }

    private void PreaperLoadGroup() {
        String sql = "select {Group.GroupID} AS GroupID, {Group.Name} AS Name from [GroupCardInfos] inner join [Group]"
            + " on {GroupCardInfos.GroupID} = {Group.GroupID} where {GroupCardInfos.CreditLines} %s 0";
        List<Row> list = new ArrayList<>(db().query(db().sql(String.format(sql, "=")), null));
        list.add(Row.of("GroupID", 100000001, "Name", L("Person")));
        c("PersonGroupSelect").DataSource(list, "Name", "GroupID");
        list = new ArrayList<>(db().query(db().sql(String.format(sql, ">")), null));
        if (IniInfo.CreditPayAdvance) {
            list.add(Row.of("GroupID", 100000001, "Name", L("Person")));
        }
        c("CreditGroup").DataSource(list, "Name", "GroupID");
        c("MasterGroup").DataSource(db().list("Group", "{GroupID} > 1400000000 AND {GroupID} < 1500000000", null), "Name", "GroupID");
    }

    private void PreaperLoadDisCount() {
        List<Row> list = new ArrayList<>();
        list.add(Row.of("ID", 0, "Count", BigDecimal.valueOf(100), "Name", L("NoDiscount")));
        list.addAll(db().list("DisCount", "{Count} < 100 ORDER BY {ID}", null));
        list.sort((a, b) -> Conv.toDecimal(b.get("Count")).compareTo(Conv.toDecimal(a.get("Count"))));
        for (String n : new String[] {"PersonDiscount", "GroupDisCount", "CreditDisCount", "ChildDisCount"}) {
            c(n).DataSource(list, "Name", "Count");
        }
    }

    public void PersonInitButton_Click() {
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

    /** Prix spéciaux « ,CCCCPPPP… » (code carburant + prix en hexadécimal) vers les listes Grade/GPrice ou EGrade/EPrice. */
    private void DealSpecialPrice(String SP, String BtnName) {
        try {
            String g;
            String p;
            if (BtnName.equals("PersonUpdatetButton")) {
                g = "Grade";
                p = "GPrice";
            } else if (BtnName.equals("EmpReadButton")) {
                g = "EGrade";
                p = "EPrice";
            } else {
                return;
            }
            if (SP == null || SP.isEmpty()) {
                return;
            }
            String[] array = SP.split(",", -1);
            for (int i = 1; i < array.length && i <= 7; i++) {
                c(g + i).SelectedValue(array[i].substring(0, 4));
                c(p + i).Text(String.valueOf(Integer.parseInt(array[i].substring(4, 8), 16)));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private String GetSpecialPrice(String BtnName) {
        StringBuilder text = new StringBuilder();
        try {
            String g;
            String p;
            if (BtnName.equals("PersonButton")) {
                g = "Grade";
                p = "GPrice";
            } else if (BtnName.equals("EmployeeUpdateButton")) {
                g = "EGrade";
                p = "EPrice";
            } else {
                return "";
            }
            for (int i = 1; i <= 7; i++) {
                if (c(g + i).SelectedIndex() > 0 && !c(p + i).Text().isEmpty()) {
                    String code = c(g + i).SelectedRow().str("Code");
                    text.append(',').append(code).append(BaseFunction.SetStrLen(Integer.toHexString(Integer.parseInt(c(p + i).Text().trim())).toUpperCase(), 4));
                }
            }
            return text.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String typeError(String asn) {
        String text = L("CardTypeError") + ",";
        return text + switch (asn.substring(0, 6)) {
            case "010004" -> L("EmployeeCard");
            case "010001" -> L("UserCard");
            case "010002" -> L("GroupCard");
            case "010012" -> L("GroupCreditCard");
            case "010011" -> L("CreditCard");
            default -> "";
        };
    }

    private Row userMessage(Row obj, String dto) {
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetUserMessage", Wire.json(obj));
        if (!r.Result()) {
            throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
        }
        return SW.dto().shape(dto, Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
    }

    private void firstConfine(Row card, String box) {
        String uc = card.str("UseConfine");
        if (!uc.isEmpty()) {
            c(box).SelectedValue(uc.split(",", -1)[0]);
        }
    }

    /** Lecture de la carte (directement ou après PWDInput) ; remplit l'onglet du bouton btn. */
    public boolean ReLoad(String Pwd, String btn) {
        try {
            ID_NeedPwdBefore = Pwd.isEmpty() ? "0" : "1";
            PwdBefore = Pwd.isEmpty() ? "9999" : Pwd;
            CardWrite.UserPublicData[] user = {null};
            if (!CardWrite.ReadCardOnLoad(Pwd, user, new BigDecimal[1])) {
                throw new IllegalStateException(L("ReadCardFailed"));
            }
            Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", user[0].ASN);
            switch (btn) {
                case "EmpReadButton" -> {
                    Row pc = userMessage(obj, "PersonCard");
                    String bin = pc.str("Asn").substring(0, 6);
                    if (!bin.equals("010004") && !bin.equals("010006")) {
                        throw new IllegalStateException(typeError(pc.str("Asn")));
                    }
                    c("StationSelect").SelectedValue(Conv.toInt(pc.get("StationID")));
                    c("EmployeeSelect").SelectedValue(Conv.toInt(pc.get("Code")));
                    c("EmpAsn").Text(pc.str("Asn"));
                    c("EmpValidTime").Text(pc.str("ValidDatetime"));
                    c("EmpPassword").Text(pc.str("CipherFlg"));
                    c("EmpPasswordConfirm").Text(pc.str("CipherFlg"));
                    DealSpecialPrice(pc.str("SpecialPrice"), "EmpReadButton");
                    c("EmpAccount").Text(pc.str("AccountNo"));
                    c("EmpPosEnable").Checked(pc.b("PosUse"));
                }
                case "PersonUpdatetButton" -> {
                    Row pc = userMessage(obj, "PersonCard");
                    StationLog.writeLog("Card:" + Wire.json(pc));
                    if (!pc.str("Asn").startsWith("010001")) {
                        throw new IllegalStateException(typeError(pc.str("Asn")));
                    }
                    c("PersonGroupSelect").SelectedValue(Conv.toInt(pc.get("GroupID")));
                    c("PersonAsn").Text(pc.str("Asn"));
                    c("PersonValidTime").Text(pc.str("ValidDatetime"));
                    c("PersonAccount").Text(pc.str("AccountNo"));
                    c("PersonPassword").Text(pc.str("CipherFlg"));
                    c("PersonName").Text(pc.str("Name"));
                    c("PersonID").Text(pc.str("Code"));
                    Integer cert = tryInt(pc.str("CertificateTypeID"));
                    c("PersonCertificate").SelectedIndex(cert != null ? cert - 1 : 0);
                    c("PersonCertificateNo").Text(pc.str("CertificateNo"));
                    c("PersonPhone").Text(pc.str("Telephone"));
                    c("PersonCardNo").Text(pc.str("VehicleNo"));
                    c("CardNoColor").SelectedIndex(Conv.toInt(pc.get("VeicleNoColor")) - 1);
                    c("CardNoType").SelectedIndex(Conv.toInt(pc.get("VeicleNoType")) - 1);
                    c("PersonPostCode").Text(pc.str("PostCode"));
                    c("PersonAddress").Text(pc.str("Address"));
                    c("PersonEmail").Text(pc.str("Email"));
                    c("PersonBirthDay").Text(pc.str("BirthDay"));
                    c("PersonLimitCardNo").Checked(pc.b("isLimitVehicleNo"));
                    c("UserPosEnable").Checked(pc.b("PosUse"));
                    c("PersonDiscount").SelectedValue(Conv.toDecimal(pc.get("Discount")));
                    c("PersonLimitOilType").SelectedValue(pc.get("LimitOil") == null ? "" : pc.str("LimitOil"));
                    c("PersonLimitVolume").Text(pc.str("LimitVolume"));
                    c("PersonLimitDaliyTime").Text(pc.str("LimitTimeDay"));
                    c("PersonLimitAmountDaliy").Text(pc.str("LimitAmountDay"));
                    c("PersonLlimitVolumeMonth").Text(pc.str("LimitVolumeMonth"));
                    c("PersonLimitAmountMonth").Text(pc.str("LimitAmountMonth"));
                    c("PersonLimitMileage").Text(pc.str("LimitMileage"));
                    c("PersonMileageTolerance").Text(pc.str("MileageTolerance"));
                    c("LimitType").SelectedIndex(Conv.toInt(pc.get("LimitTypeID")));
                    firstConfine(pc, "PersonStation");
                    DealSpecialPrice(pc.str("SpecialPrice"), "PersonUpdatetButton");
                }
                case "GroupIniButton" -> GroupRead(userMessage(obj, "GroupCard"));
                case "CreditIniButton" -> {
                    Row pc = userMessage(obj, "PersonCard");
                    if (!pc.str("Asn").startsWith("010011")) {
                        throw new IllegalStateException(typeError(pc.str("Asn")));
                    }
                    c("CreditGroup").SelectedValue(Conv.toInt(pc.get("GroupID")));
                    c("CreditLine").Text(pc.str("CreditLine"));
                    c("BillDate").Text(pc.str("BillDate"));
                    c("CreditBalance").Text("");
                    c("RepayDate").Text(pc.str("RepayDate"));
                    c("CreditAsn").Text(pc.str("Asn"));
                    c("CreditPwd").Text(pc.str("CipherFlg"));
                    c("CreditAccount").Text(pc.str("AccountNo"));
                    c("CreditName").Text(pc.str("Name"));
                    c("CreditID").Text(pc.str("Code"));
                    c("CreditCertificate").SelectedIndex(Conv.toInt(pc.get("CertificateTypeID")) - 1);
                    c("CreditCertificateNo").Text(pc.str("CertificateNo"));
                    c("CreditPhone").Text(pc.str("Telephone"));
                    c("CreditCardNo").Text(pc.str("VehicleNo"));
                    c("CreditPostCode").Text(pc.str("PostCode"));
                    c("CreditAddress").Text(pc.str("Address"));
                    c("CreditEmail").Text(pc.str("Email"));
                    c("CreditBirthDay").Text(pc.str("BirthDay"));
                    c("CreditLimitCardNo").Checked(pc.b("isLimitVehicleNo"));
                    c("CreditPosEnable").Checked(pc.b("PosUse"));
                    c("CreditDisCount").SelectedValue(Conv.toDecimal(pc.get("Discount")));
                    c("CreditLimitOilType").SelectedValue(pc.get("LimitOil") == null ? "" : pc.str("LimitOil"));
                    c("CreditLimitVolume").Text(pc.str("LimitVolume"));
                    c("CreditLimitTime").Text(pc.str("LimitTimeDay"));
                    c("CreditLimitAmount").Text(pc.str("LimitAmountDay"));
                    c("CreditLimitVolumeMonth").Text(pc.str("LimitVolumeMonth"));
                    c("CreditLimitAmountMonth").Text(pc.str("LimitAmountMonth"));
                    c("CreditLimitMileage").Text(pc.str("LimitMileage"));
                    c("CreditMileageTolerance").Text(pc.str("MileageTolerance"));
                    c("CreditLimitType").SelectedIndex(Conv.toInt(pc.get("LimitTypeID")));
                    BigDecimal bal = Conv.toDecimal(pc.get("Bal"));
                    c("CreditBalance").Text((bal.scale() > IniInfo.Mradio ? bal.setScale(IniInfo.Mradio, java.math.RoundingMode.HALF_EVEN) : bal).toPlainString());
                    firstConfine(pc, "CreditStation");
                }
                case "ChildIniButton" -> {
                    Row pc = userMessage(obj, "PersonCard");
                    c("MasterGroup").SelectedValue(Conv.toInt(pc.get("GroupID")));
                    c("ChildAsn").Text(pc.str("Asn"));
                    c("ChildValidTime").Text(pc.str("ValidDatetime"));
                    c("ChildAccount").Text(pc.str("AccountNo"));
                    c("ChildPassword").Text(pc.str("CipherFlg"));
                    c("ChildName").Text(pc.str("Name"));
                    c("ChildId").Text(pc.str("Code"));
                    // Comme l'original : index 0 si le type est numérique, sinon Convert.ToInt32 échoue
                    c("ChildCerTypeID").SelectedIndex(tryInt(pc.str("CertificateTypeID")) == null ? Conv.toInt(pc.get("CertificateTypeID")) - 1 : 0);
                    c("ChildCerTypeNo").Text(pc.str("CertificateNo"));
                    c("ChildPhone").Text(pc.str("Telephone"));
                    c("ChildCarNo").Text(pc.str("VehicleNo"));
                    c("ChildPostCode").Text(pc.str("PostCode"));
                    c("ChildAddress").Text(pc.str("Address"));
                    c("ChildEmail").Text(pc.str("Email"));
                    c("ChildBirthDay").Text(pc.str("BirthDay"));
                    c("ChildIsLimitCarNo").Checked(pc.b("isLimitVehicleNo"));
                    c("ChilrenPosEnable").Checked(pc.b("PosUse"));
                    c("ChildDisCount").SelectedValue(Conv.toDecimal(pc.get("Discount")));
                    c("ChildLimitOil").SelectedValue(pc.get("LimitOil") == null ? "" : pc.str("LimitOil"));
                    c("ChildLimitVolume").Text(pc.str("LimitVolume"));
                    c("ChildLimitTime").Text(pc.str("LimitTimeDay"));
                    c("ChildLimitAmount").Text(pc.str("LimitAmountDay"));
                    c("ChildLimitVolumeMonth").Text(pc.str("LimitVolumeMonth"));
                    c("ChildLimitAmountMonth").Text(pc.str("LimitAmountMonth"));
                    c("ChildLimitMileager").Text(pc.str("LimitMileage"));
                    c("ChildMileageTolerance").Text(pc.str("MileageTolerance"));
                    c("ChildLimitType").SelectedIndex(Conv.toInt(pc.get("LimitTypeID")));
                    firstConfine(pc, "ChildStation");
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

    private void GroupRead(Row gc) {
        String bin = gc.str("Asn").substring(0, 6);
        if (!bin.equals("010002") && !bin.equals("010012") && !bin.equals("010014")) {
            throw new IllegalStateException(typeError(gc.str("Asn")));
        }
        switch (bin) {
            case "010002" -> {
                c("GroupTypeSelect").SelectedIndex(0);
                c("CreditBox").Visible(false);
                c("LimitMinLabel").Visible(false);
                c("LimitMinBalance").Visible(false);
            }
            case "010012" -> {
                c("GroupTypeSelect").SelectedIndex(1);
                c("CreditBox").Visible(true);
                c("GroupCreditLine").Text(Conv.toStr(gc.get("CreditLine")));
                c("GroupCreateDay").Text(gc.str("BillDate"));
                c("GroupRepayDay").Text(gc.str("RepayDay"));
                c("GroupUseAmount").Text(gc.str("UseAmount"));
                c("GroupBalance").Text(gc.str("Balance"));
                c("LimitMinLabel").Visible(false);
                c("LimitMinBalance").Visible(false);
            }
            default -> {
                c("GroupTypeSelect").SelectedIndex(2);
                c("CreditBox").Visible(false);
                c("LimitMinLabel").Visible(true);
                c("LimitMinBalance").Visible(true);
            }
        }
        c("GroupDisCount").SelectedValue(Conv.toDecimal(gc.get("Discount")));
        c("GroupAsn").Text(gc.str("Asn"));
        c("GroupValidTime").Text("");
        c("GroupAccount").Text(Conv.toStr(gc.get("GroupID")));
        c("GroupPassword").Text(gc.str("CipherFlg"));
        c("GroupName").Text(gc.str("CompanyName"));
        c("GroupID").Text(gc.str("ID"));
        c("GroupCertificateNo").Text(gc.str("CertificateNo"));
        c("GroupManage").Text(gc.str("Manager"));
        c("GroupPhone").Text(gc.str("Phone"));
        c("GroupFax").Text(gc.str("Fax"));
        c("GroupPostCode").Text(gc.str("PostCode"));
        c("GroupAddress").Text(gc.str("Address"));
        c("GroupEamil").Text(gc.str("Email"));
        c("GroupLimitOil").SelectedValue(gc.get("LimitOilType"));
        c("GroupLimitVolume").Text(gc.str("LimitVolume"));
        c("GroupLimitTime").Text(gc.str("LimitTimeDay"));
        c("GroupLimitAmountDay").Text(gc.str("LimitAmountDay"));
        c("GroupLimitVolumeMonth").Text(gc.str("LimitVolumeMonth"));
        c("GroupLimitAmountMonth").Text(gc.str("LimitAmountMonth"));
        c("GroupValidTime").Text(gc.str("ValidTime"));
        if (c("LimitMinBalance").Visible()) {
            c("LimitMinBalance").Text(Conv.toStr(gc.get("MinBalance")));
        }
    }

    private static Integer tryInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** LimitType, CreditLimitType, ChildLimitType : régions, villes ou stations autorisées. */
    public void LimitType_SelectedIndexChanged() {
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
        String[] boxes = {"PersonStation", "CreditStation", "ChildStation"};
        for (String b : boxes) {
            c(b).DataSource(null, null, null);
        }
        String path;
        String display;
        String value = "CodeInner";
        switch (c(sender).SelectedIndex()) {
            case 1 -> {
                path = "GetRegion";
                display = "RegionDesc";
            }
            case 2 -> {
                path = "GetCityList";
                display = "CityDesc";
            }
            case 3 -> {
                path = "GetStationList";
                display = "Name";
                value = "Code";
            }
            default -> {
                return;
            }
        }
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/" + path, Wire.json(obj));
        if (r.Result()) {
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            for (String b : boxes) {
                c(b).DataSource(list, display, value);
            }
        }
    }

    private static String sel(Object v) {
        return v == null ? "" : v.toString();
    }

    /** PersonButton, EmployeeUpdateButton, GroupUpdateButton, CreditButton, ChildUpdateButton. */
    public void PersonButton_Click() {
        String btn = sender;
        List<String> list = new ArrayList<>();
        CardWrite.ReadKeyFromDBNotChange(list);
        try {
            if (!Regular(btn)) {
                throw new IllegalStateException(L("FormatError"));
            }
            String specialPrice = GetSpecialPrice(btn);
            switch (btn) {
                case "PersonButton" -> {
                    String[] error = {""};
                    boolean num = CardWrite.ChangeUserCardInfos(c("PersonName").Text(), c("PersonCertificateNo").Text(),
                        String.valueOf(c("PersonCertificate").SelectedIndex() + 1), ID_NeedPwdBefore, PwdBefore, c("PersonPassword").Text(),
                        c("PersonLimitOilType").SelectedRow().str("Code"), String.valueOf(c("LimitType").SelectedIndex()), sel(c("PersonStation").SelectedValue()),
                        IsZero(c("PersonLimitVolume").Text()), IsZero(c("PersonLimitDaliyTime").Text()), IsZero(c("PersonLimitAmountDaliy").Text()),
                        c("PersonCardNo").Text(), IsZero(c("PersonLlimitVolumeMonth").Text()), IsZero(c("PersonLimitAmountMonth").Text()),
                        IsZero(c("PersonLimitMileage").Text()), IsZero(c("PersonMileageTolerance").Text()), "",
                        Conv.toStr(c("PersonDiscount").SelectedRow().get("Count")), c("PersonLimitCardNo").Checked() ? "True" : "False",
                        LocalDateTime.now().plusYears(5).format(Conv.STD), "", "", "", "", "", list.get(1), specialPrice.replace(",", ""), error);
                    StationLog.writeLog("Init Error:" + error[0]);
                    if (!num) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                case "EmployeeUpdateButton" -> {
                    Row emp = c("EmployeeSelect").SelectedRow();
                    String type = Conv.toStr(emp.get("CertifyType"));
                    if (!CardWrite.ChangeEmployeeCard(emp.str("Code"), emp.str("Name"), type.isEmpty() ? "1" : type, emp.str("CertifyKey"), ID_NeedPwdBefore,
                        PwdBefore, c("EmpPassword").Text(), list.get(1), specialPrice.replace(",", ""))) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                case "GroupUpdateButton" -> {
                    if (!CardWrite.ChangeGroupCardInfos("", "", ID_NeedPwdBefore, PwdBefore, c("GroupPassword").Text(), list.get(1))) {
                        throw new IllegalStateException(L("CardWriteError"));
                    }
                }
                default -> {
                }
            }
            if (!UpdateCardInfo(btn)) {
                throw new IllegalStateException(L("UpdateFailed"));
            }
            MessageBox(L("UpdateSuccess"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static String IsZero(String str) {
        return "0".equals(str) ? "" : str;
    }

    private static String birth(String s) {
        return Conv.toDateTime(s).format(Conv.STD);
    }

    private boolean UpdateCardInfo(String btn) {
        try {
            switch (btn) {
                case "EmployeeUpdateButton" -> {
                    String asn = c("EmpAsn").Text();
                    Row info = SW.dto().make("CenterEmployeeCardInfos", "ASN", asn, "CardStatusTypeID", 0, "CardTypeID", 1, "CipherFlg", c("EmpPassword").Text(),
                        "EmployeeID", c("EmployeeSelect").SelectedRow().i("EmployeeID"), "IssueDatetime", LocalDateTime.now(), "ValidDatetime",
                        LocalDateTime.now().plusYears(5), "StationID", IniInfo.StationID, "SpecialPrice", GetSpecialPrice(btn), "PosCanUse",
                        c("EmpPosEnable").Checked());
                    String prefix = switch (asn.substring(4, 6)) {
                        case "04" -> "20";
                        case "11" -> "21";
                        case "12" -> "22";
                        case "13" -> "23";
                        default -> null;
                    };
                    if (prefix != null) {
                        info.put("CustomerAccountID", Integer.parseInt(prefix + asn.substring(12, 20)));
                    }
                    return BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateEmployeeCardInfo", Wire.json(info)).Result();
                }
                case "PersonButton" -> {
                    Object group = c("PersonGroupSelect").SelectedValue();
                    Row obj2 = SW.dto().make("PersonCard", "Asn", c("PersonAsn").Text(), "AccountNo", c("PersonAccount").Text(), "Address",
                        c("PersonAddress").Text(), "BirthDay", birth(c("PersonBirthDay").Text()), "CertificateTypeID",
                        String.valueOf(c("PersonCertificate").SelectedIndex() + 1), "CertificateNo", c("PersonCertificateNo").Text(), "CipherFlg",
                        c("PersonPassword").Text(), "Discount", Conv.toStr(c("PersonDiscount").SelectedRow().get("Count")), "Email", c("PersonEmail").Text(),
                        "VeicleNoColor", String.valueOf(c("CardNoColor").SelectedIndex()), "VeicleNoType", String.valueOf(c("CardNoType").SelectedIndex()),
                        "GroupID", group == null ? "100000001" : group.toString(), "LimitAmountDay", c("PersonLimitAmountDaliy").Text(), "LimitAmountMonth",
                        c("PersonLimitAmountMonth").Text(), "LimitOil", c("PersonLimitOilType").SelectedRow().str("Code"), "LimitTimeDay",
                        c("PersonLimitDaliyTime").Text(), "LimitTypeID", String.valueOf(c("LimitType").SelectedIndex()), "LimitVehicleNo",
                        c("PersonCardNo").Text(), "LimitVolume", c("PersonLimitVolume").Text(), "LimitVolumeMonth", c("PersonLlimitVolumeMonth").Text(),
                        "isLimitVehicleNo", c("PersonLimitCardNo").Checked(), "LimitMileage", c("PersonLimitMileage").Text(), "MileageTolerance",
                        c("PersonMileageTolerance").Text(), "Name", c("PersonName").Text(), "PostCode", c("PersonPostCode").Text(), "Telephone",
                        c("PersonPhone").Text(), "UseConfine", sel(c("PersonStation").SelectedValue()), "VehicleNo", c("PersonCardNo").Text(), "StationID",
                        String.valueOf(IniInfo.StationID), "Code", c("PersonID").Text(), "EmpID", IniInfo.LoginID, "SpecialPrice", GetSpecialPrice(btn),
                        "PosUse", c("UserPosEnable").Checked());
                    return BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateCustomerCardInfo", Wire.json(obj2)).Result();
                }
                case "GroupUpdateButton" -> {
                    Row gc = SW.dto().make("GroupCard", "Address", c("GroupAddress").Text(), "Asn", c("GroupAsn").Text(), "CertificateNo",
                        c("GroupCertificateNo").Text(), "CipherFlg", c("GroupPassword").Text(), "CompanyName", c("GroupName").Text(), "Email",
                        c("GroupEamil").Text(), "Fax", c("GroupFax").Text(), "GroupID", Conv.toInt(c("GroupAccount").Text()), "ID", c("GroupID").Text(),
                        "LimitAmountDay", c("GroupLimitAmountDay").Text(), "LimitAmountMonth", c("GroupLimitAmountMonth").Text(), "LimitTimeDay",
                        c("GroupLimitTime").Text(), "LimitVolume", c("GroupLimitVolume").Text(), "LimitVolumeMonth", c("GroupLimitVolumeMonth").Text(),
                        "Manager", c("GroupManage").Text(), "Phone", c("GroupPhone").Text(), "PostCode", c("GroupPostCode").Text(), "StationCode", IniInfo.Code,
                        "LimitOilType", c("GroupLimitOil").SelectedRow().str("Code"), "Discount", Conv.toStr(c("GroupDisCount").SelectedRow().get("Count")),
                        "EmpID", IniInfo.LoginID);
                    switch (c("GroupTypeSelect").SelectedIndex()) {
                        case 0 -> {
                            gc.put("isCreditCard", false);
                            gc.put("CreditLine", BigDecimal.ZERO);
                            gc.put("BillDate", "");
                            gc.put("RepayDay", "");
                        }
                        case 1 -> {
                            gc.put("isCreditCard", true);
                            gc.put("CreditLine", Conv.toDecimal(c("GroupCreditLine").Text()));
                            gc.put("BillDate", c("GroupCreateDay").Text());
                            gc.put("RepayDay", c("GroupRepayDay").Text());
                        }
                        case 2 -> gc.put("MinBalance", Conv.toDecimal(c("LimitMinBalance").Text()));
                        default -> {
                        }
                    }
                    CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateGroupCaridInfo", Wire.json(gc));
                    if (!r.Result()) {
                        throw new IllegalStateException(BaseFunction.DecryptByAES(r.Mess()));
                    }
                    return true;
                }
                case "CreditButton" -> {
                    // Comme l'original : la remise est lue dans PersonDiscount
                    Row obj3 = SW.dto().make("PersonCard", "Asn", c("CreditAsn").Text(), "AccountNo", c("CreditAccount").Text(), "Address",
                        c("CreditAddress").Text(), "BirthDay", birth(c("CreditBirthDay").Text()), "CertificateTypeID",
                        String.valueOf(c("CreditCertificate").SelectedIndex() + 1), "CertificateNo", c("CreditCertificateNo").Text(), "CipherFlg",
                        c("CreditPwd").Text(), "Discount", Conv.toStr(c("PersonDiscount").SelectedRow().get("Count")), "Email", c("CreditEmail").Text(),
                        "GroupID", c("CreditGroup").SelectedValue().toString(), "LimitAmountDay", c("CreditLimitAmount").Text(), "LimitAmountMonth",
                        c("CreditLimitAmountMonth").Text(), "LimitOil", c("CreditLimitOilType").SelectedRow().str("Code"), "LimitTimeDay",
                        c("CreditLimitTime").Text(), "LimitTypeID", String.valueOf(c("CreditLimitType").SelectedIndex()), "LimitVehicleNo",
                        c("CreditCardNo").Text(), "LimitVolume", c("CreditLimitVolume").Text(), "LimitVolumeMonth", c("CreditLimitVolumeMonth").Text(),
                        "isLimitVehicleNo", c("CreditLimitCardNo").Checked(), "LimitMileage", c("CreditLimitMileage").Text(), "MileageTolerance",
                        c("CreditMileageTolerance").Text(), "Name", c("CreditName").Text(), "PostCode", c("CreditPostCode").Text(), "Telephone",
                        c("CreditPhone").Text(), "UseConfine", sel(c("CreditStation").SelectedValue()), "VehicleNo", c("CreditCardNo").Text(), "StationID",
                        String.valueOf(IniInfo.StationID), "Code", c("CreditID").Text(), "CreditLine", c("CreditLine").Text(), "BillDate", c("BillDate").Text(),
                        "RepayDate", c("RepayDate").Text(), "EmpID", IniInfo.LoginID, "PosUse", c("CreditPosEnable").Checked());
                    CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateCreditCardInfo", Wire.json(obj3));
                    if (!r.Result()) {
                        MessageBox(BaseFunction.DecryptByAES(r.Mess()));
                    }
                    return r.Result();
                }
                case "ChildUpdateButton" -> {
                    Row obj = SW.dto().make("PersonCard", "Asn", c("ChildAsn").Text(), "AccountNo", c("ChildAccount").Text(), "Address",
                        c("ChildAddress").Text(), "BirthDay", birth(c("ChildBirthDay").Text()), "CertificateTypeID",
                        String.valueOf(c("ChildCerTypeID").SelectedIndex() + 1), "CertificateNo", c("ChildCerTypeNo").Text(), "CipherFlg",
                        c("ChildPassword").Text(), "Discount", Conv.toStr(c("ChildDisCount").SelectedRow().get("Count")), "GroupID",
                        c("MasterGroup").SelectedValue().toString(), "LimitAmountDay", c("ChildLimitAmount").Text(), "LimitAmountMonth",
                        c("ChildLimitAmountMonth").Text(), "LimitOil", c("ChildLimitOil").SelectedRow().str("Code"), "LimitTimeDay", c("ChildLimitTime").Text(),
                        "LimitTypeID", String.valueOf(c("ChildLimitType").SelectedIndex()), "LimitVehicleNo", c("ChildCarNo").Text(), "LimitVolume",
                        c("ChildLimitVolume").Text(), "LimitVolumeMonth", c("ChildLimitVolumeMonth").Text(), "isLimitVehicleNo", c("ChildIsLimitCarNo").Checked(),
                        "LimitMileage", c("ChildLimitMileager").Text(), "MileageTolerance", c("ChildMileageTolerance").Text(), "Name", c("ChildName").Text(),
                        "PostCode", c("ChildPostCode").Text(), "Telephone", c("ChildPhone").Text(), "UseConfine", sel(c("ChildStation").SelectedValue()),
                        "VehicleNo", c("ChildCarNo").Text(), "StationID", String.valueOf(IniInfo.StationID), "Code", c("ChildId").Text(), "EmpID",
                        IniInfo.LoginID, "PosUse", c("ChilrenPosEnable").Checked());
                    return BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/UpdateCustomerCardInfo", Wire.json(obj)).Result();
                }
                default -> {
                    return false;
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            return false;
        }
    }

    private boolean Regular(String btn) {
        try {
            int vf = IniInfo.VFormat;
            int mf = IniInfo.MFormat;
            switch (btn) {
                case "PersonButton" -> {
                    need(!c("PersonAsn").Text().isEmpty(), L("NeedCardNo"));
                    need(!c("PersonValidTime").Text().isEmpty(), L("NeedCardValidTime"));
                    need(!c("PersonAccount").Text().isEmpty(), L("NeedUserAccount"));
                    password("PersonPassword");
                    need(!c("PersonName").Text().isEmpty(), L("UserNameEmpty"));
                    need(!c("PersonID").Text().isEmpty(), L("UserIDEmpty"));
                    need(!c("PersonCertificateNo").Text().isEmpty(), L("IdentificationEmpty"));
                    need(c("PersonPhone").Text().isEmpty() || valid(c("PersonPhone").Text(), PHONE), L("PhoneError"));
                    carNo("PersonCardNo");
                    need(c("PersonPostCode").Text().isEmpty() || valid(c("PersonPostCode").Text(), POST), L("PostCodeFormatError"));
                    need(c("PersonEmail").Text().isEmpty() || valid(c("PersonEmail").Text(), MAIL), L("EamilFormatError"));
                    limitDaily("PersonLimitVolume", "LimitVolumeError", vf, 65535.0, "LimitVolumeExceeded");
                    numeric("PersonLimitDaliyTime", "LimitDayTimeError");
                    limitDaily("PersonLimitAmountDaliy", "LimitAmountDailyError", mf, 4294967295.0, "LimitAmountDailyExceeded");
                    limitMonthly("PersonLlimitVolumeMonth", "LimitVolumeMonthError", vf, 65535.0, "LimitVolumeMonthExceeded",
                        "\n" + L("OutOfStationDigits") + "\n" + L("NumberOrDecimal"));
                    limitMonthly("PersonLimitAmountMonth", "LimitAmountMonthError", mf, 4294967295.0, "LimitAmountMonthExceeded", digits());
                    numeric("PersonLimitMileage", "FuelConsumptionError");
                    numeric("PersonMileageTolerance", "MileageNumber");
                }
                case "EmployeeConfirm" -> {
                    need(!c("EmpAsn").Text().isEmpty(), L("NeedCardNo"));
                    need(!c("EmpValidTime").Text().isEmpty(), L("NeedCardValidTime"));
                    String t = c("EmpPassword").Text();
                    if (!t.isEmpty()) {
                        need(t.length() == 4, L("NeedPasswordMaxLength"));
                        need(c("EmpPasswordConfirm").Text().equals(t), L("PasswordInconformity"));
                        need(valid(t, NUM), L("PasswordIsNumber"));
                    }
                    need(!c("EmployeeSelect").Text().isEmpty(), L("SelectEmployee"));
                }
                case "GroupButton" -> {
                    need(!c("GroupAsn").Text().isEmpty(), L("NeedCardNo"));
                    need(!c("GroupValidTime").Text().isEmpty(), L("NeedCardValidTime"));
                    need(!c("GroupAccount").Text().isEmpty(), L("NeedUserAccount"));
                    password("GroupPassword");
                    need(!c("GroupName").Text().isEmpty(), L("GroupNameEmpty"));
                    need(!c("GroupID").Text().isEmpty(), L("GroupIDEmpty"));
                    need(!c("GroupCertificateNo").Text().isEmpty(), L("BusinessNoEmpty"));
                    match("GroupPhone", PHONE, L("PhoneError"));
                    match("GroupPostCode", POST, L("PostCodeFormatError"));
                    match("GroupEamil", MAIL, L("EamilFormatError"));
                    limitDaily("GroupLimitVolume", "LimitVolumeError", vf, 65535.0, "LimitVolumeExceeded");
                    numeric("GroupLimitTime", "LimitDayTimeError");
                    limitDaily("GroupLimitAmountDay", "LimitAmountDailyError", mf, 4294967295.0, "LimitAmountDailyExceeded");
                    limitMonthly("GroupLimitVolumeMonth", "LimitVolumeMonthError", vf, 65535.0, "LimitVolumeMonthExceeded", digits());
                    limitMonthly("GroupLimitAmountMonth", "LimitAmountMonthError", mf, 4294967295.0, "LimitAmountMonthExceeded",
                        "\nLe nombre de décimales dépasse celui des montants de la station\nVérifiez le nombre saisi ou la configuration de la station");
                    if (c("CreditBox").Visible()) {
                        need(!c("GroupCreditLine").Text().isEmpty(), L("CreditLineEmpty"));
                        need(isDouble(c("GroupCreditLine").Text()), L("NumberOrDecimal"));
                        days("GroupCreateDay", "BillCreateDayEmpty");
                        days("GroupRepayDay", "RepayDayEmpty");
                    }
                }
                case "CreditButton" -> {
                    need(!c("CreditLine").Text().isEmpty(), L("CreditLineEmpty"));
                    need(isDouble(c("CreditLine").Text()), L("NumberOrDecimal"));
                    need(!c("CreditAsn").Text().isEmpty(), L("NeedCardNo"));
                    password("CreditPwd");
                    need(!c("CreditAccount").Text().isEmpty(), L("NeedUserAccount"));
                    need(!c("CreditName").Text().isEmpty(), L("UserNameEmpty"));
                    need(!c("CreditID").Text().isEmpty(), L("UserIDEmpty"));
                    need(!c("CreditCertificateNo").Text().isEmpty(), "Le n° de pièce est obligatoire");
                    need(c("CreditPhone").Text().isEmpty() || !"zh-CN".equals(IniInfo.Language) || valid(c("CreditPhone").Text(), PHONE), L("PhoneError"));
                    carNo("CreditCardNo");
                    need(c("CreditPostCode").Text().isEmpty() || valid(c("CreditPostCode").Text(), POST), L("PostCodeFormatError"));
                    need(c("CreditEmail").Text().isEmpty() || valid(c("CreditEmail").Text(), MAIL), L("EamilFormatError"));
                    limitDaily("CreditLimitVolume", "LimitVolumeError", vf, 65535.0, "LimitVolumeExceeded");
                    numeric("CreditLimitTime", "LimitDayTimeError");
                    limitDaily("CreditLimitAmount", "LimitAmountDailyError", mf, 4294967295.0, "LimitAmountDailyExceeded");
                    limitMonthly("CreditLimitVolumeMonth", "LimitVolumeMonthError", vf, 65535.0, "LimitVolumeMonthExceeded", digits());
                    limitMonthly("CreditLimitAmountMonth", "LimitAmountMonthError", mf, 4294967295.0, "LimitAmountMonthExceeded", digits());
                    numeric("CreditLimitMileage", "FuelConsumptionError");
                    numeric("CreditMileageTolerance", "MileageNumber");
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
}
