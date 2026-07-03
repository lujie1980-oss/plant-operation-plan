package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ont_period")
public class OntPeriodEntity extends OntRevisionScopedEntity {

    @Column(name = "sequence_nr", nullable = false)
    public int sequenceNr;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Column(name = "granularity", nullable = false, length = 16)
    public String granularity;

    @Column(name = "shift_id", length = 64)
    public String shiftId;

    @Column(name = "parent_period_id", length = 128)
    public String parentPeriodId;

    @Column(name = "start_date_time")
    public LocalDateTime startDateTime;

    @Column(name = "end_date_time")
    public LocalDateTime endDateTime;

    @Column(name = "is_leaf", nullable = false)
    public boolean leaf = true;

    public static List<OntPeriodEntity> forRevision(String workspaceId, String revisionId) {
        return list(
                "workspaceId = ?1 and revisionId = ?2 order by sequenceNr",
                workspaceId,
                revisionId);
    }
}
