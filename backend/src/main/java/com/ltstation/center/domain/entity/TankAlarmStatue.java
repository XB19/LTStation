package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table tank_alarm_statue (origine SQL Server : TankAlarmStatue). */
@Getter
@Setter
@Entity
@Table(name = "tank_alarm_statue")
public class TankAlarmStatue {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "tl")
    private Integer tl;

    @Column(name = "cg")
    private Integer cg;

    @Column(name = "ttemp")
    private Integer ttemp;

    @Column(name = "t_level")
    private Integer tLevel;

    @Column(name = "eg")
    private Integer eg;

    @Column(name = "update_time")
    private Long updateTime;

}
