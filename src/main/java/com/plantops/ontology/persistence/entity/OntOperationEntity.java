package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ont_operation")
public class OntOperationEntity extends OntRevisionScopedEntity {

    @Column(name = "supply_order_id", nullable = false, length = 128)
    public String supplyOrderId;

    @Column(name = "plan_unit_id", length = 128)
    public String planUnitId;

    @Column(name = "sequence_nr", nullable = false)
    public int sequenceNr;

    @Column(name = "routing_sequence_no", nullable = false)
    public int routingSequenceNo;

    @Column(name = "operation_name", nullable = false, length = 256)
    public String operationName;

    @Column(name = "production_duration", nullable = false)
    public long productionDuration;

    @Column(name = "preprocessing_time", nullable = false)
    public long preprocessingTime;

    @Column(name = "postprocessing_time", nullable = false)
    public long postprocessingTime;

    @Column(name = "segment_index", nullable = false)
    public int segmentIndex;

    @Column(name = "last_segment", nullable = false)
    public boolean lastSegment = true;

    @Column(name = "parallel_group_id", length = 128)
    public String parallelGroupId;

    @Column(nullable = false)
    public boolean locked;

    @Column(name = "earliest_possible_start_own")
    public LocalDateTime earliestPossibleStartOwn;

    @Column(name = "earliest_possible_end_own")
    public LocalDateTime earliestPossibleEndOwn;

    @Column(name = "earliest_possible_start_total")
    public LocalDateTime earliestPossibleStartTotal;

    @Column(name = "earliest_possible_end_total")
    public LocalDateTime earliestPossibleEndTotal;

    @Column(name = "latest_desired_start")
    public LocalDateTime latestDesiredStart;

    @Column(name = "latest_desired_end")
    public LocalDateTime latestDesiredEnd;

    @Column(name = "planned_start_total")
    public LocalDateTime plannedStartTotal;

    @Column(name = "planned_end_total")
    public LocalDateTime plannedEndTotal;

    @Column(nullable = false)
    public boolean infeasible;

    public static List<OntOperationEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
