package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.RealTankChange : changement d'alarme d'une cuve, envoyé au Center. */
public class RealTankChange {

    public int StationID;
    public int TankID;
    public BigDecimal Height = BigDecimal.ZERO;
    public BigDecimal Volume = BigDecimal.ZERO;
    public BigDecimal WaterHeght = BigDecimal.ZERO;
    public BigDecimal Temp = BigDecimal.ZERO;
    public int TL;
    public int CG;
    public int Ttemp;
    public int TLevel;
    public int EG;
    public int CompanyID;
    public long UpdateTime;
    public String TankName;
}
