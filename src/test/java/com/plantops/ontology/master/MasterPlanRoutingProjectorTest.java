package com.plantops.ontology.master;

import com.plantops.testsupport.SpecRef;
import com.plantops.persistence.entity.MdRoutingEntity;
import com.plantops.persistence.entity.MdRoutingStepEntity;
import com.plantops.persistence.entity.MdRoutingStepImEntity;
import com.plantops.persistence.entity.MdRoutingStepOsrEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@SpecRef("AC-09")
class MasterPlanRoutingProjectorTest {

    private static final String PRODUCT = "FG-MPDM-TEST";
    private static final String COMP = "RM-MPDM-TEST";
    private static final String ROUTING = "RT-MPDM-TEST";
    private static final String ROUTING_ALT = "RT-MPDM-ALT";

    @Inject
    MasterPlanRoutingProjector projector;

    @Test
    @TestTransaction
    void projectsRoutingStepsWithResourcesAndMaterials() {
        ensureFixture();

        String pispId = com.plantops.ontology.OntologyIds.pispId(PRODUCT, StockingPoint.FG);
        var routing = projector.projectRoutingHeader(pispId, PRODUCT);
        var steps = projector.projectRoutingSteps(pispId, PRODUCT);

        assertEquals(2, routing.stepCount());
        assertEquals(2, steps.size());
        assertEquals("OP-1", steps.get(0).operationName());
        assertFalse(steps.get(0).inputMaterials().isEmpty());
        assertTrue(steps.get(0).outputMaterials().isEmpty());
        assertTrue(steps.get(1).inputMaterials().isEmpty());
        assertFalse(steps.get(1).outputMaterials().isEmpty());
        assertEquals(1, steps.get(0).standardResources().size());
    }

    @Test
    @TestTransaction
    void doesNotFabricateRoutingForUnknownProduct() {
        String raw = "RAW-MPDM-NO-ROUTING";
        String pispId = com.plantops.ontology.OntologyIds.pispId(raw, StockingPoint.RAW);
        assertFalse(projector.hasRoutingForProduct(raw));
        assertEquals(0, projector.projectRoutingHeader(pispId, raw).stepCount());
        assertTrue(projector.projectRoutingSteps(pispId, raw).isEmpty());
    }

    @Test
    @TestTransaction
    void listsMultipleRoutingsByPathPriority() {
        ensureFixture();
        ensureAlternatePath();

        String pispId = com.plantops.ontology.OntologyIds.pispId(PRODUCT, StockingPoint.FG);
        var routings = projector.listRoutingsForPisp(pispId, PRODUCT);
        assertEquals(2, routings.size());
        assertEquals(1, routings.get(0).pathPriority());
        assertEquals(2, routings.get(1).pathPriority());
        assertEquals(2, projector.projectRoutingSteps(pispId, PRODUCT, 1).size());
        assertEquals(1, projector.projectRoutingSteps(pispId, PRODUCT, 2).size());
    }

    private static void ensureAlternatePath() {
        if (MdRoutingEntity.find("workspaceId = ?1 and routingCode = ?2", MdRoutingEntity.ws(), ROUTING_ALT)
                .firstResult() != null) {
            return;
        }
        MdRoutingEntity routing = new MdRoutingEntity();
        routing.routingCode = ROUTING_ALT;
        routing.productCode = PRODUCT;
        routing.stockingPointCode = "FG";
        routing.pathPriority = 2;
        routing.name = "Fast path";
        routing.ensureWorkspace();
        routing.persist();

        MdRoutingStepEntity step = new MdRoutingStepEntity();
        step.routingCode = ROUTING_ALT;
        step.sequenceNo = 1;
        step.operationName = "OP-FAST";
        step.ensureWorkspace();
        step.persist();

        MdRoutingStepOsrEntity osr = new MdRoutingStepOsrEntity();
        osr.routingCode = ROUTING_ALT;
        osr.sequenceNo = 1;
        osr.standardResourceCode = "RES-MPDM-FAST";
        osr.resourcePriority = 1;
        osr.resourceUsageType = "SINGLE";
        osr.processTimeSeconds = BigDecimal.valueOf(600);
        osr.ensureWorkspace();
        osr.persist();
    }

    private static void ensureFixture() {
        if (MdRoutingEntity.find("workspaceId = ?1 and routingCode = ?2", MdRoutingEntity.ws(), ROUTING)
                .firstResult() != null) {
            return;
        }
        MdRoutingEntity routing = new MdRoutingEntity();
        routing.routingCode = ROUTING;
        routing.productCode = PRODUCT;
        routing.stockingPointCode = "FG";
        routing.pathPriority = 1;
        routing.name = "Test FG 工艺";
        routing.ensureWorkspace();
        routing.persist();

        MdRoutingStepEntity step1 = new MdRoutingStepEntity();
        step1.routingCode = ROUTING;
        step1.sequenceNo = 1;
        step1.operationName = "OP-1";
        step1.ensureWorkspace();
        step1.persist();

        MdRoutingStepOsrEntity osr1 = new MdRoutingStepOsrEntity();
        osr1.routingCode = ROUTING;
        osr1.sequenceNo = 1;
        osr1.standardResourceCode = "RES-MPDM-A";
        osr1.resourcePriority = 1;
        osr1.resourceUsageType = "SINGLE";
        osr1.processTimeSeconds = BigDecimal.valueOf(3600);
        osr1.ensureWorkspace();
        osr1.persist();

        MdRoutingStepEntity step2 = new MdRoutingStepEntity();
        step2.routingCode = ROUTING;
        step2.sequenceNo = 2;
        step2.operationName = "OP-2";
        step2.ensureWorkspace();
        step2.persist();

        MdRoutingStepOsrEntity osr2 = new MdRoutingStepOsrEntity();
        osr2.routingCode = ROUTING;
        osr2.sequenceNo = 2;
        osr2.standardResourceCode = "RES-MPDM-B";
        osr2.resourcePriority = 1;
        osr2.resourceUsageType = "SINGLE";
        osr2.processTimeSeconds = BigDecimal.valueOf(1800);
        osr2.ensureWorkspace();
        osr2.persist();

        MdRoutingStepImEntity im = new MdRoutingStepImEntity();
        im.routingCode = ROUTING;
        im.sequenceNo = 1;
        im.componentProductCode = COMP;
        im.componentQty = BigDecimal.ONE;
        im.ensureWorkspace();
        im.persist();
    }
}
