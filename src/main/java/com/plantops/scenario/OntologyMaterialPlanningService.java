package com.plantops.scenario;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.material.OntologyMaterialBalanceProjector;
import com.plantops.ontology.material.OntologyMaterialDemandProjector;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OntologyMaterialPlanningService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyMaterialBalanceProjector balanceProjector;

    @Inject
    OntologyMaterialDemandProjector demandProjector;

    public MaterialRequirementReportDto balance(String masterPlanVersionId) {
        return balanceProjector.project(loadGraph(masterPlanVersionId));
    }

    public MaterialRequirementReportDto compute(String masterPlanVersionId) {
        return balance(masterPlanVersionId);
    }

    public MaterialDemandDetailDto demandDetail(String productCode, String masterPlanVersionId) {
        return demandProjector.buildDemandDetail(loadGraph(masterPlanVersionId), productCode);
    }

    private OntologyGraph loadGraph(String masterPlanVersionId) {
        return authoritativeOntologyGraph.getOrLoad(
                WorkspaceResolver.currentWorkspaceId(), masterPlanVersionId);
    }
}
