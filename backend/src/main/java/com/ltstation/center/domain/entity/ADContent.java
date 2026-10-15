package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table ad_content (origine SQL Server : ADContent). */
@Getter
@Setter
@Entity
@Table(name = "ad_content")
public class ADContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "content_id")
    private Integer contentId;

    @Column(name = "url")
    private String url;

    @Column(name = "type")
    private String type;

    @Column(name = "company_id")
    private Integer companyId;

}
