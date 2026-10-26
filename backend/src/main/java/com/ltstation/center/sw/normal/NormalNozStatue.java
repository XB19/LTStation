package com.ltstation.center.sw.normal;

import com.ltstation.center.sw.core.IniInfo;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

/** NormalModel.NormalNozStatue : état temps réel d'un pistolet en mode Normal (champs publics comme l'original). */
public class NormalNozStatue {

    public Deque<Integer> Statue = new ArrayDeque<>();
    public Deque<LTNozStatuet> LTStatue = new ArrayDeque<>();
    public int StationID;
    public int ID;
    public String NozID;
    public int TankID;
    public int TurnNowStatue;
    public int MachineOpen;
    public int RaiseNoz;
    public int LastRaiseNoz;
    public boolean Auth_Statue;
    public int RecordUpload;
    public BigDecimal Price = BigDecimal.ZERO;
    public BigDecimal AmountTotal = BigDecimal.ZERO;
    public BigDecimal VolumeTotal = BigDecimal.ZERO;
    public BigDecimal CloseValue = BigDecimal.ZERO;
    public BigDecimal DutyTotal = BigDecimal.ZERO;
    public String MachineType;
    public BigDecimal SetAmount = BigDecimal.ZERO;
    public BigDecimal SetVolume = BigDecimal.ZERO;
    public BigDecimal SetPrice = BigDecimal.ZERO;
    public BigDecimal SetCloseValue = BigDecimal.ZERO;
    public int accredit;
    public BigDecimal Amont = BigDecimal.ZERO;
    public BigDecimal Volume = BigDecimal.ZERO;
    public int NozStatue;
    public int LastStatue;
    public int GradeID;
    public String GradeName;
    public int View_Statue;
    public int CompanyID;
    public String CardNo;
    public String UserName;
    public String CarNo;
    public BigDecimal Bal = BigDecimal.ZERO;
    public BigDecimal CableVolume = BigDecimal.ZERO;
    public int Box_DeviceType;
    public int IsOnDuty;
    public int AllowAccredit;
    public int pumpid;
    public int IdentifyMode;
    public BigDecimal startHeight = BigDecimal.ZERO;
    public BigDecimal endHeight = BigDecimal.ZERO;
    public long StartTime;
    public long EndTime;
    public int Address;

    public NormalNozStatue(int ID, String NozID, int TankID, int accredit, BigDecimal Price, int MachineOpen, int Box_DeviceType, int isOnDuty,
                           int AllowAccredit, int PumpID, int identifyMode) {
        StationID = IniInfo.StationID;
        this.ID = ID;
        this.NozID = NozID;
        this.TankID = TankID;
        TurnNowStatue = 0;
        this.accredit = accredit;
        SetPrice = Price;
        RaiseNoz = -1;
        View_Statue = -1;
        this.Box_DeviceType = Box_DeviceType;
        IsOnDuty = isOnDuty;
        this.AllowAccredit = AllowAccredit;
        pumpid = PumpID;
        IdentifyMode = identifyMode;
        this.Price = Price;
        // Comme l'original : le paramètre MachineOpen n'est pas repris
        for (int s : new int[] {0, 2, 3, 4, 1, 10}) {
            Statue.add(s);
        }
    }
}
