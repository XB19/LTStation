package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table media_types (origine SQL Server : MediaTypes). */
@Getter
@Setter
@Entity
@Table(name = "media_types")
public class MediaTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_type_id")
    private Integer mediaTypeId;

    @Column(name = "media_type_desc")
    private String mediaTypeDesc;

    @Column(name = "media_type_desc_en")
    private String mediaTypeDescEn;

}
