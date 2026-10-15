package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table locations (origine SQL Server : Locations). */
@Getter
@Setter
@Entity
@Table(name = "locations")
@IdClass(Locations.Pk.class)
public class Locations {

    @Id
    @Column(name = "location_id")
    private Integer locationId;

    @Id
    @Column(name = "repository_id")
    private Integer repositoryId;

    @Column(name = "location_desc")
    private String locationDesc;

    @Column(name = "lane_no")
    private Integer laneNo;

    @Column(name = "row_no")
    private Integer rowNo;

    @Column(name = "column_no")
    private Integer columnNo;

    @Column(name = "layer_no")
    private Integer layerNo;

    @Column(name = "location_status_id")
    private Integer locationStatusId;

    @Column(name = "memo")
    private String memo;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer locationId;
        private Integer repositoryId;
    }
}
