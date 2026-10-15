package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table air_device (origine SQL Server : AirDevice). */
@Getter
@Setter
@Entity
@Table(name = "air_device")
public class AirDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "oil_id")
    private Integer oilId;

    @Column(name = "oil_name")
    private String oilName;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "device_no")
    private String deviceNo;

    @Column(name = "tank_no")
    private String tankNo;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "amount_digits")
    private Integer amountDigits;

    @Column(name = "volume_digits")
    private Integer volumeDigits;

    @Column(name = "price_digits")
    private Integer priceDigits;

    @Column(name = "is_run")
    private Boolean isRun;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "connec_time")
    private LocalDateTime connecTime;

    @Column(name = "lng")
    private String lng;

    @Column(name = "lat")
    private String lat;

    @Column(name = "valid_time")
    private String validTime;

    @Column(name = "valid_flag")
    private Integer validFlag;

    @Column(name = "digits_type")
    private String digitsType;

    @Column(name = "box_port")
    private Integer boxPort;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "auth_type_id")
    private Integer authTypeId;

    @Column(name = "address")
    private Integer address;

    @Column(name = "url_point")
    private String urlPoint;

    @Column(name = "device_type_id")
    private Integer deviceTypeId;

    @Column(name = "pluse")
    private Integer pluse;

}
