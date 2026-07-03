package com.plantops.api;

import com.plantops.api.MasterDataResource;
import com.plantops.api.dto.masterdata.MasterDataDtos.BomDto;
import com.plantops.api.dto.slitting.SlittingBomScopeDto;
import com.plantops.api.dto.slitting.SlittingMaterialDemandDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.scenario.slitting.SlittingBomScopeService;
import com.plantops.scenario.slitting.SlittingMaterialDemandService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/slitting/bom")
@jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
public class SlittingBomResource {

    @Inject
    SlittingBomScopeService scopeService;

    @Inject
    SlittingMaterialDemandService demandService;

    @GET
    @Path("/scopes")
    public List<SlittingBomScopeDto> listScopes() {
        return scopeService.listScopes();
    }

    @GET
    @Path("/scopes/{scopeId}/components")
    public List<BomDto> listScopeBom(@PathParam("scopeId") String scopeId) {
        SlittingBomScopeDto scope = scopeService.requireScope(scopeId);
        String finished = scope.finishedProductCode();
        if (finished == null || finished.isBlank()) {
            throw new NotFoundException("scope has no finishedProductCode: " + scopeId);
        }
        String ws = com.plantops.workspace.WorkspaceResolver.currentWorkspaceId();
        var materialByCode = com.plantops.persistence.entity.MaterialEntity.listInWorkspace().stream()
                .collect(java.util.stream.Collectors.toMap(m -> m.materialCode, m -> m, (a, b) -> a));
        return BomComponentEntity.<BomComponentEntity>list("workspaceId", ws).stream()
                .filter(b -> finished.equals(b.finishedProductCode))
                .map(e -> MasterDataResource.toBomDto(e, materialByCode))
                .toList();
    }

    @GET
    @Path("/demands-by-material")
    public List<SlittingMaterialDemandDto> demandsByMaterial(
            @QueryParam("productCode") String productCode,
            @QueryParam("finishedProductCode") String finishedProductCode) {
        return demandService.demandsForMaterial(productCode, finishedProductCode);
    }
}
