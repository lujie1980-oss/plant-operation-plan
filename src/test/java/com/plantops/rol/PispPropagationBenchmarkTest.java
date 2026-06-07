package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PispPropagationBenchmarkTest {

    private static final int PISP_COUNT = 100;
    private static final int PERIOD_COUNT = 28;
    private static final int WARMUP_ITERATIONS = 100;
    private static final int MEASURED_ITERATIONS = 1000;
    private static final long P95_THRESHOLD_NANOS = 10_000_000L;
    // Generous CI fallback when shared runners are noisy.
    private static final long CI_P95_THRESHOLD_NANOS = 50_000_000L;

    @Test
    void propagationP95Under10ms() {
        OntologyGraph graph = buildBenchmarkGraph();
        RolEngine engine = RolEngine.withDefaultPispRules(graph);
        ProductInStockingPointPeriod target = graph.pispPeriodsById().values().stream()
                .filter(p -> p.getPeriodId().equals("P-1"))
                .findFirst()
                .orElseThrow();

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            engine.applyPropertyChange(target, "plannedSupplyTotal", 10.0 + i);
        }

        long[] samples = new long[MEASURED_ITERATIONS];
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long start = System.nanoTime();
            engine.applyPropertyChange(target, "plannedSupplyTotal", 20.0 + i);
            samples[i] = System.nanoTime() - start;
        }

        long p95 = percentile95(samples);
        long threshold = isCiEnvironment() ? CI_P95_THRESHOLD_NANOS : P95_THRESHOLD_NANOS;
        assertTrue(
                p95 < threshold,
                () -> "p95 propagation " + (p95 / 1_000_000.0) + "ms exceeded threshold "
                        + (threshold / 1_000_000.0) + "ms");
    }

    private static OntologyGraph buildBenchmarkGraph() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        List<Period> periods = new ArrayList<>(PERIOD_COUNT);
        for (int i = 0; i < PERIOD_COUNT; i++) {
            LocalDate day = start.plusDays(i);
            periods.add(new Period(OntologyIds.periodId(i), i, day, day));
        }

        OntologyGraph.Builder builder = OntologyGraph.builder().periodsOrdered(periods);
        for (int p = 0; p < PISP_COUNT; p++) {
            String productCode = "P" + p;
            String pispId = OntologyIds.pispId(productCode);
            builder.pisp(new ProductInStockingPoint(pispId, productCode, OntologyIds.DEFAULT_FG, productCode));

            List<ProductInStockingPointPeriod> chain = new ArrayList<>(PERIOD_COUNT);
            for (Period period : periods) {
                var pispp = new ProductInStockingPointPeriod(
                        OntologyIds.pisppId(pispId, period.getSequenceNr()),
                        pispId,
                        period.getId());
                pispp.setOnHand(100);
                pispp.setPlannedSupplyTotal(5);
                pispp.setPlannedDemandQuantityTotal(3);
                pispp.recalculatePlanningFields();
                builder.pispPeriod(pispp);
                chain.add(pispp);
            }
            PispRolling.rollChain(chain);
        }
        return builder.build();
    }

    private static long percentile95(long[] samples) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(sorted.length * 0.95) - 1;
        return sorted[Math.max(0, index)];
    }

    private static boolean isCiEnvironment() {
        return System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
    }
}
