package com.ltstation.center.serverdeal;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ServerDealController d'origine (1/…) : société, clé plateforme, transactions remontées par
 * les stations, codes App, commandes, points virtuels, points des cartes et fiche station.
 */
@RestController
@RequestMapping(value = "/ServerDeal", produces = ServerDealBase.TEXT)
public class ServerDealCoreController extends ServerDealBase {

    private final boolean secretSame;
    private final String platformKey;
    private final boolean stationControl;

    public ServerDealCoreController(@Qualifier("centerSugar") Sugar db, CenterDeal deal,
                                    @Value("${app.discount-per-company:true}") boolean secretSame,
                                    @Value("${app.server-deal.platform-key:}") String platformKey,
                                    @Value("${app.server-deal.station-control:false}") boolean stationControl) {
        super(db, deal);
        this.secretSame = secretSame;
        this.platformKey = platformKey;
        this.stationControl = stationControl;
    }

    @RequestMapping("/GetCompanyID")
    public String GetCompanyID(@RequestBody(required = false) String body) {
        try {
            Row modal = deal.dto().shape("WxMobileWeb.Controllers.CustomerParam", req(body));
            if (!secretSame) {
                modal.put("CompanyID", 0);
            } else {
                Row st = db.first("Stations", "{Code} = :c", p("c", modal.s("Code")));
                if (st == null) {
                    throw fail("Error");
                }
                modal.put("CompanyID", st.i("CompanyID"));
            }
            return okJson(modal);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetPlatformKey")
    public String GetPlatformKey(@RequestBody(required = false) String body) {
        try {
            return okText(platformKey);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetControl")
    public String GetControl(@RequestBody(required = false) String body) {
        try {
            return ret(stationControl, "", 0);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    /** GroupDeal : débite le compte du groupe d'une carte crédit / enfant. */
    private Row groupDeal(int groupId, Row modal) {
        try {
            Row gci = db.first("GroupCardInfos", "{GroupID} = :g AND :g <> 100000001 ORDER BY {ASN} DESC", p("g", groupId));
            if (gci == null) {
                throw fail("Le groupe n'existe pas");
            }
            CenterDeal.GroupCard g = deal.getGroupCardInfo(gci.s("ASN"));
            if (!g.ok()) {
                throw fail(g.error());
            }
            Row acc = g.accounts();
            acc.put("Balance", acc.d("Balance") - modal.d("TruelyPay"));
            acc.put("UpdateDatetime", modal.dt("TrueTime"));
            gci.put("UsedAmountMonthly", gci.d("UsedAmountMonthly") + modal.d("TruelyPay"));
            gci.put("UpdateDate", modal.dt("TrueTime"));
            db.update("GroupAccounts", acc);
            db.update("GroupCardInfos", gci);
            return acc;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** TransactionDeal : transaction de carburant remontée par une station (UploadTransaction). */
    @RequestMapping("/TransactionDeal")
    public String TransactionDeal(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                String text = reqText(body);
                Row modal = new Row(com.ltstation.center.legacy.Wire.parseRow(text));
                Row accounts = new Row();
                log.writeLog("Receive:" + text);
                Row oil = Row.of(
                    "Asn", modal.str("Asn"), "AMN", modal.d("AMN"), "BAL", modal.d("BAL"), "BankSID", modal.get("BankSID"),
                    "CarNumber", modal.get("CarNumber"), "Coupon", modal.get("Coupon"), "CouponValue", modal.get("CouponValue"),
                    "CTC", modal.get("CTC"), "Discount", modal.d("Discount"), "EmployeeID", modal.i("EmployeeID"),
                    "ErrorFlgID", modal.get("ErrorFlgID"), "Flg", "0", "GMAC", modal.get("GMAC"),
                    "GradeSpecificID", modal.i("GradeSpecificID"), "GTAC", modal.get("GTAC"), "GunId", modal.i("GunId"),
                    "Mileage", modal.get("Mileage"), "PaymentID", modal.i("Unit"), "POSTTC", modal.get("POSTTC"),
                    "PPU", modal.get("PPU"), "PreferentialPrice", modal.d("PreferentialPrice"), "PSAMASN", modal.get("PSAMASN"),
                    "PSAMTID", modal.get("PSAMTID"), "PSAMTTC", modal.get("PSAMTTC"), "StationID", modal.i("StationID"),
                    "TAC", modal.get("TAC"), "TruelyPay", modal.d("TruelyPay"), "TrueTime", modal.dt("TrueTime"),
                    "TType", modal.get("TType"), "Upload", "0", "Volume", modal.d("Volume"), "VolumeTotal", modal.get("VolumeTotal"));
                Row existing = db.first("OilTransactions", "{StationID} = :s AND {GunId} = :g AND {TrueTime} = :t",
                    p("s", oil.i("StationID"), "g", oil.i("GunId"), "t", oil.dt("TrueTime")));
                log.writeLog("Search:");
                if (existing != null) {
                    log.writeLog("Exit:");
                    return okEmpty();
                }
                if ("4".equals(modal.str("upload"))) {
                    db.insert("OilTransactions", oil);
                    tx.commit();
                    log.writeLog("4444444444444");
                    return okEmpty();
                }
                Row groupAccounts = new Row();
                String asn = modal.s("Asn");
                if (modal.d("AMN") > 0.0 && modal.d("Volume") > 0.0) {
                    if (asn.startsWith("010004")) {
                        CenterDeal.EmpCard e = deal.getEmpCardInfo(asn);
                        boolean emp = e.ok() && e.cardInfos().i("CardStatusTypeID") == 0;
                        log.writeLog("EmpGet:" + emp);
                        if (!e.ok()) {
                            throw fail(e.error());
                        }
                        if (!emp) {
                            throw fail("Ce compte n'est pas autorisé");
                        }
                        accounts = e.accounts();
                        boolean coupon = useAppCode(oil);
                        if (!coupon) {
                            accounts.put("Balance", accounts.d("Balance") - modal.d("TruelyPay"));
                        }
                        accumulate(accounts, modal);
                        accounts.put("TransTime", modal.dt("TrueTime"));
                        log.writeLog("Employee update:");
                    } else {
                        if ("00000000000000000000".equals(asn)) {
                            db.insert("OilTransactions", oil);
                            tx.commit();
                            log.writeLog("00000000000000000000");
                            return okEmpty();
                        }
                        if (asn.startsWith("110003")) {
                            Row carUser = db.first("CarUser", "{Asn} = :a", p("a", asn));
                            if (carUser == null) {
                                throw fail("L'utilisateur de la carte n'existe pas");
                            }
                            Row acc2 = db.first("CustomerAccounts", "{CustomerAccountID} = :id", p("id", carUser.i("CustomerAccountID")));
                            if (acc2 == null) {
                                throw fail("Le compte n'existe pas");
                            }
                            Row emp2 = db.first("Employees", "{EmployeeID} = :id", p("id", modal.i("EmployeeID")));
                            if (emp2 == null) {
                                throw fail("Erreur d'employé");
                            }
                            carUser.put("Balance", carUser.dec("Balance").subtract(modal.dec("TruelyPay")));
                            acc2.put("Balance", acc2.d("Balance") - modal.d("TruelyPay"));
                            carUser.put("UpdateTime", CenterDeal.now());
                            acc2.put("UpdateDatetime", LocalDateTime.now());
                            db.update("CarUser", carUser);
                            db.update("CustomerAccounts", acc2);
                            deal.addAccountLog(acc2.s("AccountNo"), Conv.round(modal.d("TruelyPay"), 2), carUser.s("Asn"),
                                carUser.d("Balance"), 11, BigDecimal.ZERO, 0.0, emp2, 1, oil.dt("TrueTime").format(Conv.STD), emp2.i("StationID"));
                            tx.commit();
                            return okEmpty();
                        }
                        CenterDeal.CusCard c = deal.getCusCardInfo(asn);
                        log.writeLog("Cus get:" + c.ok());
                        if (!c.ok()) {
                            throw fail(c.error());
                        }
                        accounts = c.accounts();
                        Row cardInfos = c.cardInfos();
                        Row customers = c.customers();
                        boolean coupon = useAppCode(oil);
                        if (!coupon) {
                            accounts.put("Balance", accounts.d("Balance") - modal.d("TruelyPay"));
                        }
                        accumulate(accounts, modal);
                        cardInfos.put("UsedAmountMonthly", cardInfos.d("UsedAmountMonthly") + modal.d("TruelyPay"));
                        cardInfos.put("UpdateDatetime", LocalDateTime.now());
                        log.writeLog("Cus update:");
                        String prefix = asn.substring(0, 6);
                        if (prefix.equals("010013") || prefix.equals("010011")) {
                            int groupId = cardInfos.i("GroupID");
                            if (groupId != 100000001) {
                                groupAccounts = groupDeal(groupId, modal);
                                if (groupAccounts == null) {
                                    throw fail("Le traitement du groupe a échoué");
                                }
                            }
                        }
                        log.writeLog("Group update:");
                        db.update("CustomerCardInfos", cardInfos);
                        log.writeLog("customerCardInfos update:");
                        Row scoreRate = null;
                        if (!secretSame) {
                            scoreRate = db.first("ScoreRate", "{OilTypeID} = :g", p("g", oil.i("GradeSpecificID")));
                        } else {
                            Row station = db.first("Stations", "{StationID} = :s", p("s", oil.i("StationID")));
                            if (station != null) {
                                scoreRate = db.first("ScoreRate", "{OilTypeID} = :g AND {CompanyID} = :c",
                                    p("g", oil.i("GradeSpecificID"), "c", station.i("CompanyID")));
                            }
                        }
                        if (scoreRate != null) {
                            BigDecimal d = scoreRate.i("ScoreType") == 0
                                ? Conv.toDecimal(oil.d("TruelyPay") / 100.0).multiply(scoreRate.dec("Rate"))
                                : Conv.toDecimal(oil.d("Volume")).divide(BigDecimal.valueOf(100)).multiply(scoreRate.dec("Rate"));
                            d = d.setScale(3, RoundingMode.HALF_EVEN);
                            cardInfos.put("Score", cardInfos.dec("Score").add(d));
                            db.insert("VirtualScoreLog", Row.of(
                                "ScoreID", cardInfos.i("CustomerID"), "FinalScore", cardInfos.dec("Score"),
                                "CreateTime", CenterDeal.now(), "OperateID", oil.i("EmployeeID"),
                                "ScoreName", customers.get("Name"), "ScorePhone", customers.get("Telephone"),
                                "ScoreType", 0, "ScoreValue", d, "Asn", cardInfos.get("ASN")));
                            db.update("CustomerCardInfos", cardInfos);
                        }
                        oil.put("PayMentDealID", 1);
                    }
                    int cardTypeId = switch (asn.substring(0, 6)) {
                        case "010001" -> 1;
                        case "010002" -> 2;
                        case "010011" -> 11;
                        case "010012" -> 12;
                        case "010013" -> 13;
                        default -> 4;
                    };
                    int paymentId = modal.i("Unit") / 16 + 1;
                    String dtime = oil.dt("TrueTime").format(Conv.STD);
                    log.writeLog("DTime:" + dtime);
                    Row asl = Row.of(
                        "ASN", asn, "AccountDatetime", LocalDateTime.now().format(Conv.STD), "AccountNo", accounts.get("AccountNo"),
                        "Amount", modal.d("TruelyPay"), "Balance", modal.d("BAL"), "CardServiceLogID", 1, "CardTypeID", cardTypeId,
                        "ChequeNo", "", "CreditCountID", "", "FeeAmount", modal.d("Volume"), "StationID", modal.i("StationID"),
                        "PaymentID", paymentId, "ProcessCode", "22", "OperatorName", "", "TerminalNo", "131000000001",
                        "TransactionTypeID", 11, "TransDatetime", dtime, "EmployeeID", modal.i("EmployeeID"));
                    if (paymentId == 11) {
                        Row def = db.first("DefPayments", "{PaymentDesc} = 'ChilrdCard'", null);
                        if (def != null) {
                            asl.put("PaymentID", def.i("PaymentID"));
                        }
                    }
                    if (asn.startsWith("010013")) {
                        asl.put("Balance", groupAccounts.d("Balance"));
                    }
                    db.insert("OilTransactions", oil);
                    log.writeLog("oilTransactions update:");
                    db.insert("AccountServiceLogs", asl);
                    log.writeLog("logs update:");
                    db.update("CustomerAccounts", accounts);
                    log.writeLog("customerAccounts update:");
                    tx.commit();
                    return okEmpty();
                }
                db.insert("OilTransactions", oil);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                log.writeLog("Error:" + ex.getMessage());
                tx.rollback();
                return err(ex);
            }
        }
    }

    /** Paiement par code App (PaymentID 160) : débite le code au lieu du compte. */
    private boolean useAppCode(Row oil) {
        if (oil.i("PaymentID") == 160 && !"".equals(oil.str("Coupon"))) {
            Row code = db.first("AppCodeUse", "{AppCodeShort} = :c AND {Asn} = :a AND {IsUsing} = 1",
                p("c", oil.str("Coupon"), "a", oil.str("Asn")));
            if (code != null) {
                code.put("UsingAmount", code.dec("UsingAmount").add(Conv.toDecimal(oil.d("TruelyPay"))));
                code.put("Amount", code.dec("Amount").subtract(Conv.toDecimal(oil.d("TruelyPay"))));
                if (code.dec("Amount").signum() <= 0) {
                    code.put("IsUsing", 2);
                }
                db.update("AppCodeUse", code);
                return true;
            }
        }
        return false;
    }

    private static void accumulate(Row acc, Row modal) {
        acc.put("TotalTimesDaily", acc.i("TotalTimesDaily") + 1);
        acc.put("TotalAmountDaily", acc.d("TotalAmountDaily") + modal.d("TruelyPay"));
        acc.put("TotalAmountMonthly", acc.d("TotalAmountMonthly") + modal.d("TruelyPay"));
        acc.put("TotalVolumeMonthly", acc.d("TotalVolumeMonthly") + modal.d("Volume"));
        acc.put("UpdateDatetime", LocalDateTime.now());
        acc.put("VolumeLastest", modal.d("Volume"));
        acc.put("MileageLastest", modal.d("Mileage"));
    }

    /** AppCodeDeal : la table AppCode n'existe pas dans la base Center d'origine (l'appel échoue). */
    @RequestMapping("/AppCodeDeal")
    public String AppCodeDeal(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row code = db.first("AppCode", "{Flag} = 0 AND ({Code} = :c OR {CodeFour} = :f) ORDER BY {OperateDate} DESC",
                    p("c", modal.str("Code"), "f", modal.str("CodeFour")));
                if (code != null) {
                    String res = okJson(code);
                    code.put("Flag", 1);
                    db.update("AppCode", code);
                    tx.commit();
                    return res;
                }
                throw fail("Le code d'opération n'existe pas");
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/AppCodeUserDeal")
    public String AppCodeUserDeal(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row use = db.first("AppCodeUse", "({IsUsing} = 0 OR {IsUsing} = 1) AND ({AppCode} = :c OR {AppCodeShort} = :c) ORDER BY {CreateTime}",
                p("c", modal.str("Code")));
            if (use != null) {
                Row ret = deal.dto().make("AppQueryReturn");
                CenterDeal.CusCard c = deal.getCusCardInfo(use.s("Asn"));
                if (c.ok()) {
                    ret.put("Asn", use.get("Asn"));
                    ret.put("Amount", use.dec("Amount"));
                    ret.put("Balance", use.dec("Balance"));
                    ret.put("Discount", c.cardInfos().dec("Discount"));
                    use.put("IsUsing", 1);
                    db.update("AppCodeUse", use);
                    return okJson(ret);
                }
                CenterDeal.EmpCard e = deal.getEmpCardInfo(use.s("Asn"));
                if (e.ok()) {
                    ret.put("Asn", use.get("Asn"));
                    ret.put("Amount", use.dec("Amount"));
                    ret.put("Balance", use.dec("Balance"));
                    ret.put("Discount", c.cardInfos().dec("Discount"));
                    use.put("IsUsing", 1);
                    db.update("AppCodeUse", use);
                    return okJson(ret);
                }
                throw fail("Card Error");
            }
            throw fail("Error");
        } catch (Exception ex) {
            log.writeLog("App Query Error：" + ex.getMessage());
            return err(ex);
        }
    }

    @RequestMapping("/CmdDowload")
    public String CmdDowload(@RequestBody(required = false) String body) {
        try {
            Row param = req(body);
            List<Row> list = db.list("CmdList", "{StationID} = :s AND {Dowload} = false AND {Deal} = 0 AND {CmdPlatType} = 3",
                p("s", param.i("StationID")));
            String res = okJson(list);
            for (Row item : list) {
                item.put("Deal", 99);
                item.put("Dowload", true);
            }
            db.updateAll("CmdList", list);
            return res;
        } catch (Exception ex) {
            return ret(false, enc(ex.getMessage()), 0);
        }
    }

    @RequestMapping("/GetScoreList")
    public String GetScoreList(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            StringBuilder w = new StringBuilder("1=1");
            if (!"".equals(modal.str("Name"))) {
                w.append(" AND {UserName} = :n");
            }
            if (!"".equals(modal.str("Phone"))) {
                w.append(" AND {Phone} = :ph");
            }
            if (modal.i("CompanyID") != 0) {
                w.append(" AND {CompanyID} = :c");
            }
            Sugar.Page page = db.page("VirtualScoreUser", w.toString(),
                p("n", modal.str("Name"), "ph", modal.str("Phone"), "c", modal.i("CompanyID")), null, modal.i("PageIndex"), 15);
            Row r = deal.dto().make("VirtualScoreRespone");
            r.put("ToatlPage", page.totalPage());
            r.put("data", deal.dto().shapeAll("Modal.VirtualScoreUser", page.rows()));
            return okJson(r);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/AddVirtualScore")
    public String AddVirtualScore(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row exists = db.first("VirtualScoreUser", "{UserName} = :u OR ({Phone} = :ph AND {CompanyID} = :c)",
                    p("u", modal.str("UserName"), "ph", modal.str("Phone"), "c", modal.i("CompanyID")));
                if (exists != null) {
                    throw fail("L'utilisateur existe déjà");
                }
                Row u = Row.of("UserName", modal.get("UserName"), "Phone", modal.get("Phone"), "CreateTime", CenterDeal.now(),
                    "CompanyID", modal.i("CompanyID"), "Score", BigDecimal.ZERO);
                Object id = db.insert("VirtualScoreUser", u);
                db.insert("VirtualScoreLog", Row.of("CreateTime", CenterDeal.now(), "FinalScore", BigDecimal.ZERO, "ScoreID", id,
                    "ScoreName", modal.get("UserName"), "ScorePhone", modal.get("Phone"), "ScoreType", 1,
                    "ScoreValue", BigDecimal.ZERO, "OperateID", modal.i("EID")));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/UpdateVirtualScore")
    public String UpdateVirtualScore(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row u = db.first("VirtualScoreUser", "{ID} = :id", p("id", modal.i("ScoreID")));
                if (u == null) {
                    throw fail("L'utilisateur n'existe pas");
                }
                Row dup = db.first("VirtualScoreUser", "{ID} <> :id AND ({UserName} = :u OR {Phone} = :ph)",
                    p("id", modal.i("ScoreID"), "u", modal.str("UserName"), "ph", modal.str("Phone")));
                if (dup != null) {
                    throw fail("Informations utilisateur en double");
                }
                u.put("UserName", modal.get("UserName"));
                u.put("Phone", modal.get("Phone"));
                db.update("VirtualScoreUser", u);
                db.insert("VirtualScoreLog", Row.of("CreateTime", CenterDeal.now(), "FinalScore", u.dec("Score"), "ScoreID", u.i("ID"),
                    "ScoreName", modal.get("UserName"), "ScorePhone", modal.get("Phone"), "ScoreType", 2,
                    "ScoreValue", BigDecimal.ZERO, "OperateID", modal.i("EID")));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/GetVirtualUser")
    public String GetVirtualUser(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row u = db.first("VirtualScoreUser", "{ID} = :id", p("id", modal.i("ID")));
            if (u == null) {
                throw fail("Empty");
            }
            return okJson(deal.dto().shape("Modal.VirtualScoreUser", u));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetVirtualScoreLog")
    public String GetVirtualScoreLog(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Sugar.Page page = db.page("VirtualScoreLog", "{ScoreID} = :s", p("s", modal.i("ScoreID")), "{CreateTime} DESC",
                modal.i("PageIndex"), 15);
            Row out = deal.dto().make("VirtualScoreLogShowList");
            List<Row> list = new ArrayList<>();
            for (Row item : page.rows()) {
                Row s = deal.dto().make("VirtualScoreLogShow");
                s.put("ID", item.i("ID"));
                s.put("ScoreID", item.i("ScoreID"));
                s.put("ScoreName", item.get("ScoreName"));
                s.put("ScorePhone", item.get("ScorePhone"));
                s.put("ScoreType", item.i("ScoreType"));
                s.put("ScoreValue", item.dec("ScoreValue"));
                s.put("FinalScore", item.dec("FinalScore"));
                s.put("CreateTime", item.l("CreateTime"));
                s.put("OperateName", deal.getEmployeeName(item.i("OperateID")));
                list.add(s);
            }
            out.put("list", list);
            out.put("TotalPage", page.totalPage());
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/DealVirtualScore")
    public String DealVirtualScore(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row modal = req(body);
                Row u = db.first("VirtualScoreUser", "{ID} = :id", p("id", modal.i("ScoreID")));
                if (u == null) {
                    throw fail("L'utilisateur de points virtuels n'existe pas");
                }
                int type;
                BigDecimal value = modal.dec("ScoreValue");
                switch (modal.i("ScoreType")) {
                    case 0 -> {
                        if (u.dec("Score").compareTo(value) >= 0) {
                            u.put("Score", u.dec("Score").subtract(value));
                            type = 3;
                        } else {
                            throw fail("Les points saisis dépassent la limite");
                        }
                    }
                    case 1 -> {
                        u.put("Score", u.dec("Score").add(value));
                        type = 4;
                    }
                    default -> type = -1;
                }
                db.insert("VirtualScoreLog", Row.of("ScoreID", modal.i("ScoreID"), "FinalScore", u.dec("Score"),
                    "CreateTime", CenterDeal.now(), "OperateID", modal.i("EID"), "ScoreName", u.get("UserName"),
                    "ScorePhone", u.get("Phone"), "ScoreType", type, "ScoreValue", value));
                db.update("VirtualScoreUser", u);
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/CardScoreLog")
    public String CardScoreLog(@RequestBody(required = false) String body) {
        try {
            Row page = req(body);
            Sugar.Page pg = db.page("VirtualScoreLog", "{Asn} = :a", p("a", page.str("Asn")), null, page.i("PageIndex"), 10);
            Row out = deal.dto().make("VirtualScoreLogShowList");
            out.put("TotalPage", pg.totalPage());
            List<Row> list = new ArrayList<>();
            for (Row item : pg.rows()) {
                list.add(deal.dto().make("VirtualScoreLogShow", "Asn", item.get("Asn"), "CreateTime", item.l("CreateTime"),
                    "FinalScore", item.dec("FinalScore"), "ScoreName", item.get("ScoreName"), "ScorePhone", item.get("ScorePhone"),
                    "ScoreValue", item.dec("ScoreValue"), "ScoreType", item.i("ScoreType")));
            }
            out.put("list", list);
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCardScoreUser")
    public String GetCardScoreUser(@RequestBody(required = false) String body) {
        try {
            Row page = req(body);
            String sql = "SELECT c.customer_id AS \"CustomerID\", c.name AS \"Name\", c.telephone AS \"Telephone\", "
                + "c.vehicle_no AS \"VehicleNo\", i.asn AS \"ASN\", i.score AS \"Score\", i.station_id AS \"StationID\" "
                + "FROM customers c, customer_card_infos i WHERE c.customer_id = i.customer_id";
            StringBuilder w = new StringBuilder("SELECT * FROM (" + sql + ") q WHERE 1=1");
            Map<String, Object> ps = p("n", page.str("QName"), "ph", page.str("QPhone"), "car", page.str("QCarNo"));
            if (secretSame) {
                List<Integer> ids = db.jdbc().queryForList("SELECT station_id FROM stations WHERE company_id = :c",
                    p("c", page.i("CompanyID")), Integer.class);
                w.append(" AND q.\"StationID\" IN (:ids)");
                ps.put("ids", ids.isEmpty() ? List.of(-2147483648) : ids);
            }
            if (!"".equals(page.str("QName"))) {
                w.append(" AND q.\"Name\" = :n");
            }
            if (!"".equals(page.str("QPhone"))) {
                w.append(" AND q.\"Telephone\" = :ph");
            }
            if (!"".equals(page.str("QCarNo"))) {
                w.append(" AND q.\"VehicleNo\" = :car");
            }
            Sugar.Page pg = db.pageQuery(w.toString(), ps, null, page.i("PageIndex"), 10);
            Row out = deal.dto().make("CardScoreReturnList");
            out.put("TotalPage", pg.totalPage());
            List<Row> list = new ArrayList<>();
            for (Row item : pg.rows()) {
                list.add(deal.dto().make("CardScoreReturn", "Asn", item.get("ASN"), "Name", item.get("Name"),
                    "Phone", item.get("Telephone"), "VehicleNo", item.get("VehicleNo"), "Score", item.dec("Score")));
            }
            out.put("list", list);
            return okJson(out);
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/GetCardScoreMess")
    public String GetCardScoreMess(@RequestBody(required = false) String body) {
        try {
            Row sp = req(body);
            CenterDeal.CusCard c = deal.getCusCardInfo(sp.s("Asn"));
            if (!c.ok()) {
                throw fail(c.error());
            }
            return okJson(deal.dto().make("CardScoreReturn", "Asn", c.cardInfos().get("ASN"), "Name", c.customers().get("Name"),
                "Phone", c.customers().get("Telephone"), "Score", c.cardInfos().dec("Score"),
                "VehicleNo", c.customers().get("VehicleNo")));
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/CardScoreDeal")
    public String CardScoreDeal(@RequestBody(required = false) String body) {
        try (Sugar.Tx tx = db.begin()) {
            try {
                Row sd = req(body);
                CenterDeal.CusCard c = deal.getCusCardInfo(sd.s("Asn"));
                if (!c.ok()) {
                    throw fail(c.error());
                }
                Row info = c.cardInfos();
                if (info.dec("Score").compareTo(sd.dec("SR")) < 0) {
                    throw fail("Les points saisis dépassent la limite");
                }
                info.put("Score", info.dec("Score").subtract(sd.dec("SR")));
                db.update("CustomerCardInfos", info);
                db.insert("VirtualScoreLog", Row.of("Asn", info.get("ASN"), "FinalScore", info.dec("Score"), "ScoreValue", sd.dec("SR"),
                    "ScoreName", c.customers().get("Name"), "ScorePhone", c.customers().get("Telephone"), "ScoreType", 1,
                    "CreateTime", CenterDeal.now()));
                tx.commit();
                return okEmpty();
            } catch (Exception ex) {
                tx.rollback();
                return err(ex);
            }
        }
    }

    @RequestMapping("/GetStation")
    public String GetStation(@RequestBody(required = false) String body) {
        try {
            Row modal = req(body);
            Row st = db.first("Stations", "{Code} = :c", p("c", modal.str("Code")));
            if (st != null) {
                Row city = db.first("Cities", "{CityID} = :id", p("id", st.i("CityID")));
                Row region = db.first("Regions", "{RegionID} = :id", p("id", city.i("RegionID")));
                Row m = deal.dto().make("ModalStations");
                m.put("Address", st.get("Address"));
                m.put("Code", st.get("Code"));
                m.put("email", st.get("Email1"));
                m.put("Manager", st.get("Manager"));
                m.put("Name", st.get("Name"));
                m.put("StationID", st.i("StationID"));
                m.put("Tel", st.get("Phone"));
                m.put("SuperCode", "0001");
                m.put("CityID", st.i("CityID"));
                m.put("CityCode", city.get("Code"));
                m.put("CityCodeInner", city.get("CodeInner"));
                m.put("RegionID", city.i("RegionID"));
                m.put("RegionCode", region.get("Code"));
                m.put("RegionCodeInner", region.get("CodeInner"));
                return okJson(m);
            }
            throw fail("Les informations de la station n'existent pas");
        } catch (Exception ex) {
            return err(ex);
        }
    }

    @RequestMapping("/UpdateStation")
    public String UpdateStation(@RequestBody(required = false) String body) {
        try {
            Row s = req(body);
            Row st = db.first("Stations", "{Code} = :c", p("c", s.str("Code")));
            if (st == null) {
                throw fail("La station n'existe pas");
            }
            st.put("Address", s.get("Address"));
            st.put("CityID", s.i("CityID"));
            st.put("Email1", s.get("email"));
            st.put("Manager", s.get("Manager"));
            st.put("Name", s.get("Name"));
            st.put("Phone", s.get("Tel"));
            Row dv = db.first("DataVersions");
            dv.put("MachineCfg", dv.i("MachineCfg") + 1);
            db.update("DataVersions", dv);
            db.update("Stations", st);
            return okEmpty();
        } catch (Exception ex) {
            return err(ex);
        }
    }
}
