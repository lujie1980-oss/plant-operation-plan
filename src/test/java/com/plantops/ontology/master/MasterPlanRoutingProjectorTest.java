package com.plantops.ontology.master;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanRoutingProjectorTest {

    private static final String PRODUCT = "FG-MPDM-TEST";
    private static final String COMP = "RM-MPDM-TEST";

    @Inject
    MasterPlanRoutingProjector projector;

    @Test
    @TestTransaction
    void projectsRoutingStepsWithResourcesAndMaterials() {
        ensureFixture();

        String pispId = com.plantops.ontology.OntologyIds.pispId(PRODUCT, com.plantops.ontology.master.StockingPoint.FG);
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
    void doesNotFabricateRoutingFromCatalogForUnknownProduct() {
        String raw = "RAW-MPDM-NO-ROUTING";
        if (MaterialEntity.findByCode(raw) == null) {
            MaterialEntity m = new MaterialEntity();
            m.materialCode = raw;
            m.materialName = "Raw without routing";
            m.materialType = "原材料";
            m.stampWorkspace();
            m.persist();
        }
        String pispId = com.plantops.ontology.OntologyIds.pispId(raw, com.plantops.ontology.master.StockingPoint.RAW);
        assertFalse(MasterPlanRoutingProjector.hasRouting(raw));
        assertEquals(0, projector.projectRoutingHeader(pispId, raw).stepCount());
        assertTrue(projector.projectRoutingSteps(pispId, raw).isEmpty());
    }

    @Test
    @TestTransaction
    void listsMultipleRoutingsByPathPriority() {
        ensureFixture();
        ensureAlternatePath();

        String pispId = com.plantops.ontology.OntologyIds.pispId(PRODUCT, com.plantops.ontology.master.StockingPoint.FG);
        var routings = projector.listRoutingsForPisp(pispId, PRODUCT);
        assertEquals(2, routings.size());
        assertEquals(1, routings.get(0).pathPriority());
        assertEquals(2, routings.get(1).pathPriority());
        assertEquals(2, projector.projectRoutingSteps(pispId, PRODUCT, 1).size());
        assertEquals(1, projector.projectRoutingSteps(pispId, PRODUCT, 2).size());
    }

    private static void ensureAlternatePath() {
        if (ProductResourceEntity.findByProductAndResource(PRODUCT, "RES-MPDM-FAST") != null) {
            return;
        }
        ProductResourceEntity fast = new ProductResourceEntity();
        fast.productCode = PRODUCT;
        fast.resourceId = "RES-MPDM-FAST";
        fast.operationName = "OP-FAST";
        fast.sequenceNo = 1;
        fast.routingPathPriority = 2;
        fast.processTimeSeconds = new BigDecimal("600");
        fast.stampWorkspace();
        fast.persist();
    }

    private static void ensureFixture() {
        if (MaterialEntity.findByCode(PRODUCT) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = PRODUCT;
            fg.materialName = "Test FG";
            fg.stampWorkspace();
            fg.persist();
        }
        if (MaterialEntity.findByCode(COMP) == null) {
            MaterialEntity rm = new MaterialEntity();
            rm.materialCode = COMP;
            rm.materialName = "Test RM";
            rm.stampWorkspace();
            rm.persist();
        }
        if (ProductResourceEntity.findByProductAndResource(PRODUCT, "RES-MPDM-A") == null) {
            ProductResourceEntity step1 = new ProductResourceEntity();
            step1.productCode = PRODUCT;
            step1.resourceId = "RES-MPDM-A";
            step1.operationName = "OP-1";
            step1.sequenceNo = 1;
            step1.processTimeSeconds = new BigDecimal("3600");
            step1.stampWorkspace();
            step1.persist();
        }
        if (ProductResourceEntity.findByProductAndResource(PRODUCT, "RES-MPDM-B") == null) {
            ProductResourceEntity step2 = new ProductResourceEntity();
            step2.productCode = PRODUCT;
            step2.resourceId = "RES-MPDM-B";
            step2.operationName = "OP-2";
            step2.sequenceNo = 2;
            step2.processTimeSeconds = new BigDecimal("1800");
            step2.stampWorkspace();
            step2.persist();
        }
        if (BomComponentEntity.findByParent(PRODUCT).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-MPDM";
            bom.bomVersion = "1";
            bom.finishedProductCode = PRODUCT;
            bom.parentProductCode = PRODUCT;
            bom.componentProductCode = COMP;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }
}
