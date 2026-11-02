package com.ltstation.center.sw.cardmake;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.hw.CardIssue;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.Mifare;
import com.ltstation.center.sw.hw.Operate;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;

/** Fenêtre de CardMake : appels ExtendUpdate/* de BaseInfo.HttpPost, avec le message du serveur en cas de refus. */
abstract class CardMakeForm extends SwForm {

    /** Corps déchiffré de la réponse ; Result = false lève le message du serveur. */
    static String send(String action, Object body) {
        CustomerRespone r = BaseFunction.post("ExtendUpdate/" + action, Wire.json(body));
        if (r.Mess() == null) {
            throw new IllegalStateException("Center ne répond pas");
        }
        String text = BaseFunction.DecryptByAES(r.Mess());
        if (!r.Result()) {
            throw new IllegalStateException(text);
        }
        return text;
    }

    static Row sendRow(String action, Object body) {
        return Wire.parseRow(send(action, body));
    }

    static List<Row> sendRows(String action, Object body) {
        return Wire.parseRows(send(action, body));
    }

    /** Card_Login_Return envoyé par la plupart des listes. */
    static Row loginData() {
        return Row.of("CompanyID", CM.CompanyID, "EmpID", CM.EmpID, "StationID", CM.StationID);
    }

    protected static String GetCardStatue(int id) {
        return BaseFunction.GetCardStatue(id);
    }

    /** ComboBox de stations (ExtendGetStation) : DisplayMember Text, ValueMember ValueID. */
    protected void LoadStations(String box) {
        List<Row> list = sendRows("ExtendGetStation", loginData());
        c(box).DataSource(list, "Text", "ValueID");
        if (!list.isEmpty()) {
            c(box).SelectedIndex(0);
        }
    }

    /** Sous-liste d'une réponse paginée. */
    protected static List<Row> listOf(Row r, String key) {
        return r.get(key) == null ? List.of() : Wire.parseRows(Wire.json(r.get(key)));
    }

    /** Date au format des requêtes de listes. */
    protected String time(String box) {
        return c(box).Value().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Date d'une réponse de Center, au format d'affichage. */
    protected static String dt(Object v) {
        if (v == null) {
            return "";
        }
        try {
            return Conv.toDateTime(v).format(Conv.STD);
        } catch (RuntimeException e) {
            return Conv.toStr(v);
        }
    }

    protected static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    // ------------------------------------------------------------ listes des fenêtres de carte

    protected void GetRule() {
        try {
            List<Row> list = new ArrayList<>(sendRows("ExtendGetScoreRule", Row.of("CompanyID", CM.CompanyID, "EmpID", CM.EmpID)));
            list.add(Row.of("ID", 0, "RuleName", "Aucune règle"));
            list.sort(Comparator.comparingLong(t -> Conv.toLong(t.get("ID"))));
            c("RuleSelect").DataSource(list, "RuleName", "ID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    protected void GetGroup() {
        try {
            List<Row> list = new ArrayList<>(sendRows("ExtendGetGroup", Row.of("CompanyID", CM.CompanyID, "EmpID", CM.EmpID)));
            list.add(Row.of("GroupID", 100000001, "Name", "Particulier"));
            list.sort(Comparator.comparingLong(t -> Conv.toLong(t.get("GroupID"))));
            c("GroupBox").DataSource(list, "Name", "GroupID");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void GroupManageBtn_Click() {
        ShowDialog("CardMake.GroupManage", r -> {
            if (r.ok()) {
                GetGroup();
            }
        });
    }

    /** Contrôle commun des limites : entier ou décimal positif. */
    protected static BigDecimal positive(String text, String name) {
        try {
            BigDecimal v = new BigDecimal(text.trim());
            if (v.signum() < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " Format Error");
        }
    }

    protected static BigDecimal discount(String text) {
        BigDecimal v;
        try {
            v = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Discount Format Error");
        }
        if (v.compareTo(new BigDecimal(100)) > 0 || v.signum() < 0) {
            throw new IllegalStateException("Discount Number Error");
        }
        return v;
    }

    // ------------------------------------------------------------ cartes

    /** Lecture du numéro : carte à puce (fabricants 0 à 2) ou Mifare (3). */
    protected String ReadAsn(int factory) {
        if (factory <= 2) {
            Operate operate = new Operate();
            boolean connected = operate.SCardConnect(CardWrite.READER);
            operate.SCardDisconnect();
            if (!connected) {
                throw new IllegalStateException("Card Reader Disconnect");
            }
            CardWrite.UserPublicData[] user = {null};
            if (!CardWrite.ReadCardNotVerify(user)) {
                throw new IllegalStateException("Card Read Failed");
            }
            return user[0].ASN;
        }
        long icdev = Mifare.rf_init(0, 9600);
        if (icdev <= 0) {
            throw new IllegalStateException("Card Reader Connect Error");
        }
        try {
            Mifare.Select(icdev);
            if (Mifare.rf_authentication_key(icdev, 0, 20, Mifare.Key()) != 0) {
                throw new IllegalStateException("Read Card Error");
            }
            return "00000000" + Mifare.rf_read_hex(icdev, 20).substring(0, 12);
        } finally {
            Mifare.rf_exit(icdev);
        }
    }

    /** Carte vierge Mifare : numéro, type et clés. */
    protected boolean Mifare_Make(String asn) {
        long icdev = 0;
        try {
            icdev = Mifare.rf_init(0, 9600);
            if (icdev <= 0) {
                throw new IllegalStateException("Card Reader Connect Error");
            }
            Mifare.Select(icdev);
            if (Mifare.rf_authentication_key(icdev, 0, 4, Mifare.Key()) != 0) {
                throw new IllegalStateException("Card Init Error");
            }
            Mifare.WriteCard(icdev, asn);
            Mifare.rf_exit(icdev);
            return true;
        } catch (RuntimeException ex) {
            if (icdev > 0) {
                Mifare.rf_exit(icdev);
            }
            MessageBox(ex.getMessage());
            return false;
        }
    }

    /** Effacement de la carte selon le fabricant ; retry : essai de la seconde commande BlueSky (création de carte). */
    protected boolean Card_Init(int factory, boolean retry) {
        Operate operate = new Operate();
        try {
            if (!operate.SCardConnect(CardWrite.READER)) {
                throw new IllegalStateException("Card Reader Disconnect");
            }
            if (factory == 1) {
                if (!CardWrite.ExtAuthorizeForFlying(operate)) {
                    throw new IllegalStateException("External Validation Error");
                }
                if (!CardWrite.DeleteForFlying(operate) && !(retry && CardWrite.NewDeleteForFlying(operate))) {
                    throw new IllegalStateException("InitializeError");
                }
            } else if (factory == 2 && !CardWrite.DeleteMF(operate, IniInfo.DHTransferKey, 0)) {
                throw new IllegalStateException("InitializeError");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException ex) {
            operate.SCardDisconnect();
            MessageBox("Card_Init:" + ex.getMessage());
            return false;
        }
    }

    /** ReadKeyFromDBSecond : clés de ExtendUpdate/GetSecretKey (cartes réelles NP). */
    private static boolean ReadKeyFromDBSecond(List<String> list) {
        try {
            List<String> seed = Wire.parseRows("[" + Wire.json(sendRow("GetSecretKey", Row.of("CompanyID", CM.CompanyID, "EmpID", CM.EmpID))) + "]")
                .get(0).get("m_sSeedKey") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            String k = BaseFunction.CONST_TEMPKEY;
            String key = k.substring(16, 24) + k.substring(32, 40) + k.substring(8, 16) + k.substring(24, 32);
            for (int i = 0; i < 20; i++) {
                list.add(BaseFunction.DES3_Decrypt(key, seed.get(i)));
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Écriture des informations personnelles d'une carte 010001 après la personnalisation. */
    private static boolean WriteCard(Operate operate, Row card) {
        List<String> list = new ArrayList<>();
        if (!CardWrite.ReadKeyFromDBNotChange(list) || list.isEmpty()) {
            return false;
        }
        String valid = LocalDateTime.now().plusYears(5).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return CardWrite.WriteOnNewPersonalCard(operate, card.str("UserName"), card.str("CNO"), "1", card.str("Password"), "", "", "",
            Conv.toStr(card.get("DayVolumeLimit")), Conv.toStr(card.get("DayLimitTime")), Conv.toStr(card.get("DayAmountLimit")), card.str("CarNo"),
            Conv.toStr(card.get("MonthVolumeLimit")), Conv.toStr(card.get("MonthAmountLimit")), "", "", "", "100", "false", valid, "", "", "", "", "",
            list.get(1), Conv.toStr(card.get("SpecialPrice")).replace(",", ""));
    }

    /**
     * Personnalisation de la carte. card : Asn, ValidTime et champs de la carte ; second : clés de GetSecretKey (cartes NP) ;
     * les informations personnelles ne sont écrites que si second = false.
     */
    protected boolean Card_Make(Row card, int factory, boolean second) {
        Operate operate = new Operate();
        try {
            if (!operate.SCardConnect(CardWrite.READER)) {
                throw new IllegalStateException("Card Reader Disconnect");
            }
            List<String> list = new ArrayList<>();
            if (!(second ? ReadKeyFromDBSecond(list) : CardWrite.ReadKeysFromDB(list)) || list.size() < 20) {
                throw new IllegalStateException("Key Error");
            }
            String asn = card.str("Asn");
            String sPublicInfo = PrepareBuildInfo(asn, card.str("ValidTime"));
            for (int i = 0; i < 20; i++) {
                String master = i == 6 || i == 11 ? BaseFunction.fnGetDeriveKey(list.get(i), BaseFunction.CON_BIN) : list.get(i);
                list.set(i, BaseFunction.fnGetDeriveKey(master, asn));
            }
            String[] keys = list.toArray(new String[0]);
            switch (factory) {
                case 0 -> CardIssue.WriteDataToACS(operate, sPublicInfo, keys);
                case 1 -> CardIssue.WriteDataFlying(operate, sPublicInfo, keys);
                case 2 -> CardIssue.WriteDataToDHCard(operate, IniInfo.DHTransferKey, sPublicInfo, keys);
                default -> {
                }
            }
            if (!second && asn.startsWith("010001") && !WriteCard(operate, card)) {
                throw new IllegalStateException("Card Build Error");
            }
            MessageBox("Card Make Success");
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException ex) {
            operate.SCardDisconnect();
            MessageBox(ex.getMessage());
            return false;
        }
    }

    private static String PrepareBuildInfo(String asn, String validTime) {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd");
            return BaseFunction.CON_BIN + "1101" + asn + LocalDateTime.now().format(f) + Conv.toDateTime(validTime).format(f) + "0100";
        } catch (RuntimeException e) {
            throw new IllegalStateException("PublicInfo Error");
        }
    }

    // ------------------------------------------------------------ exports

    /** BaseInfo.ExportExcel puis Process.Start : le fichier est envoyé au poste. */
    protected void ExportExcel(String fileName, String title, String[] head, List<Object[]> rows, Object[] sum) {
        try {
            Path file = Path.of("station", "Export", "Excel", fileName + ".xls");
            Files.createDirectories(file.getParent());
            try (HSSFWorkbook wb = new HSSFWorkbook(); FileOutputStream out = new FileOutputStream(file.toFile())) {
                Sheet sheet = wb.createSheet("sheet1");
                CellStyle style = wb.createCellStyle();
                style.setVerticalAlignment(VerticalAlignment.CENTER);
                style.setAlignment(HorizontalAlignment.CENTER);
                sheet.createRow(0).createCell(0).setCellValue(title);
                sheet.getRow(0).getCell(0).setCellStyle(style);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, head.length - 1));
                int n = 1;
                if (!rows.isEmpty()) {
                    var h = sheet.createRow(n++);
                    for (int i = 0; i < head.length; i++) {
                        h.createCell(i).setCellValue(head[i]);
                    }
                }
                for (Object[] v : rows) {
                    var row = sheet.createRow(n++);
                    row.setHeightInPoints(25f);
                    for (int i = 0; i < v.length; i++) {
                        row.createCell(i).setCellValue(v[i] == null ? "" : v[i].toString());
                    }
                }
                if (sum != null) {
                    var row = sheet.createRow(n);
                    row.setHeightInPoints(25f);
                    for (int i = 0; i < sum.length; i++) {
                        row.createCell(i).setCellValue(String.valueOf(sum[i]));
                    }
                }
                for (int k = 0; k < head.length; k++) {
                    int width = 8;
                    for (int l = 1; l <= sheet.getLastRowNum(); l++) {
                        var cell = sheet.getRow(l).getCell(k);
                        if (cell != null) {
                            cell.setCellStyle(style);
                            width = Math.max(width, cell.toString().length());
                        }
                    }
                    sheet.setColumnWidth(k, Math.min(width + 5, 200) * 256);
                }
                wb.write(out);
            }
            DownloadFile(file.toString());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
    }
}
