package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table users (origine SQL Server : Users). */
@Getter
@Setter
@Entity
@Table(name = "users")
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "password")
    private String password;

    @Column(name = "operator_type_id")
    private Integer operatorTypeId;

    @Column(name = "operator_id")
    private Integer operatorId;

    @Column(name = "role_id")
    private Integer roleId;

}
