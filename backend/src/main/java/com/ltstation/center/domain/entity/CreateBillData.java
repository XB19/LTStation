package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table create_bill_data (origine SQL Server : CreateBillData). */
@Getter
@Setter
@Entity
@Table(name = "create_bill_data")
public class CreateBillData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "create_bill_id")
    private Integer createBillId;

    @Column(name = "bill_date")
    private String billDate;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "asn")
    private String asn;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "repay_amount")
    private BigDecimal repayAmount;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

}
