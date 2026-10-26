package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/**
 * Commandes des boîtiers sans fil (CmdList, table cmd_list) et leurs données JSON (champ CmdData) :
 * CmdPreSet, CmdStatue, CmdPrint, CmdPrintTransaction, CmdReplyStatue ; UnTransaction pour les lots hors ligne.
 */
public final class WirelessCmd {

    private WirelessCmd() {
    }

    /** CenterServerDeal.Modal.CmdList */
    public static class CmdList {
        public int ID;
        public String CmdType;
        public String DeviceNo;
        public int StationID;
        public int NozID;
        public String CmdData;
        public long CreateTime;
        public int Deal;
        public int CmdPlatType;
        public boolean Dowload;
    }

    /** Wireless.CmdPreSet : prédétermination / autorisation (A1, A4, A5). */
    public static class CmdPreSet {
        public String DeviceNo;
        public int NozID;
        public int PreSetType;
        public BigDecimal PreSetValue = BigDecimal.ZERO;
        public BigDecimal PreSetPrice = BigDecimal.ZERO;
        public boolean AuthType;
        public String DealCode;
        public String CardNo;
        public String CarNo;
        public BigDecimal Bal = BigDecimal.ZERO;
    }

    /** Wireless.CmdStatue : changement de prix (A2). */
    public static class CmdStatue {
        public int NozID;
        public int PreType;
        public BigDecimal PreValue = BigDecimal.ZERO;
        public BigDecimal PrePrice = BigDecimal.ZERO;
        public int PriceDigits;
        public int Statue;
        public long setTime;
    }

    /** Wireless.CmdPrint : lignes du ticket du boîtier (14). */
    public static class CmdPrint {
        public int Count;
        public String FirstIndex;
        public String SecondIndex;
        public String ThirdIndex;
        public String FouthIndex;
        public String FifthIndex;
        public String SixthIndex;
        public String NozID;
        public String Amount;
        public String Volume;
        public String Price;
        public String Seventh;
        public String TimeIndex;
        public String EighthIndex;
        public String NinthIndex;
        public String TenthIndex;
    }

    /** Wireless.CmdPrintTransaction : réimpression d'une transaction (15). */
    public static class CmdPrintTransaction {
        public int NozID;
        public String Amount;
        public String Volume;
        public String Price;
        public String Time;
        public int AmountDigits;
        public int VolumeDigits;
        public int PriceDigits;
        public long TID;
    }

    /** Wireless.CmdReplyStatue : réponse à une trame du boîtier. */
    public static class CmdReplyStatue {
        public String CmdType;
        public String CmdValue;
    }

    /** NormalModel.UnTransaction : transaction d'un lot hors ligne (1F). */
    public static class UnTransaction {
        public int NozID;
        public BigDecimal Volume = BigDecimal.ZERO;
        public BigDecimal Amount = BigDecimal.ZERO;
        public BigDecimal Price = BigDecimal.ZERO;
        public BigDecimal AmountTotal = BigDecimal.ZERO;
        public BigDecimal VolumeTotal = BigDecimal.ZERO;
        public String OrderNumber;
        public String CardNo;
        public String TimeStr;
    }
}
