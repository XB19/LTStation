package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table menu_list (origine SQL Server : MenuList). */
@Getter
@Setter
@Entity
@Table(name = "menu_list")
public class MenuList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "menu_id")
    private Integer menuId;

    @Column(name = "menu_name")
    private String menuName;

    @Column(name = "is_using")
    private Boolean isUsing;

}
