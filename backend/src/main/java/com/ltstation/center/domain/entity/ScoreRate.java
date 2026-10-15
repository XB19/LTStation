package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table score_rate (origine SQL Server : ScoreRate). */
@Getter
@Setter
@Entity
@Table(name = "score_rate")
public class ScoreRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "oil_type_id")
    private Integer oilTypeId;

    @Column(name = "oil_type_name")
    private String oilTypeName;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "is_run")
    private Boolean isRun;

    @Column(name = "score_type")
    private Integer scoreType;

    @Column(name = "company_id")
    private Integer companyId;

}
