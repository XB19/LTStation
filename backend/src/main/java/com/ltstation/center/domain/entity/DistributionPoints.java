package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table distribution_points (origine SQL Server : DistributionPoints). */
@Getter
@Setter
@Entity
@Table(name = "distribution_points")
public class DistributionPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "distribution_point_id")
    private Integer distributionPointId;

    @Column(name = "transport_info_id")
    private Integer transportInfoId;

    @Column(name = "description")
    private String description;

    @Column(name = "code")
    private String code;

}
