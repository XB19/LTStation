package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table emp_score_rate (origine SQL Server : EmpScoreRate). */
@Getter
@Setter
@Entity
@Table(name = "emp_score_rate")
public class EmpScoreRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "auth_id")
    private Integer authId;

    @Column(name = "auth_name")
    private String authName;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "emp_type")
    private Integer empType;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "is_run")
    private Boolean isRun;

}
