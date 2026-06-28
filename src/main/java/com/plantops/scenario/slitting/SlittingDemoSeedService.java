package com.plantops.scenario.slitting;

import com.fasterxml.jackson.databind.JsonNode;
import com.plantops.persistence.entity.ChildSlittingOrderEntity;
import com.plantops.persistence.entity.MasterRollEntity;
import com.plantops.persistence.entity.SlittingPlanChildOrderEntity;
import com.plantops.persistence.entity.SlittingPlanMasterRollEntity;
import com.plantops.persistence.entity.SlittingPlanVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 从 sample-data JSON 的 slittingPlans 段创建演示分切方案。 */
@ApplicationScoped
public class SlittingDemoSeedService {

    @Transactional
    public void seedFromJson(JsonNode root) {
        if (root == null || !root.has("slittingPlans")) {
            return;
        }
        for (JsonNode planNode : root.get("slittingPlans")) {
            seedPlan(planNode);
        }
    }

    private void seedPlan(JsonNode planNode) {
        String planVersionId = text(planNode, "planVersionId");
        if (planVersionId == null || planVersionId.isBlank()) {
            return;
        }
        if (SlittingPlanVersionEntity.findByPlanVersionId(planVersionId) != null) {
            return;
        }
        String name = text(planNode, "name");
        if (name == null || name.isBlank()) {
            name = planVersionId;
        }
        SlittingPlanVersionEntity plan = new SlittingPlanVersionEntity();
        plan.stampWorkspace();
        plan.planVersionId = planVersionId;
        plan.name = name;
        plan.status = SlittingPlanVersionEntity.STATUS_DRAFT;
        plan.persist();

        for (String rollCode : readStringList(planNode, "masterRollCodes")) {
            MasterRollEntity roll = MasterRollEntity.findByRollCode(rollCode);
            if (roll == null) {
                continue;
            }
            SlittingPlanMasterRollEntity link = new SlittingPlanMasterRollEntity();
            link.stampWorkspace();
            link.planVersionId = planVersionId;
            link.masterRollId = roll.id;
            link.persist();
        }
        for (String orderCode : readStringList(planNode, "childOrderCodes")) {
            ChildSlittingOrderEntity order = ChildSlittingOrderEntity.findByOrderCode(orderCode);
            if (order == null) {
                continue;
            }
            SlittingPlanChildOrderEntity link = new SlittingPlanChildOrderEntity();
            link.stampWorkspace();
            link.planVersionId = planVersionId;
            link.childSlittingOrderId = order.id;
            link.persist();
        }
    }

    private static List<String> readStringList(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return out;
        }
        for (JsonNode item : node.get(field)) {
            if (item != null && item.isTextual()) {
                String value = item.asText().trim();
                if (!value.isEmpty()) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
