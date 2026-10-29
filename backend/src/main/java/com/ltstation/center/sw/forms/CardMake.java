package com.ltstation.center.sw.forms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardIssue;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.Operate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CardMake : émission des cartes (utilisateur, employé, groupe, crédit, enfant). La carte est d'abord créée
 * sur Center (CreateCardPre), puis personnalisée sur le lecteur ; en cas d'échec Center l'annule (CardRollDelete).
 * L'original exécute l'émission dans un thread derrière ProBar ; ici la requête attend la fin.
 */
@SwWindow("CardMake")
public class CardMake extends CardFormBase {

    public boolean IsCreate = true;
    private final Operate operate = new Operate();
    private Row CMessage;
    private int Ini_CardSelect;

    private static final String[] LIMIT_FIELDS = {"PersonLimitVolume", "PersonLimitDaliyTime", "PersonLimitAmountDaliy", "PersonLlimitVolumeMonth",
        "PersonLimitAmountMonth", "PersonLimitMileage", "PersonMileageTolerance"};

    @Override
    protected void init() {
        IsCreate = !"false".equalsIgnoreCase(argStr("IsCreate"));
    }

    public void CardMake_Load() {
        try {
            c("EmpCardType").SelectedIndex(0);
            if (!GetReader()) {
                throw new IllegalStateException(L("GetReaderFailed"));
            }
            GetStation();
            c("CardSelect").SelectedIndex(1);
            c("CardTool").SelectedIndex(0);
            c("CardNoColor").SelectedIndex(0);
            c("CardNoType").SelectedIndex(0);
            c("ChilrenCardCerTypeSelect").SelectedIndex(0);
            PreaperLoadDisCount();
            PreaperLoadOilType();
            PreaperLoadGroup();
            c("PersonDiscount").SelectedIndex(0);
            c("PersonLimitOilType").SelectedIndex(0);
            c("LimitType").SelectedIndex(0);
            for (String n : LIMIT_FIELDS) {
                On(n, "TextChanged", "SkinTxt_TextChanged");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    /**
     * Saisie numérique des limites. L'original contrôle aussi la longueur selon TabIndex 38 à 44, mais ces
     * champs ont les TabIndex 64 à 70 : seul le contrôle « chiffres uniquement » s'applique.
     */
    public void SkinTxt_TextChanged() {
        String text = c(sender).Text();
        if (text.isEmpty()) {
            return;
        }
        if (!valid(text, NUM)) {
            MessageBox(L("FormatError"));
            c(sender).Text(text.substring(0, text.length() - 1));
        }
    }

    private List<Row> gradesWithNoLimit() {
        List<Row> list = new ArrayList<>(db().list("GradeSpecifics"));
        list.add(Row.of("Name", L("NoLimit"), "GradeSpecificID", 0));
        list.sort(Comparator.comparingInt(r -> r.i("GradeSpecificID")));
        return list;
    }

    private void PreaperLoadOilType() {
        List<Row> list = gradesWithNoLimit();
        for (String n : new String[] {"PersonLimitOilType", "GroupLimitOil", "CreditLimitOilType", "ChilrenLimitOilType"}) {
            c(n).DataSource(list, "Name", "GradeSpecificID");
        }
        for (String box : new String[] {"GradePrice", "EGradePrice"}) {
            for (String n : Controls(box)) {
                if (TypeOf(n).endsWith("ComboBox")) {
                    c(n).DataSource(new ArrayList<>(list), "Name", "Code");
                }
            }
        }
    }

    private void PreaperLoadGroup() {
        c("PersonGroupSelect").DataSource(db().list("Group", "{GroupID} = 100000001 OR ({GroupID} > 1900000000 AND {GroupID} < 2000000000)", null), "Name",
            "GroupID");
        c("CreditGroup").DataSource(db().query(db().sql("select {Group.GroupID} AS GroupID, {Group.Name} AS Name from [GroupCardInfos] inner join [Group]"
            + " on {GroupCardInfos.GroupID} = {Group.GroupID} where {GroupCardInfos.CreditLines} > 0"), null), "Name", "GroupID");
        c("MasterGroupSelect").DataSource(db().list("Group", "{GroupID} > 1400000000 AND {GroupID} < 1500000000", null), "Name", "GroupID");
    }

    private void PreaperLoadDisCount() {
        List<Row> list = new ArrayList<>();
        list.add(Row.of("ID", 0, "Count", BigDecimal.valueOf(100), "Name", L("NoDiscount")));
        list.addAll(db().list("DisCount", "ORDER BY {ID}", null));
        list.sort((a, b) -> Conv.toDecimal(b.get("Count")).compareTo(Conv.toDecimal(a.get("Count"))));
        for (String n : new String[] {"PersonDiscount", "GroupDisCount", "CreditDisCount", "ChilrenDisCount"}) {
            c(n).DataSource(list, "Name", "Count");
        }
    }

    private boolean GetReader() {
        c("Reader").ItemsClear();
        String[] readers = operate.GetReaders();
        if (readers != null) {
            for (String r : readers) {
                c("Reader").ItemsAdd(r);
            }
            if (readers.length < 2) {
                throw new IllegalStateException("InvalidArgument=Value of '1' is not valid for 'SelectedIndex'.");
            }
            c("Reader").SelectedIndex(1);
            return true;
        }
        MessageBox(L("CardReaderDisconnect"));
        return false;
    }

    private List<Row> employees() {
        List<Row> list = new ArrayList<>(db().list("Employees"));
        list.sort((a, b) -> Integer.compare(b.i("EmployeeID"), a.i("EmployeeID")));
        return list;
    }

    private void GetStation() {
        try {
            c("StationSelect").ItemsClear();
            c("EmployeeSelect").ItemsClear();
            List<Row> list = db().list("Stations");
            if (list.isEmpty()) {
                throw new IllegalStateException(L("UnvalidatedInput"));
            }
            c("StationSelect").DataSource(list, "Name", "Code");
            c("EmployeeSelect").DataSource(employees(), "Name", "EmployeeID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
            Close();
        }
    }

    /** LimitType, CreditLimitType, ChilrenLimitTypeSelect : régions, villes ou stations proposées. */
    public void EmployeeSelect_SelectedIndexChanged() {
        if (!sender.equals("LimitType") && !sender.equals("CreditLimitType") && !sender.equals("ChilrenLimitTypeSelect")) {
            return;
        }
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
        String[] boxes = {"PersonStation", "CreditLimitStations", "ChildLimitStation"};
        for (String b : boxes) {
            c(b).ItemsClear();
            c(b).Text("");
        }
        String path;
        String field;
        switch (c(sender).SelectedIndex()) {
            case 1 -> {
                path = "GetRegion";
                field = "RegionDesc";
            }
            case 2 -> {
                path = "GetCityList";
                field = "CityDesc";
            }
            case 3 -> {
                path = "GetStationList";
                field = "Name";
            }
            default -> {
                return;
            }
        }
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/" + path, Wire.json(obj));
        if (r.Result()) {
            for (Row item : Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()))) {
                for (String b : boxes) {
                    c(b).ItemsAdd(item.str(field));
                }
            }
        }
    }

    public void EmployeeSelect_DropDown() {
        switch (sender) {
            case "EmployeeSelect" -> c("EmployeeSelect").DataSource(employees(), "Name", "EmployeeID");
            case "PersonLimitOilType" -> PreaperLoadOilType();
            case "PersonGroupSelect" -> PreaperLoadGroup();
            case "PersonDiscount" -> PreaperLoadDisCount();
            default -> {
            }
        }
    }

    private static int toInt(Object v) {
        return v == null || "".equals(v) ? 0 : Conv.toInt(v);
    }

    private static BigDecimal toDec(Object v) {
        return v == null || "".equals(v) ? BigDecimal.ZERO : Conv.toDecimal(v);
    }

    /** CardMakeLoad : création sur Center puis personnalisation de la carte ; show affiche ASN / validité / compte. */
    private void CardMakeLoad(java.util.function.Consumer<List<String>> show) {
        Row m = CMessage;
        try {
            Row obj2 = SW.dto().make("PreCardMessage", "CardType", m.get("CardType"), "SpecialPrice", m.get("SpecialPrice"), "Name", m.get("Name"),
                "Address", m.get("Address"), "BillDate", m.get("BillDate"), "PayDate", toInt(m.get("RepayDay")), "BirthDay", m.get("BirthDay"),
                "LimitAmount", toDec(m.get("LimitAmountDay")),
                // Comme l'original : LimitAmountMonth reçoit la limite de volume mensuelle
                "LimitAmountMonth", toDec(m.get("LimitVolumeMonth")), "LimitVolume", toDec(m.get("LimitVolume")), "LimitVolumeMonth",
                toDec(m.get("LimitVolumeMonth")), "isCreditCard", m.b("isCreditCard"), "IsLimitCarNo", m.b("isLimitVehicleNo"), "IssueTime",
                LocalDateTime.now().format(Conv.STD), "Discount", toDec(m.get("Discount")), "CNO", m.get("CertificateNo"), "CarNumber", m.get("VehicleNo"),
                "CNOType", toInt(m.get("CertificateTypeID")), "Email", m.get("Email"), "LimitOil", m.get("LimitOil"), "LimitMileage",
                toDec(m.get("LimitMileage")), "Fax", m.get("Fax"), "LimitMinBalance", m.get("LimitMinBalance"), "LimitTime", toInt(m.get("LimitTimeDay")),
                "LimitType", toInt(m.get("LimitTypeID")), "Pass", m.get("Pass"), "CreditLine", toDec(m.get("CreditLine")), "CompanyName",
                m.get("CompanyName"), "PostCode", m.get("PostCode"), "MileageTolerance", toDec(m.get("MileageTolerance")), "Phone", m.get("Telephone"),
                "LimitStation", m.get("UseConfine"), "CarColorType", toInt(m.get("VehicleNoColor")), "CarType", toInt(m.get("VehicleNoType")), "Manager",
                m.get("Manager"), "RepayDay", m.get("RepayDay"), "ID", m.get("ID"), "GroupBelongID", toInt(m.get("GroupID")), "CardGroupType",
                m.get("CardGroupType"), "OpenStationID", IniInfo.StationID, "OpenEmpID", IniInfo.LoginID, "GroupName", m.get("CompanyName"), "EmployeeID",
                m.get("EmployeeID"), "PosUse", m.b("PosUse"));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CreateCardPre", Wire.json(obj2));
            if (!r.Result()) {
                return;
            }
            Row cardMessage = SW.dto().shape("CardMessage", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
            m.put("ID", cardMessage.get("GroupID"));
            m.put("ASN", cardMessage.get("ASN"));
            m.put("CusID", cardMessage.get("CusID"));
            m.put("AccountNo", cardMessage.get("AccountNo"));
            cardMessage.put("ValidTime", LocalDateTime.now().plusYears(5).format(Conv.STD));
            try {
                show.accept(List.of(m.str("ASN"), cardMessage.str("ValidTime"), Conv.toStr(m.get("CusID"))));
                String aSN = cardMessage.str("ASN");
                if (aSN.isEmpty()) {
                    throw new IllegalStateException(L("PlSSelectCardType"));
                }
                if (!operate.SCardConnect(CardWrite.READER)) {
                    throw new IllegalStateException(L("CardReaderDisconnect"));
                }
                String sPublicInfo = PrepareBuildInfo(cardMessage);
                if (sPublicInfo == null) {
                    throw new IllegalStateException(L("CardPublishMessageFailed"));
                }
                List<String> list = new ArrayList<>();
                if (!CardWrite.ReadKeysFromDB(list)) {
                    throw new IllegalStateException(L("GetSecretError"));
                }
                for (int i = 0; i < 20; i++) {
                    String master = i == 6 || i == 11 ? BaseFunction.fnGetDeriveKey(list.get(i), BaseFunction.CON_BIN) : list.get(i);
                    list.set(i, BaseFunction.fnGetDeriveKey(master, aSN));
                }
                String[] keys = list.toArray(new String[0]);
                String btn = m.str("Btn");
                boolean ok = switch (m.str("CardTypeName")) {
                    case "0" -> personalize(btn, () -> CardIssue.WriteDataToACS(operate, sPublicInfo, keys));
                    case "1" -> personalize(btn, () -> CardIssue.WriteDataFlying(operate, sPublicInfo, keys));
                    case "2" -> personalize(btn, () -> CardIssue.WriteDataToDHCard(operate, IniInfo.DHTransferKey, sPublicInfo, keys));
                    default -> true;
                };
                if (!ok) {
                    throw new IllegalStateException(L("CardWriteError"));
                }
                operate.SCardDisconnect();
                MessageBox(L("CardMakeSuccess"));
            } catch (RuntimeException ex) {
                operate.SCardDisconnect();
                BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/CardRollDelete", Wire.json(cardMessage));
                MessageBox(L("CardMakeError") + "-" + ex.getMessage());
            }
        } catch (RuntimeException ex2) {
            operate.SCardDisconnect();
            MessageBox(L("CardMakeError") + "-" + ex2.getMessage());
        }
    }

    /** WriteDataToACS / WriteDataFlying / WriteDataToDHCard puis WriteCard(btn). */
    private boolean personalize(String btn, Runnable apdus) {
        try {
            apdus.run();
            return WriteCard(btn);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean WriteCard(String btn) {
        try {
            List<String> list = new ArrayList<>();
            CardWrite.ReadKeyFromDBNotChange(list);
            Row m = CMessage;
            boolean flag = false;
            String sPer = m.str("SpecialPrice").replace(",", "");
            if (btn.equals("EmployeeConfirm")) {
                need(c("EmpPassword").Text().equals(c("EmpPasswordConfirm").Text()), L("PasswordInconformity"));
                Row emp = db().first("Employees", "{EmployeeID} = :e", p("e", m.get("EmployeeID")));
                if (emp == null) {
                    throw new NullPointerException();
                }
                flag = CardWrite.WriteOnEmployeeCard(operate, emp.str("Code"), emp.str("Name"), String.valueOf(toInt(emp.get("CertifyType"))),
                    emp.str("CertifyKey"), m.str("Pass"), list.get(1), sPer);
                need(flag, L("CardWriteError"));
            }
            if (btn.equals("PersonButton")) {
                flag = CardWrite.WriteOnNewPersonalCard(operate, m.str("Name"), m.str("CertificateNo"), m.str("CertificateTypeID"), m.str("Pass"),
                    m.str("LimitOil"), m.str("LimitTypeID"), m.str("UseConfine"), m.str("LimitVolume"), m.str("LimitTimeDay"), m.str("LimitAmountDay"),
                    m.str("LimitVehicleNo"), m.str("LimitVolumeMonth"), m.str("LimitAmountMonth"), m.str("LimitMileage"), m.str("MileageTolerance"), "",
                    m.str("Discount"), m.b("isLimitVehicleNo") ? "True" : "False", LocalDateTime.now().plusYears(5).format(Conv.STD), "", "", "", "", "",
                    list.get(1), sPer);
                need(flag, L("CardWriteError"));
            }
            if (btn.equals("GroupButton")) {
                flag = CardWrite.WriteGroupCard(operate, m.str("CompanyName"), m.str("ID"), m.str("Pass"), list.get(1));
                need(flag, L("CardWriteError"));
            }
            if (btn.equals("CreditButton") || btn.equals("ChilrenConfirm")) {
                flag = true;
            }
            return flag;
        } catch (RuntimeException e) {
            return false;
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
            } else if (BtnName.equals("EmployeeConfirm")) {
                g = "EGrade";
                p = "EPrice";
            } else {
                return "";
            }
            for (int i = 1; i <= 7; i++) {
                if (c(g + i).SelectedIndex() > 0 && !c(p + i).Text().isEmpty()) {
                    text.append(',').append(c(g + i).SelectedRow().str("Code"))
                        .append(BaseFunction.SetStrLen(Integer.toHexString(Integer.parseInt(c(p + i).Text().trim())).toUpperCase(), 4));
                }
            }
            return text.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String colour(int i) {
        return switch (i) {
            case 5 -> "A";
            case 6 -> "F";
            default -> String.valueOf(i + 1);
        };
    }

    private static String z(String s) {
        return s.isEmpty() ? "0" : s;
    }

    private String birth(String box) {
        return Conv.toDateTime(c(box).Text()).format(Conv.STD);
    }

    private String code(String box) {
        Object v = c(box).SelectedRow().get("Code");
        return v == null ? "" : v.toString();
    }

    /** EmployeeConfirm, PersonButton, GroupButton, CreditButton, ChilrenConfirm. */
    public void EmployeeConfirm_Click() {
        String btn = sender;
        try {
            if (!Regular(btn)) {
                throw new IllegalStateException(L("FormatError"));
            }
            Row m = SW.dto().make("CardMessage", "Btn", btn, "ValidTime", LocalDateTime.now().plusYears(5).format(Conv.STD), "CardTypeName",
                String.valueOf(c("CardSelect").SelectedIndex()), "Page", c("CardTool").SelectedIndex());
            CMessage = m;
            if (btn.equals("CreditButton") && !IniInfo.CreditPayAdvance) {
                need(Conv.toDecimal(c("CreditLine").Text()).compareTo(Conv.toDecimal(c("CreditBalance").Text())) <= 0, L("OutOfCreditLine"));
            }
            switch (btn) {
                case "EmployeeConfirm" -> {
                    Row emp = c("EmployeeSelect").SelectedRow();
                    m.put("ASN", c("EmpAsn").Text());
                    m.put("AccountNo", c("EmpAccount").Text());
                    m.put("Pass", c("EmpPassword").Text());
                    m.put("EmployeeID", emp.i("EmployeeID"));
                    m.put("Name", emp.str("Name"));
                    m.put("PosUse", c("EmpPosEnable").Checked());
                    m.put("CardType", c("EmpCardType").SelectedIndex() == 0 ? "010004" : "010006");
                    m.put("CreditLine", "0");
                    m.put("BillDate", "0");
                }
                case "PersonButton" -> {
                    m.put("ASN", c("PersonAsn").Text());
                    m.put("AccountNo", c("PersonAccount").Text());
                    m.put("Address", c("PersonAddress").Text());
                    m.put("BirthDay", birth("PersonBirthDay"));
                    m.put("CertificateTypeID", String.valueOf(c("PersonCertificate").SelectedIndex() + 1));
                    m.put("CertificateNo", c("PersonCertificateNo").Text());
                    m.put("Pass", c("PersonPassword").Text());
                    m.put("Discount", Conv.toStr(c("PersonDiscount").SelectedRow().get("Count")));
                    m.put("GroupID", c("PersonGroupSelect").SelectedValue().toString());
                    m.put("LimitAmountDay", z(c("PersonLimitAmountDaliy").Text()));
                    m.put("LimitAmountMonth", z(c("PersonLimitAmountMonth").Text()));
                    m.put("LimitOil", code("PersonLimitOilType"));
                    m.put("LimitTimeDay", z(c("PersonLimitDaliyTime").Text()));
                    m.put("LimitTypeID", String.valueOf(c("LimitType").SelectedIndex()));
                    m.put("LimitVehicleNo", c("PersonCardNo").Text());
                    m.put("LimitVolume", z(c("PersonLimitVolume").Text()));
                    m.put("LimitVolumeMonth", z(c("PersonLlimitVolumeMonth").Text()));
                    m.put("isLimitVehicleNo", c("PersonLimitCardNo").Checked());
                    m.put("PosUse", c("POSEnable").Checked());
                    m.put("LimitMileage", z(c("PersonLimitMileage").Text()));
                    m.put("MileageTolerance", z(c("PersonMileageTolerance").Text()));
                    m.put("Name", c("PersonName").Text());
                    m.put("PostCode", c("PersonPostCode").Text());
                    m.put("Telephone", c("PersonPhone").Text());
                    m.put("UseConfine", GetRCSLimit("Person", c("PersonStation").Text()));
                    m.put("Code", c("PersonID").Text());
                    m.put("Email", c("PersonEmail").Text());
                    m.put("VehicleNoColor", colour(c("CardNoColor").SelectedIndex()));
                    m.put("VehicleNoType", String.valueOf(c("CardNoType").SelectedIndex() + 1));
                    m.put("CardType", "010001");
                    m.put("VehicleNo", c("PersonCardNo").Text());
                    m.put("CreditLine", "0");
                    m.put("BillDate", "0");
                }
                case "GroupButton" -> {
                    m.put("Address", c("GroupAddress").Text());
                    m.put("ASN", c("GroupAsn").Text());
                    m.put("CertificateNo", c("GroupCertificateNo").Text());
                    m.put("Pass", c("GroupPassword").Text());
                    m.put("CompanyName", c("GroupName").Text());
                    m.put("Email", c("GroupEamil").Text());
                    m.put("Fax", c("GroupFax").Text());
                    m.put("GroupID", c("GroupAccount").Text());
                    m.put("ID", c("GroupID").Text());
                    m.put("LimitAmountDay", z(c("GroupLimitAmountDay").Text()));
                    m.put("LimitAmountMonth", z(c("GroupLimitAmountMonth").Text()));
                    m.put("LimitTimeDay", z(c("GroupLimitTime").Text()));
                    m.put("LimitVolume", z(c("GroupLimitVolume").Text()));
                    m.put("LimitVolumeMonth", z(c("GroupLimitVolumeMonth").Text()));
                    m.put("Manager", c("GroupManage").Text());
                    m.put("Telephone", c("GroupPhone").Text());
                    m.put("PostCode", c("GroupPostCode").Text());
                    m.put("LimitOil", code("GroupLimitOil"));
                    int type = c("GroupTypeSelect").SelectedIndex();
                    m.put("CardGroupType", type);
                    m.put("Discount", Conv.toStr(c("GroupDisCount").SelectedRow().get("Count")));
                    if (type == 0 || type == 2) {
                        m.put("isCreditCard", false);
                        m.put("CreditLine", "0");
                        m.put("BillDate", "0");
                        m.put("RepayDay", "0");
                        m.put("CardType", type == 0 ? "010002" : "010014");
                        m.put("GroupID", "0");
                    } else if (type == 1) {
                        m.put("isCreditCard", true);
                        m.put("CreditLine", c("GroupCreditLine").Text());
                        m.put("BillDate", c("GroupCreateDay").Text());
                        m.put("RepayDay", c("GroupRepayDay").Text());
                        m.put("CardType", "010012");
                        m.put("GroupID", "0");
                    }
                    if (c("LimitMinBalance").Visible()) {
                        m.put("LimitMinBalance", Conv.toDecimal(c("LimitMinBalance").Text().isEmpty() ? "500" : c("LimitMinBalance").Text()));
                    }
                    m.put("Name", "");
                }
                case "CreditButton" -> {
                    Object group = c("CreditGroup").SelectedValue();
                    m.put("ASN", c("CreditAsn").Text());
                    m.put("AccountNo", c("CreditAccount").Text());
                    m.put("Address", c("CreditAddress").Text());
                    m.put("BirthDay", birth("CreditBirthDay"));
                    m.put("CertificateTypeID", String.valueOf(c("CreditCertificate").SelectedIndex() + 1));
                    m.put("CertificateNo", c("CreditCertificateNo").Text());
                    m.put("Pass", c("CreditPwd").Text());
                    // Comme l'original : la remise est lue dans PersonDiscount
                    m.put("Discount", Conv.toStr(c("PersonDiscount").SelectedRow().get("Count")));
                    m.put("GroupID", group == null ? "100000001" : group.toString());
                    m.put("LimitAmountDay", z(c("CreditLimitAmount").Text()));
                    m.put("LimitAmountMonth", z(c("CreditLimitAmountMonth").Text()));
                    m.put("LimitOil", code("CreditLimitOilType"));
                    m.put("LimitTimeDay", z(c("CreditLimitTime").Text()));
                    m.put("LimitTypeID", String.valueOf(c("CreditLimitType").SelectedIndex()));
                    m.put("LimitVehicleNo", c("CreditCardNo").Text());
                    m.put("LimitVolume", z(c("CreditLimitVolume").Text()));
                    m.put("LimitVolumeMonth", z(c("CreditLimitVolumeMonth").Text()));
                    m.put("isLimitVehicleNo", c("CreditLimitCardNo").Checked());
                    m.put("PosUse", c("CreditPosEnable").Checked());
                    m.put("LimitMileage", z(c("CreditLimitMileage").Text()));
                    m.put("MileageTolerance", z(c("CreditMileageTolerance").Text()));
                    m.put("Name", c("CreditName").Text());
                    m.put("PostCode", c("CreditPostCode").Text());
                    m.put("Telephone", c("CreditPhone").Text());
                    m.put("UseConfine", GetRCSLimit("Credit", c("CreditLimitStations").Text()));
                    m.put("VehicleNo", c("CreditCardNo").Text());
                    m.put("Code", c("CreditID").Text());
                    m.put("CreditLine", c("CreditLine").Text());
                    m.put("BillDate", c("BillDate").Text());
                    m.put("RepayDay", c("RepayDate").Text());
                    m.put("Email", c("CreditEmail").Text());
                    m.put("CardType", "010011");
                }
                case "ChilrenConfirm" -> {
                    if ("zh-CN".equals(IniInfo.Language)) {
                        need(!c("ChilrenCarNo").Text().isEmpty(), "车牌号不能为空");
                        need(!c("ChilrenPhone").Text().isEmpty(), "手机号不能为空");
                    }
                    m.put("ASN", c("ChilrenCardAsn").Text());
                    m.put("AccountNo", c("ChilrenCardAccount").Text());
                    m.put("Address", c("ChilrenAddress").Text());
                    m.put("BirthDay", birth("ChilrenBirthDay"));
                    m.put("CertificateTypeID", String.valueOf(c("ChilrenCardCerTypeSelect").SelectedIndex() + 1));
                    m.put("CertificateNo", c("ChilrenCardCertificateNo").Text());
                    m.put("Pass", c("ChilrenCardPassword").Text());
                    m.put("Discount", Conv.toStr(c("ChilrenDisCount").SelectedRow().get("Count")));
                    m.put("GroupID", c("MasterGroupSelect").SelectedValue().toString());
                    m.put("LimitAmountDay", z(c("ChilrenLimitAmount").Text()));
                    m.put("LimitAmountMonth", z(c("ChilrenLimitAmountMonth").Text()));
                    m.put("LimitOil", code("ChilrenLimitOilType"));
                    m.put("LimitTimeDay", z(c("ChilrenLimitTime").Text()));
                    m.put("LimitTypeID", String.valueOf(c("ChilrenLimitTypeSelect").SelectedIndex()));
                    m.put("LimitVehicleNo", z(c("ChilrenCarNo").Text()));
                    m.put("LimitVolume", z(c("ChilrenLimitVolume").Text()));
                    m.put("LimitVolumeMonth", z(c("ChilrenLimitVolumeMonth").Text()));
                    m.put("isLimitVehicleNo", c("ChilrenIsLimitCardNo").Checked());
                    m.put("PosUse", c("ChilrenPosEnable").Checked());
                    m.put("LimitMileage", z(c("ChilrenLimitMileager").Text()));
                    m.put("MileageTolerance", z(c("ChilrenMileagerTolerance").Text()));
                    m.put("Name", c("ChilrenCardName").Text());
                    m.put("PostCode", c("ChilrenPostCode").Text());
                    m.put("Telephone", c("ChilrenPhone").Text());
                    m.put("UseConfine", GetRCSLimit("Child", c("ChildLimitStation").Text()));
                    m.put("Code", c("ChilrenCardID").Text());
                    m.put("Email", c("ChilrenEmail").Text());
                    m.put("VehicleNoColor", colour(c("CardNoColor").SelectedIndex()));
                    m.put("VehicleNoType", String.valueOf(c("CardNoType").SelectedIndex() + 1));
                    m.put("CardType", "010013");
                    m.put("VehicleNo", c("ChilrenCarNo").Text());
                    m.put("CreditLine", "0");
                    m.put("BillDate", "0");
                }
                default -> {
                }
            }
            m.put("SpecialPrice", GetSpecialPrice(btn));
            CardMakeLoad(list -> {
                switch (btn) {
                    case "EmployeeConfirm" -> {
                        c("EmpAsn").Text(list.get(0));
                        c("EmpValidTime").Text(list.get(1));
                        c("EmpAccount").Text(list.get(2));
                    }
                    case "PersonButton" -> {
                        c("PersonAsn").Text(list.get(0));
                        c("PersonValidTime").Text(list.get(1));
                        c("PersonAccount").Text(list.get(2));
                    }
                    case "GroupButton" -> {
                        c("GroupAsn").Text(list.get(0));
                        c("GroupValidTime").Text(list.get(1));
                        c("GroupAccount").Text(list.get(2));
                    }
                    case "CreditButton" -> {
                        c("CreditAsn").Text(list.get(0));
                        c("CreditAccount").Text(list.get(2));
                    }
                    case "ChilrenConfirm" -> {
                        c("ChilrenCardAsn").Text(list.get(0));
                        c("ChilrenCardAccount").Text(list.get(2));
                    }
                    default -> {
                    }
                }
            });
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private String PrepareBuildInfo(Row m) {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
            return BaseFunction.CON_BIN + "1101" + m.str("ASN") + LocalDateTime.now().format(f) + Conv.toDateTime(m.get("ValidTime")).format(f) + "0100";
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void CardTool_SelectedIndexChanged() {
        switch (c("CardTool").SelectedIndex()) {
            case 0 -> PreaperCusCardInfo();
            case 2 -> PreaperGroupCardInfo();
            case 3 -> PreaperCreditCardInfo();
            case 1 -> PreaperEmpCardInfo();
            case 4 -> PreaperChilrenCardInfo();
            default -> {
            }
        }
    }

    /** DateTime.Now.ToLongDateString() : date longue, relue ensuite par Convert.ToDateTime. */
    private static String longDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private Row lastAsn(int type) {
        String aSN = BaseFunction.GetASN(type);
        return aSN.isEmpty() ? null : Wire.parseRow(BaseFunction.DecryptByAES(aSN));
    }

    private void PreaperChilrenCardInfo() {
        try {
            Row info = lastAsn(6);
            if (info != null) {
                long num = Conv.toLong(info.get("ASN"));
                long num2 = Conv.toLong(info.get("CustomerID"));
                if (num == 0L) {
                    c("ChilrenCardAsn").Text("01000611000000000001");
                    c("ChilrenCardAccount").Text("1300000001");
                    c("ChilrenCardID").Text("600001");
                } else {
                    c("ChilrenCardAsn").Text(BaseFunction.ConverToStr(String.valueOf(num + 1), 20));
                    String account = String.valueOf(num2 + 1);
                    c("ChilrenCardAccount").Text(account);
                    c("ChilrenCardID").Text("6" + account.substring(account.length() - 5));
                    c("ChilrenBirthDay").Text(longDate());
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void PreaperCusCardInfo() {
        try {
            Row info = lastAsn(1);
            if (info == null) {
                c("PersonAsn").Text("01000111000000000001");
            } else {
                long num = Conv.toLong(info.get("ASN"));
                if (num == 0L) {
                    c("PersonAsn").Text("01000111000000000001");
                    c("PersonAccount").Text("2100000001");
                } else {
                    c("PersonAsn").Text(BaseFunction.ConverToStr(String.valueOf(num + 1), 20));
                    c("PersonAccount").Text(String.valueOf(Conv.toLong(info.get("CustomerID")) + 1));
                }
            }
            c("PersonValidTime").Text(LocalDateTime.now().plusYears(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            c("PersonCertificate").SelectedIndex(0);
            String account = c("PersonAccount").Text();
            if (!account.isEmpty()) {
                c("PersonID").Text(account.substring(account.length() - 6));
            }
            c("PersonBirthDay").Text(longDate());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void PreaperGroupCardInfo() {
        c("CreditBox").Visible(false);
        c("GroupDisCount").SelectedIndex(0);
        PreaperLoadOilType();
    }

    private void PreaperGroupMessage() {
        try {
            String text = "";
            switch (c("GroupTypeSelect").SelectedIndex()) {
                case 0 -> {
                    text = BaseFunction.GetASN(2);
                    c("CreditBox").Visible(false);
                }
                case 1 -> {
                    text = BaseFunction.GetASN(12);
                    c("CreditBox").Visible(true);
                }
                case 2 -> {
                    text = BaseFunction.GetASN(5);
                    c("CreditBox").Visible(false);
                }
                default -> {
                }
            }
            if (!text.isEmpty()) {
                Row gc = SW.dto().shape("GroupCard", Wire.parseRow(BaseFunction.DecryptByAES(text)));
                c("GroupAsn").Text(BaseFunction.ConverToStr(String.valueOf(Conv.toLong(gc.get("Asn")) + 1), 20));
                String account = String.valueOf(gc.i("GroupID") + 1);
                c("GroupAccount").Text(account);
                String tail = account.substring(account.length() - 5);
                switch (String.valueOf(gc.i("GroupID")).substring(0, 2)) {
                    case "19" -> c("GroupID").Text("9" + tail);
                    case "18" -> c("GroupID").Text("8" + tail);
                    case "14" -> c("GroupID").Text("7" + tail);
                    default -> {
                    }
                }
                c("GroupValidTime").Text(LocalDateTime.now().plusYears(5).format(Conv.STD));
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void PreaperCredit() {
        c("CreditCertificate").SelectedIndex(0);
        c("CreditLimitType").SelectedIndex(0);
        PreaperLoadOilType();
        c("CreditLimitOilType").SelectedIndex(0);
        PreaperLoadGroup();
    }

    private void PreaperCreditCardInfo() {
        try {
            c("groupBox13").Visible(!IniInfo.CreditPayAdvance);
            PreaperCredit();
            Row info = lastAsn(3);
            if (info == null) {
                c("CreditAsn").Text("01001111000000000001");
                return;
            }
            long num = Conv.toLong(info.get("ASN"));
            c("CreditAsn").Text(num == 0L ? "01001111000000000001" : BaseFunction.ConverToStr(String.valueOf(num + 1), 20));
            String asn = c("CreditAsn").Text();
            c("CreditAccount").Text("17" + asn.substring(asn.length() - 8));
            c("CreditID").Text(asn.substring(asn.length() - 6));
            c("CreditBirthDay").Text(LocalDateTime.now().format(Conv.STD));
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void PreaperEmpCardInfo() {
        boolean emp = c("EmpCardType").SelectedIndex() == 0;
        Row info = lastAsn(emp ? 4 : 8);
        if (info == null) {
            c("EmpAsn").Text(emp ? "01000411000000000001" : "01000611000000000001");
        } else {
            BigDecimal num = Conv.toDecimal(info.get("ASN"));
            if (num.signum() == 0) {
                c("EmpAsn").Text(emp ? "01000411000000000001" : "01000611000000000001");
                c("EmpAccount").Text(emp ? "2000000001" : "1600000001");
            } else {
                c("EmpAsn").Text(BaseFunction.ConverToStr(num.add(BigDecimal.ONE).toPlainString(), 20));
                c("EmpAccount").Text(String.valueOf(Conv.toLong(info.get("CustomerAccountID")) + 1));
            }
        }
        c("EmpValidTime").Text(LocalDateTime.now().plusYears(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    /** Codes des régions / villes / stations choisies (texte « a,b,c » de la liste). */
    private String GetRCSLimit(String BtnType, String StationText) {
        StringBuilder text = new StringBuilder();
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
        int num = switch (BtnType) {
            case "Person" -> c("LimitType").SelectedIndex();
            case "Credit" -> c("CreditLimitType").SelectedIndex();
            case "Child" -> c("ChilrenLimitTypeSelect").SelectedIndex();
            default -> -1;
        };
        String path;
        String field;
        String codeField = "CodeInner";
        switch (num) {
            case 1 -> {
                path = "GetRegion";
                field = "RegionDesc";
            }
            case 2 -> {
                path = "GetCityList";
                field = "CityDesc";
            }
            case 3 -> {
                path = "GetStationList";
                field = "Name";
                codeField = "Code";
            }
            default -> {
                return "";
            }
        }
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/" + path, Wire.json(obj));
        if (r.Result()) {
            List<Row> list = Wire.parseRows(BaseFunction.DecryptByAES(r.Mess()));
            for (String part : StationText.split(",", -1)) {
                // Comme l'original : seules les régions comparent le texte sans espaces
                String wanted = num == 1 ? part.trim() : part;
                for (Row item : list) {
                    if (item.str(field).equals(wanted)) {
                        text.append(item.str(codeField)).append(',');
                        break;
                    }
                }
            }
        }
        return text.toString();
    }

    public void GroupTypeSelect_SelectedIndexChanged() {
        try {
            int i = c("GroupTypeSelect").SelectedIndex();
            c("CreditBox").Visible(i == 1);
            c("LimitMinLabel").Visible(i == 2);
            c("LimitMinBalance").Visible(i == 2);
            c("label110").Visible(i == 2);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CreditGroup_SelectedIndexChanged() {
        try {
            Row group = SW.dto().make("Group", "Code", IniInfo.Code, "GroupID", Conv.toInt(c("CreditGroup").SelectedValue()));
            CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetCreditGroupMess", Wire.json(group));
            if (r.Result()) {
                Row info = SW.dto().shape("CenterGroupCardInfos", Wire.parseRow(BaseFunction.DecryptByAES(r.Mess())));
                c("CreditBalance").Text(Conv.toStr(info.get("CreditLines")));
                c("BillDate").Text(Conv.toStr(info.get("DateAccount")));
                c("RepayDate").Text(Conv.toStr(info.get("DateRepayment")));
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void CardSelect_SelectedIndexChanged() {
        int i = c("CardSelect").SelectedIndex();
        for (String n : new String[] {"PersonInitButton", "EmpIniButton", "GroupIniButton", "CreditIniButton"}) {
            c(n).Visible(i != 0);
        }
        if (i == 0 || i == 1 || i == 2) {
            c("ChilrenInit").Visible(i != 0);
        }
    }

    private boolean Regular(String btn) {
        try {
            int vf = IniInfo.VFormat;
            int mf = IniInfo.MFormat;
            boolean zh = "zh-CN".equals(IniInfo.Language);
            switch (btn) {
                case "PersonButton" -> {
                    password("PersonPassword");
                    need(!c("PersonName").Text().isEmpty(), L("UserNameEmpty"));
                    need(!c("PersonCertificateNo").Text().isEmpty(), L("UserIDEmpty"));
                    if (zh) {
                        need(!c("PersonCardNo").Text().isEmpty(), "车牌号不能为空");
                        need(!c("PersonPhone").Text().isEmpty(), "手机号不能为空");
                    }
                    match("PersonPhone", NUM, L("PhoneError"));
                    carNo("PersonCardNo");
                    match("PersonPostCode", POST, L("PostCodeFormatError"));
                    match("PersonEmail", MAIL, L("EamilFormatError"));
                    limitDaily("PersonLimitVolume", "LimitVolumeError", vf, 65535.0, "LimitVolumeExceeded");
                    numeric("PersonLimitDaliyTime", "LimitDayTimeError");
                    limitDaily("PersonLimitAmountDaliy", "LimitAmountDailyError", mf, 4294967295.0, "LimitAmountDailyExceeded");
                    limitMonthly("PersonLlimitVolumeMonth", "LimitVolumeMonthError", vf, 65535.0, "LimitVolumeMonthExceeded", digits());
                    limitMonthly("PersonLimitAmountMonth", "LimitAmountMonthError", mf, 4294967295.0, "LimitAmountMonthExceeded", digits());
                    numeric("PersonLimitMileage", "FuelConsumptionError");
                    numeric("PersonMileageTolerance", "MileageNumber");
                    prices("GradePrice");
                }
                case "EmployeeConfirm" -> {
                    String t = c("EmpPassword").Text();
                    if (!t.isEmpty()) {
                        need(t.length() == 4, L("NeedPasswordMaxLength"));
                        need(c("EmpPasswordConfirm").Text().equals(t), L("PasswordInconformity"));
                        need(valid(t, NUM), L("PasswordIsNumber"));
                    }
                    need(!c("EmployeeSelect").Text().isEmpty(), L("SelectEmployee"));
                    prices("EGradePrice");
                }
                case "GroupButton" -> {
                    password("GroupPassword");
                    need(!c("GroupName").Text().isEmpty(), L("GroupNameEmpty"));
                    need(!c("GroupCertificateNo").Text().isEmpty(), L("BusinessNoEmpty"));
                    match("GroupPhone", NUM, L("PhoneError"));
                    match("GroupPostCode", POST, L("PostCodeFormatError"));
                    match("GroupEamil", MAIL, L("EamilFormatError"));
                    limitDaily("GroupLimitVolume", "LimitVolumeError", vf, 65535.0, "LimitVolumeExceeded");
                    numeric("GroupLimitTime", "LimitDayTimeError");
                    limitDaily("GroupLimitAmountDay", "LimitAmountDailyError", mf, 4294967295.0, "LimitAmountDailyExceeded");
                    limitMonthly("GroupLimitVolumeMonth", "LimitVolumeMonthError", vf, 65535.0, "LimitVolumeMonthExceeded", digits());
                    limitMonthly("GroupLimitAmountMonth", "LimitAmountMonthError", mf, 4294967295.0, "LimitAmountMonthExceeded", digits());
                    if (c("CreditBox").Visible()) {
                        need(!c("GroupCreditLine").Text().isEmpty(), L("CreditLineEmpty"));
                        need(isDouble(c("GroupCreditLine").Text()), L("NumberOrDecimal"));
                        days("GroupCreateDay", "BillCreateDayEmpty");
                        days("GroupRepayDay", "RepayDayEmpty");
                    }
                    if (c("LimitMinBalance").Visible()) {
                        match("LimitMinBalance", DEC, L("LimitLevelError") + "\n" + L("NumberOrDecimal"));
                    }
                }
                case "CreditButton" -> {
                    if (!IniInfo.CreditPayAdvance) {
                        need(!c("CreditLine").Text().isEmpty(), L("CreditLineEmpty"));
                        need(isDouble(c("CreditLine").Text()), L("NumberOrDecimal"));
                        need(!(Double.parseDouble(c("CreditLine").Text().trim()) > Conv.toDouble(c("CreditBalance").Text())), L("OutOfCreditLine"));
                    }
                    password("CreditPwd");
                    need(!c("CreditName").Text().isEmpty(), L("UserNameEmpty"));
                    need(!c("CreditCertificateNo").Text().isEmpty(), L("IdentificationEmpty"));
                    if (zh) {
                        need(!c("CreditCardNo").Text().isEmpty(), "车牌号不能为空");
                        need(!c("CreditPhone").Text().isEmpty(), "手机号不能为空");
                    }
                    match("CreditPhone", NUM, L("PhoneError"));
                    carNo("CreditCardNo");
                    match("CreditPostCode", POST, L("PostCodeFormatError"));
                    match("CreditEmail", MAIL, L("EamilFormatError"));
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

    /** Prix spéciaux saisis : chiffres uniquement. */
    private void prices(String box) {
        for (String n : Controls(box)) {
            if (TypeOf(n).endsWith("TextBox")) {
                need(c(n).Text().isEmpty() || valid(c(n).Text(), NUM), "Le prix préférentiel doit être un nombre");
            }
        }
    }

    private void Card_Init() {
        Operate op = new Operate();
        try {
            if (!op.SCardConnect(CardWrite.READER)) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            switch (Ini_CardSelect) {
                case 1 -> {
                    if (!CardWrite.ExtAuthorizeForFlying(op)) {
                        throw new IllegalStateException(L("ExternalValidationError"));
                    }
                    if (!CardWrite.DeleteForFlying(op) && !CardWrite.NewDeleteForFlying(op)) {
                        throw new IllegalStateException(L("InitializeError"));
                    }
                }
                case 2 -> {
                    if (!CardWrite.DeleteMF(op, IniInfo.DHTransferKey, 0)) {
                        throw new IllegalStateException(L("InitializeError"));
                    }
                }
                default -> {
                }
            }
            op.SCardDisconnect();
            MessageBox(L("InitializeSuccess"));
        } catch (RuntimeException ex) {
            op.SCardDisconnect();
            MessageBox(ex.getMessage());
        }
    }

    public void PersonInitButton_Click() {
        try {
            Ini_CardSelect = c("CardSelect").SelectedIndex();
            Card_Init();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void MasterGroupSelect_SelectedIndexChanged() {
        int groupID = c("MasterGroupSelect").SelectedRow().i("GroupID");
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Mess", String.valueOf(groupID));
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetMasterMess", Wire.json(obj));
        if (!r.Result()) {
            return;
        }
        Object parsed = Wire.parse(BaseFunction.DecryptByAES(r.Mess()));
        if (parsed == null) {
            return;
        }
        Row infos = SW.dto().shape("CenterGroupCardInfos", Conv.toRow(parsed));
        Row group = db().first("Group", "{GroupID} = :g", p("g", infos.get("GroupID")));
        if (group != null) {
            c("MasterCardID").Text(Conv.toStr(group.get("GroupID")));
            c("MasterCardName").Text(group.str("Name"));
        }
        c("ChilrenIsLimitCardNo").Checked(infos.b("isLimitVehicleNo"));
        String oil = infos.str("LimitOil");
        c("ChilrenLimitOilType").SelectedValue(!oil.isEmpty() ? BaseFunction.GetOilIDByCode(Conv.toInt(oil)) : 0);
        c("ChilrenDisCount").SelectedValue(Conv.toDecimal(infos.get("Discount")));
        c("ChilrenLimitVolume").Text(Conv.toStr(infos.get("LimitVolume")));
        c("ChilrenLimitTime").Text(Conv.toStr(infos.get("LimitTime")));
        c("ChilrenLimitAmount").Text(Conv.toStr(infos.get("LimitAmount")));
        c("ChilrenLimitVolumeMonth").Text(Conv.toStr(infos.get("LimitVolumeMonthly")));
        c("ChilrenLimitAmountMonth").Text(Conv.toStr(infos.get("LimitAmountMonthly")));
        c("ChilrenLimitMileager").Text(Conv.toStr(infos.get("LimitMileage")));
        c("ChilrenMileagerTolerance").Text(Conv.toStr(infos.get("MileageTolerance")));
        c("ChilrenLimitTypeSelect").SelectedIndex(infos.i("LimitTypeID"));
    }

    public void EmpCardType_SelectedIndexChanged() {
        // vide dans l'original
    }
}
