package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table webpages_o_auth_membership (origine SQL Server : webpages_OAuthMembership). */
@Getter
@Setter
@Entity
@Table(name = "webpages_o_auth_membership")
@IdClass(WebpagesOAuthMembership.Pk.class)
public class WebpagesOAuthMembership {

    @Id
    @Column(name = "provider")
    private String provider;

    @Id
    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(name = "user_id")
    private Integer userId;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String provider;
        private String providerUserId;
    }
}
