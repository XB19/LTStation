package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Table joy_fill_merch_info (origine SQL Server : JoyFill_merchInfo). */
@Getter
@Setter
@Entity
@Table(name = "joy_fill_merch_info")
public class JoyFillMerchInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "sub_mch_id")
    private String subMchId;

}
