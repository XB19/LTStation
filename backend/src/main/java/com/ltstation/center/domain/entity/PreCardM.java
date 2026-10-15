package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table pre_card_m (origine SQL Server : PreCard_M). */
@Getter
@Setter
@Entity
@Table(name = "pre_card_m")
public class PreCardM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "\"using\"")
    private Boolean using;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "card_type")
    private Integer cardType;

}
