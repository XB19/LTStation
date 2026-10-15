package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table customer_card_infos (origine SQL Server : CustomerCardInfos). */
@Getter
@Setter
@Entity
@Table(name = "customer_card_infos")
public class CustomerCardInfos {

    @Id
    @Column(name = "asn")
    private String asn;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "customer_account_id")
    private Integer customerAccountId;

    @Column(name = "group_id")
    private Integer groupId;

    @Column(name = "is_credit_card")
    private Boolean isCreditCard;

    @Column(name = "credit_lines")
    private Double creditLines;

    @Column(name = "date_account")
    private Integer dateAccount;

    @Column(name = "date_repayment")
    private Integer dateRepayment;

    @Column(name = "account_no")
    private String accountNo;

    @Column(name = "cipher_flg")
    private String cipherFlg;

    @Column(name = "discount")
    private Double discount;

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

    @Column(name = "issue_card_fee")
    private Double issueCardFee;

    @Column(name = "station_id")
    private Integer stationId;

    @Column(name = "issue_datetime")
    private LocalDateTime issueDatetime;

    @Column(name = "valid_datetime")
    private LocalDateTime validDatetime;

    @Column(name = "card_status_type_id")
    private Integer cardStatusTypeId;

    @Column(name = "score")
    private Double score;

    @Column(name = "score_last_load_time")
    private LocalDateTime scoreLastLoadTime;

    @Column(name = "category_list")
    private String categoryList;

    @Column(name = "classification_id")
    private Integer classificationId;

    @Column(name = "use_confine_in_store")
    private String useConfineInStore;

    @Column(name = "used_amount_monthly")
    private Double usedAmountMonthly;

    @Column(name = "preferential_price")
    private Double preferentialPrice;

    @Column(name = "limit_type_id")
    private Integer limitTypeId;

    @Column(name = "memo1")
    private String memo1;

    @Column(name = "update_datetime")
    private LocalDateTime updateDatetime;

    @Column(name = "is_limit_vehicle_no")
    private Boolean isLimitVehicleNo;

    @Column(name = "media_type_id")
    private Integer mediaTypeId;

    @Column(name = "cylinder_code")
    private String cylinderCode;

    @Column(name = "cylinder_reg_code")
    private String cylinderRegCode;

    @Column(name = "cylinder_type_id")
    private Integer cylinderTypeId;

    @Column(name = "cylinder_volumn")
    private String cylinderVolumn;

    @Column(name = "cylinder_created_date")
    private LocalDateTime cylinderCreatedDate;

    @Column(name = "cylinder_verify_date")
    private LocalDateTime cylinderVerifyDate;

    @Column(name = "cylinder_expiration_date")
    private LocalDateTime cylinderExpirationDate;

    @Column(name = "cylinder_reg_date")
    private LocalDateTime cylinderRegDate;

    @Column(name = "cylinder_deadline")
    private LocalDateTime cylinderDeadline;

    @Column(name = "cylinder_reg_department")
    private String cylinderRegDepartment;

    @Column(name = "special_price")
    private String specialPrice;

    @Column(name = "open_accoun_emp_name")
    private String openAccounEmpName;

    @Column(name = "company_id")
    private Integer companyId;

    @Column(name = "np_value")
    private String npValue;

}
