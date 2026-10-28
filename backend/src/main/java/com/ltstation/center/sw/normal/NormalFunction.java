package com.ltstation.center.sw.normal;

import static com.ltstation.center.legacy.Sugar.p;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.legacy.Wire;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.core.MultiLanguage;
import com.ltstation.center.sw.core.SW;
import com.ltstation.center.sw.hw.SerialClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    /** BoxBase.transactionDealFlag : verrou des transactions (enregistrement / envoi). */
    public static final Object transactionDealFlag = new Object();

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

    // ------------------------------------------------------------ clôtures (BaseFunction.Settle*)

    private static final com.ltstation.center.legacy.LegacyLog SettleLog = new com.ltstation.center.legacy.LegacyLog("NormalMainLog");

    /** NormalTankHistory.CreateTime est une chaîne (varchar, convertie implicitement par SQL Server). */
    private static final String HIST_TIME = "CAST(NULLIF({NormalTankHistory.CreateTime}, '') AS bigint)";

    private static int reportType(String DailyNo) {
        return switch (DailyNo.length()) {
            case 10 -> 0;
            case 8 -> 1;
            case 6 -> 2;
            default -> 3;
        };
    }

    private static Row summaryRow(String DailyNo) {
        Row r = new Row();
        r.put("DailyNo", DailyNo);
        r.put("CreateTime", BaseFunction.ConvertDateTimeToInt());
        r.put("OperateName", IniInfo.LoginName);
        r.put("Upload", false);
        r.put("ReportType", reportType(DailyNo));
        return r;
    }

    /** NormalNozSettle : premier / dernier relevé et totaux par pistolet, produit et prix (listes alignées par NozID, GradeID, Price). */
    private static List<List<Row>> NormalNozSettle(long start, long end) {
        String range = " where {NormalTransaction.CreateTime} between " + start + " and " + end;
        String window = "SELECT * FROM (SELECT {NormalTransaction.NozID} AS NozID, {NormalTransaction.GradeID} AS GradeID, {NormalTransaction.Price} AS Price,"
            + " {NormalTransaction.Amount} AS Amount, {NormalTransaction.Volume} AS Volume, {NormalTransaction.VolumeTotal} AS VolumeTotal,"
            + " {NormalTransaction.AmountTotal} AS AmountTotal, ROW_NUMBER() OVER(PARTITION BY {NormalTransaction.NozID}, {NormalTransaction.GradeID},"
            + " {NormalTransaction.Price} ORDER BY {NormalTransaction.CreateTime} %s) AS NEWINDEX FROM [NormalTransaction]" + range
            + ") b WHERE NEWINDEX = 1 order by NozID, GradeID, Price";
        String summary = "select {NormalTransaction.NozID} AS NozID, {NormalTransaction.GradeID} AS GradeID, {NormalTransaction.Price} AS Price,"
            + " SUM({NormalTransaction.Amount}) as Amount, SUM({NormalTransaction.Volume}) as Volume, Count(*) as Count from [NormalTransaction]" + range
            + " group by {NormalTransaction.NozID}, {NormalTransaction.GradeID}, {NormalTransaction.Price} order by 1, 2, 3";
        return List.of(
            db().query(db().sql(window.formatted("asc")), null),
            db().query(db().sql(window.formatted("desc")), null),
            db().query(db().sql(summary), null));
    }

    /** BaseFunction.SettleNoz : une ligne NormalDailyNozSummary par pistolet / produit / prix. */
    public static boolean SettleNoz(String DailyNo, long start, long end) {
        try {
            List<List<Row>> settle = NormalNozSettle(start, end);
            List<Row> reportStarts = settle.get(0);
            List<Row> reportEnds = settle.get(1);
            List<Row> reportSummarys = settle.get(2);
            for (int i = 0; i < reportStarts.size(); i++) {
                Row s = reportStarts.get(i);
                Row e = reportEnds.get(i);
                Row sum = reportSummarys.get(i);
                BigDecimal totalStart = s.dec("VolumeTotal").subtract(s.dec("Volume"));
                Row r = summaryRow(DailyNo);
                r.put("DailyAmount", sum.dec("Amount"));
                r.put("DailyVolume", sum.dec("Volume"));
                r.put("NozID", s.i("NozID"));
                r.put("GradeID", sum.i("GradeID"));
                r.put("GradeName", BaseFunction.GetNormalGradeByID(sum.i("GradeID")));
                r.put("Price", sum.dec("Price"));
                r.put("Count", sum.i("Count"));
                r.put("TotalVolumeEnd", e.dec("VolumeTotal"));
                r.put("TotalVolumeStart", totalStart);
                r.put("VolumeError", e.dec("VolumeTotal").subtract(totalStart).subtract(sum.dec("Volume")));
                // comme l'original : début - fin - volume (et non fin - début - volume)
                if (s.dec("VolumeTotal").subtract(e.dec("VolumeTotal")).subtract(sum.dec("Volume")).signum() != 0) {
                    r.put("ExpectionError", 2);
                }
                db().insert("NormalDailyNozSummary", r);
            }
            return true;
        } catch (RuntimeException ex) {
            SettleLog.writeLog("Settle Error:" + DailyNo + " " + ex.getMessage());
            return false;
        }
    }

    /** NormalGradeDetialSettle : montant / volume du produit au prix donné, par moyen de paiement (espèces, RFID, carte IC, employé). */
    private static Row[] NormalGradeDetialSettle(long start, long end, int GradeID, BigDecimal Price) {
        Row[] sum = {
            sumRow(MultiLanguage.getText("Cash")), sumRow("RFID"), sumRow(MultiLanguage.getText("ICCard")), sumRow(MultiLanguage.getText("EmployeeCard"))
        };
        String sql = "select SUBSTRING({NormalTransaction.CardNo}, 1, 9) as CardNo, Sum({NormalTransaction.Amount}) as Amount, SUM({NormalTransaction.Volume}) as Volume"
            + " from [NormalTransaction] where {NormalTransaction.CreateTime} between " + start + " and " + end
            + " and {NormalTransaction.GradeID} = :g and {NormalTransaction.Price} = :p group by SUBSTRING({NormalTransaction.CardNo}, 1, 9)";
        for (Row item : db().query(db().sql(sql), p("g", GradeID, "p", Price))) {
            int idx = sumIndex(item.str("CardNo"), false);
            if (idx >= 0) {
                addSum(sum[idx], item);
            }
        }
        return sum;
    }

    /** Ligne du résumé pour un préfixe de carte : vide = espèces, 000000000 = employé, 1 = RFID, 2 = carte IC, 4 = facture. */
    private static int sumIndex(String cardNo, boolean withBill) {
        if (cardNo == null || cardNo.isEmpty()) {
            return 0;
        }
        return switch (cardNo) {
            case "000000000" -> 3;
            case "000000001" -> 1;
            case "000000002" -> 2;
            case "000000004" -> withBill ? 4 : -1;
            default -> -1;
        };
    }

    private static Row sumRow(String cardNo) {
        return Row.of("CardNo", cardNo, "Count", 0, "Amount", BigDecimal.ZERO, "Volume", BigDecimal.ZERO);
    }

    private static void addSum(Row target, Row item) {
        target.put("Count", target.i("Count") + item.i("Count"));
        target.put("Amount", target.dec("Amount").add(item.dec("Amount")));
        target.put("Volume", target.dec("Volume").add(item.dec("Volume")));
    }

    /** BaseFunction.SettleEnery : une ligne NormalDailyEnergySummary par produit et prix, avec le détail par moyen de paiement. */
    public static boolean SettleEnery(String DailyNo, long start, long end) {
        try {
            String sql = "select * from (select {NormalGrade.ID} AS ID, {NormalGrade.GradeName} AS GradeName from [NormalGrade]) a full join"
                + " (select {NormalTransaction.GradeID} AS GradeID, {NormalTransaction.Price} AS Price, Count(*) as Count, SUM({NormalTransaction.Volume}) as Volume,"
                + " SUM({NormalTransaction.Amount}) as Amount from [NormalTransaction] where {NormalTransaction.CreateTime} between " + start + " and " + end
                + " group by {NormalTransaction.GradeID}, {NormalTransaction.Price}) b on a.ID = b.GradeID";
            List<Row> list = new ArrayList<>();
            for (Row item : db().query(db().sql(sql), null)) {
                Row r = summaryRow(DailyNo);
                r.put("Amount", item.dec("Amount"));
                r.put("GradeID", item.i("ID"));
                r.put("GradeName", item.str("GradeName"));
                r.put("Volume", item.dec("Volume"));
                r.put("TotalCount", item.i("Count"));
                r.put("Price", item.dec("Price"));
                Row[] sum = NormalGradeDetialSettle(start, end, item.i("GradeID"), item.dec("Price"));
                Row detail = new Row();
                detail.put("Unitprice", item.dec("Price"));
                detail.put("CashAmount", sum[0].dec("Amount"));
                detail.put("CashVolume", sum[0].dec("Volume"));
                detail.put("OilticketAmount", BigDecimal.ZERO);
                detail.put("OilticketVolume", BigDecimal.ZERO);
                detail.put("KeepaccountsAmount", BigDecimal.ZERO);
                detail.put("Keepaccountsvolume", BigDecimal.ZERO);
                detail.put("TotalAmount", item.dec("Amount"));
                detail.put("TotalVolume", item.dec("Volume"));
                detail.put("RFIDAmount", sum[1].dec("Amount"));
                detail.put("RFIDVolume", sum[1].dec("Volume"));
                detail.put("UserAmount", sum[2].dec("Amount"));
                detail.put("UserVolume", sum[2].dec("Volume"));
                detail.put("EmployeeAmount", sum[3].dec("Amount"));
                detail.put("EmployeeVolume", sum[3].dec("Volume"));
                r.put("GradeDetial", Wire.json(detail));
                list.add(r);
            }
            db().insertAll("NormalDailyEnergySummary", list);
            return true;
        } catch (RuntimeException ex) {
            SettleLog.writeLog("Settle Error:" + DailyNo + " " + ex.getMessage());
            return false;
        }
    }

    /** NormalStockSettle : relevé de début, ventes, relevé de fin et dépotages par cuve utilisée (listes alignées par cuve). */
    public static List<List<Row>> NormalStockSettle(long start, long end) {
        String tanks = "(select {NormalTank.ID} AS ID, {NormalTank.TankName} AS TankName from [NormalTank] where {NormalTank.IsUsing} = true) c";
        String meter = "select * from " + tanks + " left join (SELECT * FROM (SELECT {NormalTankHistory.TankID} AS TankID,"
            + " {NormalTankHistory.CurrentVolume} AS CurrentVolume, ROW_NUMBER() OVER(PARTITION BY {NormalTankHistory.TankID}"
            + " ORDER BY " + HIST_TIME + " desc) AS NEWINDEX FROM [NormalTankHistory] where " + HIST_TIME + " <= %d) b"
            + " WHERE NEWINDEX = 1) d on d.TankID = c.ID order by c.ID";
        String saler = "select * from " + tanks + " left join (select {NormalTransaction.TankID} AS TankID, SUM({NormalTransaction.Volume}) as Volume"
            + " from [NormalTransaction] where {NormalTransaction.CreateTime} between " + start + " and " + end
            + " group by {NormalTransaction.TankID}) b on c.ID = b.TankID order by c.ID";
        String stock = "select * from " + tanks + " left join (select {NormalTankStock.TankID} AS TankID, SUM({NormalTankStock.StockVolume}) as StockVolume"
            + " from [NormalTankStock] where {NormalTankStock.CreateTime} between " + start + " and " + end
            + " group by {NormalTankStock.TankID}) b on c.ID = b.TankID order by c.ID";
        return List.of(db().query(db().sql(meter.formatted(start)), null), db().query(db().sql(saler), null),
            db().query(db().sql(meter.formatted(end)), null), db().query(db().sql(stock), null));
    }

    /** BaseFunction.SettleStock : mouvement de stock par cuve, plus une ligne en anomalie pour les ventes sur une cuve inconnue. */
    public static boolean SettleStock(String DailyNo, long start, long end) {
        try {
            List<List<Row>> settle = NormalStockSettle(start, end);
            List<Row> reportStarts = settle.get(0);
            List<Row> salerSums = settle.get(1);
            List<Row> reportEnds = settle.get(2);
            List<Row> reportStock = settle.get(3);
            List<Row> list = new ArrayList<>();
            for (int i = 0; i < reportStarts.size(); i++) {
                BigDecimal volumeStart = reportStarts.get(i).dec("CurrentVolume");
                BigDecimal volumeEnd = reportEnds.get(i).dec("CurrentVolume");
                BigDecimal saled = salerSums.get(i).dec("Volume");
                BigDecimal stocked = reportStock.get(i).dec("StockVolume");
                Row r = summaryRow(DailyNo);
                r.put("SalerVolume", saled);
                r.put("StockVolume", stocked);
                r.put("TankID", reportStarts.get(i).i("ID"));
                r.put("TankName", reportStarts.get(i).str("TankName"));
                r.put("VolumeEnd", volumeEnd);
                r.put("VolumeStart", volumeStart);
                r.put("Error", volumeEnd.subtract(stocked).add(saled).subtract(volumeStart));
                r.put("ExpectionError", 0);
                list.add(r);
            }
            String lost = "select * from (select * from (select {NormalTank.ID} AS ID, {NormalTank.TankNo} AS TankNo, {NormalTank.TankName} AS TankName from [NormalTank]) a"
                + " right join (select {NormalTransaction.TankID} AS TankID, SUM({NormalTransaction.Volume}) as Volume from [NormalTransaction]"
                + " where {NormalTransaction.CreateTime} between " + start + " and " + end + " group by {NormalTransaction.TankID}) b on a.ID = b.TankID) c"
                + " where c.TankNo IS NULL";
            for (Row item : db().query(db().sql(lost), null)) {
                Row r = summaryRow(DailyNo);
                r.put("SalerVolume", item.dec("Volume"));
                r.put("StockVolume", BigDecimal.ZERO);
                r.put("TankID", item.i("TankID"));
                r.put("TankName", item.str("TankName"));
                r.put("VolumeEnd", BigDecimal.ZERO);
                r.put("VolumeStart", BigDecimal.ZERO);
                r.put("Error", item.dec("Volume"));
                r.put("ExpectionError", 1);
                // comme l'original : pas de type 3 pour ces lignes
                r.put("ReportType", Math.min(reportType(DailyNo), 2));
                list.add(r);
            }
            db().insertAll("NormalDailyStockSummary", list);
            return true;
        } catch (RuntimeException ex) {
            SettleLog.writeLog("Settle Error:" + DailyNo + " " + ex.getMessage());
            return false;
        }
    }

    /** BaseFunction.SettleSummary : totaux par moyen de paiement (espèces, RFID, carte IC, employé, facture). */
    public static boolean SettleSummary(String DailyNo, long start, long end) {
        try {
            Row[] sum = {
                sumRow(MultiLanguage.getText("Cash")), sumRow("RFID"), sumRow(MultiLanguage.getText("ICCard")),
                sumRow(MultiLanguage.getText("EmployeeCard")), sumRow("Bill")
            };
            String sql = "select SUBSTRING(CardNo, 1, 9) as CardNo, COUNT(*) as Count, Sum(Amount) as Amount, SUM(Volume) as Volume from"
                + " (select {NormalTransaction.Amount} AS Amount, {NormalTransaction.Volume} AS Volume, CASE WHEN {NormalTransaction.CardNo} = '00000000000000000000'"
                + " THEN NULL ELSE {NormalTransaction.CardNo} END AS CardNo from [NormalTransaction] where {NormalTransaction.CreateTime} between " + start + " and " + end
                + ") a group by SUBSTRING(a.CardNo, 1, 9)";
            for (Row item : db().query(db().sql(sql), null)) {
                int idx = sumIndex(item.str("CardNo"), true);
                if (idx >= 0) {
                    addSum(sum[idx], item);
                }
            }
            String settleTime = BaseFunction.StampToDateTime(String.valueOf(start)).format(Conv.STD) + "---"
                + BaseFunction.StampToDateTime(String.valueOf(end)).format(Conv.STD);
            List<Row> list = new ArrayList<>();
            for (Row item : sum) {
                Row r = summaryRow(DailyNo);
                r.put("Amount", item.dec("Amount"));
                r.put("TotalCount", item.i("Count"));
                r.put("Volume", item.dec("Volume"));
                r.put("CardNo", item.str("CardNo"));
                r.put("SettleTime", settleTime);
                list.add(r);
            }
            db().insertAll("NormalDailySummary", list);
            return true;
        } catch (RuntimeException ex) {
            SettleLog.writeLog("Settle Error:" + DailyNo + " " + ex.getMessage());
            return false;
        }
    }

    /** GetTransactionVolumeTotal(NozID, CreateTime) : transaction d'un pistolet à un instant donné (null si absente). */
    public static Row GetTransactionVolumeTotal(int NozID, long CreateTime) {
        try {
            return db().first("NormalTransaction", "{NozID} = :n AND {CreateTime} = :c", p("n", NozID, "c", CreateTime));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
