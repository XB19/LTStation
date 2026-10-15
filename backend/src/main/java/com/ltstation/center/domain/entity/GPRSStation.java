package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_station (origine SQL Server : GPRSStation). */
@Getter
@Setter
@Entity
@Table(name = "gprs_station")
public class GPRSStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "station_address")
    private String stationAddress;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_run")
    private Boolean isRun;

    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "tele_phone")
    private String telePhone;

    @Column(name = "amount_digits")
    private Integer amountDigits;

    @Column(name = "volume_digits")
    private Integer volumeDigits;

    @Column(name = "price_digits")
    private Integer priceDigits;

    @Column(name = "digits_number")
    private Integer digitsNumber;

    @Column(name = "user_id")
    private Integer userId;

}
