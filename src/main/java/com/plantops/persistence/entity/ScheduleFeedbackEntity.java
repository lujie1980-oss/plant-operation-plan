package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "schedule_feedback", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "detail_schedule_version_id", "operation_id"
}))
public class ScheduleFeedbackEntity extends WorkspaceScopedEntity {

    @Column(name = "feedback_id", nullable = false, length = 64)
    public String feedbackId;

    public String masterPlanVersionId;

    @Column(name = "detail_schedule_version_id", nullable = false, length = 64)
    public String detailScheduleVersionId;

    public String workOrderNo;

    public int operationSeq;

    public String operationId;

    public String resourceId;

    /** ENT-PR（产线 lineId）；细排反馈按 PR 扣减 PRP 可用产能（TODO-24 P5）。 */
    public String physicalResourceId;

    public LocalDateTime plannedStart;

    public LocalDateTime plannedEnd;

    public LocalDate slotDate;

    public int durationMinutes;

    public String scope;

    public LocalDate planningAnchorDate;

    public LocalDateTime feedbackTs;

    public static List<ScheduleFeedbackEntity> listForDetailSchedule(String detailScheduleVersionId) {
        return list(
                "workspaceId = ?1 and detailScheduleVersionId = ?2 order by workOrderNo, operationSeq",
                ws(),
                detailScheduleVersionId);
    }

    public static List<ScheduleFeedbackEntity> listFrozenUpTo(LocalDate cutoffInclusive) {
        return list(
                "workspaceId = ?1 and scope = ?2 and slotDate <= ?3 order by slotDate, resourceId",
                ws(),
                ScheduleFeedbackScope.FROZEN.name(),
                cutoffInclusive);
    }

    public static long deleteForDetailSchedule(String detailScheduleVersionId) {
        return delete("workspaceId = ?1 and detailScheduleVersionId = ?2", ws(), detailScheduleVersionId);
    }
}
