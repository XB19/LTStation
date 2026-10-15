package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table alarm_type (origine SQL Server : AlarmType). */
@Getter
@Setter
@Entity
@Table(name = "alarm_type")
public class AlarmType {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "description")
    private String description;

}
