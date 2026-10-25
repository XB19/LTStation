package com.ltstation.center.sw.forms;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.hw.CardWrite;
import com.ltstation.center.sw.hw.Operate;
import java.util.ArrayList;
import java.util.List;

/**
 * CreatePSAM : initialisation d'une carte PSAM (ACS, BSky, LYZN) avec les clés de la base.
 * L'original lance MakePSAM dans un thread derrière la fenêtre ProBar ; ici la requête attend la fin.
 */
@SwWindow("CreatePSAM")
public class CreatePSAM extends SwForm {

    private final Operate operate = new Operate();
    private String CardSelected = "";
    private String sTransferKey = "";

    private void Init() {
        c("CardTypeSelect").SelectedIndex(0);
        c("ACS").Checked(true);
    }

    private boolean GetReader() {
        c("Reader").ItemsClear();
        String[] readers = operate.GetReaders();
        if (readers != null) {
            for (String r : readers) {
                c("Reader").ItemsAdd(r);
            }
            if (readers.length == 0) {
                throw new IllegalStateException("InvalidArgument=Value of '0' is not valid for 'SelectedIndex'.");
            }
            c("Reader").SelectedIndex(0);
            return true;
        }
        MessageBox(L("CardReaderDisconnect"));
        return false;
    }

    private void MakePSAM() {
        try {
            List<String> list = new ArrayList<>();
            if (!CardWrite.ReadKeysFromDB(list)) {
                throw new IllegalStateException(L("GetSecretError"));
            }
            String[] keys = list.toArray(new String[0]);
            switch (CardSelected) {
                case "ACS" -> {
                    if (!BuildPsam4(sTransferKey, keys)) {
                        throw new IllegalStateException(L("CardMakeError"));
                    }
                }
                case "BSky" -> {
                    sTransferKey = "000102030405060708090A0B0C0D0E0F";
                    if (!BuildPSAMFlying(sTransferKey, keys)) {
                        throw new IllegalStateException(L("CardMakeError"));
                    }
                }
                case "LYZN" -> {
                    sTransferKey = IniInfo.PsamTransferKeyLYZN;
                    if (!CardWrite.DeleteMF(operate, sTransferKey, 1)) {
                        throw new IllegalStateException("83");
                    }
                    if (!BuildPsamDH(sTransferKey, keys)) {
                        throw new IllegalStateException("80");
                    }
                }
                default -> {
                }
            }
            MessageBox(L("CardMakeSuccess"));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void CardMake_Click() {
        try {
            if (!operate.SCardConnect(c("Reader").Text())) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            sTransferKey = c("Encrypt").Text();
            if (sTransferKey.length() != 32) {
                throw new IllegalStateException("61");
            }
            CardSelected = "";
            for (String n : Controls("CardRadio")) {
                if (TypeOf(n).endsWith("RadioButton") && c(n).Checked()) {
                    CardSelected = n;
                }
            }
            MakePSAM();
        } catch (RuntimeException ex) {
            operate.SCardDisconnect();
            MessageBox(ex.getMessage());
        }
    }

    /** Envoie la commande ; lève l'erreur donnée si le mot d'état n'est pas 9000. */
    private String cmd(String apdu, String error) {
        String[] recv = {""};
        if (!"9000".equals(operate.ScardCommand(apdu, recv))) {
            throw new IllegalStateException(error);
        }
        return recv[0];
    }

    private static final String[] PSAM4 = {
        "00 30 00 00 00", "80 E0 3F 00 16 38 FF FF FF FF 1E FF FF 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31", "80 E0 EF 1E 07 28 00 03 FF FF FF FF",
        "00 D6 00 00 03 88 01 01", "80 E0 00 01 07 2C 00 40 00 51 FF FF",
        "00 E2 00 00 17 01 15 70 13 61 11 4F 0D A0 00 00 00 03 53 49 4E 4F 50 45 43 31 50 00",
        "00 E2 00 00 17 02 15 70 13 61 11 4F 0D A0 00 00 00 03 53 49 4E 4F 50 45 43 32 50 00", "80 E0 EF 01 07 3F 02 15 FF FF FF FF",
        "00 E2 00 00 15 00 33 00 00 00 ", "00 E2 00 00 15 01 33 01 00 00 ",
        "80 E0 EF 15 07 28 00 0E 00 51 FF FF", "00 D6 00 00 0E 01 00 10 11 00 00 00 00 00 01 01 01 01 01", "80 E0 EF 16 07 28 00 06 00 51 FF FF",
        "00 D6 00 00 06 13 10 00 00 00 01", "80 E0 DF 01 15 38 06 00 FF FF 17 FF FF A0 00 00 00 03 53 49 4E 4F 50 45 43 31",
        "80 E0 EF 17 07 28 00 19 00 51 FF FF", "00 D6 00 00 19 01 10 FF FF FF FF FF FF FF 35 FF FF FF FF FF FF FF 20 13 06 01 20 99 12 31",
        "80 E0 EF 11 0B 29 00 04 00 00 FF FF 00 00 00 00", "80 E0 EF 01 07 3F 05 15 FF FF FF FF", "00 E2 00 00 15 42 33 01 00 00 ",
        "00 E2 00 00 15 01 33 01 00 00 ", "00 E2 00 00 15 15 33 01 00 00 ", "00 E2 00 00 15 28 33 01 00 00",
        "00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31",
        "80 E0 DF 02 15 38 06 00 FF FF 17 FF FF A0 00 00 00 03 53 49 4E 4F 50 45 43 32", "80 E0 EF 17 07 28 00 19 00 51 FF FF",
        "00 D6 00 00 19 01 10 FF FF FF FF FF FF FF 35 FF FF FF FF FF FF FF 20 13 06 01 20 99 12 31",
        "80 E0 EF 11 0B 29 00 04 00 00 FF FF 00 00 00 00", "80 E0 EF 01 07 3F 05 15 FF FF FF FF", "00 E2 00 00 15 42 33 01 00 00 ",
        "00 E2 00 00 15 01 33 01 00 00 ", "00 E2 00 00 15 15 33 01 00 00", "00 E2 00 00 15 28 33 01 00 00"
    };

    private boolean BuildPsam4(String sTransferKey, String[] sKeyList) {
        try {
            for (int i = 0; i < 33; i++) {
                String text = switch (i) {
                    case 8 -> PSAM4[i] + sTransferKey;
                    case 19, 29 -> PSAM4[i] + sKeyList[6];
                    case 9, 20, 30, 22, 32 -> PSAM4[i] + sKeyList[1];
                    case 21, 31 -> PSAM4[i] + sKeyList[17];
                    default -> PSAM4[i];
                };
                cmd(text.replace(" ", ""), L("InitializeError"));
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Commande sécurisée : défi de 4 octets, MAC 3DES sur la commande, puis envoi commande + MAC. */
    private void macCmd(String key, String apdu) {
        String random = cmd("0084000004", "66");
        String[] mac = {""};
        CardWrite.fnbMAC_3DES(key, random + "00000000", apdu, mac, 8);
        cmd(apdu + mac[0].substring(0, 8), "203");
    }

    private boolean BuildPSAMFlying(String sTransferKey, String[] sKeyList) {
        try {
            macCmd(sTransferKey, "840E3F0004");
            macCmd(sTransferKey, "84E00000243F0070FF010F010F010F010F01FFFFFFFF0E315041592E5359532E4444463031");
            cmd("80E00000110000050A18CC011A010F010F01FFFFFFFF", "203");
            cmd("80D4000018 0001010F0FFF0010" + sTransferKey, "71");
            cmd("80D4000018 01011090FF330010" + sKeyList[1], "71");
            cmd("80E0000011 EF1500000E0000000000000000FFFFFFFF", "203");
            cmd("00D695000E 0100101100000000000101010101", "203");
            cmd("80E0000011EF160000060000000000000000FFFFFFFF", "203");
            cmd("00D6960006131000000001", "203");
            macCmd(sTransferKey, "84E00000233F0190FF010F010F010F010F01FFFFFFFF0DA00000000353494E4F50454331");
            cmd("80E0000011EF170000190000000000000000FFFFFFFF", "203");
            cmd("00D6970019 0110FFFFFFFFFFFFFF35FFFFFFFFFFFFFF2013060120991231", "203");
            cmd("80E00000110000050C18CC011A010F010F01FFFFFFFF", "203");
            cmd("80D40000180001010F0FFF0010" + sTransferKey, "71");
            cmd("80D40000184201010F0FFF0110" + sKeyList[6], "71");
            cmd("80D400001801011090FF330010" + sKeyList[1], "71");
            cmd("80D40000181201010FFF330010" + sKeyList[17], "71");
            cmd("80D40000182801000000000010" + sKeyList[1], "71");
            cmd("80ED000000", "71");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void auth(String sTransferKey) {
        if (!CardWrite.ExtAuthorizeDH(operate, sTransferKey)) {
            throw new IllegalStateException("65");
        }
    }

    /** Application SINOPEC (DF01 ou DF02) d'une PSAM LYZN : fichiers, clés et données. */
    private void appDH(String sTransferKey, String[] sKeyList, String create, String select) {
        cmd(create, "79");
        cmd(select, "79");
        auth(sTransferKey);
        for (String apdu : new String[] {"80F5000009FE018C001015000040", "80F501000AEF176000250000000000",
            "00D69700190110FFFFFFFFFFFFFF35FFFFFFFFFFFFFF2019120120991231", "80F502000AEF186000040000010000", "80F502000AFF0178000C00FFFF0000",
            "80F502000AFF026000020000000000", "80F502000AFF0360000800FFFF0000", "00A4000002FF02", "00D60000020A0A", "0084000008",
            "80F10000081111111111111111"}) {
            cmd(apdu, "79");
        }
        if (!WriteKeyFileDH_PSAM(sKeyList, sTransferKey)) {
            throw new IllegalStateException("31");
        }
    }

    private boolean BuildPsamDH(String sTransferKey, String[] sKeyList) {
        try {
            cmd("00A40000023F00", "7");
            auth(sTransferKey);
            cmd("80F5000009FE028C000A15000040", "79");
            cmd("80F501000aEF1560000E0000000000", "79");
            cmd("00D695000E0100101100000000000101010101", "79");
            cmd("80F501000a00166000060000000000", "79");
            cmd("00D6960006131000000001", "79");
            appDH(sTransferKey, sKeyList, "80E60800153F0120000100002DA00000000353494E4F50454331", "00A404000DA00000000353494E4F50454331");
            cmd("00A40000023F00", "7");
            auth(sTransferKey);
            appDH(sTransferKey, sKeyList, "80E60800153F0220000100002DA00000000353494E4F50454332", "00A404000DA00000000353494E4F50454332");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean WriteKeyFileDH_PSAM(String[] sList, String sMDMK) {
        try {
            for (int i = 0; i < 4; i++) {
                String[] random = {""};
                if (!CardWrite.fnbGetChanllenge(operate, random, 4)) {
                    throw new IllegalStateException("95");
                }
                String isDataIn = BaseFunction.cmdDHWriteKeys_PSAM[i] + switch (i) {
                    case 0, 1 -> sList[6];
                    case 2 -> sList[17];
                    default -> sList[1];
                };
                String text = BaseFunction.DES3_Encrypt(sMDMK, isDataIn);
                String[] mac = {""};
                CardWrite.fnbMAC_3DES(sMDMK, random[0] + BaseFunction.cmdDH_PSAMRANDOMPADDING, BaseFunction.cmdDHWriteKeys_PSAMHEAD + text, mac, 4);
                cmd(BaseFunction.cmdDHWriteKeys_PSAMHEAD + text + mac[0], "70");
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void CreatePSAM_Load() {
        try {
            if (!GetReader()) {
                throw new IllegalStateException(L("CardReaderDisconnect"));
            }
            Init();
        } catch (RuntimeException e) {
            Close();
        }
    }
}
