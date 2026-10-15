package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table tax_bill (origine SQL Server : TaxBill). */
@Getter
@Setter
@Entity
@Table(name = "tax_bill")
public class TaxBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_tax")
    private String companyTax;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "check_time")
    private LocalDateTime checkTime;

    @Column(name = "pay_for_email")
    private String payForEmail;

}
