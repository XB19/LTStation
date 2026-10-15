package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table apps (origine SQL Server : Apps). */
@Getter
@Setter
@Entity
@Table(name = "apps")
public class Apps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_id")
    private Integer appId;

    @Column(name = "app_desc")
    private String appDesc;

}
