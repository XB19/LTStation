package com.ltstation.center.sw.normalforms;

import com.ltstation.center.legacy.Conv;
import com.ltstation.center.legacy.Row;
import com.ltstation.center.legacy.Sugar;
import com.ltstation.center.sw.core.BaseFunction;
import com.ltstation.center.sw.core.IniInfo;
import com.ltstation.center.sw.form.SwForm;
import com.ltstation.center.sw.form.SwWindow;
import com.ltstation.center.sw.normal.BaseInfo;
import com.ltstation.center.sw.normal.BoxBase;
import com.ltstation.center.sw.normal.BoxShiftClose;
import com.ltstation.center.sw.normal.NormalEngine;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NormalForm.BoxScreenV2 : écran de caisse du mode Normal (ouvert par « ScreenShow »). Vignettes des pompes, factures en cours
 * (un bouton par facture), lignes carburant / produits / coupons, paiements, menus du bas sur deux pages et clôtures.
 * Les AdjustXxxPanel d'origine (tailles calculées à l'écran) sont laissés à la mise en page du poste.
 */
@SwWindow("NormalForm.BoxScreenV2")
public class BoxScreenV2 extends SwForm implements NormalEngine.BoxScreenView {

    public final List<BoxScreenNozV2> nozs = new CopyOnWriteArrayList<>();
    public int SelectNozID = -1;
    private int BottomPage = 1;
    public String BillOrderNow = "";
    private boolean TimeRun;

    private static final String[] menuNames = {"TankChart", "SalesRecord", "PrintShift", "PrintDaySum", "PrintMonthSum", "TaxInvoice", "CancelInvoice",
        "InformalSale", "TestNozzle", "ReturnProduct", "Tools", "AddInCash", "TakeOutCash", "CloseShift", "CloseDay", "CloseMonth", "ChangeOilPrice",
        "ChangeUser", "BillPay", "Exit"};

    private static String[] menuShows() {
        return new String[] {L("TankChart"), L("SaleRecord"), L("Printshift"), L("Printdaysummary"), L("PrintMonthsummary"), L("Taxinvoice"),
            L("Cancelinvoice"), L("Informalsale"), L("Testnozzle"), L("Returntheproduct"), L("Tools"), L("Addincash"), L("Takeoutcash"), L("Closeshift"),
            L("Closeday"), L("CloseMonth"), L("Changeoilprice"), L("Changeuser"), L("PersonalBill"), L("Exit")};
    }

    private TankChartDisplay tankform;
    private String CreditCardNumber = "";
    private String FleetCardNumber = "";
    private int PayWay = -1;
    /** Boutons des factures (OrderListPanel.Controls), dans l'ordre d'ajout. */
    private final List<String> bills = new ArrayList<>();

    private static final String RED = "#ff0000";
    private static final String BLUE = "#09a3dc";
    private static final byte[] CASH_DRAWER = {114, (byte) 244, (byte) 132, 1};

    public void BoxScreenV2_Load() {
        try {
            On("$this", "FormClosed", "BoxScreenV2_FormClosed");
            ShowBottomBtn(BottomPage);
            c("TimeLabel").Text(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            LoadNozPanel();
            BillOrderNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            TimeRun = true;
            Timer("RunTime", 1000);
            NormalEngine.boxScreen = this;
            List<String> list = db().query(db().sql("BillOrder", "select distinct {BillOrderNumber} as b from [BillOrder] where {BillComplete} = 0"), null)
                .stream().map(t -> t.str("b")).toList();
            if (list.isEmpty()) {
                CreateBillBtn("");
            } else {
                for (int i = 0; i < list.size(); i++) {
                    CreateBillBtn(list.get(i));
                    if (i == list.size() - 1) {
                        BillOrderNow = list.get(i);
                        LoadOilDataView();
                    }
                }
            }
            c("ShowVatBtn").Text(IniInfo.vatRun ? "No Show Vat" : "Show Vat");
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void BoxScreenV2_FormClosed() {
        TimeRun = false;
        if (NormalEngine.boxScreen == this) {
            NormalEngine.boxScreen = null;
        }
    }

    public void RunTime() {
        if (TimeRun) {
            c("TimeLabel").Text(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")));
        }
    }

    private void ShowBottomBtn(int Page) {
        try {
            ClearControls("BottomPanel");
            String[] shows = menuShows();
            int from = Page == 1 ? 0 : 14;
            int count = Page == 1 ? 14 : 6;
            for (int i = 0; i < count; i++) {
                String name = menuNames[from + i];
                AddControl("BottomPanel", name, "SkinButton", shows[from + i], 150, 45);
                On(name, "Click", "TankChartBtn_Click");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void BottomNextBtn_Click() {
        if (BottomPage == 1) {
            BottomPage = 2;
            c("BottomNextBtn").BackgroundImage("res/BoxBack.png");
        } else {
            BottomPage = 1;
            c("BottomNextBtn").BackgroundImage("res/BoxNext.png");
        }
        ShowBottomBtn(BottomPage);
    }

    private void LoadNozPanel() {
        try {
            List<Integer> pumps = db().query(db().sql("NormalNoz", "select distinct {PumpID} as p from [NormalNoz] where {IsUsing} = true"), null)
                .stream().map(t -> t.i("p")).toList();
            for (int pump : pumps) {
                BoxScreenNozV2 noz = new BoxScreenNozV2(pump);
                noz.SelectNoz = this::NormalNoz_SelectNoz;
                nozs.add(noz);
                Embed(noz, "NozViewPanel");
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void NormalNoz_SelectNoz(int NozID) {
        try {
            if (SelectNozID == NozID) {
                ShowDialog("BoxScreenView.BoxNozTransction", r -> {
                    if ("OK".equals(r.result())) {
                        Noz_TransferDeal(BillOrderNow);
                    }
                }, "NozID", NozID, "BillOrder", BillOrderNow);
            }
            SelectNozID = NozID;
            for (BoxScreenNozV2 noz : nozs) {
                noz.ClearImage(noz.BoxPumpID == NozID ? 1 : 0);
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void Noz_TransferDeal(String OrderNumber) {
        try {
            LoadData(db().list("BillOrder", "{BillOrderNumber} = :b AND {BillComplete} = 0", p("b", OrderNumber)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Menus du bas (boutons créés par ShowBottomBtn). */
    public void TankChartBtn_Click() {
        try {
            switch (sender) {
                case "TestNozzle" -> ShowDialog("NormalForm.StationPassword", r -> {
                    if ("OK".equals(r.result())) {
                        Ask("Are you sure?", yes -> {
                            if (yes) {
                                PayWay = 7;
                                DealBill(0);
                            }
                        });
                    }
                }, "url", "ServerDeal/GetStationSetPassword");
                case "CloseMonth" -> {
                    Row boxDay = db().first("BoxDayList", "{MonthComplete} = 0 ORDER BY {ID}", null);
                    if (boxDay != null) {
                        Ask(boxDay.str("BoxMonth") + " Close Month?", yes -> {
                            if (!yes) {
                                return;
                            }
                            List<Row> list2 = db().list("BoxDayList", "{BoxMonth} = :m AND {MonthComplete} = 0", p("m", boxDay.str("BoxMonth")));
                            if (BoxShiftClose.CloseMonthCheck(list2) == null) {
                                for (Row item : list2) {
                                    item.put("MonthComplete", 1);
                                }
                                db().updateAll("BoxDayList", list2);
                            }
                            MessageBox("Clôture mensuelle réussie");
                        });
                    }
                }
                case "CloseShift" -> {
                    if (db().first("NormalTransaction", "{SaleCheck} = '' OR {SaleCheck} IS NULL", null) != null
                        || db().first("BillOrder", "{BillComplete} = 0", null) != null) {
                        MessageBox("Impossible de clôturer le poste : des transactions ne sont pas réglées");
                    } else {
                        Ask("Close Shift?", yes -> {
                            if (yes) {
                                String error = BoxShiftClose.CloseShiftCheck();
                                if (error != null) {
                                    MessageBox(error);
                                }
                                MessageBox("Clôture du poste réussie");
                            }
                        });
                    }
                }
                case "PrintShift" -> ShowDialog("BoxScreenView.ShiftPrintView", r -> { });
                case "PrintDaySum" -> ShowDialog("BoxScreenView.DayPrintView", r -> { });
                case "SalesRecord" -> ShowDialog("BoxScreenView.InternalUserView", r -> { });
                case "ChangeOilPrice" -> ShowDialog("BoxScreenView.BoxPriceChange", r -> { });
                case "TankChart" -> {
                    if (tankform == null) {
                        tankform = Show(new TankChartDisplay());
                    } else {
                        CloseChild(tankform);
                        tankform = null;
                    }
                }
                case "CloseDay" -> {
                    Row boxShift = db().first("BoxShiftList", "{DayComplete} = 0 AND {Complete} <> 0 ORDER BY {ID}", null);
                    if (boxShift != null) {
                        Ask(boxShift.str("BoxDay") + " Close Day?", yes -> {
                            if (!yes) {
                                return;
                            }
                            List<Row> list = db().list("BoxShiftList", "{BoxDay} = :d AND {Complete} <> 0 AND {DayComplete} = 0", p("d", boxShift.str("BoxDay")));
                            if (BoxShiftClose.CloseDayCheck(list) == null) {
                                for (Row item : list) {
                                    item.put("DayComplete", 1);
                                }
                                db().updateAll("BoxShiftList", list);
                            }
                            MessageBox("Clôture journalière réussie");
                        });
                    }
                }
                case "PrintMonthSum" -> ShowDialog("BoxScreenView.MonthPrintView", r -> { });
                case "Exit" -> {
                    BoxScreenV2_FormClosed();
                    Close();
                }
                case "InformalSale" -> ShowDialog("BoxScreenViewV2.JumpDeal", r -> { });
                case "BillPay" -> ShowDialog("NormalForm.StationPassword", r -> {
                    if ("OK".equals(r.result())) {
                        ShowDialog("BoxScreenViewV2.BillPay", r2 -> { });
                    }
                }, "url", "ServerDeal/GetStationSetPassword");
                default -> {
                    // TaxInvoice, CancelInvoice, ReturnProduct, Tools, AddInCash, TakeOutCash, ChangeUser : sans action dans l'original
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private BoxScreenNozV2 selected() {
        if (SelectNozID == -1) {
            throw new IllegalStateException(L("BoxSelectPump"));
        }
        return nozs.stream().filter(t -> t.BoxPumpID == SelectNozID).findFirst().orElse(null);
    }

    public void StopOnePumpBtn_Click() {
        try {
            switch (sender) {
                case "StopOnePumpBtn", "OpenOnePumpBtn" -> {
                    boolean stop = sender.equals("StopOnePumpBtn");
                    BoxScreenNozV2 noz = selected();
                    if (noz == null) {
                        return;
                    }
                    for (Row item : db().list("NormalNoz", "{PumpID} = :p", p("p", SelectNozID))) {
                        NormalEngine.UpdateNozMess(item.i("NozID"), item.str("NozID"), "", stop ? 13 : 14);
                    }
                    noz.BoxAllow = stop ? 1 : 0;
                    noz.UpdateView(noz.BoxNozStatue, stop ? 1 : 0, noz.BoxGradeName, noz.BoxAmount, 0);
                }
                case "StopAllPumpBtn", "OpenAllPumpBtn" -> {
                    boolean stop = sender.equals("StopAllPumpBtn");
                    NormalEngine.UpdateNozMess(0, "", "", stop ? 11 : 12);
                    for (BoxScreenNozV2 noz : nozs) {
                        noz.BoxAllow = stop ? 1 : 0;
                        noz.UpdateView(noz.BoxNozStatue, stop ? 1 : 0, noz.BoxGradeName, noz.BoxAmount, 0);
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void CreateBillBtn(String order) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String name = order.isEmpty() ? now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) : order;
            String text = order.isEmpty() ? now.format(DateTimeFormatter.ofPattern("HHmmss")) : order.substring(8, 14);
            BillOrderNow = name;
            for (String b : bills) {
                c(b).BackColor(BLUE);
            }
            AddControl("OrderListPanel", name, "SkinButton", text, 60, 35).BackColor(RED);
            On(name, "Click", "BillClick");
            bills.remove(name);
            bills.add(name);
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void BillClick() {
        try {
            BillSelect(sender);
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void BillSelect(String name) {
        for (String b : bills) {
            if (b.equals(name)) {
                c(b).BackColor(RED);
                BillOrderNow = b;
            } else {
                c(b).BackColor(BLUE);
            }
        }
        LoadOilDataView();
    }

    private void LoadOilDataView() {
        try {
            LoadData(db().list("BillOrder", "{BillOrderNumber} = :b AND {BillComplete} = 0", p("b", BillOrderNow)));
            LoadProductBill();
            LoadCouponData(db().list("BoxScreenCoupon", "{BillOrderNumber} = :b", p("b", BillOrderNow)));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadData(List<Row> list) {
        try {
            g("OilDataView").RowsClear();
            BigDecimal num = BigDecimal.ZERO;
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), "Noz", item.get("NozID"), item.get("Volume"), item.get("Amount"),
                    BaseFunction.StampToDateTime(item.str("TransactionTime")));
                num = num.add(item.dec("Amount"));
            }
            c("AmountLabel").Text(num.toPlainString());
            c("TotalAmountLabel").Text("Total : " + num.toPlainString());
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static BigDecimal parse(String t) {
        try {
            return new BigDecimal(t.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal amountLabel() {
        if (c("AmountLabel").Text().isEmpty()) {
            c("AmountLabel").Text("0");
        }
        if (c("TotalAmountLabel").Text().isEmpty()) {
            c("TotalAmountLabel").Text("Total : 0");
        }
        BigDecimal v = parse(c("AmountLabel").Text());
        return v == null ? BigDecimal.ZERO : v;
    }

    private void LoadCouponData(List<Row> list) {
        try {
            BigDecimal num = BigDecimal.ZERO;
            BigDecimal result = amountLabel();
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), item.str("CouponType"), item.str("CouponType"), "", item.dec("CouponValue").toPlainString(), "");
                if ("Money".equals(item.str("CouponType"))) {
                    num = num.add(item.dec("CouponValue"));
                } else {
                    result = result.subtract(item.dec("CouponValue"));
                }
            }
            c("AmountLabel").Text(result.toPlainString());
            c("TotalAmountLabel").Text("Total : " + result.toPlainString());
            c("ChangeLabel").Text(num.signum() != 0 ? num.subtract(result).abs().toPlainString() : "");
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void LoadProductBill() {
        try {
            List<Row> list = db().list("ProductLog", "{BillOrderNumber} = :b", p("b", BillOrderNow));
            BigDecimal result = amountLabel();
            for (Row item : list) {
                g("OilDataView").RowsAdd(item.get("ID"), "Product", item.str("Name"), item.get("Quantity"), item.get("Amount"),
                    BaseFunction.StampToDateTime(item.str("CreateTime")));
                result = result.add(item.dec("Amount"));
            }
            c("AmountLabel").Text(result.toPlainString());
            c("TotalAmountLabel").Text("Total : " + result.toPlainString());
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void AddBillOrderBtn_Click() {
        CreateBillBtn("");
        LoadData(db().list("BillOrder", "{BillOrderNumber} = :b AND {BillComplete} = 0", p("b", BillOrderNow)));
    }

    private void confirmed(Runnable then) {
        ShowDialog("NormalForm.StationPassword", r -> {
            if ("OK".equals(r.result())) {
                then.run();
            }
        }, "url", "ServerDeal/GetStationSetPassword");
    }

    public void CashBtn_Click() {
        try {
            switch (sender) {
                case "QRCODEBtn" -> PayWay = 3;
                case "CouponBtn", "DiscountBtn" -> {
                    String type = sender.equals("CouponBtn") ? "Coupon" : "Discount";
                    if (!c("AmountBox").Text().isEmpty()) {
                        BigDecimal v = parse(c("AmountBox").Text());
                        if (v != null) {
                            SetCouponDiscount(type, v.toPlainString());
                        }
                        c("AmountBox").Text("");
                    }
                    c("MessageLabel").Text("Saisissez " + type + " Value");
                }
                case "ShowReceiptBtn" -> ShowDialog("BoxScreenView.ReceiptForm", r -> { });
                case "InternalUseBtn" -> confirmed(() -> Ask("Are you sure?", yes -> {
                    if (yes) {
                        PayWay = 8;
                        if (!BillOrderNow.isEmpty()) {
                            DealBill(0);
                        }
                    }
                }));
                case "CashBtn" -> {
                    PayWay = 0;
                    if (!BillOrderNow.isEmpty()) {
                        DealBill(0);
                    }
                }
                case "CreditCardBtn" -> {
                    PayWay = 1;
                    c("MessageLabel").Text("Saisissez votre carte de crédit");
                    CreditCardNumber = c("AmountBox").Text();
                }
                case "FleetCardBtn" -> {
                    PayWay = 2;
                    c("MessageLabel").Text("Saisissez votre carte flotte");
                    CreditCardNumber = c("AmountBox").Text();
                }
                case "OKBtn" -> {
                    if (PayWay == -1) {
                        throw new IllegalStateException("Choisissez d'abord un moyen de paiement");
                    }
                    CreditCardNumber = c("AmountBox").Text();
                    DealBill(0);
                }
                case "BankTransferBtn" -> PayWay = 4;
                case "PersonalCreditBtn" -> ShowDialog("BoxScreenView.PersonalCreditView", r -> {
                    if ("OK".equals(r.result())) {
                        PayWay = 6;
                        DealBill(r.data().i("ValueID"));
                    }
                });
                case "ShowVatBtn" -> confirmed(() -> {
                    IniInfo.vatRun = !IniInfo.vatRun;
                    c("ShowVatBtn").Text(IniInfo.vatRun ? L("ShowVat") : L("NoShowVat"));
                    Row stations = db().first("Stations");
                    if (stations != null) {
                        stations.put("AutoMeasure", IniInfo.vatRun);
                        db().update("Stations", stations);
                    }
                });
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private static void OpenCashDrawer(boolean checkOpen) {
        if (NormalEngine.CashDraw != null && (!checkOpen || NormalEngine.CashDraw.PortIsOpen())) {
            NormalEngine.CashDraw.sendbyte(CASH_DRAWER, 0, CASH_DRAWER.length);
        }
    }

    public void OpenCashBoxBtn_Click() {
        try {
            switch (sender) {
                case "OpenCashBoxBtn" -> OpenCashDrawer(false);
                case "QuickPrintBtn", "PrinterBtn", "MeterBtn" -> ShowDialog("BoxScreenViewV2.BoradPrintForm", r -> { }, "OperateType", sender);
                case "OtherProductBtn" -> ShowDialog("BoxScreenView.OtherProductView", r -> LoadOilDataView(), "BillOrderNumber", BillOrderNow);
                case "CustomerBtn" -> ShowDialog("BoxScreenView.BoxCustomer", r -> { });
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    private void SetCouponDiscount(String CDType, String Value) {
        try {
            db().insert("BoxScreenCoupon", Row.of("BillOrderNumber", BillOrderNow, "CouponType", CDType, "CouponValue", new BigDecimal(Value),
                "CreateTime", BaseInfo.ConvertDateTimeToInt(LocalDateTime.now())));
            LoadOilDataView();
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void ClearBtn_Click() {
        try {
            switch (sender) {
                case "ClearBtn" -> c("AmountBox").Text("");
                case "ItemCancelBtn" -> {
                    int i = g("OilDataView").CurrentRowIndex();
                    long num = -1;
                    String text = "";
                    if (i >= 0) {
                        num = Conv.toLong(g("OilDataView").Cell(i, 0));
                        text = String.valueOf(g("OilDataView").Cell(i, 1));
                    }
                    switch (text) {
                        case "Coupon", "Discount", "Money" -> db().delete("BoxScreenCoupon", "{ID} = :i", p("i", num));
                        case "Product" -> {
                            Row log = db().first("ProductLog", "{ID} = :i", p("i", num));
                            if (log != null) {
                                Row product = db().first("ProductList", "{ID} = :i", p("i", log.get("ProductRealID")));
                                if (product != null) {
                                    product.put("Quantity", product.i("Quantity") + log.i("Quantity"));
                                    db().update("ProductList", product);
                                }
                                db().delete("ProductLog", "{ID} = :i", p("i", num));
                            }
                        }
                        default -> {
                            Row bill = db().first("BillOrder", "{ID} = :i", p("i", num));
                            if (bill != null) {
                                db().exec(db().sql("NormalTransaction", "update [NormalTransaction] set {SaleCheck} = '' where {ID} = :t"),
                                    p("t", bill.get("TransactionID")));
                                db().delete("BillOrder", "{ID} = :i", p("i", num));
                            }
                        }
                    }
                    LoadOilDataView();
                }
                case "SaleCancelBtn" -> {
                    db().exec(db().sql("NormalTransaction", "update [NormalTransaction] set {SaleCheck} = '' where {SaleCheck} = :s"),
                        p("s", BillOrderNow + "-1"));
                    db().delete("BillOrder", "{BillOrderNumber} = :b", p("b", BillOrderNow));
                    db().delete("BoxScreenCoupon", "{BillOrderNumber} = :b", p("b", BillOrderNow));
                    for (Row item : db().list("ProductLog", "{BillOrderNumber} = :b", p("b", BillOrderNow))) {
                        Row product = db().first("ProductList", "{ID} = :i", p("i", item.get("ProductRealID")));
                        if (product != null) {
                            product.put("Quantity", product.i("Quantity") + item.i("Quantity"));
                            db().update("ProductList", product);
                        }
                    }
                    db().delete("ProductLog", "{BillOrderNumber} = :b", p("b", BillOrderNow));
                    g("OilDataView").RowsClear();
                    c("AmountLabel").Text("0");
                    c("TotalAmountLabel").Text("0");
                    c("ChangeLabel").Text("");
                }
                case "HomeBtn" -> {
                    BottomPage = 1;
                    c("BottomNextBtn").BackgroundImage("res/BoxNext.png");
                    ShowBottomBtn(BottomPage);
                }
                default -> {
                }
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Paiement de la facture en cours (PayWay) ; client à crédit si PersonalCreditID &lt;&gt; 0. */
    private void DealBill(int PersonalCreditID) {
        Sugar.Tx tx = db().begin();
        try {
            List<Row> list = db().list("BillOrder", "{BillOrderNumber} = :b AND {BillComplete} = 0", p("b", BillOrderNow));
            boolean flag = false;
            BigDecimal num = BigDecimal.ZERO;
            if (!list.isEmpty()) {
                for (Row item : list) {
                    item.put("BillPayMent", PayWay);
                    item.put("BillComplete", 1);
                    item.put("ConfirmTime", BaseFunction.ConvertDateTimeToInt());
                    num = num.add(item.dec("Amount"));
                    item.put("BoxShift", IniInfo.BoxShift);
                }
                db().updateAll("BillOrder", list);
                db().exec(db().sql("NormalTransaction", "update [NormalTransaction] set {SaleCheck} = :n where {SaleCheck} = :o"),
                    p("n", BillOrderNow + "-2", "o", BillOrderNow + "-1"));
                flag = true;
            }
            List<BigDecimal> discount = db().list("BoxScreenCoupon", "{BillOrderNumber} = :b AND {CouponType} = 'Discount'", p("b", BillOrderNow))
                .stream().map(t -> t.dec("CouponValue")).toList();
            List<BigDecimal> coupon = db().list("BoxScreenCoupon", "{BillOrderNumber} = :b AND {CouponType} = 'Coupon'", p("b", BillOrderNow))
                .stream().map(t -> t.dec("CouponValue")).toList();
            for (Row item : db().list("ProductLog", "{BillOrderNumber} = :b", p("b", BillOrderNow))) {
                num = num.add(item.dec("Amount"));
                flag = true;
            }
            if (flag) {
                if (PersonalCreditID != 0) {
                    Row credit = db().first("PersonalCredit", "{ID} = :i", p("i", PersonalCreditID));
                    BigDecimal num2 = db().list("PersonCreditLog", "{PersonCreditID} = :i AND {CreateTime} > :u",
                        p("i", PersonalCreditID, "u", credit.get("UpdateTime"))).stream().map(t -> t.dec("Amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (credit.dec("UseAmount").add(num).add(num2).compareTo(credit.dec("CreditLimit")) > 0) {
                        throw new IllegalStateException(L("BalanceNotEnough"));
                    }
                    db().insert("PersonCreditLog", Row.of("StationID", IniInfo.StationID, "Amount", num, "CardNumber", credit.get("CardNumber"),
                        "CompanyID", IniInfo.CompanyID, "CreateTime", BaseFunction.ConvertDateTimeToInt(), "PersonCreditID", credit.get("ID"), "Upload", 0));
                }
                BoxBase.InsertBillOrder(BillOrderNow, num, PayWay, CreditCardNumber.isEmpty() ? FleetCardNumber : CreditCardNumber, discount, coupon,
                    PersonalCreditID, new String[1]);
                if (bills.remove(BillOrderNow)) {
                    RemoveControl(BillOrderNow);
                }
                ClearBill();
                OpenCashDrawer(true);
                PayWay = -1;
            }
            tx.commit();
        } catch (RuntimeException ex) {
            tx.rollback();
            MessageBox(ex.getMessage());
        }
    }

    private void ClearBill() {
        try {
            g("OilDataView").RowsClear();
            c("AmountLabel").Text("0");
            c("TotalAmountLabel").Text("Total : 0");
            c("AmountBox").Text("");
            c("MessageLabel").Text(IniInfo.LoginName);
            CreditCardNumber = "";
            FleetCardNumber = "";
            if (!bills.isEmpty()) {
                // PerformClick du dernier bouton de facture
                BillSelect(bills.get(bills.size() - 1));
            } else {
                CreateBillBtn("");
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    /** Appelée par le fil DealScreenPumpData du moteur toutes les 500 ms pour chaque pistolet. */
    @Override
    public void UpdateNozView(int NozID, int NozStatue, String GradeName, String Amount, int PumpID) {
        try {
            BoxScreenNozV2 noz = nozs.stream().filter(t -> t.BoxPumpID == PumpID).findFirst().orElse(null);
            if (noz == null) {
                return;
            }
            // Fil du moteur : même verrou que les requêtes du poste (Invoke vers le fil d'interface dans l'original)
            synchronized (lock()) {
                UpdateNozViewLocked(noz, NozID, NozStatue, GradeName, Amount);
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    private void UpdateNozViewLocked(BoxScreenNozV2 noz, int NozID, int NozStatue, String GradeName, String Amount) {
        try {
            if (noz.CurrentNozID != NozID || noz.BoxNozStatue != NozStatue || NozStatue == 3) {
                if (NozStatue == 1 || NozStatue == 3) {
                    noz.UpdateView(NozStatue, noz.BoxAllow, GradeName, Amount, NozID);
                }
                // BoxNozStatue comparé au numéro de pistolet, comme dans l'original
                if (noz.BoxNozStatue == NozID && noz.BoxNozStatue == 3 && NozStatue == 0) {
                    noz.UpdateView(NozStatue, noz.BoxAllow, GradeName, Amount, NozID);
                }
                if (NozStatue == 0 && noz.CurrentNozID == NozID && noz.BoxNozStatue != 0) {
                    noz.UpdateView(NozStatue, noz.BoxAllow, GradeName, Amount, NozID);
                }
            }
        } catch (RuntimeException ignored) {
            // comme l'original
        }
    }

    public void SevenBtn_Click() {
        try {
            if (!sender.equals("PQBtn")) {
                c("AmountBox").Text(c("AmountBox").Text() + c(sender).Text());
            }
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }

    public void Note100Btn_Click() {
        try {
            int num = switch (sender) {
                case "Note100Btn" -> 100;
                case "Note500Btn" -> 500;
                case "Note1000Btn" -> 1000;
                default -> 0;
            };
            if (parse(c("AmountLabel").Text()) == null) {
                throw new IllegalStateException("Amount Error");
            }
            SetCouponDiscount("Money", String.valueOf(num));
        } catch (RuntimeException ex) {
            MessageBox(ex.getMessage());
        }
    }
}
