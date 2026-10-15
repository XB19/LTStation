package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_colors (origine SQL Server : DefColors). */
@Getter
@Setter
@Entity
@Table(name = "def_colors")
public class DefColors {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "color_id")
    private Integer colorId;

    @Column(name = "color_desc")
    private String colorDesc;

}
