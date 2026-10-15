package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table online_deposit (origine SQL Server : OnlineDeposit). */
@Getter
@Setter
@Entity
@Table(name = "online_deposit")
public class OnlineDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "is_using")
    private Integer isUsing;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "dis_count_amount")
    private BigDecimal disCountAmount;

    @Column(name = "charge_type")
    private Integer chargeType;

    @Column(name = "operate_name")
    private String operateName;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "company_id")
    private Integer companyId;

}
