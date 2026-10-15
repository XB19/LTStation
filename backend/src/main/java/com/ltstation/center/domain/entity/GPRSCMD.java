package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table gprscmd (origine SQL Server : GPRSCMD). */
@Getter
@Setter
@Entity
@Table(name = "gprscmd")
public class GPRSCMD {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "iccid")
    private String iccid;

    @Column(name = "gunid")
    private Integer gunid;

    @Column(name = "cmd_type")
    private Integer cmdType;

    @Column(name = "cmd_value")
    private String cmdValue;

    @Column(name = "is_deal")
    private Boolean isDeal;

    @Column(name = "create_time")
    private LocalDateTime createTime;

}
