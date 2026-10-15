package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table transport_infoes (origine SQL Server : TransportInfoes). */
@Getter
@Setter
@Entity
@Table(name = "transport_infoes")
public class TransportInfoes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transport_info_id")
    private Integer transportInfoId;

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "vendor_category_id")
    private Integer vendorCategoryId;

    @Column(name = "contacts")
    private String contacts;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

}
