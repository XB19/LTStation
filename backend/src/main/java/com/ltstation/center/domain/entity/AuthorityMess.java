package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table authority_mess (origine SQL Server : AuthorityMess). */
@Getter
@Setter
@Entity
@Table(name = "authority_mess")
public class AuthorityMess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_id_mess")
    private Integer userIdMess;

    @Column(name = "name")
    private String name;

    @Column(name = "authority_content")
    private String authorityContent;

    @Column(name = "curr_auth_id")
    private Integer currAuthId;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "new_company_id")
    private Integer newCompanyId;

    @Column(name = "is_new")
    private Integer isNew;

}
