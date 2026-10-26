package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.SerialClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Membres de ServerDeal.BaseFunction et de Wireless.BaseInfo propres au mode Normal
 * (pistolets des boîtiers, téléchargement des programmes, limites des cartes RFID, alarmes PLC).
 */
public final class NormalFunction {

    private NormalFunction() {
    }

    private static Sugar db() {
        return SW.db();
    }

    /** Message Windows (Msg, wParam, lParam) envoyé à la fenêtre « NormalSystem » (NormalMainForm.WndProc). */
    public interface MainMessage {
        void accept(int Msg, long wParam, long lParam);
    }

    /** SendMessage(FindWindow(null, « NormalSystem »), …) : NormalMainForm s'y abonne à l'ouverture. */
    public static volatile MainMessage MainFormMessage = (a, b, c) -> { };

    /** BaseFunction.PumpNozData : dernier état « en cours » de chaque pompe. */
    public static final ConcurrentHashMap<Integer, NormalNozStatue> PumpNozData = new ConcurrentHashMap<>();

    /** BaseFunction.FileList : programmes de boîtier chargés pour le téléchargement. */
    public static final List<DowLoadFileParam> FileList = new CopyOnWriteArrayList<>();

    /** BaseInfo.TankAlarmList : changements d'alarme des cuves à envoyer au Center. */
    public static final Queue<RealTankChange> TankAlarmList = new ConcurrentLinkedQueue<>();

    /** NormalMainForm.NetErrorCount : échecs réseau consécutifs vers le Center. */
    public static volatile int NetErrorCount;

    /** BaseInfo.SetLength : complète à gauche par des zéros. */
    public static String SetLength(String str, int len) {
        StringBuilder s = new StringBuilder(str);
        while (s.length() < len) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    /** BaseInfo.DealToFloat : BitConverter.ToSingle (petit-boutiste) ; 0 en cas d'erreur. */
    public static float DealToFloat(byte[] bytes) {
        try {
            int bits = (bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8 | (bytes[2] & 0xFF) << 16 | (bytes[3] & 0xFF) << 24;
            return Float.intBitsToFloat(bits);
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    /**
     * BaseInfo.DealPLCAlarm : ouvre (AlarmData ≠ 0) ou clôt l'alarme système PLC de la zone / du bit donnés.
     * Renvoie le type d'alarme (100 + bit pour MW28, 200 + bit pour MB5, 300 + bit pour MB10) ; 0 en cas d'erreur.
     */
    public static int DealPLCAlarm(int StationID, int CompanyID, String AlarmRegion, int AlarmIndex, int AlarmData, String[] Error) {
        Error[0] = "";
        try {
            int AlarmType = switch (AlarmRegion) {
                case "MW28" -> 100 + AlarmIndex;
                case "MB5" -> 200 + AlarmIndex;
                case "MB10" -> 300 + AlarmIndex;
                default -> 0;
            };
            if (AlarmType == 0) {
                throw new IllegalStateException("AlarmType Error");
            }
            long now = com.ltstation.center.sw.core.BaseFunction.ConvertDateTimeToInt();
            if (AlarmData == 0) {
                Row a = db().first("SystemAlarm", "{Deal} = false AND {StationID} = :s AND {CompanyID} = :c AND {AlarmTypeID} = :t",
                    p("s", StationID, "c", CompanyID, "t", AlarmType));
                if (a != null) {
                    a.put("EndTime", now);
                    a.put("UpdateTime", now);
                    a.put("Deal", true);
                    a.put("Upload", false);
                    db().update("SystemAlarm", a);
                }
            } else {
                db().insert("SystemAlarm", Row.of("StationID", StationID, "CompanyID", CompanyID, "StartTime", now, "AlarmTypeID", AlarmType, "Deal", false,
                    "Name", "PLC", "UpdateTime", now, "Upload", false));
            }
            return AlarmType;
        } catch (RuntimeException ex) {
            Error[0] = ex.getMessage();
            return 0;
        }
    }

    /** Résultat de GetProgramDataVersion. */
    public record ProgramPage(boolean ok, long TotalPage, String ProgramData, String Error, String FileName) {
    }

    /** BaseFunction.GetProgramDataVersion : page de 512 octets (complétée par des F) d'un programme de boîtier. */
    public static ProgramPage GetProgramDataVersion(int ProgramType, int Index) {
        long total = 0;
        String fileName = "";
        try {
            DowLoadFileParam f = FileList.stream().filter(t -> t.FileIndex == ProgramType).findFirst().orElse(null);
            if (f == null) {
                throw new IllegalStateException(MultiLanguage.getText("ProgramFileMissing"));
            }
            fileName = f.FileName;
            total = f.Data.length / 512 + 1;
            if (total < Index) {
                throw new IllegalStateException("Finished");
            }
            int num = Index != total ? 512 : f.Data.length - (Index - 1) * 512;
            byte[] array = new byte[num];
            System.arraycopy(f.Data, (Index - 1) * 512, array, 0, num);
            StringBuilder text = new StringBuilder(SerialClass.byteToHexStr(array));
            while (text.length() < 1024) {
                text.append('F');
            }
            return new ProgramPage(true, total, text.toString(), "", fileName);
        } catch (RuntimeException ex) {
            return new ProgramPage(false, total, "", ex.getMessage(), fileName);
        }
    }

    /** Résultat de GetRFIDLimit : prédétermination (type 0 = montant, 1 = volume) ou code de refus res. */
    public record RFIDLimit(boolean ok, BigDecimal PreSetValue, int PreSetType, String res, String Error) {
    }

    private static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : Conv.toDecimal(v);
    }

    /**
     * BaseFunction.GetRFIDLimit : limites journalières / mensuelles (nombre, montant, volume) de la carte,
     * converties en prédétermination ; customerAccounts.Balance reçoit le solde du groupe pour un groupe de type 1.
     */
    public static RFIDLimit GetRFIDLimit(Row customerCardInfos, Row customerAccounts, NormalNozStatue noz, SumTransaction sum) {
        String res = "0";
        BigDecimal num = BigDecimal.valueOf(-1);
        BigDecimal num2 = BigDecimal.valueOf(-1);
        BigDecimal minus1 = BigDecimal.valueOf(-1);
        try {
            int m = IniInfo.Mradio;
            int v = IniInfo.Vradio;
            Object limitTime = customerCardInfos.get("LimitTime");
            if (limitTime != null && Conv.toInt(limitTime) > 0 && customerAccounts.get("TotalTimesDaily") != null
                && Conv.toInt(customerAccounts.get("TotalTimesDaily")) + sum.SumCount >= Conv.toInt(limitTime)) {
                res = "241";
                throw new IllegalStateException("Limit Time");
            }
            if (Conv.toDouble(customerCardInfos.get("LimitAmount")) > 0.0) {
                BigDecimal limit = dec(customerCardInfos.get("LimitAmount"));
                BigDecimal used = dec(customerAccounts.get("TotalAmountDaily")).add(sum.SumAmount);
                if (used.compareTo(limit) >= 0) {
                    res = "242";
                    throw new IllegalStateException("Limit Daily Amount");
                }
                num = limit.subtract(used).setScale(m, RoundingMode.HALF_EVEN);
                if (num.signum() <= 0) {
                    res = "242";
                    throw new IllegalStateException("Limit Daily Amount");
                }
            }
            if (Conv.toDouble(customerCardInfos.get("LimitAmountMonthly")) > 0.0) {
                BigDecimal limit = dec(customerCardInfos.get("LimitAmountMonthly"));
                BigDecimal used = dec(customerAccounts.get("TotalAmountMonthly")).add(sum.SumAmount);
                if (used.compareTo(limit) >= 0) {
                    res = "243";
                    throw new IllegalStateException("Limit Month Amount");
                }
                BigDecimal num3 = limit.subtract(used).setScale(m, RoundingMode.HALF_EVEN);
                if (num3.signum() <= 0) {
                    res = "243";
                    throw new IllegalStateException("Limit Month Amount");
                }
                num = num.compareTo(minus1) == 0 ? num3 : num.min(num3);
            }
            if (Conv.toDouble(customerCardInfos.get("LimitVolume")) > 0.0) {
                BigDecimal limit = dec(customerCardInfos.get("LimitVolume"));
                BigDecimal used = dec(customerAccounts.get("TotalVolumeDaily")).add(sum.SumVolume);
                if (used.compareTo(limit) >= 0) {
                    res = "244";
                    throw new IllegalStateException("Limit Daily Volume");
                }
                num2 = limit.subtract(used).setScale(v, RoundingMode.HALF_EVEN);
                if (num2.signum() <= 0) {
                    res = "244";
                    throw new IllegalStateException("Limit Daily Volume");
                }
            }
            if (Conv.toDouble(customerCardInfos.get("LimitVolumeMonthly")) > 0.0) {
                BigDecimal limit = dec(customerCardInfos.get("LimitVolumeMonthly"));
                BigDecimal used = dec(customerAccounts.get("TotalVolumeMonthly")).add(sum.SumVolume);
                if (used.compareTo(limit) >= 0) {
                    res = "245";
                    throw new IllegalStateException("Limit Month Volume");
                }
                BigDecimal num4 = limit.subtract(used).setScale(v, RoundingMode.HALF_EVEN);
                if (num4.signum() <= 0) {
                    res = "245";
                    throw new IllegalStateException("Limit Month Volume");
                }
                num2 = num2.compareTo(minus1) == 0 ? num4 : num2.min(num4);
            }
            BigDecimal num5 = minus1;
            if (num2.compareTo(minus1) != 0) {
                if (noz.Price.signum() != 0) {
                    num5 = noz.Price.multiply(num2);
                } else {
                    Row price = db().first("NormalPriceInfo", "{GradeID} = :g", p("g", noz.GradeID));
                    if (price == null) {
                        res = "240";
                        throw new IllegalStateException("Grade Lost");
                    }
                    num5 = num2.multiply(dec(price.get("Price")));
                }
            }
            Row group = db().first("Group", "{GroupID} = :g", p("g", customerCardInfos.get("GroupID")));
            if (group != null && group.i("GroupType") == 1) {
                customerAccounts.put("Balance", Conv.round(Conv.toDouble(group.get("Balance")), 3));
            }
            BigDecimal balance = dec(customerAccounts.get("Balance"));
            if (num5.compareTo(balance) > 0) {
                num5 = minus1;
            }
            BigDecimal PreSetValue;
            int PreSetType = 0;
            if (num5.compareTo(minus1) != 0) {
                if (num.compareTo(minus1) != 0 && num5.compareTo(num) >= 0) {
                    PreSetValue = num;
                } else {
                    PreSetType = 1;
                    PreSetValue = num2.setScale(v, RoundingMode.HALF_EVEN);
                }
            } else if (num.compareTo(minus1) != 0) {
                PreSetValue = num.min(balance.subtract(sum.SumAmount)).setScale(v, RoundingMode.HALF_EVEN);
            } else {
                PreSetValue = balance.subtract(sum.SumAmount).setScale(v, RoundingMode.HALF_EVEN);
            }
            return new RFIDLimit(true, PreSetValue, PreSetType, res, "");
        } catch (RuntimeException ex) {
            return new RFIDLimit(false, minus1, 0, res, ex.getMessage());
        }
    }
}
