package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.NormalTankSerial : état temps réel d'une cuve en mode Normal. */
public class NormalTankSerial {

    public int ID;
    public String TankNo;
    public String TankName;
    public int GradeID;
    public BigDecimal TankHeight = BigDecimal.ZERO;
    public BigDecimal TankVolume = BigDecimal.ZERO;
    public BigDecimal CurrentHeight = BigDecimal.ZERO;
    public BigDecimal CurrentVolume = BigDecimal.ZERO;
    public BigDecimal CurrentWater = BigDecimal.ZERO;
    public BigDecimal CurrentTemp = BigDecimal.ZERO;
    public BigDecimal OverHeight = BigDecimal.ZERO;
    public BigDecimal LowerHeight = BigDecimal.ZERO;
    public BigDecimal OverWaterHeight = BigDecimal.ZERO;
    public BigDecimal LowerWaterHeight = BigDecimal.ZERO;
    public BigDecimal OverTemp = BigDecimal.ZERO;
    public BigDecimal LowerTemp = BigDecimal.ZERO;
    public boolean IsUsing;
    public long CreateTime;
    public long UpdateTime;
    public long RaiseStartTime;
    public BigDecimal RaiseStartVolume = BigDecimal.ZERO;
    public BigDecimal MaxLevel = BigDecimal.ZERO;
    public BigDecimal CurrentLevel = BigDecimal.ZERO;
    public BigDecimal OverImportLevel = BigDecimal.ZERO;
    public BigDecimal RaiseLevel = BigDecimal.ZERO;
    public int RaiseFlag;
    public int KeepTime;
    public long RaiseEndTime;
    public int FirstRun;
    public BigDecimal CalculateHeight = BigDecimal.ZERO;
    public BigDecimal CalculateVolume = BigDecimal.ZERO;
    public BigDecimal SumCalculateVolume = BigDecimal.ZERO;
    public long CalculateTime;
    public int TL;
    public int CG;
    public int Ttemp;
    public int TLevel;
    public int EG;
}
