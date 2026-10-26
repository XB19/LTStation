package com.ltstation.center.sw.normal;

import java.math.BigDecimal;
import java.util.List;

/** NormalModel.LTNozStatuet : commande en attente pour le boîtier (autorisation, prix, initialisation…). */
public class LTNozStatuet {

    public int ProgramType = -1;
    public int SerialPort;
    public int NozID;
    public int PreType;
    public BigDecimal PreValue = BigDecimal.ZERO;
    public BigDecimal PrePrice = BigDecimal.ZERO;
    public int Statue;
    public List<LTNozPrice> NPList;
    public int DealCode;
    public String CardNo;
    public String CarNo;
    public BigDecimal Bal = BigDecimal.ZERO;
    public int TankID;
}
