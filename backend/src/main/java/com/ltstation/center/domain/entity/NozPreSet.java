package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table noz_pre_set (origine SQL Server : NozPreSet). */
@Getter
@Setter
@Entity
@Table(name = "noz_pre_set")
public class NozPreSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "pre_set_type")
    private Integer preSetType;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "dowload")
    private Boolean dowload;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "deal")
    private Integer deal;

    @Column(name = "deal_amount")
    private BigDecimal dealAmount;

    @Column(name = "deal_code")
    private String dealCode;

}
