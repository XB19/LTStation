package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_shift (origine SQL Server : GPRSShift). */
@Getter
@Setter
@Entity
@Table(name = "gprs_shift")
public class GPRSShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "gun_id")
    private Integer gunId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "shift_no")
    private String shiftNo;

    @Column(name = "create_time")
    private LocalDateTime createTime;

}
