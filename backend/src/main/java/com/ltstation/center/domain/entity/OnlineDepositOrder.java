package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table online_deposit_order (origine SQL Server : OnlineDepositOrder). */
@Getter
@Setter
@Entity
@Table(name = "online_deposit_order")
public class OnlineDepositOrder {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_using")
    private Boolean isUsing;

    @Column(name = "open_id")
    private String openId;

    @Column(name = "asn")
    private String asn;

}
