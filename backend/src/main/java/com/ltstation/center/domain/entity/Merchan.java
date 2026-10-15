package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table merchan (origine SQL Server : Merchan). */
@Getter
@Setter
@Entity
@Table(name = "merchan")
public class Merchan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "serverappid")
    private String serverappid;

    @Column(name = "servermch_id")
    private String servermchId;

    @Column(name = "sub_mch_id")
    private String subMchId;

    @Column(name = "sub_appid")
    private String subAppid;

    @Column(name = "sub_app_secret")
    private String subAppSecret;

    @Column(name = "app_secret")
    private String appSecret;

    @Column(name = "server_type")
    private Integer serverType;

    @Column(name = "unique_code")
    private String uniqueCode;

}
