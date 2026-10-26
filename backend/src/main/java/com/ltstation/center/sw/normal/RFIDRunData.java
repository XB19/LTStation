package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.RFIDRunData : état d'un lecteur RFID de pistolet. */
public class RFIDRunData {

    public int ID;
    public String DeviceNo;
    public int StationID;
    public int RFIDNo;
    public int Address;
    public int NozID;
    public int Box_Port;
    public int CompanyID;
    public String StationName;
    public String Tag;
    public String UserName;
    public String CarNo;
    public BigDecimal Bal = BigDecimal.ZERO;
    public String UpdateTime;
    public boolean IsOnline;
    public String Version;
    public String IsWork;
}
