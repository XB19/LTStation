package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table amount_radio (origine SQL Server : AmountRadio). */
@Getter
@Setter
@Entity
@Table(name = "amount_radio")
public class AmountRadio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "radio")
    private BigDecimal radio;

    @Column(name = "create_time")
    private LocalDateTime createTime;

}
