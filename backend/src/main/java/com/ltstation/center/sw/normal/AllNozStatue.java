package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** Wireless.AllNozStatue : état d'un pistolet d'un boîtier sans fil. */
public class AllNozStatue {

    public int StationID;
    public int NozID;
    public BigDecimal Price = BigDecimal.ZERO;
    public BigDecimal CurrentVolume = BigDecimal.ZERO;
    public BigDecimal CurrentAmount = BigDecimal.ZERO;
    public int GradeID;
    public String GradeName;
    public int LastStatue;
    public int Statue;
    public BigDecimal VolumeTotal = BigDecimal.ZERO;
    public BigDecimal AmountTotal = BigDecimal.ZERO;
    public long UpdateTime;
    public int CompanyID;
    public String DeviceNo;
    public int Box_Port;
    public int StationType;
    public int PriceDigits;
    public int VolumeDigits;
    public int AmountDigits;
    public String CardNo;
    public String UserName;
    public String CarNo;
    public BigDecimal Bal = BigDecimal.ZERO;
    public int TankID;
    public long NozRunTime;
    public BigDecimal Volume = BigDecimal.ZERO;
    public BigDecimal Amount = BigDecimal.ZERO;
    public int MRadio;
    public int VRadio;
    public int PRadio;
    public boolean ConnectFirst;
    public int RaiseNoz;
    public int LastRaiseNoz;
    public int AuthTypeID;
    public int RecordUpload;
    public String ErrorCode;
    public String Version;
    public int IsOnDuty;
    public String AuthMessage;
    public int BoxPortID;
    public int PUMPID;
    public int IdentifyMode;
    public int Address;
}
