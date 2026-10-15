package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table webpages_roles (origine SQL Server : webpages_Roles). */
@Getter
@Setter
@Entity
@Table(name = "webpages_roles")
public class WebpagesRoles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "role_name")
    private String roleName;

}
