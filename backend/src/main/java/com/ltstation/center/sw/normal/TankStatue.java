package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.TankStatue : état d'une cuve mesurée par un boîtier sans fil. */
public class TankStatue {

    public int ID;
    public int StationID;
    public int TankID;
    public String TankName;
    public BigDecimal Price = BigDecimal.ZERO;
    public int GradeID;
    public String GradeName;
    public String DeviceNo;
    public int Statue;
    public BigDecimal OilHeight = BigDecimal.ZERO;
    public BigDecimal OilVolume = BigDecimal.ZERO;
    public BigDecimal WaterHeight = BigDecimal.ZERO;
    public BigDecimal Temp = BigDecimal.ZERO;
    public BigDecimal TotalHeight = BigDecimal.ZERO;
    public BigDecimal TotalVolume = BigDecimal.ZERO;
    public int CompanyID;
    public long UpdateTime;
    public int TL;
    public int CG;
    public int Ttemp;
    public int TLevel;
    public int EG;
    public BigDecimal OilMeasuredValue = BigDecimal.ZERO;
    public BigDecimal OilCalibrationValue = BigDecimal.ZERO;
    public BigDecimal WaterMeasuredValue = BigDecimal.ZERO;
    public BigDecimal WaterCalibrationValue = BigDecimal.ZERO;
    public long RaiseStartTime;
    public BigDecimal RaiseStartVolume = BigDecimal.ZERO;
    public BigDecimal MaxLevel = BigDecimal.ZERO;
    public BigDecimal CurrentLevel = BigDecimal.ZERO;
    public BigDecimal OverImportLevel = BigDecimal.ZERO;
    public BigDecimal RaiseLevel = BigDecimal.ZERO;
    public int RaiseFlag;
    public int KeepTime;
    public long RaiseEndTime;
    public int FirstImport;
    public String TankMac;
    public String IP;
    public String Port;
    public boolean isRun;
    public int isImport;
}
