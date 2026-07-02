package com.plantops.ontology;

import com.plantops.ontology.master.Product;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PhysicalResourcePeriod;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.ForecastDemand;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.supply.BomDependency;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.OperationOutputMaterial;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OntologyGraph {

    private final Map<String, Product> productsById;
    private final StockingPoint defaultStockingPoint;
    private final Map<String, ProductInStockingPoint> pispsById;
    private final Map<String, CustomerOrderLine> customerOrderLinesById;
    private final Map<String, CustomerOrderLineDelivery> customerOrderLineDeliveriesById;
    private final Map<String, ForecastDemand> forecastDemandsById;
    private final Map<String, Demand> demandsById;
    private final Map<String, SupplyOrder> supplyOrdersById;
    private final Map<String, PlanUnit> planUnitsById;
    private final Map<String, Operation> operationsById;
    private final Map<String, OperationOnStandardResource> operationOnStandardResourceById;
    private final Map<String, Supply> suppliesById;
    private final Map<String, OperationInputMaterial> operationInputMaterialsById;
    private final Map<String, OperationOutputMaterial> operationOutputMaterialsById;
    private final List<Fulfillment> fulfillments;
    private final List<BomDependency> bomDependencies;
    private final Map<String, ProductInStockingPointPeriod> pispPeriodsById;
    private final Map<String, PhysicalResourcePeriod> prpById;
    private final Map<String, StandardResourcePeriod> srpById;
    private final Map<String, ResourceCapacityAssignment> resourceCapacityAssignmentsById;
    private final List<Period> periodsOrdered;

    private OntologyGraph(
            Map<String, Product> productsById,
            StockingPoint defaultStockingPoint,
            Map<String, ProductInStockingPoint> pispsById,
            Map<String, CustomerOrderLine> customerOrderLinesById,
            Map<String, CustomerOrderLineDelivery> customerOrderLineDeliveriesById,
            Map<String, ForecastDemand> forecastDemandsById,
            Map<String, Demand> demandsById,
            Map<String, SupplyOrder> supplyOrdersById,
            Map<String, PlanUnit> planUnitsById,
            Map<String, Operation> operationsById,
            Map<String, OperationOnStandardResource> operationOnStandardResourceById,
            Map<String, Supply> suppliesById,
            Map<String, OperationInputMaterial> operationInputMaterialsById,
            Map<String, OperationOutputMaterial> operationOutputMaterialsById,
            List<Fulfillment> fulfillments,
            List<BomDependency> bomDependencies,
            Map<String, ProductInStockingPointPeriod> pispPeriodsById,
            Map<String, PhysicalResourcePeriod> prpById,
            Map<String, StandardResourcePeriod> srpById,
            Map<String, ResourceCapacityAssignment> resourceCapacityAssignmentsById,
            List<Period> periodsOrdered) {
        this.productsById = Collections.unmodifiableMap(productsById);
        this.defaultStockingPoint = defaultStockingPoint;
        this.pispsById = Collections.unmodifiableMap(pispsById);
        this.customerOrderLinesById = Collections.unmodifiableMap(customerOrderLinesById);
        this.customerOrderLineDeliveriesById = Collections.unmodifiableMap(customerOrderLineDeliveriesById);
        this.forecastDemandsById = Collections.unmodifiableMap(forecastDemandsById);
        this.demandsById = Collections.unmodifiableMap(demandsById);
        this.supplyOrdersById = Collections.unmodifiableMap(supplyOrdersById);
        this.planUnitsById = Collections.unmodifiableMap(planUnitsById);
        this.operationsById = Collections.unmodifiableMap(operationsById);
        this.operationOnStandardResourceById = Collections.unmodifiableMap(operationOnStandardResourceById);
        this.suppliesById = Collections.unmodifiableMap(suppliesById);
        this.operationInputMaterialsById = Collections.unmodifiableMap(operationInputMaterialsById);
        this.operationOutputMaterialsById = Collections.unmodifiableMap(operationOutputMaterialsById);
        this.fulfillments = List.copyOf(fulfillments);
        this.bomDependencies = List.copyOf(bomDependencies);
        this.pispPeriodsById = Collections.unmodifiableMap(pispPeriodsById);
        this.prpById = Collections.unmodifiableMap(prpById);
        this.srpById = Collections.unmodifiableMap(srpById);
        this.resourceCapacityAssignmentsById = new LinkedHashMap<>(resourceCapacityAssignmentsById);
        this.periodsOrdered = List.copyOf(periodsOrdered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Product product(String id) {
        return productsById.get(id);
    }

    public StockingPoint defaultStockingPoint() {
        return defaultStockingPoint;
    }

    public ProductInStockingPoint pisp(String id) {
        return pispsById.get(id);
    }

    public Map<String, ProductInStockingPoint> pispsById() {
        return pispsById;
    }

    public CustomerOrderLine customerOrderLine(String id) {
        return customerOrderLinesById.get(id);
    }

    public Map<String, CustomerOrderLine> customerOrderLinesById() {
        return customerOrderLinesById;
    }

    public CustomerOrderLineDelivery customerOrderLineDelivery(String id) {
        return customerOrderLineDeliveriesById.get(id);
    }

    public Map<String, CustomerOrderLineDelivery> customerOrderLineDeliveriesById() {
        return customerOrderLineDeliveriesById;
    }

    public List<CustomerOrderLineDelivery> deliveriesForCustomerOrderLine(String customerOrderLineId) {
        return customerOrderLineDeliveriesById.values().stream()
                .filter(d -> customerOrderLineId.equals(d.getCustomerOrderLineId()))
                .toList();
    }

    public ForecastDemand forecastDemand(String id) {
        return forecastDemandsById.get(id);
    }

    public Map<String, ForecastDemand> forecastDemandsById() {
        return forecastDemandsById;
    }

    public Demand demand(String id) {
        return demandsById.get(id);
    }

    public Map<String, Demand> demandsById() {
        return demandsById;
    }

    public SupplyOrder supplyOrder(String id) {
        return supplyOrdersById.get(id);
    }

    public Map<String, SupplyOrder> supplyOrdersById() {
        return supplyOrdersById;
    }

    public Operation operation(String id) {
        return operationsById.get(id);
    }

    public Map<String, Operation> operationsById() {
        return operationsById;
    }

    public PlanUnit planUnit(String id) {
        return planUnitsById.get(id);
    }

    public Map<String, PlanUnit> planUnitsById() {
        return planUnitsById;
    }

    public List<PlanUnit> planUnitsForSupplyOrder(String supplyOrderId) {
        return planUnitsById.values().stream()
                .filter(pu -> supplyOrderId.equals(pu.getSupplyOrderId()))
                .sorted(Comparator.comparingInt(PlanUnit::getSequenceNr))
                .toList();
    }

    public List<Operation> operationsForSupplyOrder(String supplyOrderId) {
        return operationsById.values().stream()
                .filter(op -> supplyOrderId.equals(op.getSupplyOrderId()))
                .sorted(Comparator.comparingInt(Operation::getSequenceNr))
                .toList();
    }

    public List<Operation> operationsForPlanUnit(String planUnitId) {
        return operationsById.values().stream()
                .filter(op -> planUnitId.equals(op.getPlanUnitId()))
                .sorted(Comparator.comparingInt(Operation::getSequenceNr))
                .toList();
    }

    public OperationOnStandardResource operationOnStandardResource(String id) {
        return operationOnStandardResourceById.get(id);
    }

    public Map<String, OperationOnStandardResource> operationOnStandardResourceById() {
        return operationOnStandardResourceById;
    }

    public List<OperationOnStandardResource> operationsOnStandardResourceFor(String operationId) {
        return operationOnStandardResourceById.values().stream()
                .filter(oosr -> operationId.equals(oosr.getOperationId()))
                .sorted(OperationResourceBinding.byPriority())
                .toList();
    }

    public Supply supply(String id) {
        return suppliesById.get(id);
    }

    public Map<String, Supply> suppliesById() {
        return suppliesById;
    }

    public List<Supply> suppliesForSupplyOrder(String supplyOrderId) {
        return suppliesById.values().stream()
                .filter(s -> supplyOrderId.equals(s.getSupplyOrderId()))
                .toList();
    }

    public OperationInputMaterial operationInputMaterial(String id) {
        return operationInputMaterialsById.get(id);
    }

    public Map<String, OperationInputMaterial> operationInputMaterialsById() {
        return operationInputMaterialsById;
    }

    public List<OperationInputMaterial> operationInputMaterialsForOperation(String operationId) {
        return operationInputMaterialsById.values().stream()
                .filter(oim -> operationId.equals(oim.getOperationId()))
                .toList();
    }

    public OperationOutputMaterial operationOutputMaterial(String id) {
        return operationOutputMaterialsById.get(id);
    }

    public Map<String, OperationOutputMaterial> operationOutputMaterialsById() {
        return operationOutputMaterialsById;
    }

    public List<OperationOutputMaterial> operationOutputMaterialsForOperation(String operationId) {
        return operationOutputMaterialsById.values().stream()
                .filter(oom -> operationId.equals(oom.getOperationId()))
                .toList();
    }

    public List<Fulfillment> fulfillments() {
        return fulfillments;
    }

    public List<Fulfillment> fulfillmentsForDemand(String demandId) {
        return fulfillments.stream()
                .filter(ff -> demandId.equals(ff.getDemandId()))
                .toList();
    }

    public List<Fulfillment> fulfillmentsForSupply(String supplyId) {
        return fulfillments.stream()
                .filter(ff -> supplyId.equals(ff.getSupplyId()))
                .toList();
    }

    public List<Fulfillment> fulfillmentsForSupplyOrder(String supplyOrderId) {
        java.util.Set<String> supplyIds = suppliesForSupplyOrder(supplyOrderId).stream()
                .map(Supply::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (supplyIds.isEmpty()) {
            return List.of();
        }
        return fulfillments.stream()
                .filter(ff -> supplyIds.contains(ff.getSupplyId()))
                .toList();
    }

    public List<BomDependency> bomDependencies() {
        return bomDependencies;
    }

    public BomDependency bomDependency(String id) {
        return bomDependencies.stream()
                .filter(dep -> id.equals(dep.getId()))
                .findFirst()
                .orElse(null);
    }

    public List<BomDependency> bomDependenciesForParent(String parentSupplyOrderId) {
        return bomDependencies.stream()
                .filter(dep -> parentSupplyOrderId.equals(dep.getParentSupplyOrderId()))
                .toList();
    }

    public ProductInStockingPointPeriod pispPeriod(String id) {
        return pispPeriodsById.get(id);
    }

    public Map<String, ProductInStockingPointPeriod> pispPeriodsById() {
        return pispPeriodsById;
    }

    public PhysicalResourcePeriod prp(String id) {
        return prpById.get(id);
    }

    public Map<String, PhysicalResourcePeriod> prpById() {
        return prpById;
    }

    public StandardResourcePeriod srp(String id) {
        return srpById.get(id);
    }

    public Map<String, StandardResourcePeriod> srpById() {
        return srpById;
    }

    public ResourceCapacityAssignment resourceCapacityAssignment(String id) {
        return resourceCapacityAssignmentsById.get(id);
    }

    public Map<String, ResourceCapacityAssignment> resourceCapacityAssignmentsById() {
        return Collections.unmodifiableMap(resourceCapacityAssignmentsById);
    }

    public void replaceResourceCapacityAssignments(List<ResourceCapacityAssignment> assignments) {
        resourceCapacityAssignmentsById.clear();
        if (assignments == null) {
            return;
        }
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment != null && assignment.getId() != null) {
                resourceCapacityAssignmentsById.put(assignment.getId(), assignment);
            }
        }
    }

    public List<ResourceCapacityAssignment> resourceCapacityAssignmentsForOperation(String operationId) {
        return resourceCapacityAssignmentsById.values().stream()
                .filter(rca -> operationId.equals(rca.getOperationId()))
                .toList();
    }

    public List<ResourceCapacityAssignment> resourceCapacityAssignmentsForSrp(String standardResourcePeriodId) {
        return resourceCapacityAssignmentsById.values().stream()
                .filter(rca -> standardResourcePeriodId.equals(rca.getStandardResourcePeriodId()))
                .toList();
    }

    public List<Period> periodsOrdered() {
        return periodsOrdered;
    }

    public static final class Builder {

        private final Map<String, Product> productsById = new LinkedHashMap<>();
        private StockingPoint defaultStockingPoint = StockingPoint.defaultFg();
        private final Map<String, ProductInStockingPoint> pispsById = new LinkedHashMap<>();
        private final Map<String, CustomerOrderLine> customerOrderLinesById = new LinkedHashMap<>();
        private final Map<String, CustomerOrderLineDelivery> customerOrderLineDeliveriesById = new LinkedHashMap<>();
        private final Map<String, ForecastDemand> forecastDemandsById = new LinkedHashMap<>();
        private final Map<String, Demand> demandsById = new LinkedHashMap<>();
        private final Map<String, SupplyOrder> supplyOrdersById = new LinkedHashMap<>();
        private final Map<String, PlanUnit> planUnitsById = new LinkedHashMap<>();
        private final Map<String, Operation> operationsById = new LinkedHashMap<>();
        private final Map<String, OperationOnStandardResource> operationOnStandardResourceById = new LinkedHashMap<>();
        private final Map<String, Supply> suppliesById = new LinkedHashMap<>();
        private final Map<String, OperationInputMaterial> operationInputMaterialsById = new LinkedHashMap<>();
        private final Map<String, OperationOutputMaterial> operationOutputMaterialsById = new LinkedHashMap<>();
        private final List<Fulfillment> fulfillments = new java.util.ArrayList<>();
        private final List<BomDependency> bomDependencies = new java.util.ArrayList<>();
        private final Map<String, ProductInStockingPointPeriod> pispPeriodsById = new LinkedHashMap<>();
        private final Map<String, PhysicalResourcePeriod> prpById = new LinkedHashMap<>();
        private final Map<String, StandardResourcePeriod> srpById = new LinkedHashMap<>();
        private final Map<String, ResourceCapacityAssignment> resourceCapacityAssignmentsById = new LinkedHashMap<>();
        private List<Period> periodsOrdered = List.of();

        public Builder product(Product product) {
            productsById.put(product.getId(), product);
            return this;
        }

        public Builder defaultStockingPoint(StockingPoint stockingPoint) {
            this.defaultStockingPoint = stockingPoint;
            return this;
        }

        public Builder pisp(ProductInStockingPoint pisp) {
            pispsById.put(pisp.getId(), pisp);
            return this;
        }

        public Builder customerOrderLine(CustomerOrderLine line) {
            customerOrderLinesById.put(line.getId(), line);
            return this;
        }

        public Builder customerOrderLineDelivery(CustomerOrderLineDelivery delivery) {
            customerOrderLineDeliveriesById.put(delivery.getId(), delivery);
            return this;
        }

        public Builder forecastDemand(ForecastDemand forecastDemand) {
            forecastDemandsById.put(forecastDemand.getId(), forecastDemand);
            return this;
        }

        public Builder demand(Demand demand) {
            demandsById.put(demand.getId(), demand);
            return this;
        }

        public Map<String, Demand> demandsById() {
            return demandsById;
        }

        public Map<String, Operation> operationsById() {
            return operationsById;
        }

        public Builder supplyOrder(SupplyOrder supplyOrder) {
            supplyOrdersById.put(supplyOrder.getId(), supplyOrder);
            return this;
        }

        public Builder operation(Operation operation) {
            operationsById.put(operation.getId(), operation);
            return this;
        }

        public Builder operationOnStandardResource(OperationOnStandardResource oosr) {
            operationOnStandardResourceById.put(oosr.getId(), oosr);
            return this;
        }

        public Builder planUnit(PlanUnit planUnit) {
            planUnitsById.put(planUnit.getId(), planUnit);
            return this;
        }

        public Builder supply(Supply supply) {
            suppliesById.put(supply.getId(), supply);
            return this;
        }

        public Map<String, Supply> suppliesById() {
            return suppliesById;
        }

        public Map<String, CustomerOrderLineDelivery> customerOrderLineDeliveriesById() {
            return customerOrderLineDeliveriesById;
        }

        public Map<String, CustomerOrderLine> customerOrderLinesById() {
            return customerOrderLinesById;
        }

        public Builder operationInputMaterial(OperationInputMaterial oim) {
            operationInputMaterialsById.put(oim.getId(), oim);
            return this;
        }

        public Builder operationOutputMaterial(OperationOutputMaterial oom) {
            operationOutputMaterialsById.put(oom.getId(), oom);
            return this;
        }

        public Builder fulfillment(Fulfillment fulfillment) {
            fulfillments.add(fulfillment);
            return this;
        }

        public List<Fulfillment> fulfillments() {
            return fulfillments;
        }

        public Map<String, OperationInputMaterial> operationInputMaterialsById() {
            return operationInputMaterialsById;
        }

        public Map<String, SupplyOrder> supplyOrdersById() {
            return supplyOrdersById;
        }

        public Builder bomDependency(BomDependency bomDependency) {
            bomDependencies.add(bomDependency);
            return this;
        }

        public List<BomDependency> bomDependencies() {
            return bomDependencies;
        }

        public Builder pispPeriod(ProductInStockingPointPeriod pispPeriod) {
            pispPeriodsById.put(pispPeriod.getId(), pispPeriod);
            return this;
        }

        public Builder physicalResourcePeriod(PhysicalResourcePeriod prp) {
            prpById.put(prp.getId(), prp);
            return this;
        }

        public Map<String, PhysicalResourcePeriod> prpByIdSnapshot() {
            return Map.copyOf(prpById);
        }

        public Builder standardResourcePeriod(StandardResourcePeriod srp) {
            srpById.put(srp.getId(), srp);
            return this;
        }

        public Map<String, StandardResourcePeriod> srpByIdSnapshot() {
            return Map.copyOf(srpById);
        }

        public Builder resourceCapacityAssignment(ResourceCapacityAssignment rca) {
            resourceCapacityAssignmentsById.put(rca.getId(), rca);
            return this;
        }

        public Builder periodsOrdered(List<Period> periods) {
            this.periodsOrdered = periods;
            return this;
        }

        public OntologyGraph build() {
            return new OntologyGraph(
                    productsById,
                    defaultStockingPoint,
                    pispsById,
                    customerOrderLinesById,
                    customerOrderLineDeliveriesById,
                    forecastDemandsById,
                    demandsById,
                    supplyOrdersById,
                    planUnitsById,
                    operationsById,
                    operationOnStandardResourceById,
                    suppliesById,
                    operationInputMaterialsById,
                    operationOutputMaterialsById,
                    fulfillments,
                    bomDependencies,
                    pispPeriodsById,
                    prpById,
                    srpById,
                    resourceCapacityAssignmentsById,
                    periodsOrdered);
        }
    }
}
