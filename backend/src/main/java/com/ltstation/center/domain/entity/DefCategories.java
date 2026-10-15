package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_categories (origine SQL Server : DefCategories). */
@Getter
@Setter
@Entity
@Table(name = "def_categories")
public class DefCategories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "category_desc")
    private String categoryDesc;

}
