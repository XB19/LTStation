package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table credit_card_bills (origine SQL Server : CreditCardBills). */
@Getter
@Setter
@Entity
@Table(name = "credit_card_bills")
public class CreditCardBills {

    @Id
    @Column(name = "credit_card_bill_id")
    private Integer creditCardBillId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "date_from")
    private LocalDateTime dateFrom;

    @Column(name = "date_to")
    private LocalDateTime dateTo;

    @Column(name = "amount_unpaid_privious")
    private Double amountUnpaidPrivious;

    @Column(name = "amount_due")
    private Double amountDue;

    @Column(name = "amount_repayment")
    private Double amountRepayment;

    @Column(name = "date_payment")
    private LocalDateTime datePayment;

    @Column(name = "payment_id")
    private Integer paymentId;

    @Column(name = "closing_status_id")
    private Integer closingStatusId;

    @Column(name = "memo")
    private String memo;

}
