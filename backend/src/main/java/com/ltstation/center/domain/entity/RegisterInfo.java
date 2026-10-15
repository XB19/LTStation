package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table register_info (origine SQL Server : RegisterInfo). */
@Getter
@Setter
@Entity
@Table(name = "register_info")
public class RegisterInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "source_info")
    private String sourceInfo;

    @Column(name = "secret_info")
    private String secretInfo;

}
