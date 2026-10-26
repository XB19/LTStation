package com.ltstation.center.sw.hw;

import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;

/**
 * Personnalisation des cartes utilisateur (structure des fichiers et clés), commune à CardMake et CardChange
 * dans l'original (WriteDataToACS / WriteDataFlying / WriteDataToDHCard, séquences APDU identiques).
 * Les méthodes s'arrêtent avant WriteCard, propre à chaque fenêtre.
 */
public final class CardIssue {

    private CardIssue() {
    }

    private static String cmd(Operate operate, String apdu, String error) {
        String[] recv = {""};
        if (!"9000".equals(operate.ScardCommand(apdu, recv))) {
            throw new IllegalStateException(error);
        }
        return recv[0];
    }

    private static final String[] ACS = {
        "00 30 00 00 00", "00 E0 3F 00 13 3F 00 1E FF FF 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31", "00 E0 01 00 07 EF 1E FF FF FF 00 03",
        "00 D6 00 00 03 88 01 01", "00 E0 04 01 07 EF 01 00 50 FF 00 40", "00 E2 00 00 16 01 14 70 12 61 10 4F 0C A0 00 00 00 03 53 49 4E 4F 50 45 43 50 00",
        "00 E2 00 00 16 02 14 70 12 61 10 4F 0C A0 00 00 00 03 4C 4F 59 41 4C 54 59 50 00", "00 E0 38 00 11 DF 01 1E 15 FF A0 00 00 00 03 53 49 4E 4F 50 45 43",
        "00 E0 01 00 07 EF 1E FF FF FF 00 04", "00 D6 00 00 04 9F 08 01 02",
        "00 E0 01 15 07 EF 01 00 50 FF 00 1E", "00 D6 00 00 1E", "00 E0 01 16 07 EF 02 00 50 FF 00 29", "00 D6 00 00 29", "00 E0 01 1B 07 EF 03 00 10 FF 00 20",
        "00 D6 00 00 20", "00 E0 01 1C 07 EF 04 00 50 FF 00 60", "00 D6 00 00 60", "00 E0 01 1D 07 EF 0A 00 50 FF 00 20",
        "00 D6 00 00 20 FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF",
        "00 E0 0C 00 07 EF 05 FF FF FF 15 0B", "00 E2 00 00 15 01 FF 01 01 00", "00 E2 00 00 15 01 FF 02 01 00", "00 E2 00 00 15 02 FF 01 01 00",
        "00 E2 00 00 15 07 FF 01 01 00", "00 E2 00 00 15 0C FF 01 01 00", "00 E2 00 00 15 08 FF 01 01 00", "00 E2 00 00 15 03 FF 00 00 00",
        "00 E2 00 00 15 05 33 00 00 00", "00 E2 00 00 15 09 33 00 00 00",
        "00 E2 00 00 15 04 33 00 00 00", "00 E2 00 00 15 0A FF 01 00 00", "00 E0 0A 00 07 EF 06 FF FF FF 0A 01", "00 E2 00 00 0A 01 33 02 99 99 FF FF FF FF FF",
        "00 E0 0F 18 07 EF 07 16 F6 F6 18 0A", "00 E0 0D 00 07 EF 08 FF FF FF 16 01",
        "00 E2 00 00 16 00 00 00 00 00 01 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00", "00 E0 0B 00 07 EF 09 FF FF FF 28 01",
        "00 E2 00 00 28 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 33 00",
        "00 44 00 00 00",
        "00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31", "00 E0 38 00 11 DF 02 1E 15 FF A0 00 00 00 03 4C 4F 59 41 4C 54 59",
        "00 E0 01 00 07 EF 1E FF FF FF 00 04", "00 D6 00 00 04 9F 08 01 02", "00 E0 01 15 07 EF 01 00 50 FF 00 1E", "00 D6 00 00 1E",
        "00 E0 01 16 07 EF 02 00 50 FF 00 29", "00 D6 00 00 29", "00 E0 01 1B 07 EF 03 00 10 FF 00 20", "00 D6 00 00 20",
        "00 E0 01 1C 07 EF 04 00 50 FF 00 60", "00 D6 00 00 60", "00 E0 0C 00 07 EF 05 FF FF FF 15 0B", "00 E2 00 00 15 11 FF 01 01 00",
        "00 E2 00 00 15 11 FF 02 01 00", "00 E2 00 00 15 12 FF 01 01 00", "00 E2 00 00 15 17 FF 01 01 00", "00 E2 00 00 15 1C FF 01 01 00",
        "00 E2 00 00 15 18 FF 01 01 00", "00 E2 00 00 15 13 FF 00 00 00",
        "00 E2 00 00 15 05 33 00 00 00", "00 E2 00 00 15 09 33 00 00 00", "00 E2 00 00 15 04 33 00 00 00", "00 E2 00 00 15 0A FF 01 00 00",
        "00 E0 0A 00 07 EF 06 FF FF FF 0A 01", "00 E2 00 00 0A 01 33 02 99 99 FF FF FF FF FF", "00 E0 1F 18 07 EF 07 10 FF FF 15 0A",
        "00 E0 1D 00 07 EF 08 FF FF FF 4B 01",
        "00 E2 00 00 4B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 01",
        "00 E0 1B 00 07 EF 09 FF FF FF 2B 01",
        "00 E2 00 00 2B 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 33 00",
        "00 44 00 00 00"
    };

    /** Cartes ACS : 72 commandes ; comme l'original, le mot d'état n'est pas contrôlé. */
    public static void WriteDataToACS(Operate operate, String sPublicInfo, String[] sKeyList) {
        for (int i = 0; i < 72; i++) {
            String text = switch (i) {
                case 11, 45 -> ACS[i] + sPublicInfo;
                case 13, 47 -> ACS[i] + BaseFunction.CON_USERINFO;
                case 15, 49 -> ACS[i] + BaseFunction.CON_GENERALINFO;
                case 17, 51 -> ACS[i] + BaseFunction.CON_SENSITIVITYINFO;
                case 21, 53 -> ACS[i] + sKeyList[6];
                case 22, 54 -> ACS[i] + sKeyList[11];
                case 23, 55 -> ACS[i] + sKeyList[7];
                case 24, 56 -> ACS[i] + sKeyList[9];
                case 25, 57 -> ACS[i] + sKeyList[15];
                case 26, 58 -> ACS[i] + sKeyList[8];
                case 27, 59 -> ACS[i] + sKeyList[10];
                case 28, 60 -> ACS[i] + sKeyList[1];
                case 29, 61 -> ACS[i] + sKeyList[4];
                case 30, 62 -> ACS[i] + sKeyList[5];
                case 31, 63 -> ACS[i] + sKeyList[0];
                default -> ACS[i];
            };
            operate.ScardCommand(text.replace(" ", ""), new String[1]);
        }
    }

    /** Cartes BSky (Flying) ; lève le code d'erreur de l'original. */
    public static void WriteDataFlying(Operate operate, String sPublicInfo, String[] sKeyList) {
        String[] r = {""};
        cmd(operate, "00A404000E315041592E5359532E4444463031", "7");
        cmd(operate, "80E2000008910205A503880101", "80");
        cmd(operate, "80E200010A1F010720141128171457", "80");
        cmd(operate, "80E200021C01011961184F0CA00000000353494E4F504543500753494E4F504543", "80");
        cmd(operate, "80E200031C01021961184F0CA0000000034C4F59414C545950074C4F59414C5459", "80");
        cmd(operate, "00A404000CA00000000353494E4F504543", "11");
        cmd(operate, "80E200002A910227FFFFFFFFA5219F0C1E" + sPublicInfo, "80");
        cmd(operate, "80E000001100000510180000000000000000FFFFFFFF", "71");
        cmd(operate, "80D40000180001010F0FFF0010" + IniInfo.FlyingAppKey, "71");
        cmd(operate, "80D40000181101010F0FFF0010" + sKeyList[7], "71");
        cmd(operate, "80D40000181201010F0FFF0010" + sKeyList[10], "71");
        cmd(operate, "80D40000181001010F0FFF0010" + sKeyList[6], "71");
        cmd(operate, "80D40000181002010F0FFF0010" + sKeyList[11], "71");
        cmd(operate, "80D40000180D010A0F0F33000299990000000000000000000000000000", "71");
        cmd(operate, "80D40000181301010F0FFF0010" + sKeyList[9], "71");
        cmd(operate, "80D40000181501010F0FFF0010" + sKeyList[15], "71");
        cmd(operate, "80D40000181401010F0FFF0010" + sKeyList[8], "71");
        cmd(operate, "80D40000180101010F0F330010" + sKeyList[1], "71");
        cmd(operate, "80D40000180301010F0F330010" + sKeyList[4], "71");
        cmd(operate, "80D40000180401010F0F330010" + sKeyList[5], "71");
        operate.ScardCommand("80E00000110018030A171A00C00000000000FFFFFFFF", r);
        operate.ScardCommand("80E000001100010C03271A00000000000000FFFFFFFF", r);
        operate.ScardCommand("80E000001100160000290000500000000000FFFFFFFF", r);
        cmd(operate, "00D6960029" + BaseFunction.CON_USERINFO, "69");
        operate.ScardCommand("80E0000011001B000020000000003A000000FFFFFFFF", r);
        cmd(operate, "00D69B0020" + BaseFunction.CON_GENERALINFO, "69");
        operate.ScardCommand("80E0000011001500001E0000500000000000FFFFFFFF", r);
        cmd(operate, "00D695001E" + sPublicInfo, "69");
        operate.ScardCommand("80E0000011001C0000600000500000000000FFFFFFFF", r);
        cmd(operate, "00D69C0060" + BaseFunction.CON_SENSITIVITYINFO, "69");
        operate.ScardCommand("80E0000011001D0000200000500000000000FFFFFFFF", r);
        cmd(operate, "00D69D0020" + "FF".repeat(32), "69");
        operate.ScardCommand("80ED000000", r);
        cmd(operate, "00A404000CA00000000353494E4F504543", "11");
        cmd(operate, "80E200002A910227FFFFFFFFA5219F0C1E" + sPublicInfo, "80");
    }

    private static void auth(Operate operate, String sTransferKey) {
        if (!CardWrite.ExtAuthorizeDH(operate, sTransferKey)) {
            throw new IllegalStateException("65");
        }
    }

    /** Application d'une carte LYZN (AID SINOPEC ou LOYALTY) : fichiers, clés, informations publiques. */
    private static void appDH(Operate operate, String sTransferKey, String sPublicInfo, String[] sKeyList, String aid) {
        cmd(operate, "80E001000AEFA01C01FF0000000000", "79");
        cmd(operate, "00DC0104196F35840C" + aid + "A5259F0801029F0C1E", "79");
        for (String apdu : new String[] {"80E001000AEF1501001E4100000000", "80E001000AEF160100294100000000", "80E001000AEF1B0100230100000002",
            "80E001000AEF058A011D0000027FFF", "00DC012C1D" + "00".repeat(29), "80E001000AEF1089002800FFFFFFFF",
            "00D6900028" + "0001" + "00".repeat(38), "80E001000AEF18070A17000002FFFF", "80E001000AEF1C0100614100000000",
            "80E001000AEF1D0100404100000000", "80E001000BEF0122151900FFFFFFFF03", "80E001000BEF032A010F000000000000",
            "00DC011C0F0155000002029999FFFFFFFFFFFF54"}) {
            cmd(operate, apdu, "79");
        }
        if (!CardWrite.WriteKeyFileDH(operate, sKeyList, sTransferKey)) {
            throw new IllegalStateException("71");
        }
        if (!CardWrite.WriteInfoFileDH(operate, sPublicInfo)) {
            throw new IllegalStateException("69");
        }
        cmd(operate, "0084000008", "79");
        cmd(operate, "80F10000081111111111111111", "79");
    }

    /** Cartes LYZN (DH) ; lève le code d'erreur de l'original. */
    public static void WriteDataToDHCard(Operate operate, String sTransferKey, String sPublicInfo, String[] sKeyList) {
        String[] r = {""};
        if ("9000".equals(operate.ScardCommand("00A4040008A000000003000000", r))) {
            CardWrite.CreateDF_DH_java(operate, sTransferKey, sPublicInfo, sKeyList);
            return;
        }
        cmd(operate, "00A40000023F00", "7");
        auth(operate, sTransferKey);
        cmd(operate, "80E001000AEFA01C01FF0000000000", "79");
        cmd(operate, "00DC0104176F15840E315041592E5359532E4444463031A503880101", "79");
        cmd(operate, "80E001000BEF0022101900FFFFFFFF03", "71");
        cmd(operate, "80E001000AEF01440040000000FFFF", "71");
        cmd(operate, "00DC010C10610E4F0CA00000000353494E4F504543", "79");
        cmd(operate, "00DC020C10610E4F0CA0000000034C4F59414C5459", "79");
        cmd(operate, "80E0000013DF01390001AA0CA00000000353494E4F504543", "73");
        cmd(operate, "00A404000CA00000000353494E4F504543", "73");
        auth(operate, sTransferKey);
        appDH(operate, sTransferKey, sPublicInfo, sKeyList, "A00000000353494E4F504543");
        cmd(operate, "00A40000023F00", "7");
        auth(operate, sTransferKey);
        cmd(operate, "80E0000013DF02390001AA0CA0000000034C4F59414C5459", "7");
        cmd(operate, "00A404000CA0000000034C4F59414C5459", "7");
        auth(operate, sTransferKey);
        appDH(operate, sTransferKey, sPublicInfo, sKeyList, "A0000000034C4F59414C5459");
    }
}
