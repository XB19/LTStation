package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table sale_mains (origine SQL Server : SaleMains). */
@Getter
@Setter
@Entity
@Table(name = "sale_mains")
public class SaleMains {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Integer saleId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "counter_no")
    private Integer counterNo;

    @Column(name = "employee_id")
    private Integer employeeId;

    @Column(name = "date_sale")
    private LocalDateTime dateSale;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "ticket_no")
    private String ticketNo;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "quantity")
    private Double quantity;

    @Column(name = "amount_total")
    private Double amountTotal;

    @Column(name = "ticket_trashed_id")
    private Boolean ticketTrashedId;

    @Column(name = "memo")
    private String memo;

}
