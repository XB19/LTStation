package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table gprs_tank (origine SQL Server : GPRSTank). */
@Getter
@Setter
@Entity
@Table(name = "gprs_tank")
public class GPRSTank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "grade_id")
    private Integer gradeId;

    @Column(name = "tank_height")
    private BigDecimal tankHeight;

    @Column(name = "tank_volume")
    private BigDecimal tankVolume;

    @Column(name = "tank_real_height")
    private BigDecimal tankRealHeight;

    @Column(name = "tank_real_volume")
    private BigDecimal tankRealVolume;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "percentage")
    private BigDecimal percentage;

    @Column(name = "is_run")
    private Boolean isRun;

}
