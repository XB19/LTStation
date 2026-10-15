package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table certificate_types (origine SQL Server : CertificateTypes). */
@Getter
@Setter
@Entity
@Table(name = "certificate_types")
public class CertificateTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificate_type_id")
    private Integer certificateTypeId;

    @Column(name = "certificate_type_desc")
    private String certificateTypeDesc;

    @Column(name = "certificate_type_desc_en")
    private String certificateTypeDescEn;

}
