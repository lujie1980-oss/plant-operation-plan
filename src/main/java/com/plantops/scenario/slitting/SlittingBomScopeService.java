package com.plantops.scenario.slitting;

import com.plantops.api.dto.slitting.SlittingBomScopeDto;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SlittingBomScopeService {

    public List<SlittingBomScopeDto> listScopes() {
        Map<String, SlittingBomScopeDto> scopes = new LinkedHashMap<>();
        for (MasterRollEntity roll : MasterRollEntity.listInWorkspace()) {
            String finished = resolveFinished(roll.finishedProductCode, roll.productCode, roll.rollCode);
            String id = "master:" + roll.rollCode;
            scopes.put(
                    id,
                    new SlittingBomScopeDto(
                            id,
                            "MASTER_ROLL",
                            "母卷 · " + roll.rollCode,
                            finished,
                            roll.productCode != null ? roll.productCode : finished));
        }
        for (ChildSlittingOrderEntity order : ChildSlittingOrderEntity.listInWorkspace()) {
            String finished = resolveFinished(order.finishedProductCode, order.productCode, order.orderCode);
            String id = "order:" + order.orderCode;
            scopes.put(
                    id,
                    new SlittingBomScopeDto(
                            id,
                            "CHILD_ORDER",
                            "分切需求 · " + order.orderCode,
                            finished,
                            order.productCode));
        }
        return new ArrayList<>(scopes.values());
    }

    public SlittingBomScopeDto requireScope(String scopeId) {
        return listScopes().stream()
                .filter(s -> s.scopeId().equals(scopeId))
                .findFirst()
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("BOM scope not found: " + scopeId));
    }

    private static String resolveFinished(String finished, String product, String fallback) {
        if (finished != null && !finished.isBlank()) {
            return finished.trim();
        }
        if (product != null && !product.isBlank()) {
            return product.trim();
        }
        return fallback;
    }
}
