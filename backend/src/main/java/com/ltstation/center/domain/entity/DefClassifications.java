package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table def_classifications (origine SQL Server : DefClassifications). */
@Getter
@Setter
@Entity
@Table(name = "def_classifications")
public class DefClassifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "classification_desc")
    private String classificationDesc;

}
