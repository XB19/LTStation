package com.ltstation.center.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Table webpages_users_in_roles (origine SQL Server : webpages_UsersInRoles). */
@Getter
@Setter
@Entity
@Table(name = "webpages_users_in_roles")
@IdClass(WebpagesUsersInRoles.Pk.class)
public class WebpagesUsersInRoles {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Id
    @Column(name = "role_id")
    private Integer roleId;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private Integer userId;
        private Integer roleId;
    }
}
