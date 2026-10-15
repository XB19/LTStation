package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Table webpages_membership (origine SQL Server : webpages_Membership). */
@Getter
@Setter
@Entity
@Table(name = "webpages_membership")
public class WebpagesMembership {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    @Column(name = "confirmation_token")
    private String confirmationToken;

    @Column(name = "is_confirmed")
    private Boolean isConfirmed;

    @Column(name = "last_password_failure_date")
    private LocalDateTime lastPasswordFailureDate;

    @Column(name = "password_failures_since_last_success")
    private Integer passwordFailuresSinceLastSuccess;

    @Column(name = "password")
    private String password;

    @Column(name = "password_changed_date")
    private LocalDateTime passwordChangedDate;

    @Column(name = "password_salt")
    private String passwordSalt;

    @Column(name = "password_verification_token")
    private String passwordVerificationToken;

    @Column(name = "password_verification_token_expiration_date")
    private LocalDateTime passwordVerificationTokenExpirationDate;

}
