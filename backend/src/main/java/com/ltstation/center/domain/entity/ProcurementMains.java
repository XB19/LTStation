package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table procurement_mains (origine SQL Server : ProcurementMains). */
@Getter
@Setter
@Entity
@Table(name = "procurement_mains")
public class ProcurementMains {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "procurement_id")
    private Integer procurementId;

    @Column(name = "mode_id")
    private Integer modeId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "date_document")
    private LocalDateTime dateDocument;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "manufacturer_id")
    private Integer manufacturerId;

    @Column(name = "vendor_no")
    private String vendorNo;

    @Column(name = "purchase_order")
    private String purchaseOrder;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "currency_id")
    private Integer currencyId;

    @Column(name = "cost1")
    private Double cost1;

    @Column(name = "cost2")
    private Double cost2;

    @Column(name = "cost3")
    private Double cost3;

    @Column(name = "amount_discount")
    private Double amountDiscount;

    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "amount_total")
    private Double amountTotal;

    @Column(name = "closing_id")
    private Boolean closingId;

    @Column(name = "memo")
    private String memo;

}
