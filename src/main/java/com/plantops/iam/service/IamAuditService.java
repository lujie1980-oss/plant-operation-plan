package com.plantops.iam.service;

import com.plantops.iam.entity.IamAuditLogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IamAuditService {

    @Transactional
    public void log(String actorUserId, String action, String targetType, String targetId, String payloadJson) {
        IamAuditLogEntity row = new IamAuditLogEntity();
        row.actorUserId = actorUserId;
        row.action = action;
        row.targetType = targetType;
        row.targetId = targetId;
        row.payloadJson = payloadJson;
        row.persist();
    }
}
