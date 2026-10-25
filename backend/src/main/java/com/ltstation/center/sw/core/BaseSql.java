package com.ltstation.center.sw.core;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BaseSql (DataBaseDeal) : données des trois tableaux de la fenêtre principale (dernières
 * transactions, totaux par employé, état des pistolets) et station locale.
 */
public final class BaseSql {

    private BaseSql() {
    }

    private static Sugar db() {
        return SW.db();
    }

    /** GetOilData : 50 dernières transactions du poste en cours (Shift = "1"). */
    public static List<Row> GetOilData(int deal) {
        String order = deal == 1 ? "{PayMentDealID}, {TrueTime} DESC" : "{TrueTime} DESC";
        List<Row> rows = db().list("OilTransactions", "{Shift} = '1' ORDER BY " + order + " LIMIT 50", null);
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            out.add(SW.dto().make("OilDataLately", "ID", r.i("ID"), "GunID", r.i("POSP"), "Volume", Conv.toDecimal(r.d("Volume")),
                "TruePay", Conv.toDecimal(r.d("TruelyPay")), "TrueTime", Conv.toDateTime(r.get("TrueTime")).format(Conv.STD),
                "PayMentDealID", r.i("PayMentDealID") != 0));
        }
        return out;
    }

    /** GetTotalOilData : volume et montant du poste en cours par employé. */
    public static List<Row> GetTotalOilData() {
        List<Row> rows = db().query("SELECT employee_id, sum(volume) AS sv, sum(truely_pay) AS sa FROM oil_transactions "
            + "WHERE shift = '1' AND employee_id <> 0 GROUP BY employee_id", null);
        DecimalFormat money = new DecimalFormat(IniInfo.MoneyFormat);
        DecimalFormat vol = new DecimalFormat(IniInfo.VolumeFormat);
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            out.add(SW.dto().make("TotalOilDataLately", "EmpName", GetEmpName(r.i("employee_id")),
                "TotalAmount", money.format(Conv.toDecimal(r.get("sa"))), "TotalVolume", vol.format(Conv.toDecimal(r.get("sv")))));
        }
        return out;
    }

    private static String GetOilNameByCode(int code) {
        try {
            Row g = db().first("GradeSpecifics", "{Code} = :c", Sugar.p("c", String.valueOf(code)));
            return g == null ? "" : g.s("Name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** GetNozStatue : pistolets avec carburant, prix en vigueur et dernière transaction. */
    public static List<Row> GetNozStatue() {
        try {
            List<Row> out = new ArrayList<>();
            for (Row gun : db().list("GunConfig", "ORDER BY {GUNCODE}", null)) {
                String oil = "";
                String ppu = "";
                Row tank = db().first("tankConfig", "{ID} = :id", Sugar.p("id", gun.i("TANKCODE")));
                if (tank != null) {
                    oil = GetOilNameByCode(tank.i("OilCode"));
                    Row price = db().first("PriceInfos", "{Code} = :c", Sugar.p("c", String.valueOf(tank.i("OilCode"))));
                    if (price != null) {
                        ppu = LocalDateTime.now().isAfter(Conv.toDateTime(price.get("ValidateTime")))
                            ? Conv.toStr(price.get("NewPPU")) : Conv.toStr(price.get("CurrentPPU"));
                    }
                }
                Row last = db().first("OilTransactions", "{GunId} = :g ORDER BY {TrueTime} DESC", Sugar.p("g", gun.i("GUNCODE")));
                Row s = SW.dto().make("GunStatue");
                s.put("GunID", String.valueOf(gun.i("GUNCODE")));
                if (last != null) {
                    s.put("LastTime", Conv.toStr(last.get("TrueTime")));
                    s.put("VolumeTotal", Conv.toStr(last.get("VolumeTotal")));
                    s.put("LastAmount", Conv.toStr(last.get("TruelyPay")));
                    s.put("LastVolume", Conv.toStr(last.get("Volume")));
                } else {
                    s.put("LastTime", "");
                    s.put("VolumeTotal", "");
                    s.put("LastAmount", "");
                    s.put("LastVolume", "");
                }
                s.put("OilType", oil);
                s.put("PPU", ppu);
                s.put("Statue", MultiLanguage.getText("Unknow"));
                out.add(s);
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Row GetStation() {
        return db().first("Stations");
    }

    private static String GetEmpName(int id) {
        Row e = db().first("Employees", "{EmployeeID} = :e", Sugar.p("e", id));
        return e == null ? "" : e.s("Name");
    }

    /** Valeur décimale ou zéro. */
    static BigDecimal dec(Object o) {
        return o == null ? BigDecimal.ZERO : Conv.toDecimal(o);
    }
}
