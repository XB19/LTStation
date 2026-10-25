package com.ltstation.center.sw.hw;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.BaseFunction.CustomerRespone;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.SW;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ServerDeal.CardWrite : lecture et écriture des cartes de carburant (cartes CPU, applications
 * SINOPEC / LOYALTY) sur le lecteur ACS ACR1281. Mêmes commandes APDU, mêmes calculs de MAC et
 * de clés dérivées que l'original ; les paramètres « out » sont des tableaux d'un élément.
 */
public final class CardWrite {

    /** Lecteur utilisé par le logiciel d'origine (interface sans contact du ACR1281). */
    public static final String READER = "ACS ACR1281 1S Dual Reader PICC 0";

    public static final LegacyLog cardwriteLog = new LegacyLog("cardwriteLog");

    private static final String DEFAULT_PIN = "9999";
    private static final String cmdSelectEtAID = "00A404000CA00000000353494E4F504543";
    private static final String cmdReadPublicInfo = "00B095001E";
    private static final String cmdVerifyPIN = "00200000";
    private static final String cmdUpdatePublicFile = "04D69500";
    private static final String cmdUpdateSecretFile = "04D69C00";
    private static final String cmdUpdateBasicFile = "04D69600";
    private static final String cmdUpdateGeneralFile = "00D69B00";
    private static final String cmdGetChallenge = "0084000004";
    private static final String INITVALUE_LOAD = "0000000000000000";
    public static final String cmdReadGeneralInfo = "00B09B0020";
    private static final String cmdChangePIN = "805E0100";
    private static final String cmdReloadPIN = "805E0000";
    private static final String cmdGetBalance = "805C000104";
    private static final String cmdInitForLoad = "80500001";
    private static final String cmdCreditForLoad = "80520000";
    private static final String cmdInitForPurchase = "80500101";
    private static final String cmdDebitForPurchase = "80540100";
    private static final String cmdGetLockProof = "E0CA0000";
    private static final String cmdInitForGreyUnLock = "E07A0901";
    private static final String cmdGreyUnLock = "E07E0900";
    private static final String CARDIDENTIFY = "0100";
    private static final String SFI_PUBLIC_DATA = "95";
    private static final String SFI_BASIC_DATA = "96";
    private static final String SFI_GENERAL_DATA = "9B";
    private static final String SFI_GENERAL_DATA_LC = "9b";
    private static final String SFI_SECRECT_DATA = "9C";
    private static final String DESCONST = "80";
    private static final String TTC = "00000001";
    private static final String F_2 = "FF";
    private static final String F_4 = "FFFF";
    private static final String F_8 = "FFFFFFFF";
    private static final String F_30 = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private static final String F_80 = "F".repeat(80);
    private static final String ID_LIMIT_REGIONS = "01";
    private static final String ID_LIMIT_CITIES = "02";
    private static final String ID_LIMIT_STATIONS = "04";
    private static final String ID_NOLIMIT_STATIONS = "FF";
    private static final String GENERAL_INFO_FOR_USERCARD = "0".repeat(62);
    private static final String[] cmdWriteInfosDH = {"00D695001E", "00D6960029", "00D69b0020", "00D69c0060"};
    private static final String[] cmdDHWriteKeys = {"450101FF00000000", "440101FF00000000", "430101FF00000000", "400101FF00000000",
        "400201FF00000000", "410101FF00000000", "460101FF00000000", "470101FF00000000", "420101FF00000000", "4E0101FF00000000",
        "490101FF00000000"};
    private static final String[] cmdDHWriteKeysHead = {"80D4000124", "80D4000224", "80D4000324", "80D4000424", "80D4000524",
        "80D4000624", "80D4000724", "80D4000824", "80D4000924", "80D4000a24", "80D4000b24"};
    private static final String[] cmdWriteInfosDH_Java = {"04D6950022", "04D696002D", "00D69b0020", "04D69c0064"};
    private static final String[] cmdDHWriteKeys_Java = {"450101FF0000", "440101FF0000", "430101FF0000", "400101FF0000",
        "400201FF0000", "410101FF0000", "460101FF0000", "470101F00000", "420101FF0000", "4E0101FF0000", "490101FF0000"};
    private static final String cmdDHWriteKeysHead_Java = "84D400001C";

    /** Informations publiques de la carte (fichier 0x15). */
    public static final class UserPublicData {
        public String IssueID = "";
        public String App_Kind = "";
        public String Version = "";
        public String ASN = "";
        public String Start_Date = "";
        public String Valid_Date = "";
        public String Issue_FCI = "";
    }

    /** Preuve de verrouillage (carte grise). */
    public static final class GreyCardRead {
        public boolean isGrey;
        public String m_sGreyStatus = "";
        public String m_sBalance_Grey = "";
        public String m_sCTC = "";
        public String m_sTID = "";
        public String m_sGTime = "";
        public String m_sGMAC2 = "";
        public String m_sGTAC = "";
        public BigDecimal Balance = BigDecimal.ZERO;
    }

    private CardWrite() {
    }

    private static String hex(long v) {
        return Long.toHexString(v);
    }

    private static String now(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /** (int)(montant * MFormat) en hexadécimal sur 8 caractères. */
    private static String amountHex(String amount) {
        return BaseFunction.ConverToStr(hex(Conv.toDecimal(amount).multiply(BigDecimal.valueOf(IniInfo.MFormat)).intValue()), 8);
    }

    private static BigDecimal money(long value) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(IniInfo.MFormat), IniInfo.Mradio, RoundingMode.HALF_EVEN);
    }

    // ------------------------------------------------------------ clés de Center

    private static List<Row> secretKeys() {
        Row obj = SW.dto().make("CustomerParam", "Code", IniInfo.Code, "Start", 0);
        CustomerRespone r = BaseFunction.HttpPost(IniInfo.Center + "ServerDeal/GetSecretKey", Wire.json(obj));
        return r.Result() ? Wire.parseRows(BaseFunction.DecryptByAES(r.Mess())) : null;
    }

    public static boolean ReadKeyFromDBNotChange(List<String> list) {
        list.clear();
        try {
            List<Row> list2 = secretKeys();
            if (list2 != null) {
                for (int i = 0; i < 20; i++) {
                    list.add(list2.get(i).s("D_Data"));
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    private static String tempKey() {
        String k = BaseFunction.CONST_TEMPKEY;
        return k.substring(16, 24) + k.substring(32, 40) + k.substring(8, 16) + k.substring(24, 32);
    }

    public static boolean ReadKeysFromDB(List<String> list) {
        list.clear();
        try {
            List<Row> list2 = secretKeys();
            if (list2 != null) {
                for (int i = 0; i < 20; i++) {
                    list.add(BaseFunction.DES3_Decrypt(tempKey(), list2.get(i).s("D_Data")));
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ cartes de crédit, employé, groupe

    public static boolean WriteOnCreditCard(Operate operate, String sPwd, String sData) {
        try {
            UserPublicData[] user = {null};
            Init(operate, user, new String[1]);
            if (!scVerifyPin(operate, DEFAULT_PIN)) {
                throw new IllegalStateException("卡片产商类型错误");
            }
            if (!UpdateCreditCardInfo(operate, user[0], sPwd, sData)) {
                throw new IllegalStateException("记账卡信息修改失败");
            }
            if (!sPwd.isEmpty() && !sPwd.equals(DEFAULT_PIN) && !fnChangePIN(operate, DEFAULT_PIN, sPwd)) {
                throw new IllegalStateException("更新密码错误");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean UpdateCreditCardInfo(Operate operate, UserPublicData user, String Password, String sData) {
        try {
            String[] OutKey = {""};
            ReadKey(sData, OutKey);
            String key = BaseFunction.fnGetDeriveKey(OutKey[0], user.ASN);
            String text = Password.isEmpty() ? "00" : "01";
            if (!fnbUpDateEFBinFile(operate, text + GENERAL_INFO_FOR_USERCARD, SFI_GENERAL_DATA, user.IssueID, user.ASN, key)) {
                throw new IllegalStateException("");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ChangeCreditCardInfos(String ID_NeedPwdBefore, String OldPwd, String NewPwd, String sData) {
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {null};
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("读取卡信息失败");
            }
            if (OldPwd.isEmpty()) {
                OldPwd = DEFAULT_PIN;
            }
            if (!scVerifyPin(operate, OldPwd)) {
                throw new IllegalStateException("1");
            }
            if (!UpdateCreditCardInfo(operate, user[0], NewPwd, sData)) {
                throw new IllegalStateException("数据修改失败");
            }
            if (!SetPasswordNew(operate, ID_NeedPwdBefore, OldPwd, NewPwd)) {
                throw new IllegalStateException("重置密码失败");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean WriteOnEmployeeCard(Operate operate, String sEmployeeCode, String sEmployee, String sIDType, String sID,
                                              String sPwd, String sData, String sPer) {
        try {
            UserPublicData[] user = {null};
            Init(operate, user, new String[1]);
            if (!scVerifyPin(operate, DEFAULT_PIN)) {
                throw new IllegalStateException("卡片产商类型错误");
            }
            if (!UpdateEmployeeCardInfo(operate, user[0].ASN, sEmployeeCode, sEmployee, sIDType, sID, sPwd, sData, sPer, user[0].IssueID)) {
                throw new IllegalStateException("卡更新失败");
            }
            if (!sPwd.isEmpty() && !fnChangePIN(operate, DEFAULT_PIN, sPwd)) {
                throw new IllegalStateException("更新密码错误");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean WriteGroupCard(Operate operate, String UserName, String ID, String Pwd, String sData) {
        try {
            UserPublicData[] user = {null};
            boolean num = Init(operate, user, new String[1]);
            if (!(num & scVerifyPin(operate, DEFAULT_PIN))) {
                throw new IllegalStateException("1");
            }
            if (!UpdateGroupCardInfo(operate, user[0], UserName, ID, Pwd, sData)) {
                throw new IllegalStateException("");
            }
            if (!Pwd.isEmpty()) {
                fnChangePIN(operate, DEFAULT_PIN, Pwd);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean UpdateGroupCardInfo(Operate operate, UserPublicData user, String UserName, String ID, String Password, String sData) {
        try {
            String[] OutKey = {""};
            ReadKey(sData, OutKey);
            String key = BaseFunction.fnGetDeriveKey(OutKey[0], user.ASN);
            String personalString = GetPersonalString(UserName, ID, "0");
            if (!fnbUpDateEFBinFile(operate, personalString, SFI_BASIC_DATA, user.IssueID, user.ASN, key)) {
                throw new IllegalStateException("");
            }
            String text = Password.isEmpty() ? "00" : "01";
            if (!fnbUpDateEFBinFile(operate, text + GENERAL_INFO_FOR_USERCARD, SFI_GENERAL_DATA, user.IssueID, user.ASN, key)) {
                throw new IllegalStateException("");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean WriteOnNewPersonalCard(Operate operate, String UserName, String ID, String IDType, String Password, String LimitOil,
                                                 String LimitType, String Stations, String LimitQuantity, String LimitTimes, String LimitAmount,
                                                 String LimitCarNumber, String LimitVolumeMonthly, String LimitAmountMonthly, String LimitMileage,
                                                 String MileageTolerance, String PreferentialPrice, String Discount, String isLimitCarNumber,
                                                 String ValidDate, String bk2, String bk3, String bk4, String bk5, String bk6, String sData, String sPer) {
        try {
            UserPublicData[] user = {null};
            boolean flag = Init(operate, user, new String[1]);
            flag &= scVerifyPin(operate, DEFAULT_PIN);
            if (!flag) {
                throw new IllegalStateException("1");
            }
            flag &= UpdateCardInfo(operate, user[0], UserName, ID, IDType, Password, LimitOil, LimitType, Stations, LimitQuantity, LimitTimes,
                LimitAmount, LimitCarNumber, LimitVolumeMonthly, LimitAmountMonthly, LimitMileage, MileageTolerance, PreferentialPrice, Discount,
                isLimitCarNumber, ValidDate, bk2, bk3, bk4, bk5, bk6, sData, sPer, new String[1]);
            if (!flag) {
                throw new IllegalStateException("2");
            }
            if (!Password.isEmpty()) {
                flag &= fnChangePIN(operate, DEFAULT_PIN, Password);
                if (!flag) {
                    throw new IllegalStateException("3");
                }
            }
            return flag;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ChangeUserCardInfos(String UserName, String ID, String IDType, String ID_NeedPwdBefore, String Password,
                                              String NewPassword, String LimitOil, String LimitType, String Stations, String LimitQuantity,
                                              String LimitTimes, String LimitAmount, String LimitCarNumber, String LimitVolumeMonthly,
                                              String LimitAmountMonthly, String LimitMileage, String MileageTolerance, String PreferentialPrice,
                                              String Discount, String isLimitCarNumber, String ValidDate, String bk2, String bk3, String bk4,
                                              String bk5, String bk6, String sData, String sPer, String[] Error) {
        Error[0] = "";
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {null};
            boolean num = Init(operate, user, new String[1]);
            String text = Password.isEmpty() ? DEFAULT_PIN : Password;
            if (!(num & scVerifyPin(operate, text))) {
                throw new IllegalStateException("1");
            }
            if (!UpdateCardInfo(operate, user[0], UserName, ID, IDType, NewPassword, LimitOil, LimitType, Stations, LimitQuantity, LimitTimes,
                LimitAmount, LimitCarNumber, LimitVolumeMonthly, LimitAmountMonthly, LimitMileage, MileageTolerance, PreferentialPrice, Discount,
                isLimitCarNumber, ValidDate, bk2, bk3, bk4, bk5, bk6, sData, sPer, Error)) {
                throw new IllegalStateException("2 " + Error[0]);
            }
            if (!SetPasswordNew(operate, ID_NeedPwdBefore, Password, NewPassword)) {
                throw new IllegalStateException("51");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException ex) {
            operate.SCardDisconnect();
            Error[0] = ex.getMessage();
            return false;
        }
    }

    public static boolean ChangeEmployeeCard(String sEmployeeCode, String sEmployeeName, String sIDType, String sID, String ID_NeedPwdBefore,
                                             String Password, String NewPassword, String sData, String sper) {
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {null};
            boolean num = Init(operate, user, new String[1]);
            String text = Password.isEmpty() ? DEFAULT_PIN : Password;
            if (!(num & scVerifyPin(operate, text))) {
                throw new IllegalStateException("1");
            }
            if (!UpdateEmployeeCardInfo(operate, user[0].ASN, sEmployeeCode, sEmployeeName, sIDType, sID, NewPassword, sData, sper, user[0].IssueID)) {
                throw new IllegalStateException("2");
            }
            if (!SetPasswordNew(operate, ID_NeedPwdBefore, Password, NewPassword)) {
                throw new IllegalStateException("51");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean ChangeGroupCardInfos(String UserName, String ID, String ID_NeedPwdBefore, String OldPwd, String NewPwd, String sData) {
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {null};
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("读取卡信息失败");
            }
            if (OldPwd.isEmpty()) {
                OldPwd = DEFAULT_PIN;
            }
            if (!scVerifyPin(operate, OldPwd)) {
                throw new IllegalStateException("1");
            }
            if (!UpdateGroupCardInfo(operate, user[0], UserName, ID, NewPwd, sData)) {
                throw new IllegalStateException("数据修改失败");
            }
            if (!SetPasswordNew(operate, ID_NeedPwdBefore, OldPwd, NewPwd)) {
                throw new IllegalStateException("重置密码失败");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    private static boolean SetPasswordNew(Operate operate, String ID_NeedPwdBefore, String OldPassword, String NewPassword) {
        try {
            String text = OldPassword.isEmpty() ? DEFAULT_PIN : OldPassword;
            if (!NewPassword.isEmpty()) {
                if ("1".equals(ID_NeedPwdBefore)) {
                    if (!text.equals(NewPassword) && !fnChangePIN(operate, text, NewPassword)) {
                        throw new IllegalStateException("39");
                    }
                } else if (!fnChangePIN(operate, DEFAULT_PIN, NewPassword)) {
                    throw new IllegalStateException("39");
                }
            } else if ("1".equals(ID_NeedPwdBefore) && !fnChangePIN(operate, text, DEFAULT_PIN)) {
                throw new IllegalStateException("39");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean UpdateCardInfo(Operate operate, UserPublicData userPublicData, String UserName, String ID, String IDType,
                                          String Password, String LimitOil, String LimitType, String Stations, String LimitQuantity,
                                          String LimitTimes, String LimitAmount, String LimitCarNumber, String LimitVolumeMonthly,
                                          String LimitAmountMonthly, String LimitMileage, String MileageTolerance, String PreferentialPrice,
                                          String Discount, String isLimitCarNumber, String ValidDate, String bk2, String bk3, String bk4,
                                          String bk5, String bk6, String sData, String sPer, String[] Error) {
        Error[0] = "";
        try {
            String[] OutKey = {""};
            boolean flag = ReadKey(sData, OutKey);
            if (!flag) {
                throw new IllegalStateException("UpdateCardInfo ReadKey Error");
            }
            String key = BaseFunction.fnGetDeriveKey(OutKey[0], userPublicData.ASN);
            String publicString = GetPublicString(userPublicData, ValidDate);
            flag &= fnbUpDateEFBinFile(operate, publicString, SFI_PUBLIC_DATA, userPublicData.IssueID, userPublicData.ASN, key);
            if (!flag) {
                throw new IllegalStateException("fnbUpDateEFBinFile 1 Error");
            }
            String personalString = GetPersonalString(UserName, ID, IDType);
            flag &= fnbUpDateEFBinFile(operate, personalString, SFI_BASIC_DATA, userPublicData.IssueID, userPublicData.ASN, key);
            if (!flag) {
                throw new IllegalStateException("fnbUpDateEFBinFile 2 Error");
            }
            String text = "00";
            if (!Password.isEmpty() && !Password.equals("9999")) {
                text = "01";
            }
            StringBuilder text2 = new StringBuilder(text + "000000" + sPer);
            for (int i = sPer.length(); i < 56; i++) {
                text2.append('0');
            }
            if (!(flag & fnbUpDateEFBinFile(operate, text2.toString(), SFI_GENERAL_DATA, userPublicData.IssueID, userPublicData.ASN, key))) {
                throw new IllegalStateException("SFI_GENERAL_DATA Error");
            }
            String[] sSecretData = {""};
            flag = GetSecretString(LimitOil, LimitType, Stations, LimitQuantity, LimitTimes, LimitAmount, LimitCarNumber, LimitVolumeMonthly,
                LimitAmountMonthly, LimitMileage, MileageTolerance, PreferentialPrice, isLimitCarNumber, ValidDate, bk2, bk3, bk4, bk5, bk6,
                Discount, sSecretData, Error);
            if (!flag) {
                throw new IllegalStateException("GetSecretString Error: " + Error[0]);
            }
            flag &= fnbUpDateEFBinFile(operate, sSecretData[0], SFI_SECRECT_DATA, userPublicData.IssueID, userPublicData.ASN, key);
            if (!flag) {
                throw new IllegalStateException("SFI_SECRECT_DATA Error: " + Error[0]);
            }
            return flag;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return false;
        }
    }

    // ------------------------------------------------------------ lecture

    public static boolean ReadPinID(String[] res) {
        res[0] = "";
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            if (!Init(operate, new UserPublicData[1], new String[1])) {
                throw new IllegalStateException("初始化失败");
            }
            String text = scReadCardGeneralInfo(operate);
            operate.SCardDisconnect();
            res[0] = text.substring(0, 2);
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean ReadCardAsn(String[] res) {
        res[0] = "";
        try {
            Operate operate = new Operate();
            operate.SCardConnect(READER);
            UserPublicData[] user = {null};
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化失败");
            }
            scReadCardGeneralInfo(operate);
            operate.SCardDisconnect();
            res[0] = user[0].ASN;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ReadCardNotVerify(UserPublicData[] user) {
        user[0] = new UserPublicData();
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化失败");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean ReloadPin(String NewPwd, String sData1, String sData2) {
        UserPublicData[] user = {new UserPublicData()};
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化失败");
            }
            if (!ReloadPassword(operate, user[0], NewPwd, sData1, sData2)) {
                throw new IllegalStateException("密码重载失败");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    private static boolean ReloadPassword(Operate operate, UserPublicData userPublicData, String NewPwd, String sData1, String sData2) {
        try {
            String text = "01";
            if (NewPwd.isEmpty()) {
                NewPwd = DEFAULT_PIN;
                text = "00";
            }
            String[] OutKey = {""};
            ReadKey(sData1, OutKey);
            if (OutKey[0].isEmpty()) {
                throw new IllegalStateException("61");
            }
            String aSN = userPublicData.ASN;
            String text2 = BaseFunction.fnGetDeriveKey(OutKey[0], aSN);
            String[] OsTAC = {""};
            if (!fnbTAC(text2, "0000000000000000", text, OsTAC)) {
                throw new IllegalStateException("41");
            }
            if (!fnbReloadPIN(operate, NewPwd + OsTAC[0]) && !fnbReloadPINLong(operate, NewPwd, text2)) {
                throw new IllegalStateException("11");
            }
            if (!scVerifyPin(operate, NewPwd)) {
                throw new IllegalStateException("19");
            }
            ReadKey(sData2, OutKey);
            if (OutKey[0].isEmpty()) {
                throw new IllegalStateException("61");
            }
            text2 = BaseFunction.fnGetDeriveKey(OutKey[0], aSN);
            if (!fnbUpDateEFBinFile(operate, text + GENERAL_INFO_FOR_USERCARD, SFI_GENERAL_DATA, userPublicData.IssueID, userPublicData.ASN, text2)) {
                throw new IllegalStateException("28");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ReadCardOnLoad(String sPwd, UserPublicData[] user, BigDecimal[] Balance) {
        Balance[0] = BigDecimal.ZERO;
        user[0] = new UserPublicData();
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化失败");
            }
            if (scReadCardGeneralInfo(operate).substring(0, 2).equals("00")) {
                sPwd = DEFAULT_PIN;
            }
            if (!scVerifyPin(operate, sPwd)) {
                throw new IllegalStateException("密码输入错误");
            }
            if (!scGetBalance(operate, Balance)) {
                throw new IllegalStateException("读取错误");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean ReadGrey(String Pwd, UserPublicData[] user, GreyCardRead[] grey, String[] ErrorMessage) {
        user[0] = new UserPublicData();
        Operate operate = new Operate();
        ErrorMessage[0] = "";
        grey[0] = new GreyCardRead();
        try {
            if (!operate.SCardConnect(READER)) {
                throw new IllegalStateException("读卡器连接失败");
            }
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化信息读取失败");
            }
            scReadCardGeneralInfo(operate);
            if (Pwd.isEmpty()) {
                Pwd = "9999";
            }
            if (!scVerifyPin(operate, Pwd)) {
                throw new IllegalStateException("密码验证出错");
            }
            String[] res = {""};
            if (!fnReadGrey(operate, res)) {
                throw new IllegalStateException("读取失败");
            }
            GreyCardRead g = grey[0];
            String r = res[0];
            g.isGrey = Integer.parseInt(r.substring(0, 2)) != 0;
            g.m_sGreyStatus = r.substring(0, 2);
            g.m_sBalance_Grey = r.substring(6, 14);
            g.m_sCTC = r.substring(14, 18);
            g.m_sTID = r.substring(18, 30);
            g.m_sGTime = r.substring(30, 44);
            g.m_sGMAC2 = r.substring(44, 52);
            g.m_sGTAC = r.substring(52, 60);
            g.Balance = money(Long.parseLong(g.m_sBalance_Grey, 16));
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    private static boolean fnReadGrey(Operate operate, String[] res) {
        res[0] = "";
        try {
            String text = operate.ScardCommand(CreateCommand(cmdGetLockProof, 30), res);
            if (text.equals("9000")) {
                return true;
            }
            throw new IllegalStateException("Error");
        } catch (RuntimeException e) {
            res[0] = "";
            return false;
        }
    }

    public static boolean WriteGreyUnlock(String Amount, String sTID, String Pwd, String sData, String[] ErrorMessage) {
        ErrorMessage[0] = "";
        Operate operate = new Operate();
        try {
            if (!operate.SCardConnect(READER)) {
                throw new IllegalStateException("读卡器连接失败");
            }
            UserPublicData[] user = {null};
            if (!Init(operate, user, new String[1])) {
                throw new IllegalStateException("初始化信息读取失败");
            }
            scReadCardGeneralInfo(operate);
            if (Pwd.isEmpty()) {
                Pwd = "9999";
            }
            if (!scVerifyPin(operate, Pwd)) {
                throw new IllegalStateException("密码验证出错");
            }
            String text = "01";
            String text2 = amountHex(Amount);
            String text3 = now("yyyyMMddHHmmss");
            String text4 = "000000000000";
            text += text4;
            String[] recvData = {""};
            if (!fnbInitForGreyUnLock(operate, text, recvData)) {
                throw new IllegalStateException("Init Error");
            }
            if (recvData[0].length() < 36) {
                throw new IllegalStateException("Init Length Error");
            }
            String r = recvData[0];
            String text5 = r.substring(0, 8);
            String text6 = r.substring(8, 12);
            String text7 = r.substring(12, 16);
            String text8 = r.substring(20, 28);
            String text9 = r.substring(28, 36);
            String[] OutKey = {""};
            ReadKey(sData, OutKey);
            String isKey = BaseFunction.fnGetDeriveKey(OutKey[0], user[0].ASN);
            String[] OsDataOut = {""};
            if (!BaseFunction.fnbTripleDESEncrypt(isKey, text8 + text7 + "8000", OsDataOut)) {
                throw new IllegalStateException("Error");
            }
            String[] OsMAC = {""};
            if (!fnbMAC_DES(OsDataOut[0], INITVALUE_LOAD, text5 + text6 + "95" + text4, OsMAC)) {
                throw new IllegalStateException("Error");
            }
            if (!text9.equals(OsMAC[0])) {
                throw new IllegalStateException("密钥不匹配");
            }
            String[] OsMAC2 = {""};
            if (!fnbMAC_DES(OsDataOut[0], INITVALUE_LOAD, text2 + "95" + text4 + text3, OsMAC2)) {
                throw new IllegalStateException("Error");
            }
            if (!fnbGreyUnLock(operate, text2 + text3 + OsMAC2[0])) {
                throw new IllegalStateException("Error");
            }
            operate.SCardDisconnect();
            return true;
        } catch (RuntimeException ex) {
            ErrorMessage[0] = ex.getMessage();
            operate.SCardDisconnect();
            return false;
        }
    }

    private static boolean fnbInitForGreyUnLock(Operate operate, String LsData, String[] recvData) {
        recvData[0] = "";
        try {
            String text = CreateCommand(cmdInitForGreyUnLock, LsData, 18);
            cardwriteLog.writeLog("fnbInitForGreyUnLock:" + text);
            if (!operate.ScardCommand(text, recvData).equals("9000")) {
                throw new IllegalStateException("Error");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean fnbGreyUnLock(Operate operate, String LsData) {
        return operate.ScardCommand(CreateCommand(cmdGreyUnLock, LsData, 4), new String[1]).equals("9000");
    }

    // ------------------------------------------------------------ chargement / déchargement (圈存 / 圈提)

    public static boolean WriteOnLoad(String Asn, String saveAmount, String sTID, String Pwd, String sData) {
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {new UserPublicData()};
            Init(operate, user, new String[1]);
            if (!user[0].ASN.equals(Asn)) {
                throw new IllegalStateException("卡号错误");
            }
            if (scReadCardGeneralInfo(operate).substring(0, 2).equals("00")) {
                Pwd = DEFAULT_PIN;
            }
            if (!scVerifyPin(operate, Pwd)) {
                throw new IllegalStateException("密码错误");
            }
            String[] OutKey = {""};
            ReadKey(sData, OutKey);
            if (OutKey[0].isEmpty()) {
                throw new IllegalStateException("123");
            }
            String key = BaseFunction.fnGetDeriveKey(OutKey[0], Asn);
            String text2 = amountHex(saveAmount);
            String text3 = now("yyyyMMddHHmmss");
            String text4 = "00000000000" + sTID;
            text4 = text4.substring(text4.length() - 12);
            String text = "01" + text2 + text4;
            String[] recv = {""};
            if (!fnbInitForLoad(operate, text, recv)) {
                throw new IllegalStateException("初始化失败");
            }
            if (!recv[0].isEmpty()) {
                String text5 = recv[0].substring(8, 12);
                text = recv[0].substring(16, 24) + text5 + "8000";
                String[] OsDataOut = {""};
                if (!BaseFunction.fnbTripleDESEncrypt(key, text, OsDataOut)) {
                    throw new IllegalStateException("33");
                }
                String[] OsMAC = {""};
                fnbMAC_DES(OsDataOut[0], INITVALUE_LOAD, text2 + "01" + text4 + text3, OsMAC);
                if (!fnbCreditForLoad(operate, text3 + OsMAC[0])) {
                    throw new IllegalStateException("37");
                }
                operate.SCardDisconnect();
                return true;
            }
            throw new IllegalStateException("321");
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    public static boolean WriteOnUnload(String Asn, String sAmount, String sTID, String Pwd, String sData) {
        Operate operate = new Operate();
        try {
            operate.SCardConnect(READER);
            UserPublicData[] user = {new UserPublicData()};
            Init(operate, user, new String[1]);
            if (!user[0].ASN.equals(Asn)) {
                throw new IllegalStateException("卡号错误");
            }
            if (scReadCardGeneralInfo(operate).substring(0, 2).equals("00")) {
                Pwd = DEFAULT_PIN;
            }
            if (!scVerifyPin(operate, Pwd)) {
                throw new IllegalStateException("密码错误");
            }
            String[] OutKey = {""};
            ReadKey(sData, OutKey);
            if (OutKey[0].isEmpty()) {
                throw new IllegalStateException("123");
            }
            String isKey = BaseFunction.fnGetDeriveKey(BaseFunction.fnGetDeriveKey(OutKey[0], user[0].IssueID), user[0].ASN);
            String text2 = amountHex(sAmount);
            String text = "01" + text2 + sTID;
            String[] recv = {""};
            if (!fnbInitForPurchase(operate, text, recv)) {
                throw new IllegalStateException("圈提初始化失败");
            }
            if (!recv[0].isEmpty()) {
                text = recv[0].substring(22, 30) + recv[0].substring(8, 12) + "0001";
                String[] OsDataOut = {""};
                if (!BaseFunction.fnbTripleDESEncrypt(isKey, text, OsDataOut)) {
                    throw new IllegalStateException("33");
                }
                String text3 = now("yyyyMMddHHmmss");
                String[] OsMAC = {""};
                fnbMAC_DES(OsDataOut[0], INITVALUE_LOAD, text2 + "05" + sTID + text3, OsMAC);
                if (!fnbDebitForPurchase(operate, TTC + text3 + OsMAC[0])) {
                    throw new IllegalStateException("圈提失败");
                }
                operate.SCardDisconnect();
                return true;
            }
            throw new IllegalStateException("读取信息错误");
        } catch (RuntimeException e) {
            operate.SCardDisconnect();
            return false;
        }
    }

    private static boolean scGetBalance(Operate operate, BigDecimal[] Balance) {
        Balance[0] = BigDecimal.ZERO;
        try {
            String[] recvData = {""};
            operate.ScardCommand(cmdGetBalance, recvData);
            Balance[0] = money(Integer.parseUnsignedInt(recvData[0].substring(0, 8), 16) & 0xFFFFFFFFL);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String scReadCardGeneralInfo(Operate operate) {
        String[] recvData = {""};
        return operate.ScardCommand(cmdReadGeneralInfo, recvData).equals("9000") ? recvData[0] : "";
    }

    // ------------------------------------------------------------ contenu des fichiers de la carte

    private static String tail(String s, int n) {
        return s.substring(s.length() - n);
    }

    private static boolean GetSecretString(String LimitOil, String LimitType, String Stations, String LimitQuantity, String LimitTimes,
                                           String LimitAmount, String LimitCarNumber, String LimitVolumeMonthly, String LimitAmountMonthly,
                                           String LimitMileage, String MileageTolerance, String PreferentialPrice, String isLimitCarNumber,
                                           String bk1, String bk2, String bk3, String bk4, String bk5, String bk6, String Discount,
                                           String[] sSecretData, String[] Error) {
        try {
            StringBuilder s = new StringBuilder();
            Error[0] = "";
            s.append(!LimitOil.isEmpty() && LimitOil.length() == 4 ? LimitOil : F_4);
            if (Stations == null || Stations.isEmpty()) {
                s.append(ID_NOLIMIT_STATIONS).append(F_80);
            } else {
                int num = 8;
                switch (LimitType) {
                    case "1" -> {
                        s.append(ID_LIMIT_REGIONS);
                        num = 2;
                    }
                    case "2" -> {
                        s.append(ID_LIMIT_CITIES);
                        num = 4;
                    }
                    case "4" -> {
                        s.append(ID_LIMIT_STATIONS);
                        num = 8;
                    }
                    default -> {
                    }
                }
                // Comme l'original : les identifiants ne sont pas recopiés, seules des zones de zéros sont écrites
                StringBuilder text2 = new StringBuilder();
                String text3 = "";
                for (String ignored : Stations.split(",", -1)) {
                    text3 = "00000000" + text3;
                    text2.append(tail(text3, num));
                }
                text2.append(F_80);
                s.append(text2, 0, 80);
            }
            if (LimitQuantity.isEmpty() || LimitQuantity.equals("0")) {
                s.append(F_4);
            } else {
                s.append('1').append(tail("000" + LimitQuantity, 3));
            }
            if (LimitTimes.isEmpty() || LimitTimes.equals("0")) {
                s.append(F_2);
            } else {
                s.append(tail("00" + BaseFunction.ConverToStr(hex(Integer.parseInt(LimitTimes)), 2), 2));
            }
            if (LimitAmount.isEmpty() || LimitAmount.equals("0")) {
                s.append(F_8);
            } else {
                s.append(tail("00000000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(LimitAmount) * IniInfo.MFormat)), 8), 8));
            }
            s.append(!Conv.toBool(isLimitCarNumber) ? "FF" : "00");
            if (LimitCarNumber.isEmpty() || LimitCarNumber.equals("0")) {
                s.append(F_30);
            } else {
                s.append(BaseFunction.ConvertToHex(LimitCarNumber + " ".repeat(32)), 0, 30);
            }
            if (LimitVolumeMonthly.isEmpty() || LimitVolumeMonthly.equals("0")) {
                s.append(F_4);
            } else {
                s.append(tail("0000" + BaseFunction.ConverToStr(hex(Integer.parseInt(LimitVolumeMonthly)), 4), 4));
            }
            if (LimitAmountMonthly.isEmpty() || LimitAmountMonthly.equals("0")) {
                s.append(F_8);
            } else {
                s.append(tail("00000000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(LimitAmountMonthly) * IniInfo.MFormat)), 8), 8));
            }
            if (LimitMileage.isEmpty() || LimitMileage.equals("0")) {
                s.append(F_4);
            } else {
                s.append(tail("0000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(LimitMileage) * 100.0)), 4), 4));
            }
            if (MileageTolerance.isEmpty() || MileageTolerance.equals("0")) {
                s.append(F_2);
            } else {
                s.append(tail("00" + MileageTolerance, 2));
            }
            if (PreferentialPrice.isEmpty() || PreferentialPrice.equals("0")) {
                s.append(F_4);
            } else {
                s.append(tail("0000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(PreferentialPrice) * 100.0)), 4), 4));
            }
            if (Discount.isEmpty() || Discount.equals("0")) {
                s.append("2710");
            } else {
                s.append(tail("0000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(Discount) * 100.0)), 4), 4));
            }
            s.append(BaseFunction.ConverToStr(IniInfo.platform, 4));
            s.append(bk1.isEmpty() ? F_4 : tail("0000" + BaseFunction.ConverToStr(hex(Conv.toDateTime(bk1).getYear()), 4), 4));
            s.append(bk2.isEmpty() ? F_4 : tail("0000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(bk2) * 10.0)), 4), 4));
            s.append(bk3.isEmpty() ? F_4 : tail("0000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(bk3) * 100.0)), 4), 4));
            s.append(bk4.isEmpty() ? F_8 : tail("00000000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(bk4) * 100.0)), 8), 8));
            s.append(bk5.isEmpty() ? F_8 : tail("00000000" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(bk5) * 100.0)), 8), 8));
            s.append(bk6.isEmpty() ? F_2 : tail("00" + BaseFunction.ConverToStr(hex((int) (Double.parseDouble(bk6) * 1.0)), 2), 2));
            sSecretData[0] = s.toString();
            return true;
        } catch (RuntimeException ex) {
            sSecretData[0] = "";
            Error[0] = ex.getMessage();
            return false;
        }
    }

    private static String GetPublicString(UserPublicData u, String ValidDate) {
        String text = ValidDate.substring(0, 4) + ValidDate.substring(5, 7) + ValidDate.substring(8, 10);
        return u.IssueID + u.App_Kind + u.Version + u.ASN + u.Start_Date + text + u.Issue_FCI;
    }

    private static boolean fnChangePIN(Operate operate, String IsOldPIN, String IsNewPIN) {
        return operate.ScardCommand(CreateCommand(cmdChangePIN, IsOldPIN + "FF" + IsNewPIN), new String[1]).equals("9000");
    }

    private static boolean UpdateEmployeeCardInfo(Operate operate, String ASN, String sEmployeeCode, String sEmployeeName, String sIDType,
                                                  String sID, String sPwd, String sData, String sPer, String IssueID) {
        try {
            String[] OutKey = {""};
            if (!ReadKey(sData, OutKey) || OutKey[0].isEmpty()) {
                throw new IllegalStateException("密钥分解错误");
            }
            String key = BaseFunction.fnGetDeriveKey(OutKey[0], ASN);
            fnbUpDateEFBinFile(operate, GetPersonalString(sEmployeeName, sID, sIDType), SFI_BASIC_DATA, IssueID, ASN, key);
            String text;
            String text2;
            if (sPwd.equals("9999") || sPwd.isEmpty()) {
                text = "00";
                text2 = DEFAULT_PIN;
            } else {
                text = "01";
                text2 = sPwd.substring(0, 4);
            }
            StringBuilder text3 = new StringBuilder(text + BaseFunction.ConverToStr(hex(Integer.parseInt(sEmployeeCode))) + text2 + sPer);
            for (int i = sPer.length(); i < 56; i++) {
                text3.append('0');
            }
            if (!fnbUpDateEFBinFile(operate, text3.toString(), SFI_GENERAL_DATA, IssueID, ASN, key)) {
                throw new IllegalStateException("更新个人信息错误");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Sélection de l'application et lecture des informations publiques ; Account = numéro de compte selon le type de carte. */
    private static boolean Init(Operate operate, UserPublicData[] userOut, String[] Account) {
        UserPublicData user = new UserPublicData();
        userOut[0] = user;
        Account[0] = "";
        String[] recv = {""};
        operate.ScardCommand(cmdSelectEtAID, recv);
        operate.ScardCommand(cmdReadPublicInfo, recv);
        String r = recv[0];
        if (r.length() > 56) {
            user.IssueID = r.substring(0, 16);
            user.App_Kind = r.substring(16, 18);
            user.Version = r.substring(18, 20);
            user.ASN = r.substring(20, 40);
            user.Start_Date = r.substring(40, 48);
            user.Valid_Date = r.substring(48, 52) + "-" + r.substring(52, 54) + "-" + r.substring(54, 56);
            user.Issue_FCI = r.substring(56, 58);
            switch (user.ASN.substring(4, 6)) {
                case "04" -> Account[0] = "20" + user.ASN.substring(12, 20);
                case "01" -> Account[0] = "21" + user.ASN.substring(12, 20);
                case "11" -> Account[0] = "22" + user.ASN.substring(12, 20);
                case "12" -> Account[0] = "23" + user.ASN.substring(12, 20);
                case "13" -> Account[0] = "24" + user.ASN.substring(12, 20);
                default -> {
                }
            }
            return true;
        }
        return false;
    }

    private static boolean scVerifyPin(Operate operate, String sPin) {
        return operate.ScardCommand(CreateCommand(cmdVerifyPIN, sPin), new String[1]).equals("9000");
    }

    private static String CreateCommand(String Header, int Le) {
        return Header + BaseFunction.ConverToStr(hex(Le), 2);
    }

    private static String CreateCommand(String Header, String Data) {
        return Header + BaseFunction.ConverToStr(hex(Data.length() / 2)) + Data;
    }

    private static String CreateCommand(String Header, String Data, String cMAC) {
        return Header + BaseFunction.ConverToStr(hex(Data.length() / 2 + 4)) + Data + cMAC;
    }

    private static String CreateCommand(String Header, String Data, int Le) {
        return Header + BaseFunction.ConverToStr(hex(Data.length() / 2)) + Data + BaseFunction.ConverToStr(hex(Le), 2);
    }

    private static boolean ReadKey(String inKey, String[] OutKey) {
        try {
            OutKey[0] = BaseFunction.DES3_Decrypt(tempKey(), inKey);
            return true;
        } catch (RuntimeException e) {
            OutKey[0] = "";
            return false;
        }
    }

    private static String GetPersonalString(String UserName, String ID, String IDType) {
        String s = CARDIDENTIFY;
        String text = BaseFunction.ConvertToHex(UserName);
        while (text.length() < 40) {
            text = "20" + text;
        }
        s += text;
        text = BaseFunction.ConvertToHex(ID);
        while (text.length() < 36) {
            text = "20" + text;
        }
        s += text;
        return s + "0" + IDType.substring(IDType.length() - 1);
    }

    private static boolean fnbUpDateEFBinFile(Operate operate, String IsData, String IsSFI, String IsBIN, String IsASN, String Key) {
        try {
            String apdu = "";
            if (IsSFI.equals(SFI_GENERAL_DATA) || IsSFI.equals(SFI_GENERAL_DATA_LC)) {
                apdu = CreateCommand(cmdUpdateGeneralFile, IsData);
            } else {
                String text = BaseFunction.ConverToStr(hex(IsData.length() / 2 + 4));
                String text2 = "04D6" + IsSFI + "00" + text;
                String[] ORandom = {""};
                if (!fnbGetChanllenge(operate, ORandom)) {
                    throw new IllegalStateException("");
                }
                String[] OsMAC = {""};
                fnbMAC_3DES(Key, ORandom[0], text2 + IsData, OsMAC, 4);
                if (IsSFI.equals(SFI_PUBLIC_DATA)) {
                    apdu = CreateCommand(cmdUpdatePublicFile, IsData, OsMAC[0]);
                } else if (IsSFI.equals(SFI_BASIC_DATA)) {
                    apdu = CreateCommand(cmdUpdateBasicFile, IsData, OsMAC[0]);
                } else if (IsSFI.equals(SFI_SECRECT_DATA)) {
                    apdu = CreateCommand(cmdUpdateSecretFile, IsData, OsMAC[0]);
                }
            }
            return operate.ScardCommand(apdu, new String[1]).equals("9000");
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean fnbGetChanllenge(Operate operate, String[] ORandom) {
        String[] recv = {""};
        if (operate.ScardCommand(cmdGetChallenge, recv).equals("9000")) {
            ORandom[0] = recv[0] + "00000000";
            return true;
        }
        ORandom[0] = "";
        return false;
    }

    public static boolean fnbGetChanllenge(Operate operate, String[] ORandom, int bytLen) {
        ORandom[0] = "";
        try {
            String[] recv = {""};
            if (!operate.ScardCommand(CreateCommand(BaseFunction.cmdGetChallenge, bytLen), recv).equals("9000")) {
                throw new IllegalStateException("66");
            }
            ORandom[0] = bytLen == 4 ? recv[0].substring(0, recv[0].length() - 4) + "00000000" : recv[0];
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ fabrication des cartes (CardMake)

    /** Échange de clés GlobalPlatform (SCP02) avec la carte vierge, puis création de l'application PBOC. */
    public static boolean ExtAuthorizeForHD_Java(Operate operate, String key) {
        try {
            String[] r = {""};
            if (!operate.ScardCommand("00a4040007A000000003000000", r).equals("9000")) {
                throw new IllegalStateException("33");
            }
            if (!operate.ScardCommand("8050000008D4DCA09B01A881B5", r).equals("9000")) {
                throw new IllegalStateException("33");
            }
            String text2 = "D4DCA09B01A881B5";
            String text3 = r[0].substring(24, 40);
            String text4 = text3.substring(0, 4);
            String data = "0182" + text4 + "000000000000000000000000";
            String data2 = "0101" + text4 + "000000000000000000000000";
            String[] OsMac = {""};
            String[] OsMac2 = {""};
            fnb3Des_CBC(key, data, OsMac);
            fnb3Des_CBC(key, data2, OsMac2);
            StringBuilder text5 = new StringBuilder(text3 + text2 + "80");
            int num = 16 - text5.length() % 16;
            for (int i = 0; i < num; i++) {
                text5.append('0');
            }
            String[] OsMac3 = {""};
            fnb3Des_CBC(OsMac[0], text5.toString(), OsMac3);
            String mac3 = OsMac3[0].substring(OsMac3[0].length() - 16);
            String[] OsMAC = {""};
            fnbMAC_3DES(OsMac2[0], "0000000000000000", "8482000010" + mac3, OsMAC, 8);
            String text6 = ("8482000010" + mac3 + OsMAC[0]).toUpperCase();
            if (!operate.ScardCommand(text6, r).equals("9000")) {
                throw new IllegalStateException("33");
            }
            if (!operate.ScardCommand("80E40080104F0E315041592E5359532E4444463031", r).equals("9000")) {
                operate.ScardCommand("80E40000104F0E315041592E5359532E4444463031", r);
            }
            if (!operate.ScardCommand("80E60C003009A000525941505030320AA00052594150503032010E315041592E5359532E4444463031010408C9063F00041F00000000", r).equals("9000")) {
                throw new IllegalStateException("80");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void cmd(Operate operate, String apdu, String err) {
        if (!operate.ScardCommand(apdu, new String[1]).equals("9000")) {
            throw new IllegalStateException(err);
        }
    }

    /** Création des applications SINOPEC et LOYALTY et écriture des clés (lève une exception avec le code d'erreur d'origine). */
    public static boolean CreateDF_DH_java(Operate operate, String sTransferKey, String sPublicInfo, String[] sKeyList) {
        cmd(operate, "00A404000e315041592E5359532E4444463031", "7");
        cmd(operate, "80DA9F0F10" + sTransferKey, "7");
        if (!ExtAuthorizeDH(operate, sTransferKey)) {
            throw new IllegalStateException("65");
        }
        cmd(operate, "80DA9F04176F15840E315041592E5359532E4444463031A503880101", "79");
        cmd(operate, "80E0000008EF011C0200000000", "71");
        cmd(operate, "00DC010C10610E4F0CA00000000353494E4F504543", "79");
        cmd(operate, "00DC020C10610E4F0CA0000000034C4F59414C5459", "79");
        cmd(operate, "80E0000010DF01390CA00000000353494E4F504543", "73");
        cmd(operate, "80E0000010DF02390CA0000000034C4F59414C5459", "73");
        for (String aid : new String[] {"00A404000CA00000000353494E4F504543", "00A404000CA0000000034C4F59414C5459"}) {
            boolean sinopec = aid.endsWith("504543");
            cmd(operate, aid, sinopec ? "73" : "7");
            cmd(operate, "80DA9F0F10" + sTransferKey, "7");
            if (!ExtAuthorizeDH(operate, sTransferKey)) {
                throw new IllegalStateException("65");
            }
            cmd(operate, "80DA9F02020010", "79");
            cmd(operate, sinopec ? "80DA9F04196F35840CA00000000353494E4F504543A5259F0801029F0C1E"
                : "80DA9F04196F35840CA0000000034C4F59414C5459A5259F0801029F0C1E", "79");
            cmd(operate, "80E0000008EF1528001E000021", "79");
            cmd(operate, "80E0000008EF16280029000021", "79");
            cmd(operate, "80E0000008EF1B280020000200", "79");
            cmd(operate, "80E0000008EF182E0A1702FF00", "79");
            cmd(operate, "80E0000008EF1C280060000021", "79");
            cmd(operate, "80E0000008EF1D280040000021", "79");
            cmd(operate, "805E0100051234FF9999", "79");
            if (!WriteKeyFileDH_Java(operate, sKeyList, sTransferKey)) {
                throw new IllegalStateException("71");
            }
            cmd(operate, "00200000029999", "79");
            if (!WriteInfoFileDH_Java(operate, sPublicInfo, sKeyList[1])) {
                throw new IllegalStateException("69");
            }
            cmd(operate, "80DA9F050202FD", "79");
        }
        cmd(operate, "00A40000023F00", "7");
        if (!ExtAuthorizeDH(operate, sTransferKey)) {
            throw new IllegalStateException("65");
        }
        cmd(operate, "80DA9F050202FD", "79");
        return true;
    }

    private static boolean WriteInfoFileDH_Java(Operate operate, String sPublicInfo, String AMKey) {
        try {
            for (int i = 0; i < 4; i++) {
                String[] ORandom = {""};
                if (!fnbGetChanllenge(operate, ORandom)) {
                    throw new IllegalStateException("95");
                }
                String text2 = cmdWriteInfosDH_Java[i] + switch (i) {
                    case 0 -> sPublicInfo;
                    case 1 -> BaseFunction.CON_USERINFO;
                    case 2 -> BaseFunction.CON_GENERALINFO;
                    default -> BaseFunction.CON_SENSITIVITYINFO;
                };
                if (i != 2) {
                    String[] OsMAC = {""};
                    if (!fnbMAC_3DES(AMKey, ORandom[0], text2, OsMAC, 4)) {
                        throw new IllegalStateException("MAC 错误");
                    }
                    text2 += OsMAC[0];
                }
                if (!operate.ScardCommand(text2, new String[1]).equals("9000")) {
                    throw new IllegalStateException("68");
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Ordre d'écriture des clés de la liste de Center (index des 11 clés de l'application). */
    private static final int[] KEY_ORDER = {1, 5, 4, 6, 11, 7, 9, 8, 10, 15, 0};

    private static boolean WriteKeyFileDH_Java(Operate operate, String[] sKeyList, String sTransferKey) {
        try {
            for (int i = 0; i < 11; i++) {
                String[] ORandom = {""};
                if (!fnbGetChanllenge(operate, ORandom)) {
                    throw new IllegalStateException("95");
                }
                String text = BaseFunction.DES3_Encrypt(sTransferKey, cmdDHWriteKeys_Java[i] + sKeyList[KEY_ORDER[i]]);
                String[] OsMAC = {""};
                if (!fnbMAC_3DES(sTransferKey, ORandom[0], cmdDHWriteKeysHead_Java + text, OsMAC, 4)) {
                    throw new IllegalStateException("MAC错误");
                }
                if (!operate.ScardCommand(cmdDHWriteKeysHead_Java + text + OsMAC[0], new String[1]).equals("9000")) {
                    throw new IllegalStateException("70");
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ExtAuthorizeDH(Operate operate, String sTransferKey) {
        try {
            String[] ORandom = {""};
            if (!fnbGetChanllenge(operate, ORandom, 8)) {
                throw new IllegalStateException("95");
            }
            String[] OsDataOut = {""};
            if (!BaseFunction.fnbTripleDESEncrypt(sTransferKey, ORandom[0], OsDataOut)) {
                throw new IllegalStateException("");
            }
            return operate.ScardCommand(CreateCommand(BaseFunction.cmdExtAuthorize, OsDataOut[0]), new String[1]).equals("9000");
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean WriteKeyFileDH(Operate operate, String[] sList, String sMDMK) {
        try {
            for (int i = 0; i < 11; i++) {
                String[] ORandom = {""};
                if (!fnbGetChanllenge(operate, ORandom, 8)) {
                    throw new IllegalStateException("95");
                }
                String text = BaseFunction.DES3_Encrypt(sMDMK, cmdDHWriteKeys[i] + sList[KEY_ORDER[i]]);
                String[] OsMAC = {""};
                if (!fnbMAC_3DES(sMDMK, ORandom[0], cmdDHWriteKeysHead[i] + text, OsMAC, 4)) {
                    throw new IllegalStateException("");
                }
                if (!operate.ScardCommand(cmdDHWriteKeysHead[i] + text + OsMAC[0], new String[1]).equals("9000")) {
                    throw new IllegalStateException("70");
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean WriteInfoFileDH(Operate operate, String sPublicInfo) {
        for (int i = 0; i < 4; i++) {
            String text = cmdWriteInfosDH[i] + switch (i) {
                case 0 -> sPublicInfo;
                case 1 -> BaseFunction.CON_USERINFO;
                case 2 -> BaseFunction.CON_GENERALINFO;
                default -> BaseFunction.CON_SENSITIVITYINFO;
            };
            if (!operate.ScardCommand(text, new String[1]).equals("9000")) {
                return false;
            }
        }
        return true;
    }

    public static boolean DeleteMF(Operate operate, String sTransferKey, int mode) {
        try {
            String[] r = {""};
            if (operate.ScardCommand("00A4040008A000000003000000", r).equals("9000")) {
                if (!ExtAuthorizeForHD_Java(operate, "404142434445464748494A4B4C4D4E4F")) {
                    throw new IllegalStateException("33");
                }
                return true;
            }
            cmd(operate, "00CA9F7F0D", "6");
            cmd(operate, BaseFunction.cmdSelectByFID, "7");
            if (mode == 0 && !operate.ScardCommand("00A4000002EFA0", r).equals("9000")) {
                return true;
            }
            if (!ExtAuthorizeDH(operate, sTransferKey)) {
                throw new IllegalStateException("96");
            }
            if (!fnbGetChanllenge(operate, new String[1], 8)) {
                throw new IllegalStateException("95");
            }
            cmd(operate, "80E40000081111111111111111", "33");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean fnbInitForLoad(Operate operate, String IsInitData, String[] recv) {
        return operate.ScardCommand(CreateCommand(cmdInitForLoad, IsInitData, 16), recv).equals("9000");
    }

    private static boolean fnbCreditForLoad(Operate operate, String IsCreditData) {
        return operate.ScardCommand(CreateCommand(cmdCreditForLoad, IsCreditData, 4), new String[1]).equals("9000");
    }

    private static boolean fnbInitForPurchase(Operate operate, String IsData, String[] recv) {
        return operate.ScardCommand(CreateCommand(cmdInitForPurchase, IsData, 15), recv).equals("9000");
    }

    private static boolean fnbDebitForPurchase(Operate operate, String IsData) {
        return operate.ScardCommand(CreateCommand(cmdDebitForPurchase, IsData, 8), new String[1]).equals("9000");
    }

    // ------------------------------------------------------------ MAC et chiffrement

    private static String padMac(String data) {
        StringBuilder text2 = new StringBuilder(data + DESCONST);
        int num = text2.length() % 16;
        if (num > 0) {
            for (int i = 0; i < 16 - num; i++) {
                text2.append('0');
            }
        }
        return text2.toString();
    }

    private static String chainDes(String data, String init, String key) {
        String text = init.substring(0, 16);
        int num = data.length() / 16;
        for (int j = 0; j < num; j++) {
            byte[] a = SerialClass.strToToHexByte(data.substring(16 * j, 16 * j + 16));
            byte[] b = SerialClass.strToToHexByte(text);
            for (int k = 0; k < 8; k++) {
                a[k] ^= b[k];
            }
            text = BaseFunction.Encrypt_DES16(SerialClass.byteToHexStr(a), key);
        }
        return text;
    }

    public static boolean fnbMAC_3DES(String IsKey, String IsInitValue, String IsDataIn, String[] OsMAC, int len) {
        try {
            String k1 = IsKey.substring(0, 16);
            String k2 = IsKey.substring(16, 32);
            String text = chainDes(padMac(IsDataIn), IsInitValue, k1);
            text = BaseFunction.Decrypt_DES16(text, k2);
            text = BaseFunction.Encrypt_DES16(text, k1);
            OsMAC[0] = text.substring(0, len * 2);
            return true;
        } catch (RuntimeException e) {
            OsMAC[0] = "";
            return false;
        }
    }

    private static boolean fnbMAC_DES(String IsKey, String IsInitValue, String IsDataIn, String[] OsMAC) {
        OsMAC[0] = "";
        try {
            OsMAC[0] = chainDes(padMac(IsDataIn), IsInitValue, IsKey.substring(0, 16)).substring(0, 8);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean ExtAuthorizeForFlying(Operate operate) {
        try {
            String text = "404142434445464748494A4B4C4D4E4F";
            String[] r = {""};
            cmd(operate, "00A4040000", "33");
            cmd(operate, "00A4040008A000000003000000", "33");
            if (!operate.ScardCommand("8050000008D4DCA09B01A881B5", r).equals("9000")) {
                throw new IllegalStateException("33");
            }
            String text3 = "D4DCA09B01A881B5";
            String text4 = r[0].substring(24, 40);
            String text5 = text4.substring(0, 4);
            String[] OsMac = {""};
            String[] OsMac2 = {""};
            if (!fnb3Des_CBC(text, "0182" + text5 + "000000000000000000000000", OsMac)) {
                throw new IllegalStateException("数据出错");
            }
            if (!fnb3Des_CBC(text, "0101" + text5 + "000000000000000000000000", OsMac2)) {
                throw new IllegalStateException("数据出错");
            }
            StringBuilder text6 = new StringBuilder(text4 + text3 + "80");
            int num = text6.length() % 16;
            for (int i = 0; i < 16 - num; i++) {
                text6.append('0');
            }
            String[] OsMac3 = {""};
            fnb3Des_CBC(OsMac[0], text6.toString(), OsMac3);
            String mac3 = OsMac3[0].substring(OsMac3[0].length() - 16);
            String[] OsMAC = {""};
            if (!fnbMAC_3DES(OsMac2[0], "0000000000000000", "8482000010" + mac3, OsMAC, 8)) {
                throw new IllegalStateException("数据出错");
            }
            cmd(operate, "8482000010" + mac3 + OsMAC[0], "初始化出错");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean DeleteForFlying(Operate operate) {
        try {
            String[] array = {"80E40080104F0E315041592E5359532E4444463031", "80E400800E4F0CA00000000353494E4F504543",
                "80E400800E4F0CA0000000034C4F59414C5459"};
            for (int i = 0; i < 3; i++) {
                operate.ScardCommand(array[i % 3], new String[1]);
                // array[i % 4] de l'original : même élément pour i < 3
                operate.ScardCommand(array[i % 4], new String[1]);
            }
            cmd(operate, "80E60C002708D15600013203220108D1560001328322010E315041592E5359532E4444463031010402C90000", "80");
            cmd(operate, "80E60C002908D15600013203220108D1560001328322010CA00000000353494E4F504543010006C90455AAE0B000", "73");
            cmd(operate, "80E60C002908D15600013203220108D1560001328322010CA0000000034C4F59414C5459010006C90455AAE0B000", "75");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean NewDeleteForFlying(Operate operate) {
        try {
            String[] array = {"80E40000104F0E315041592E5359532E4444463031", "80E400000E4F0CA00000000353494E4F504543",
                "80E400000E4F0CA0000000034C4F59414C5459", "80E40000124F10A00000000353494E4F504543FFFFFFFF"};
            for (int i = 0; i < 4; i++) {
                operate.ScardCommand(array[i], new String[1]);
                operate.ScardCommand(array[i], new String[1]);
            }
            cmd(operate, "80E60C002708D15600013203220108D1560001328322010E315041592E5359532E4444463031010402C90000", "80");
            cmd(operate, "80E60C002908D15600013203220108D1560001328322010CA00000000353494E4F504543010006C90455AAE0B000", "73");
            cmd(operate, "80E60C002908D15600013203220108D1560001328322010CA0000000034C4F59414C5459010006C90455AAE0B000", "75");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean fnb3Des_CBC(String key, String data, String[] OsMac) {
        OsMac[0] = "";
        try {
            int length = data.length();
            if (key.length() != 32 || length % 16 != 0 || length == 0) {
                throw new IllegalStateException("Error");
            }
            String[] out = {""};
            if (!BaseFunction.fnbTripleDESEncrypt(key, fnsEOR("0000000000000000", data.substring(0, 16)), out)) {
                throw new IllegalStateException("1");
            }
            StringBuilder text = new StringBuilder(out[0]);
            for (int i = 1; i < length / 16; i++) {
                BaseFunction.fnbTripleDESEncrypt(key, fnsEOR(out[0], data.substring(16 * i, 16 * i + 16)), out);
                text.append(out[0]);
            }
            OsMac[0] = text.toString();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean fnbTAC(String IsKey, String IsInitValue, String IsDataIn, String[] OsTAC) {
        return fnbMAC_DES(fnsEOR(IsKey.substring(0, 16), IsKey.substring(IsKey.length() - 16)), IsInitValue, IsDataIn, OsTAC);
    }

    private static boolean fnbReloadPIN(Operate operate, String IsReloadData) {
        return operate.ScardCommand(CreateCommand(cmdReloadPIN, IsReloadData), new String[1]).equals("9000");
    }

    private static boolean fnbReloadPINLong(Operate operate, String password, String key) {
        try {
            String[] r = {""};
            operate.ScardCommand("0084000008", r);
            String text = "805e000006" + password;
            String[] OsMAC = {""};
            fnbMAC_3DES(key, r[0], text, OsMAC, 4);
            return operate.ScardCommand(text + OsMAC[0], r).equals("9000");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String fnsEOR(String a, String b) {
        byte[] x = SerialClass.strToToHexByte(a);
        byte[] y = SerialClass.strToToHexByte(b);
        byte[] z = new byte[8];
        for (int i = 0; i < 8; i++) {
            z[i] = (byte) (x[i] ^ y[i]);
        }
        return SerialClass.byteToHexStr(z);
    }

    /** Lecteurs PC/SC présents (diagnostic du matériel). */
    public static List<String> readers() {
        String[] r = new Operate().GetReaders();
        return r == null ? List.of() : new ArrayList<>(List.of(r));
    }
}
