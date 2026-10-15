package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table credit_card_bill_details (origine SQL Server : CreditCardBillDetails). */
@Getter
@Setter
@Entity
@Table(name = "credit_card_bill_details")
@IdClass(CreditCardBillDetails.Pk.class)
public class CreditCardBillDetails {

    @Id
    @Column(name = "credit_card_bill_id")
    private Integer creditCardBillId;

    @Id
    @Column(name = "account_service_log_id")
    private Integer accountServiceLogId;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer creditCardBillId;
        private Integer accountServiceLogId;
    }
}
