package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "iam_audit_log")
public class IamAuditLogEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "actor_user_id", nullable = false, length = 50)
    public String actorUserId;

    @Column(nullable = false, length = 100)
    public String action;

    @Column(name = "target_type", length = 50)
    public String targetType;

    @Column(name = "target_id", length = 200)
    public String targetId;

    @Column(name = "payload_json")
    public String payloadJson;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
