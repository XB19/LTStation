package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table non_transaction (origine SQL Server : NonTransaction). */
@Getter
@Setter
@Entity
@Table(name = "non_transaction")
public class NonTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "asn")
    private String asn;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "balance")
    private BigDecimal balance;

    @Column(name = "memo")
    private String memo;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "emp_id")
    private Integer empId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "fuel_type")
    private Integer fuelType;

    @Column(name = "p_license")
    private String pLicense;

    @Column(name = "o_reading")
    private String oReading;

}
