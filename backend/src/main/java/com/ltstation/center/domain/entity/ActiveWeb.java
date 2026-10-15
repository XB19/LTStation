package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table active_web (origine SQL Server : ActiveWeb). */
@Getter
@Setter
@Entity
@Table(name = "active_web")
public class ActiveWeb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "secret_info")
    private String secretInfo;

}
