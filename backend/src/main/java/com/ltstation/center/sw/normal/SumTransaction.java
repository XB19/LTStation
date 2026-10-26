package com.ltstation.center.sw.normal;

import java.math.BigDecimal;

/** NormalModel.SumTransaction : cumul des transactions d'une carte depuis la dernière synchronisation. */
public class SumTransaction {

    public BigDecimal SumAmount = BigDecimal.ZERO;
    public BigDecimal SumVolume = BigDecimal.ZERO;
    public int SumCount;
}
