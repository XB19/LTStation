package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table box_port_set (origine SQL Server : BoxPortSet). */
@Getter
@Setter
@Entity
@Table(name = "box_port_set")
public class BoxPortSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "devcie_no")
    private String devcieNo;

    @Column(name = "port")
    private Integer port;

    @Column(name = "buad")
    private Integer buad;

    @Column(name = "stop")
    private Integer stop;

    @Column(name = "parity")
    private Integer parity;

    @Column(name = "agree_ment_type")
    private Integer agreeMentType;

    @Column(name = "interval")
    private Integer interval;

    @Column(name = "device_type")
    private Integer deviceType;

    @Column(name = "create_time")
    private Long createTime;

    @Column(name = "company_id")
    private Integer companyId;

}
