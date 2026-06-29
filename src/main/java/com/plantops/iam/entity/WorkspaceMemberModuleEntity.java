package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_member_module")
@IdClass(WorkspaceMemberModuleId.class)
public class WorkspaceMemberModuleEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "user_id", length = 50)
    public String userId;

    @Id
    @Column(name = "module_id", length = 20)
    public String moduleId;

    @Column(name = "access_level", nullable = false, length = 10)
    public String accessLevel = "NONE";

    public static java.util.List<WorkspaceMemberModuleEntity> findByMember(String workspaceId, String userId) {
        return list("workspaceId = ?1 and userId = ?2", workspaceId, userId);
    }
}
