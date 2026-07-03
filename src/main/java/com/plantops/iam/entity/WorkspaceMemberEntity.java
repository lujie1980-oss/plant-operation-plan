package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_member")
@IdClass(WorkspaceMemberId.class)
public class WorkspaceMemberEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "user_id", length = 50)
    public String userId;

    @Column(nullable = false, length = 20)
    public String role = "MEMBER";

    public boolean isOwner() { return "OWNER".equals(role); }
    public boolean isAdmin() { return "WS_ADMIN".equals(role) || isOwner(); }
}
