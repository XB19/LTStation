package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table group_card_infos (origine SQL Server : GroupCardInfos). */
@Getter
@Setter
@Entity
@Table(name = "group_card_infos")
public class GroupCardInfos {

    @Column(name = "group_id")
    private Integer groupId;

    @Id
    @Column(name = "asn")
    private String asn;

    @Column(name = "valid_datetime")
    private LocalDateTime validDatetime;

    @Column(name = "is_credit_card")
    private Boolean isCreditCard;

    @Column(name = "credit_lines")
    private Double creditLines;

    @Column(name = "date_account")
    private Integer dateAccount;

    @Column(name = "date_repayment")
    private Integer dateRepayment;

    @Column(name = "cipher_flg")
    private String cipherFlg;

    @Column(name = "card_status_type_id")
    private Integer cardStatusTypeId;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "issue_datetime")
    private LocalDateTime issueDatetime;

    @Column(name = "group_account_id")
    private Integer groupAccountId;

    @Column(name = "used_amount_monthly")
    private Double usedAmountMonthly;

    @Column(name = "memo1")
    private String memo1;

    @Column(name = "limit_type_id")
    private Integer limitTypeId;

    @Column(name = "use_confine")
    private String useConfine;

    @Column(name = "limit_vehicle_no")
    private String limitVehicleNo;

    @Column(name = "limit_oil")
    private String limitOil;

    @Column(name = "limit_volume")
    private Double limitVolume;

    @Column(name = "limit_time")
    private Integer limitTime;

    @Column(name = "limit_amount")
    private Double limitAmount;

    @Column(name = "limit_volume_monthly")
    private Double limitVolumeMonthly;

    @Column(name = "limit_amount_monthly")
    private Double limitAmountMonthly;

    @Column(name = "limit_mileage")
    private Double limitMileage;

    @Column(name = "mileage_tolerance")
    private Double mileageTolerance;

    @Column(name = "quota_range")
    private Double quotaRange;

    @Column(name = "limit_amount_oth")
    private Double limitAmountOth;

    @Column(name = "limit_amount_oth_monthly")
    private Double limitAmountOthMonthly;

    @Column(name = "limit_time_oth")
    private Integer limitTimeOth;

    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "category_list")
    private String categoryList;

    @Column(name = "use_confine_in_store")
    private String useConfineInStore;

    @Column(name = "discount")
    private Double discount;

    @Column(name = "preferential_price")
    private Double preferentialPrice;

    @Column(name = "is_limit_vehicle_no")
    private Boolean isLimitVehicleNo;

    @Column(name = "media_type_id")
    private Integer mediaTypeId;

    @Column(name = "min_balance")
    private BigDecimal minBalance;

    @Column(name = "company_id")
    private Integer companyId;

}
