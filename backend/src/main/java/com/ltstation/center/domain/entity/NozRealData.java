package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table noz_real_data (origine SQL Server : NozRealData). */
@Getter
@Setter
@Entity
@Table(name = "noz_real_data")
public class NozRealData {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "statue")
    private Integer statue;

    @Column(name = "amount_total")
    private BigDecimal amountTotal;

    @Column(name = "volume_total")
    private BigDecimal volumeTotal;

    @Column(name = "price")
    private BigDecimal price;

}
