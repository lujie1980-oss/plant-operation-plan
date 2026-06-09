package com.plantops.ontology;

import com.plantops.ontology.master.Product;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.SupplyOrder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OntologyGraph {

    private final Map<String, Product> productsById;
    private final StockingPoint defaultStockingPoint;
    private final Map<String, ProductInStockingPoint> pispsById;
    private final Map<String, SupplyOrder> supplyOrdersById;
    private final Map<String, ProductInStockingPointPeriod> pispPeriodsById;
    private final Map<String, StandardResourcePeriod> srpById;
    private final List<Period> periodsOrdered;

    private OntologyGraph(
            Map<String, Product> productsById,
            StockingPoint defaultStockingPoint,
            Map<String, ProductInStockingPoint> pispsById,
            Map<String, SupplyOrder> supplyOrdersById,
            Map<String, ProductInStockingPointPeriod> pispPeriodsById,
            Map<String, StandardResourcePeriod> srpById,
            List<Period> periodsOrdered) {
        this.productsById = Collections.unmodifiableMap(productsById);
        this.defaultStockingPoint = defaultStockingPoint;
        this.pispsById = Collections.unmodifiableMap(pispsById);
        this.supplyOrdersById = Collections.unmodifiableMap(supplyOrdersById);
        this.pispPeriodsById = Collections.unmodifiableMap(pispPeriodsById);
        this.srpById = Collections.unmodifiableMap(srpById);
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

    public SupplyOrder supplyOrder(String id) {
        return supplyOrdersById.get(id);
    }

    public Map<String, SupplyOrder> supplyOrdersById() {
        return supplyOrdersById;
    }

    public ProductInStockingPointPeriod pispPeriod(String id) {
        return pispPeriodsById.get(id);
    }

    public Map<String, ProductInStockingPointPeriod> pispPeriodsById() {
        return pispPeriodsById;
    }

    public StandardResourcePeriod srp(String id) {
        return srpById.get(id);
    }

    public Map<String, StandardResourcePeriod> srpById() {
        return srpById;
    }

    public List<Period> periodsOrdered() {
        return periodsOrdered;
    }

    public static final class Builder {

        private final Map<String, Product> productsById = new LinkedHashMap<>();
        private StockingPoint defaultStockingPoint = StockingPoint.defaultFg();
        private final Map<String, ProductInStockingPoint> pispsById = new LinkedHashMap<>();
        private final Map<String, SupplyOrder> supplyOrdersById = new LinkedHashMap<>();
        private final Map<String, ProductInStockingPointPeriod> pispPeriodsById = new LinkedHashMap<>();
        private final Map<String, StandardResourcePeriod> srpById = new LinkedHashMap<>();
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

        public Builder supplyOrder(SupplyOrder supplyOrder) {
            supplyOrdersById.put(supplyOrder.getId(), supplyOrder);
            return this;
        }

        public Builder pispPeriod(ProductInStockingPointPeriod pispPeriod) {
            pispPeriodsById.put(pispPeriod.getId(), pispPeriod);
            return this;
        }

        public Builder standardResourcePeriod(StandardResourcePeriod srp) {
            srpById.put(srp.getId(), srp);
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
                    supplyOrdersById,
                    pispPeriodsById,
                    srpById,
                    periodsOrdered);
        }
    }
}
