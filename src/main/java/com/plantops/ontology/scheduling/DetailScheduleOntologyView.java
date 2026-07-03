package com.plantops.ontology.scheduling;

import java.time.LocalDate;
import java.util.List;

/** Read-only MOD-SCH ontology projection; not merged into {@link com.plantops.ontology.OntologyGraph} until SCH-P1. */
public record DetailScheduleOntologyView(
        String detailScheduleVersionId,
        LocalDate planningAnchorDate,
        List<OperationSchedule> operationSchedules,
        List<PhysicalResourceCapacityAssignmentSchedule> capacityAssignments) {}
