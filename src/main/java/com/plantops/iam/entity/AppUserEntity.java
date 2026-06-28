package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class AppUserEntity extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", length = 50)
    public String userId;

    @Column(name = "login_name", nullable = false, length = 100)
    public String loginName;

    @Column(name = "display_name", nullable = false, length = 200)
    public String displayName;

    @Column(name = "password_hash", length = 200)
    public String passwordHash;

    @Column(name = "is_super_admin", nullable = false)
    public boolean superAdmin;

    @Column(nullable = false, length = 20)
    public String status = "ACTIVE";

    @Column(name = "last_login_at")
    public LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
