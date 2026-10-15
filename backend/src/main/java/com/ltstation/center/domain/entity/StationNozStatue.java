package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table station_noz_statue (origine SQL Server : StationNozStatue). */
@Getter
@Setter
@Entity
@Table(name = "station_noz_statue")
public class StationNozStatue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "noz_id")
    private Integer nozId;

    @Column(name = "current_amount")
    private BigDecimal currentAmount;

    @Column(name = "current_volume")
    private BigDecimal currentVolume;

    @Column(name = "amount_total")
    private BigDecimal amountTotal;

    @Column(name = "volume_total")
    private BigDecimal volumeTotal;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "grade_name")
    private String gradeName;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "update_time")
    private Long updateTime;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "statue")
    private Integer statue;

}
