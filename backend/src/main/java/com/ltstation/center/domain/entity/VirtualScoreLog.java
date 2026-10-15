package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table virtual_score_log (origine SQL Server : VirtualScoreLog). */
@Getter
@Setter
@Entity
@Table(name = "virtual_score_log")
public class VirtualScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "score_id")
    private Integer scoreId;

    @Column(name = "score_name")
    private String scoreName;

    @Column(name = "score_phone")
    private String scorePhone;

    @Column(name = "score_type")
    private Integer scoreType;

    @Column(name = "score_value")
    private BigDecimal scoreValue;

    @Column(name = "final_score")
    private BigDecimal finalScore;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "operate_id")
    private Integer operateId;

    @Column(name = "asn")
    private String asn;

}
