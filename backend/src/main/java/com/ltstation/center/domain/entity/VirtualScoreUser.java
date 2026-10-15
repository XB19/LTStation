package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Table virtual_score_user (origine SQL Server : VirtualScoreUser). */
@Getter
@Setter
@Entity
@Table(name = "virtual_score_user")
public class VirtualScoreUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "score")
    private BigDecimal score;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "company_id")
    private Integer companyId;

}
