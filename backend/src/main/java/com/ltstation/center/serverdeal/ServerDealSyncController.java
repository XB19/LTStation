package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (2/…) : téléchargements périodiques des stations (clients,
 * cartes, comptes, groupes, versions, carburants, prix, listes noires/blanches/grises, coupons,
 * tickets, employés, points de distribution, transports) et remontée de l'état des cuves.
 */
@RestController
@RequestMapping("/ServerDeal")
public class ServerDealSyncController extends ServerDealBase {

    private static final DateTimeFormatter FFF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final boolean secretSame;

    public ServerDealSyncController(@Qualifier("centerSugar") Sugar db, CenterDeal deal,
                                    @Value("${app.discount-per-company:true}") boolean secretSame) {
        super(db, deal);
        this.secretSame = secretSame;
    }

    private String page(String cls, String table, String where, Object[] params, String order, Row modal) {
        Sugar.Page pg = db.page(table, where, p(params), order, modal.i("Start"), 100);
        return okJson(ent(cls, pg.rows()), pg.totalPage());
    }

    @RequestMapping("/GetCustomer")
    public String GetCustomer(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            return page("Customers", "Customers", secretSame ? "{CompanyID} = :c" : null,
                new Object[] {"c", st.i("CompanyID")}, "{CustomerID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private List<Row> cardInfos(Row modal, Row st) {
        String company = secretSame ? " AND {CompanyID} = :c" : "";
        Sugar.Page pg = modal.b("DowloadAll")
            ? db.page("CustomerCardInfos", secretSame ? "{CompanyID} = :c" : null, p("c", st.i("CompanyID")), "{CustomerID}",
                modal.i("Start"), 100)
            : db.page("CustomerCardInfos", "{UpdateDatetime} > :t" + company,
                p("c", st.i("CompanyID"), "t", LocalDateTime.now().minusHours(5)), "{UpdateDatetime} DESC", modal.i("Start"), 100);
        modal.put("__pages", pg.totalPage());
        return pg.rows();
    }

    @RequestMapping("/GetCustomerCardInform")
    public String GetCustomerCardInform(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            List<Row> list = cardInfos(modal, st);
            return okJson(ent("CustomerCardInfos", list), modal.i("__pages"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    private static String fff(Object v) {
        return v == null ? null : Conv.toDateTime(v).format(FFF);
    }

    @RequestMapping("/GetCustomerCardInformV2")
    public String GetCustomerCardInformV2(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            List<Row> out = new ArrayList<>();
            for (Row item : cardInfos(modal, st)) {
                Row v = deal.dto().shape("CustomerCardInfosV2", item);
                v.put("IssueDatetime", fff(item.get("IssueDatetime") == null ? Conv.MIN_DATE : item.get("IssueDatetime")));
                v.put("ValidDatetime", fff(item.get("ValidDatetime") == null ? Conv.MIN_DATE : item.get("ValidDatetime")));
                v.put("UpdateDatetime", fff(item.get("UpdateDatetime")));
                out.add(v);
            }
            return okJson(out, modal.i("__pages"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** Remarque : l'original filtre la société avec SecretSame == 0 pour les mises à jour récentes. */
    private List<Row> accounts(Row modal, Row st) {
        Sugar.Page pg = modal.b("DowloadAll")
            ? db.page("CustomerAccounts", secretSame ? "{CompanyID} = :c" : null, p("c", st.i("CompanyID")),
                "{CustomerAccountID} DESC", modal.i("Start"), 100)
            : db.page("CustomerAccounts", "{UpdateDatetime} > :t" + (!secretSame ? " AND {CompanyID} = :c" : ""),
                p("c", st.i("CompanyID"), "t", LocalDateTime.now().minusHours(5)), "{UpdateDatetime} DESC", modal.i("Start"), 100);
        modal.put("__pages", pg.totalPage());
        return pg.rows();
    }

    @RequestMapping("/GetCustomerAccount")
    public String GetCustomerAccount(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            List<Row> list = accounts(modal, st);
            return okJson(ent("CustomerAccounts", list), modal.i("__pages"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCustomerAccountV2")
    public String GetCustomerAccountV2(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            List<Row> out = new ArrayList<>();
            for (Row a : accounts(modal, st)) {
                Row v = deal.dto().shape("CustomerAccountsV2", a);
                for (String k : new String[] {"DepositDatetime", "ConsumeDatetime", "UpdateDatetime", "TransTime"}) {
                    v.put(k, fff(a.get(k)));
                }
                out.add(v);
            }
            return okJson(out, modal.i("__pages"));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGroup")
    public String GetGroup(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("Groups", "Groups", null, new Object[0], "{GroupID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGroupCardInfos")
    public String GetGroupCardInfos(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return modal.b("DowloadAll")
                ? page("GroupCardInfos", "GroupCardInfos", null, new Object[0], "{GroupID}", modal)
                : page("GroupCardInfos", "GroupCardInfos", "{UpdateDate} > :t", new Object[] {"t", LocalDateTime.now().minusHours(5)},
                    "{UpdateDate} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGroupAccounts")
    public String GetGroupAccounts(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return modal.b("DowloadAll")
                ? page("GroupAccounts", "GroupAccounts", null, new Object[0], "{GroupAccountID}", modal)
                : page("GroupAccounts", "GroupAccounts", "{UpdateDatetime} > :t",
                    new Object[] {"t", LocalDateTime.now().minusHours(5)}, "{UpdateDatetime} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetVersion")
    public String GetVersion(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            Row dv = db.first("DataVersions");
            if (dv == null) {
                throw fail("La configuration du centre est erronée, veuillez la vérifier");
            }
            return okJson(ent("DataVersions", dv));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetOilType")
    public String GetOilType(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return okJson(ent("GradeSpecifics", db.list("GradeSpecifics", "ORDER BY {Code} DESC", null)));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetPrice")
    public String GetPrice(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            List<Row> rows = db.query("SELECT sc.station_id, st.code, sc.current_ppu, sc.new_ppu, sc.current_density, sc.newdensity, "
                + "sc.validate_time FROM grade_specifics st LEFT JOIN price_infos sc ON st.grade_specific_id = sc.grade_specific_id "
                + "WHERE sc.station_id = :s", p("s", st.i("StationID")));
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                out.add(deal.dto().make("PriceDataShow", "StationID", r.get("station_id"), "Code", r.get("code"),
                    "CurrentPPU", Conv.toDecimal(r.get("current_ppu")), "NewPPU", Conv.toDecimal(r.get("new_ppu")),
                    "CurrentDensity", r.get("current_density"), "Newdensity", r.get("newdensity"),
                    "ValidateTime", Conv.toDateTime(r.get("validate_time")).format(Conv.STD)));
            }
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetBasicBlack")
    public String GetBasicBlack(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("BasicBlackLists", "BasicBlackLists", null, new Object[0], "{BasicBlackListID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetAppendBlack")
    public String GetAppendBlack(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("AppendBlackLists", "AppendBlackLists", null, new Object[0], "{AppendBlackListID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetDelBlack")
    public String GetDelBlack(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("DeleteBlackLists", "DeleteBlackLists", null, new Object[0], "{DeleteBlackListID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetWhite")
    public String GetWhite(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            return page("WhiteCardLists", "WhiteCardLists", "{StationID} = :s", new Object[] {"s", st.i("StationID")},
                "{ValidateTime} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetGray")
    public String GetGray(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("GrayCardLists", "GrayCardLists", null, new Object[0], "{GrayCardListID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCouponInfoes")
    public String GetCouponInfoes(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("CouponInfoes", "CouponInfoes", null, new Object[0], "{CouponInfoID} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetTicket")
    public String GetTicket(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("TicketManagements", "TicketManagements", null, new Object[0], "{TicketManagementID} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetEmployee")
    public String GetEmployee(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            Sugar.Page pg = db.page("Employees", "{StationID} = :s", p("s", st.i("StationID")), "{EmployeeID}", modal.i("Start"), 100);
            List<Row> list = ent("Employees", pg.rows());
            for (Row item : list) {
                item.put("Birthday", Conv.toDateTime(item.get("Birthday")).format(Conv.STD));
            }
            return okJson(list, pg.totalPage());
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetEmployeeCardInfoMessage")
    public String GetEmployeeCardInfoMessage(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = stationByCode(modal.str("Code"));
            return page("EmployeeCardInfos", "EmployeeCardInfos", "{StationID} = :s", new Object[] {"s", st.i("StationID")},
                "{EmployeeID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetDistributionPoint")
    public String GetDistributionPoint(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("DistributionPoints", "DistributionPoints", null, new Object[0], "{DistributionPointID}", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetTransportInfo")
    public String GetTransportInfo(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            stationByCode(modal.str("Code"));
            return page("TransportInfoes", "TransportInfoes", null, new Object[0], "{TransportInfoID} DESC", modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/UpLoadTankStatueDeal")
    public String UpLoadTankStatueDeal(@RequestBody(required = false) String body) {
        try {
            Row st = req(body);
            for (Row item : st.rows("list")) {
                Row ts = db.first("TankStatus", "{StationID} = :s AND {ID} = :id", p("s", st.i("StationID"), "id", item.i("ID")));
                boolean add = ts == null;
                if (add) {
                    ts = new Row();
                }
                ts.put("StationID", st.i("StationID"));
                ts.put("ID", item.i("ID"));
                ts.put("OilHeight", Conv.round(item.d("OilHeight"), 3));
                ts.put("OilVol", Conv.round(item.d("OilVol"), 3));
                ts.put("OilTemp", Conv.round(item.d("OilTemp"), 3));
                ts.put("Time", LocalDateTime.now());
                ts.put("TankHeight", Conv.round(item.d("Height"), 3));
                ts.put("TankVol", Conv.round(item.d("Vol"), 3));
                ts.put("OilCode", item.get("OilCode"));
                if (add) {
                    db.insert("TankStatus", ts);
                } else {
                    db.update("TankStatus", ts);
                }
            }
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }

    @RequestMapping("/UpLoadTankHistoryDeal")
    public String UpLoadTankHistoryDeal(@RequestBody(required = false) String body) {
        try {
            Row m = req(body);
            m.remove("ID");
            db.insert("TankMeasurements", m);
            return okEmpty();
        } catch (Exception ex) {
            return ret(false, ex.getMessage(), 0);
        }
    }
}
