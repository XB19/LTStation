package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table credit_bill (origine SQL Server : CreditBill). */
@Getter
@Setter
@Entity
@Table(name = "credit_bill")
public class CreditBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "repay_amount")
    private BigDecimal repayAmount;

    @Column(name = "c_type")
    private Integer cType;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "bill_date")
    private String billDate;

}
