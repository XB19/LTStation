package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table order_check (origine SQL Server : OrderCheck). */
@Getter
@Setter
@Entity
@Table(name = "order_check")
public class OrderCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "asn")
    private String asn;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "submit_emp")
    private Integer submitEmp;

    @Column(name = "check_emp")
    private Integer checkEmp;

}
