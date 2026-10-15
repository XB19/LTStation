package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table dispatcher (origine SQL Server : Dispatcher). */
@Getter
@Setter
@Entity
@Table(name = "dispatcher")
public class Dispatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "app_id")
    private Integer appId;

    @Column(name = "mod_id")
    private Integer modId;

    @Column(name = "function_id")
    private Integer functionId;

    @Column(name = "text")
    private String text;

    @Column(name = "sub_text")
    private String subText;

    @Column(name = "text_1")
    private String text1;

    @Column(name = "sub_text_1")
    private String subText1;

    @Column(name = "controller_name")
    private String controllerName;

    @Column(name = "action_name")
    private String actionName;

    @Column(name = "is_show")
    private Boolean isShow;

}
