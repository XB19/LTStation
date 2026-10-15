package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_refuel_order_data (origine SQL Server : JoyFill_refuelOrderData). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_refuel_order_data")
public class JoyFillRefuelOrderData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "company_uuid")
    private String companyUuid;

    @Column(name = "station_uuid")
    private String stationUuid;

    @Column(name = "number")
    private String number;

    @Column(name = "oil_type")
    private String oilType;

    @Column(name = "vehicle_no")
    private String vehicleNo;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "fueled_at")
    private LocalDateTime fueledAt;

    @Column(name = "request_status")
    private Integer requestStatus;

    @Column(name = "request_result")
    private String requestResult;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "energy_id")
    private Integer energyId;

    @Column(name = "energy_name")
    private String energyName;

}
