package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_vendor_categories (origine SQL Server : DefVendorCategories). */
@Getter
@Setter
@Entity
@Table(name = "def_vendor_categories")
public class DefVendorCategories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vendor_category_id")
    private Integer vendorCategoryId;

    @Column(name = "vendor_category_desc")
    private String vendorCategoryDesc;

}
