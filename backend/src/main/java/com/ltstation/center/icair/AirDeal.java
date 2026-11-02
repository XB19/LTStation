package com.ltstation.center.icair;

import static com.ltstation.center.icair.AirProto.asciiFloat;
import static com.ltstation.center.icair.AirProto.int16;
import static com.ltstation.center.icair.AirProto.pad;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.LegacyLog;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.serverdeal.CenterDeal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Traitements communs aux deux protocoles des appareils (ICAirController d'origine) :
 * cuves et alarmes, débit des comptes, points, contrôle des cartes.
 */
@Component
public class AirDeal {

    /** BLSQueryReBack. */
    public static class Query {
        public int result;
        public String asn = "";
        public String pass = "";
        public BigDecimal maxAmount = BigDecimal.ZERO;
        public BigDecimal maxVolume = BigDecimal.ZERO;
        public BigDecimal balance = BigDecimal.ZERO;
    }

    /** CreditCardInfo. */
    public static class Credit {
        public String asn = "";
        public String pass = "";
        public String vehicleNo = "";
        public int limitVehicleNo;
        public int mileageTolerance;
        public int suspendType;
        public BigDecimal preferentialPrice = BigDecimal.ZERO;
        public BigDecimal discount = BigDecimal.ZERO;
        public BigDecimal oilConsumption = BigDecimal.ZERO;
        public BigDecimal mileageLastest = BigDecimal.ZERO;
        public BigDecimal volumeLastest = BigDecimal.ZERO;
        public BigDecimal maxVolume = BigDecimal.ZERO;
        public BigDecimal maxAmount = BigDecimal.ZERO;
        public BigDecimal balance = BigDecimal.ZERO;
    }

    private final Sugar db;
    private final CenterDeal deal;
    private final AirNotify notify;
    private final boolean secretSame;
    private final LegacyLog log = new LegacyLog("ICAir");

    public AirDeal(CenterDeal deal, AirNotify notify, @Value("${app.air.secret-same:1}") int secretSame) {
        this.db = deal.db();
        this.deal = deal;
        this.notify = notify;
        this.secretSame = secretSame == 1;
    }

    private static Map<String, Object> p(Object... kv) {
        return Sugar.p(kv);
    }

    private static boolean filled(String v) {
        return v != null && !v.isEmpty();
    }

    private static String now() {
        return LocalDateTime.now().format(Conv.STD);
    }

    private static BigDecimal dec(double v) {
        return BigDecimal.valueOf(v);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return CenterDeal.min(a, b);
    }

    public Row tankOf(Row air) {
        return db.first("AirTank", "{ID} = :i", p("i", Conv.toInt(air.str("TankNo"))));
    }

    /** Numéro inconnu : noté une seule fois, sauf s'il attend déjà son affectation à une société. */
    public void dealUnknow(List<Row> list, String no) {
        try {
            if (!list.isEmpty() || db.first("UnknowDevice", "{UnKnowDeviceNo} = :n", p("n", no)) != null
                || db.first("Device_Company_List", "{DeviceNo} = :n", p("n", no)) != null) {
                return;
            }
            db.insert("UnknowDevice", Row.of("UnKnowDeviceNo", no, "DateTime", now()));
        } catch (RuntimeException ignored) {
            // comme l'original : l'enregistrement est facultatif
        }
    }

    private void closeAlarm(Row alarm, Integer tankId, Integer stationId, String type) {
        alarm.set("Deal", true).set("EndTime", CenterDeal.now());
        db.update("TankAlarmHistory", alarm);
        if (type != null) {
            notify.tankAlarm(stationId, tankId, type, 0);
        }
    }

    private void openAlarm(Row tank, int type, String monitorType) {
        Map<String, Object> w = p("t", type, "s", tank.i("StationID"), "k", tank.i("ID"));
        if (db.first("TankAlarmHistory", "{AlarmTypeID} = :t AND {StationID} = :s AND {TankID} = :k AND {Deal} = false", w) != null) {
            return;
        }
        long now = CenterDeal.now();
        db.insert("TankAlarmHistory", Row.of("StationID", tank.i("StationID"), "TankID", tank.i("ID"), "TankName", tank.str("TankName"),
            "CompanyID", tank.i("CompanyID"), "AlarmTypeID", type, "Deal", false, "StartTime", now, "UpdateTime", now));
        if (monitorType != null) {
            notify.tankAlarm(tank.i("StationID"), tank.i("ID"), monitorType, 1);
        }
    }

    /** DealTankAlarrm : alarmes lues dans l'octet 8 du bloc, puis hauteur, volume et historique horaire de la cuve. */
    public void dealTankAlarm(Row tank, BigDecimal height, String oilHeight) {
        try (Sugar.Tx tx = db.begin()) {
            int tl = 0;
            int cg = 0;
            int ttemp = 0;
            int level = 0;
            int eg = 0;
            String hour = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH"));
            int stationId = tank.i("StationID");
            int tankId = tank.i("ID");
            if (oilHeight.length() >= 18) {
                String bits = pad(Integer.toBinaryString(Integer.parseInt(oilHeight.substring(16, 18), 16)), 8);
                for (int i = 1; i < bits.length(); i++) {
                    if (bits.charAt(i) != '0') {
                        switch (i) {
                            case 7 -> tl = 7;
                            case 6 -> cg = 6;
                            case 5 -> ttemp = 5;
                            case 4 -> ttemp = 4;
                            case 3 -> level = 3;
                            case 2 -> level = 2;
                            default -> eg = 1;
                        }
                    }
                }
                for (Row a : db.list("TankAlarmHistory", "{Deal} = false AND {StationID} = :s AND {TankID} = :k", p("s", stationId, "k", tankId))) {
                    switch (a.i("AlarmTypeID")) {
                        case 7 -> {
                            if (tl == 0) {
                                closeAlarm(a, tankId, stationId, "LEAK");
                            }
                        }
                        case 6 -> {
                            if (cg == 0) {
                                closeAlarm(a, tankId, stationId, "OIL_DENSITY");
                            }
                        }
                        case 4, 5 -> {
                            if (ttemp == 0) {
                                closeAlarm(a, tankId, stationId, "FIRE");
                            }
                        }
                        case 2, 3 -> {
                            if (level == 0) {
                                closeAlarm(a, tankId, stationId, null);
                            }
                        }
                        case 1 -> {
                            if (eg == 0) {
                                closeAlarm(a, tankId, stationId, null);
                            }
                        }
                        default -> {
                        }
                    }
                }
                if (tl != 0) {
                    openAlarm(tank, 7, "LEAK");
                }
                if (cg != 0) {
                    openAlarm(tank, cg, "OIL_DENSITY");
                }
                if (ttemp != 0) {
                    openAlarm(tank, ttemp, "FIRE");
                }
                if (level != 0) {
                    openAlarm(tank, level, null);
                }
                if (eg != 0) {
                    openAlarm(tank, 1, null);
                }
            }
            if (height.signum() != 0) {
                tank.set("CurrentHeight", height).set("ElectrostaticGrounding", eg).set("Tankleakage", tl).set("CombustibleGas", cg)
                    .set("Tankhightemperature", ttemp).set("TankLevel", level);
                String last = Conv.toDateTime(tank.get("UpdateTime")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH"));
                tank.set("UpdateTime", now());
                Map<String, Object> v = p("v", tank.i("TankVolumeID"), "h", height);
                Row up = first(db.list("TankVolume", "{TankVolumeID} = :v AND {Height} > :h ORDER BY {Height}", v));
                Row down = first(db.list("TankVolume", "{TankVolumeID} = :v AND {Height} < :h ORDER BY {Height} DESC", v));
                if (up != null && down != null) {
                    BigDecimal slope = up.dec("Volume").subtract(down.dec("Volume")).divide(up.dec("Height").subtract(down.dec("Height")), 28, RoundingMode.HALF_EVEN);
                    tank.set("CurrentOil", down.dec("Volume").add(slope.multiply(height.subtract(down.dec("Height")))).setScale(2, RoundingMode.HALF_EVEN));
                }
                Row real = db.first("TankRealData", "{TankID} = :k AND {StationID} = :s", p("k", tankId, "s", stationId));
                if (real != null) {
                    db.update("TankRealData", real.set("CurrentHeight", height).set("CurrentVolume", tank.dec("CurrentOil")));
                } else {
                    db.insert("TankRealData", Row.of("StationID", stationId, "TankID", tankId, "CompanyID", tank.i("CompanyID"),
                        "CurrentHeight", height, "CurrentVolume", tank.dec("CurrentOil"), "TankName", tank.str("TankName"), "GradeID", tank.i("GradeID"),
                        "GradeName", deal.getGraName(tank.i("GradeID")), "TankHeight", tank.dec("TotalHeight"), "TankVolume", tank.dec("TotalVolume"),
                        "UpdateTime", String.valueOf(CenterDeal.now())));
                }
                if (!last.equals(hour)) {
                    db.insert("TankHistory", Row.of("TankID", tankId, "CreateTime", now(), "Height", height,
                        "Volume", tank.dec("CurrentOil"), "StationID", stationId));
                }
                db.update("AirTank", tank);
            }
            tx.commit();
        } catch (RuntimeException ex) {
            log.writeLog("Cuve : " + ex.getMessage());
        }
    }

    private static Row first(List<Row> rows) {
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** DealTankHeight : bloc de jauge placé avant la trame de l'ancien protocole. */
    public void dealTankHeight(Row air, String oilHeight) {
        try {
            Row tank = tankOf(air);
            if (tank == null) {
                return;
            }
            String head = oilHeight.substring(0, 4);
            if ("FFFF".equals(head)) {
                dealTankAlarm(tank, asciiFloat(oilHeight.substring(4, 20)), oilHeight);
            } else if ("FF02".equals(head)) {
                String rest = oilHeight.substring(4);
                List<Row> tanks = db.list("AirTank", "{StationID} = :s AND {IsRun} = true", p("s", air.i("StationID")));
                int n = 0;
                while (!rest.isEmpty()) {
                    BigDecimal h = asciiFloat(rest.substring(0, 16));
                    if (tanks.size() > n) {
                        dealTankAlarm(tanks.get(n), h, rest);
                    }
                    rest = rest.substring(24);
                    n++;
                }
            } else {
                dealTankAlarm(tank, BigDecimal.valueOf(int16(oilHeight)).setScale(3), oilHeight);
            }
        } catch (RuntimeException ignored) {
            // bloc de jauge illisible : la trame reste traitée, comme l'original
        }
    }

    /** DealDeviceLocation : position $GPRMC, de ddmm.mmmm vers degrés décimaux. */
    public void location(String lng, String lat, String no) {
        try {
            double x = Double.parseDouble(lng);
            double y = Double.parseDouble(lat);
            double dx = (int) (x / 100.0) + (x / 100.0 - (int) (x / 100.0)) * 100.0 / 60.0;
            double dy = (int) (y / 100.0) + (y / 100.0 - (int) (y / 100.0)) * 100.0 / 60.0;
            for (Row a : db.list("AirDevice", "{DeviceNo} = :n AND {IsRun} = true", p("n", no))) {
                db.update("AirDevice", a.set("lng", Conv.num(dx)).set("lat", Conv.num(dy)));
            }
        } catch (RuntimeException ignored) {
            // position invalide : ignorée comme l'original
        }
    }

    public int accountType(String asn) {
        if (asn.startsWith("FFFFFF")) {
            Row d = db.first("JoyFill_fleetDriver", "{asn} = :a", p("a", asn));
            return d == null ? 0 : d.i("accountType");
        }
        return 0;
    }

    /** StationSideDeal : montants et volumes pas encore débités. */
    private double[] pending(String asn) {
        try {
            Row r = db.queryFirst(db.sql("OilTransactions", "SELECT COALESCE(SUM({TruelyPay}), 0) a, COALESCE(SUM({Volume}), 0) v FROM [OilTransactions] WHERE {Flg} = '0' AND {Asn} = :a"), p("a", asn));
            return new double[] {Conv.round(r.d("a"), 3), Conv.round(r.d("v"), 3)};
        } catch (RuntimeException ex) {
            return new double[] {0, 0};
        }
    }

    /** CardQuery et CardQuery_liters : solde en montant ou en litres. */
    public Query cardQuery(String asn, String oilCode, Row air, boolean liters) {
        Query q = new Query();
        try {
            CenterDeal.CusCard c = deal.getCusCardInfo(asn);
            if (!c.ok() || c.cardInfos().i("CardStatusTypeID") != 0) {
                q.result = 7;
                throw new IllegalStateException(c.error());
            }
            Row info = c.cardInfos();
            Row acc = c.accounts();
            if (secretSame) {
                Row st = db.first("Stations", "{StationID} = :s", p("s", air.i("StationID")));
                if (st == null) {
                    q.result = 8;
                    throw new IllegalStateException("Station introuvable");
                }
                if (asn.startsWith("FFFFFF") && st.i("CompanyID") != info.i("CompanyID")) {
                    q.result = 8;
                    throw new IllegalStateException("Société différente");
                }
            }
            String limitOil = info.str("LimitOil");
            if (filled(limitOil) && !"0".equals(limitOil) && !limitOil.equals(oilCode)) {
                q.result = 6;
                throw new IllegalStateException("Carburant non autorisé");
            }
            q.balance = dec(acc.d("Balance")).setScale(3, RoundingMode.HALF_EVEN);
            q.asn = info.s("ASN");
            if (q.balance.signum() <= 0) {
                q.result = 1;
                throw new IllegalStateException("Solde insuffisant");
            }
            q.pass = info.str("CipherFlg") == null ? "0000" : info.s("CipherFlg");
            double[] side = pending(asn);
            int times = acc.i("TotalTimesDaily");
            BigDecimal day = dec(Conv.round(acc.d("TotalAmountDaily") + side[0], 3));
            BigDecimal month = dec(Conv.round(acc.d("TotalAmountMonthly") + side[0], 3));
            // l'original additionne le montant, et non le volume, dans le calcul en litres
            BigDecimal volume = dec(Conv.round(acc.d("TotalVolumeMonthly") + (liters ? side[0] : side[1]), 3));
            double la = info.d("LimitAmount");
            double lam = info.d("LimitAmountMonthly");
            double lv = info.d("LimitVolume");
            double lvm = info.d("LimitVolumeMonthly");
            if (info.i("LimitTime") > 0 && times >= info.i("LimitTime")) {
                q.result = 9;
                throw new IllegalStateException("Limite journalière de passages");
            }
            if (la > 0 && day.compareTo(dec(la)) >= 0) {
                q.result = 2;
                throw new IllegalStateException("Limite journalière de montant");
            }
            if (lam > 0 && month.compareTo(dec(lam)) >= 0) {
                q.result = 4;
                throw new IllegalStateException("Limite mensuelle de montant");
            }
            if (lvm > 0 && volume.compareTo(dec(lvm)) >= 0) {
                q.result = 5;
                throw new IllegalStateException("Limite mensuelle de litres");
            }
            q.maxVolume = liters ? dec(Conv.round(acc.d("Balance"), 3)) : BigDecimal.ONE.negate();
            if (lv > 0 && lvm == 0) {
                q.maxVolume = dec(lv);
            }
            if (lv == 0 && lvm > 0) {
                q.maxVolume = dec(lvm).subtract(volume);
            }
            if (lv > 0 && lvm > 0) {
                q.maxVolume = min(dec(lvm).subtract(volume), dec(lv));
            }
            if (liters) {
                Row tank = tankOf(air);
                Row grade = db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", tank.i("GradeID")));
                Row price = db.first("PriceInfos", "{StationID} = :s AND {GradeSpecificID} = :g", p("s", air.i("StationID"), "g", grade.i("GradeSpecificID")));
                q.maxAmount = q.maxVolume.multiply(dec(price.d("NewPPU"))).setScale(3, RoundingMode.HALF_EVEN);
            } else {
                q.maxAmount = dec(acc.d("Balance")).setScale(3, RoundingMode.HALF_EVEN);
            }
            if (la > 0 && lam == 0) {
                q.maxAmount = min(q.maxAmount, dec(la).subtract(day));
            }
            if (la == 0 && lam > 0) {
                q.maxAmount = min(q.maxAmount, dec(lam).subtract(month));
            }
            if (la > 0 && lam > 0) {
                q.maxAmount = min(q.maxAmount, min(dec(la).subtract(day), dec(lam).subtract(month)));
            }
            q.result = 0;
        } catch (RuntimeException ex) {
            log.writeLog("Contrôle de carte : " + ex.getMessage());
        }
        return q;
    }

    /** Cmd_CreditCheck : autorisation de la carte sur l'ancien protocole ; ci.suspendType porte le refus. */
    public Credit creditCheck(String cache, Row device) {
        Credit ci = new Credit();
        try {
            CenterDeal.CusCard c = deal.getCusCardInfo(cache.substring(14, 34));
            if (!c.ok()) {
                throw new IllegalStateException(c.error());
            }
            Row info = c.cardInfos();
            Row acc = c.accounts();
            ci.asn = info.s("ASN");
            String confine = info.s("UseConfine");
            ci.pass = info.str("CipherFlg") == null ? "9999" : pad(info.s("CipherFlg"), 4);
            ci.limitVehicleNo = info.b("isLimitVehicleNo") ? 0 : 255;
            String plate = info.s("LimitVehicleNo");
            ci.vehicleNo = !plate.isEmpty() ? (AirProto.str(plate.getBytes(AirProto.ANSI)) + "202020202020202020202020202020").substring(0, 30)
                : "202020202020202020202020202020";
            ci.mileageTolerance = info.i("MileageTolerance");
            ci.suspendType = info.i("CardStatusTypeID");
            checkZone(ci, info.i("LimitTypeID"), confine, device);
            String limitOil = info.str("LimitOil");
            if (filled(limitOil) && !"0".equals(limitOil) && !"-1".equals(limitOil)) {
                Row g = db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", device.i("OilID")));
                if (g == null || !limitOil.equals(g.str("Code"))) {
                    ci.suspendType = 6;
                }
            }
            ci.preferentialPrice = dec(info.d("PreferentialPrice")).setScale(2, RoundingMode.HALF_EVEN);
            ci.discount = dec(info.d("Discount")).setScale(0, RoundingMode.HALF_EVEN);
            double balance = Conv.round(acc.d("Balance"), 2);
            int times = acc.i("TotalTimesDaily");
            BigDecimal day = dec(acc.d("TotalAmountDaily")).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal month = dec(acc.d("TotalAmountMonthly")).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal volume = dec(acc.d("TotalVolumeMonthly")).setScale(2, RoundingMode.HALF_EVEN);
            ci.mileageLastest = dec(acc.d("MileageLastest"));
            ci.volumeLastest = dec(acc.d("VolumeLastest"));
            double[] side = pending(ci.asn);
            balance -= Conv.round(side[0], 2);
            day = day.add(dec(side[0]));
            // l'original ajoute le volume en attente au cumul mensuel en montant
            month = month.add(dec(side[1]));
            if (balance <= 0) {
                balance = 0;
            }
            double la = info.d("LimitAmount");
            double lam = info.d("LimitAmountMonthly");
            double lv = info.d("LimitVolume");
            double lvm = info.d("LimitVolumeMonthly");
            if (info.i("LimitTime") > 0 && times >= info.i("LimitTime")) {
                ci.suspendType = 7;
                throw new IllegalStateException("Limite journalière de passages");
            }
            if (la > 0 && day.compareTo(dec(la)) >= 0) {
                ci.suspendType = 8;
                throw new IllegalStateException("Limite journalière de montant");
            }
            if (lam > 0 && month.compareTo(dec(lam)) >= 0) {
                ci.suspendType = 9;
                throw new IllegalStateException("Limite mensuelle de montant");
            }
            if (lvm > 0 && volume.compareTo(dec(lvm)) >= 0) {
                ci.suspendType = 10;
                throw new IllegalStateException("Limite mensuelle de litres");
            }
            ci.maxVolume = BigDecimal.ONE.negate();
            if (lv > 0 && lvm == 0) {
                ci.maxVolume = dec(lv);
            }
            if (lv == 0 && lvm > 0) {
                ci.maxVolume = dec(lvm).subtract(volume);
            }
            if (lv > 0 && lvm > 0) {
                ci.maxVolume = min(dec(lvm).subtract(volume), dec(lv));
            }
            if (ci.maxVolume.compareTo(BigDecimal.ONE) < 0 && ci.maxVolume.compareTo(BigDecimal.ONE.negate()) != 0) {
                ci.suspendType = 10;
                throw new IllegalStateException("Volume trop faible");
            }
            ci.maxAmount = dec(balance).setScale(2, RoundingMode.HALF_EVEN);
            if (la > 0 && lam == 0) {
                ci.maxAmount = min(ci.maxAmount, dec(la).subtract(day));
            }
            if (la == 0 && lam > 0) {
                ci.maxAmount = min(ci.maxAmount, dec(lam).subtract(month));
            }
            if (la > 0 && lam > 0) {
                ci.maxAmount = min(ci.maxAmount, min(dec(la).subtract(day), dec(lam).subtract(month)));
            }
            ci.balance = dec(balance).setScale(2, RoundingMode.HALF_EVEN);
            if (ci.balance.signum() <= 0) {
                ci.suspendType = 2;
            }
        } catch (RuntimeException ignored) {
            // ci.suspendType indique déjà le motif, comme l'original
        }
        return ci;
    }

    /** Restriction de la carte à une région (1), une ville (2) ou une station (3). */
    private void checkZone(Credit ci, int limitType, String confine, Row device) {
        if (limitType < 1 || limitType > 3) {
            return;
        }
        int refused = 10 + limitType;
        Row st = device == null ? null : db.first("Stations", "{StationID} = :s", p("s", device.i("StationID")));
        Row city = st == null || limitType == 3 ? null : db.first("Cities", "{CityID} = :c", p("c", st.i("CityID")));
        Row region = city == null || limitType != 1 ? null : db.first("Regions", "{RegionID} = :r", p("r", city.i("RegionID")));
        String id = switch (limitType) {
            case 1 -> region == null ? null : region.s("RegionID");
            case 2 -> city == null ? null : city.s("CityID");
            default -> st == null ? null : st.s("StationID");
        };
        if (id == null || !confine.contains(id)) {
            ci.suspendType = refused;
            throw new IllegalStateException("Zone non autorisée");
        }
    }

    private void addScoreLog(int id, BigDecimal current, BigDecimal delta, BigDecimal balance) {
        try {
            db.insert("ScoreLog", Row.of("CreateTime", now(), "CurrentScore", current, "DealScore", delta,
                "CustomerAccountID", id, "ScoreBalance", balance));
        } catch (RuntimeException ignored) {
            // le journal des points n'interrompt pas l'opération
        }
    }

    private void currAuth(int authId, BigDecimal amount, int stationId) {
        try {
            Row auth = db.first("AuthorityMess", "{ID} = :i", p("i", authId));
            if (auth == null) {
                return;
            }
            int user = auth.i("User_IDMess");
            Row rate = db.first("EmpScoreRate", "{AuthID} = :a", p("a", user));
            if (rate != null) {
                BigDecimal delta = amount.multiply(rate.dec("Rate"));
                for (Row e : db.list("Employees", "{StationID} = :s AND {PostCode} = :p", p("s", stationId, "p", String.valueOf(user)))) {
                    BigDecimal current = e.dec("Score");
                    e.set("Score", current.add(delta));
                    db.update("Employees", e);
                    addScoreLog(e.i("EmployeeID"), current, delta, e.dec("Score"));
                }
            }
            if (auth.i("CurrAuthID") != 0) {
                currAuth(auth.i("CurrAuthID"), amount, stationId);
            }
        } catch (RuntimeException ignored) {
            // les points des responsables n'interrompent pas l'opération
        }
    }

    private void empScore(String phone, BigDecimal amount) {
        try {
            Row emp = db.first("Employees", "{Telephone} = :t", p("t", phone));
            if (emp == null) {
                throw new IllegalStateException("Employé introuvable");
            }
            int auth = Conv.toInt(emp.str("PostCode"));
            Row rate = db.first("EmpScoreRate", "{AuthID} = :a", p("a", auth));
            if (rate != null) {
                BigDecimal current = emp.dec("Score");
                BigDecimal delta = amount.multiply(rate.dec("Rate"));
                emp.set("Score", current.add(delta));
                db.update("Employees", emp);
                addScoreLog(emp.i("EmployeeID"), current, delta, emp.dec("Score"));
            }
            Row mess = db.first("AuthorityMess", "{User_IDMess} = :a", p("a", auth));
            if (mess != null && mess.i("CurrAuthID") != 0) {
                currAuth(mess.i("CurrAuthID"), amount, emp.i("StationID"));
            }
        } catch (RuntimeException ignored) {
            // les points des employés n'interrompent pas l'opération
        }
    }

    private void dealScore(Row oil) {
        try {
            Row grade = db.first("GradeSpecifics", "{GradeSpecificID} = :g", p("g", oil.i("GradeSpecificID")));
            if (grade == null) {
                throw new IllegalStateException("Carburant introuvable");
            }
            Row rate = db.first("ScoreRate", "{ScoreType} = 0 AND {OilTypeID} = :o", p("o", Conv.toInt(grade.str("Code"))));
            Row info = db.first("CustomerCardInfos", "{ASN} = :a", p("a", oil.s("Asn")));
            if (info == null) {
                throw new IllegalStateException("Utilisateur introuvable");
            }
            Row cus = db.first("Customers", "{CustomerID} = :c", p("c", info.i("CustomerID")));
            if (cus == null) {
                throw new IllegalStateException("Utilisateur introuvable");
            }
            BigDecimal pay = dec(oil.d("TruelyPay"));
            if (rate != null) {
                BigDecimal current = info.dec("Score");
                BigDecimal delta = rate.dec("Rate").multiply(pay);
                info.set("Score", current.add(delta));
                db.update("CustomerCardInfos", info);
                addScoreLog(info.i("CustomerAccountID"), current, delta, info.dec("Score"));
            }
            if (filled(cus.str("EmpPhone"))) {
                empScore(cus.str("EmpPhone"), pay);
            }
        } catch (RuntimeException ignored) {
            // les points n'interrompent pas l'opération
        }
    }

    private boolean dealGroup(int groupId, Row oil, Row emp) {
        try {
            Row g = db.first("GroupCardInfos", "{GroupID} = :g", p("g", groupId));
            if (g == null) {
                throw new IllegalStateException("Groupe introuvable");
            }
            CenterDeal.GroupCard c = deal.getGroupCardInfo(g.s("ASN"));
            if (!c.ok()) {
                throw new IllegalStateException(c.error());
            }
            Row acc = c.accounts();
            Row info = c.cardInfos();
            double pay = oil.d("TruelyPay");
            acc.set("Balance", acc.d("Balance") - pay).set("UpdateDatetime", now());
            info.set("UsedAmountMonthly", info.d("UsedAmountMonthly") + pay).set("UpdateDate", now());
            db.update("GroupAccounts", acc);
            db.update("GroupCardInfos", info);
            return deal.addAccountLog(acc.s("AccountNo"), pay, info.s("ASN"), acc.d("Balance"), 11, BigDecimal.ZERO, oil.d("Volume"), emp, 1,
                oil.s("TrueTime"), oil.i("StationID")) != 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Row employeeFor(Row oil, boolean fallback) {
        Row e = db.first("Employees", "{EmployeeID} = :e", p("e", oil.i("EmployeeID")));
        if (e == null && fallback) {
            e = db.first("Employees", "{StationID} = :s", p("s", oil.i("StationID")));
            e = e == null ? db.first("Employees") : e;
        }
        if (e == null) {
            throw new IllegalStateException("Employé introuvable");
        }
        return e;
    }

    /** DealCustomer : débit du compte de la carte. Renvoie "" si tout va bien, sinon le motif de l'échec. */
    public String dealCustomer(Row oil) {
        try {
            String asn = oil.s("Asn");
            String time = oil.s("TrueTime");
            if (db.first("AccountServiceLogs", "{ASN} = :a AND {TransDatetime} = :t", p("a", asn, "t", Conv.toDateTime(time))) != null) {
                return "";
            }
            double pay = oil.d("TruelyPay");
            double vol = oil.d("Volume");
            String prefix = asn.substring(0, 6);
            if ("000000".equals(prefix)) {
                return "";
            }
            if ("010004".equals(prefix)) {
                CenterDeal.EmpCard c = deal.getEmpCardInfo(asn);
                if (!c.ok()) {
                    throw new IllegalStateException(c.error());
                }
                Row acc = c.accounts();
                acc.set("Balance", acc.d("Balance") - Conv.round(pay, 2)).set("UpdateDatetime", now());
                Row emp = employeeFor(oil, false);
                db.update("CustomerAccounts", acc);
                if (deal.addAccountLog(acc.s("AccountNo"), pay, asn, oil.d("BAL"), 11, BigDecimal.ZERO, vol, emp, 1, time, oil.i("StationID")) == 0) {
                    throw new IllegalStateException("Écriture du journal impossible");
                }
                return "";
            }
            CenterDeal.CusCard c = deal.getCusCardInfo(asn);
            if (!c.ok()) {
                throw new IllegalStateException(c.error());
            }
            Row acc = c.accounts();
            Row info = c.cardInfos();
            double p2 = Conv.round(pay, 2);
            double v2 = Conv.round(vol, 2);
            acc.set("Balance", Conv.round(acc.d("Balance"), 2) - p2)
                .set("TotalAmountDaily", acc.d("TotalAmountDaily") + p2)
                .set("TotalAmountMonthly", acc.d("TotalAmountMonthly") + p2)
                .set("TotalVolumeMonthly", acc.d("TotalVolumeMonthly") + v2)
                .set("VolumeLastest", v2).set("TransTime", time).set("UpdateDatetime", now());
            if (oil.i("TType") == 0) {
                acc.set("TotalTimesDaily", acc.i("TotalTimesDaily") + 1);
            }
            db.update("CustomerAccounts", acc);
            info.set("UpdateDatetime", now()).set("UsedAmountMonthly", info.d("UsedAmountMonthly") + p2);
            db.update("CustomerCardInfos", info);
            Row emp = employeeFor(oil, true);
            if (deal.addAccountLog(acc.s("AccountNo"), pay, asn, oil.d("BAL"), 11, BigDecimal.ZERO, vol, emp, 1, time, oil.i("StationID")) == 0) {
                throw new IllegalStateException("Écriture du journal impossible");
            }
            dealScore(oil);
            if ("010013".equals(prefix) && !dealGroup(info.i("GroupID"), oil, emp)) {
                throw new IllegalStateException("Échec du traitement du groupe");
            }
            if ("FFFFFF".equals(prefix)) {
                Row driver = db.first("JoyFill_fleetDriver", "{asn} = :a", p("a", asn));
                if (driver != null) {
                    if (db.first("PreCard_M", "{teamId} = :t", p("t", driver.i("teamId"))) == null) {
                        throw new IllegalStateException("Carte de flotte introuvable");
                    }
                    driver.set("balance", driver.i("accountType") == 2 ? driver.d("balance") - vol : acc.d("Balance"));
                    db.update("JoyFill_fleetDriver", driver);
                }
            }
            return "";
        } catch (RuntimeException ex) {
            String m = ex.getMessage();
            return m == null || m.isEmpty() ? "Erreur" : m;
        }
    }

    /** DealTankVoume : sortie de stock de la cuve de l'appareil. */
    public boolean dealTankVolume(Row air, Row oil) {
        try {
            Row tank = tankOf(air);
            if (tank == null) {
                throw new IllegalStateException("Cuve introuvable");
            }
            BigDecimal vol = dec(oil.d("Volume"));
            BigDecimal current = tank.dec("CurrentOil");
            db.insert("TankStock", Row.of("CreateTime", CenterDeal.now(), "StationID", air.i("StationID"),
                "StationName", deal.getStationName(air.i("StationID")), "StockVolume", vol.signum() <= 0 ? BigDecimal.ZERO : vol.negate(),
                "TankID", tank.i("ID"), "TankName", tank.str("TankName"), "StartStock", current, "FinalStock", current.subtract(vol),
                "CompanyID", tank.i("CompanyID")));
            tank.set("CurrentOil", current.subtract(vol));
            Row real = db.first("TankRealData", "{TankID} = :k AND {StationID} = :s", p("k", tank.i("ID"), "s", tank.i("StationID")));
            if (real != null) {
                db.update("TankRealData", real.set("CurrentVolume", real.dec("CurrentVolume").subtract(vol)));
            }
            db.update("AirTank", tank);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Copie de la consommation dans le journal de la carte de flotte. */
    public void fleetRecord(Row oil, Row driver) {
        double vol = oil.d("Volume");
        double amn = oil.d("AMN");
        boolean litres = driver.i("accountType") == 2;
        double change = litres ? vol : amn;
        db.insert("JoyFill_fleetCardRecord", Row.of("companyId", driver.get("companyId"), "companyName", driver.get("companyName"),
            "stationId", driver.get("stationId"), "stationName", driver.get("stationName"), "teamId", driver.get("teamId"), "teamName", driver.get("teamName"),
            "consumeStationId", oil.i("StationID"), "consumeStationName", deal.getStationName(oil.i("StationID")), "driverId", driver.i("id"),
            "driverName", driver.get("driverName"), "mCardID", oil.s("Asn"), "numberPlate", driver.get("numberPlate"), "gunId", oil.i("GunId"),
            "liters", vol, "totalLiters", oil.d("VolumeTotal"), "beforeBalance", driver.d("balance"), "changeAmount", change,
            "afterBalance", driver.d("balance") - change, "changeTime", oil.s("TrueTime"), "energyId", oil.i("GradeSpecificID"),
            "energyName", deal.getGraName(oil.i("GradeSpecificID")), "accountType", driver.i("accountType")));
    }
}
