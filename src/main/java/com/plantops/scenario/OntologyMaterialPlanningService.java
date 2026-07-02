package com.plantops.scenario;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.AutoReservationRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.AutoReservationResultDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.CreateFulfillmentRequest;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.EligibleSupplyListDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.FulfillmentDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.PeriodDemandListDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.ReservationAlertDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanRequest;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanResultDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyRoutingCandidateDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.material.OntologyMaterialBalanceProjector;
import com.plantops.ontology.material.OntologyMaterialDemandProjector;
import com.plantops.ontology.material.OntologyMaterialEligibleSupplyProjector;
import com.plantops.ontology.material.OntologyMaterialPeriodDemandProjector;
import com.plantops.ontology.material.OntologyMaterialReservationService;
import com.plantops.ontology.material.OntologyMaterialSupplyPlanService;
import com.plantops.ontology.material.OntologyMaterialSupplyRoutingService;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class OntologyMaterialPlanningService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyMaterialBalanceProjector balanceProjector;

    @Inject
    OntologyMaterialDemandProjector demandProjector;

    @Inject
    OntologyMaterialSupplyRoutingService supplyRoutingService;

    @Inject
    OntologyMaterialSupplyPlanService supplyPlanService;

    @Inject
    OntologyMaterialPeriodDemandProjector periodDemandProjector;

    @Inject
    OntologyMaterialEligibleSupplyProjector eligibleSupplyProjector;

    @Inject
    OntologyMaterialReservationService reservationService;

    public MaterialRequirementReportDto balance(String masterPlanVersionId) {
        return balanceProjector.project(loadGraph(masterPlanVersionId));
    }

    public MaterialRequirementReportDto compute(String masterPlanVersionId) {
        return balance(masterPlanVersionId);
    }

    public MaterialDemandDetailDto demandDetail(String productCode, String masterPlanVersionId) {
        return demandProjector.buildDemandDetail(loadGraph(masterPlanVersionId), productCode);
    }

    public List<SupplyRoutingCandidateDto> routingCandidates(
            String pispId,
            String periodFrom,
            String periodTo,
            Double quantity,
            String masterPlanVersionId) {
        OntologyGraph graph = loadGraph(masterPlanVersionId);
        double qty = quantity != null ? quantity : 0.0;
        return supplyRoutingService.listRoutingCandidates(graph, pispId, periodFrom, periodTo, qty);
    }

    public CreateSupplyPlanResultDto createSupplyPlan(
            String pispId,
            CreateSupplyPlanRequest request,
            String masterPlanVersionId) {
        return supplyPlanService.createSupplyPlan(pispId, request, masterPlanVersionId);
    }

    public PeriodDemandListDto periodDemands(
            String pispId,
            String periodFrom,
            String periodTo,
            String masterPlanVersionId) {
        return periodDemandProjector.project(loadGraph(masterPlanVersionId), pispId, periodFrom, periodTo);
    }

    public EligibleSupplyListDto eligibleSupplies(String demandId, String masterPlanVersionId) {
        return eligibleSupplyProjector.project(loadGraph(masterPlanVersionId), demandId);
    }

    public FulfillmentDto createFulfillment(CreateFulfillmentRequest request, String masterPlanVersionId) {
        return reservationService.createFulfillment(request, masterPlanVersionId);
    }

    public AutoReservationResultDto autoReserve(AutoReservationRequest request, String masterPlanVersionId) {
        return reservationService.autoReserve(request, masterPlanVersionId);
    }

    public List<ReservationAlertDto> reservationAlerts(
            String pispId,
            String periodFrom,
            String periodTo,
            String masterPlanVersionId) {
        return reservationService.reservationAlerts(
                loadGraph(masterPlanVersionId), pispId, periodFrom, periodTo);
    }

    private OntologyGraph loadGraph(String masterPlanVersionId) {
        return authoritativeOntologyGraph.getOrLoad(
                WorkspaceResolver.currentWorkspaceId(), masterPlanVersionId);
    }
}
