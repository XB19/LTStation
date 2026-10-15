package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table commodities (origine SQL Server : Commodities). */
@Getter
@Setter
@Entity
@Table(name = "commodities")
public class Commodities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commodity_id")
    private Integer commodityId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "bar")
    private String bar;

    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "specification")
    private String specification;

    @Column(name = "model")
    private String model;

    @Column(name = "manufacturer_id")
    private Integer manufacturerId;

    @Column(name = "unit_id")
    private Integer unitId;

    @Column(name = "unit_package_id")
    private Integer unitPackageId;

    @Column(name = "color_id")
    private Integer colorId;

    @Column(name = "size")
    private String size;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "unit_weight_id")
    private Integer unitWeightId;

    @Column(name = "use")
    private String use;

    @Column(name = "cost")
    private Double cost;

    @Column(name = "cost_default")
    private Double costDefault;

    @Column(name = "economic_lot_size")
    private Double economicLotSize;

    @Column(name = "inventory_satety")
    private Double inventorySatety;

    @Column(name = "img_path")
    private String imgPath;

    @Column(name = "short_key")
    private String shortKey;

    @Column(name = "date_create")
    private LocalDateTime dateCreate;

    @Column(name = "date_change")
    private LocalDateTime dateChange;

    @Column(name = "memo")
    private String memo;

    @Column(name = "reserve_a")
    private String reserveA;

    @Column(name = "reserve_b")
    private String reserveB;

    @Column(name = "reserve_c")
    private String reserveC;

}
