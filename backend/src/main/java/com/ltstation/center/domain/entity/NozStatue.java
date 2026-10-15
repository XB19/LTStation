package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table noz_statue (origine SQL Server : NozStatue). */
@Getter
@Setter
@Entity
@Table(name = "noz_statue")
public class NozStatue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "volume_total")
    private BigDecimal volumeTotal;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "oil_code")
    private String oilCode;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "update_time")
    private Long updateTime;

}
