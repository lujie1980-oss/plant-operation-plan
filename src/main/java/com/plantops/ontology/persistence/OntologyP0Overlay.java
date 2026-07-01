package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationOutputMaterial;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;

/**
 * Overlays P0 entities from a committed {@code ont_*} revision onto a legacy-loader graph shell.
 */
public final class OntologyP0Overlay {

    private OntologyP0Overlay() {}

    public static OntologyGraph apply(OntologyGraph loaderBase, OntologyGraph restoredP0) {
        OntologyGraph.Builder builder = OntologyGraph.builder();

        builder.defaultStockingPoint(loaderBase.defaultStockingPoint());
        loaderBase.pispsById().values().forEach(builder::pisp);
        loaderBase.customerOrderLinesById().values().forEach(builder::customerOrderLine);
        loaderBase.customerOrderLineDeliveriesById().values().forEach(builder::customerOrderLineDelivery);
        loaderBase.forecastDemandsById().values().forEach(builder::forecastDemand);

        for (Demand demand : restoredP0.demandsById().values()) {
            builder.demand(demand);
        }
        for (SupplyOrder supplyOrder : restoredP0.supplyOrdersById().values()) {
            builder.supplyOrder(supplyOrder);
        }
        for (PlanUnit planUnit : loaderBase.planUnitsById().values()) {
            builder.planUnit(planUnit);
        }
        for (Operation operation : restoredP0.operationsById().values()) {
            builder.operation(operation);
        }
        for (OperationOnStandardResource oosr : loaderBase.operationOnStandardResourceById().values()) {
            builder.operationOnStandardResource(oosr);
        }
        for (Supply supply : loaderBase.suppliesById().values()) {
            builder.supply(supply);
        }
        for (OperationInputMaterial oim : loaderBase.operationInputMaterialsById().values()) {
            builder.operationInputMaterial(oim);
        }
        for (OperationOutputMaterial oom : loaderBase.operationOutputMaterialsById().values()) {
            builder.operationOutputMaterial(oom);
        }
        for (Fulfillment fulfillment : restoredP0.fulfillments()) {
            builder.fulfillment(fulfillment);
        }
        loaderBase.bomDependencies().forEach(builder::bomDependency);

        for (ProductInStockingPointPeriod pispp : restoredP0.pispPeriodsById().values()) {
            builder.pispPeriod(pispp);
        }
        for (StandardResourcePeriod srp : restoredP0.srpById().values()) {
            builder.standardResourcePeriod(srp);
        }
        for (ResourceCapacityAssignment rca : restoredP0.resourceCapacityAssignmentsById().values()) {
            builder.resourceCapacityAssignment(rca);
        }

        builder.periodsOrdered(loaderBase.periodsOrdered());
        return builder.build();
    }
}
