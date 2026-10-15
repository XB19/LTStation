package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table member_discount (origine SQL Server : MemberDiscount). */
@Getter
@Setter
@Entity
@Table(name = "member_discount")
public class MemberDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "member_name")
    private String memberName;

    @Column(name = "discount")
    private BigDecimal discount;

    @Column(name = "total_consume")
    private BigDecimal totalConsume;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_using")
    private Boolean isUsing;

}
