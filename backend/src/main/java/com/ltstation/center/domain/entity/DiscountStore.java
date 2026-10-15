package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table discount_store (origine SQL Server : DiscountStore). */
@Getter
@Setter
@Entity
@Table(name = "discount_store")
@IdClass(DiscountStore.Pk.class)
public class DiscountStore {

    @Id
    @Column(name = "group_id")
    private Integer groupId;

    @Id
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "discount")
    private Double discount;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer groupId;
        private Integer categoryId;
    }
}
