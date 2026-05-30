package com.plantops.api.dto;

import java.util.List;

public record PipelineResultDto(
        String pipelineRunId,
        List<PipelineRunLogLineDto> executionLog,
        List<DemandPoolEntryDto> demandPool,
        List<KittingResultDto> kittingResults,
        CapacityAnalysisDto capacityAnalysis,
        MasterPlanResultDto masterPlan,
        DetailScheduleResultDto detailSchedule,
        MasterPlanRefreshResultDto masterPlanRefresh,
        DispatchResultDto dispatch,
        KpiReportDto kpiReport
) {

    public PipelineResultDto(
            String pipelineRunId,
            List<PipelineRunLogLineDto> executionLog,
            List<DemandPoolEntryDto> demandPool,
            List<KittingResultDto> kittingResults,
            CapacityAnalysisDto capacityAnalysis,
            MasterPlanResultDto masterPlan,
            DetailScheduleResultDto detailSchedule,
            DispatchResultDto dispatch,
            KpiReportDto kpiReport) {
        this(
                pipelineRunId,
                executionLog,
                demandPool,
                kittingResults,
                capacityAnalysis,
                masterPlan,
                detailSchedule,
                detailSchedule != null ? detailSchedule.masterPlanRefresh() : null,
                dispatch,
                kpiReport);
    }
}
