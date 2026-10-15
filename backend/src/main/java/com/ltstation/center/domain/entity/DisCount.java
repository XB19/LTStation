package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table dis_count (origine SQL Server : DisCount). */
@Getter
@Setter
@Entity
@Table(name = "dis_count")
public class DisCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "dis_count")
    private Integer disCount;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "dis_count_value")
    private BigDecimal disCountValue;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "is_run")
    private Boolean isRun;

}
