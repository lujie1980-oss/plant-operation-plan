package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** PRP → SRP 产能聚合（ADR-17 · RULE-SUP-05 Layer 2）。 */
public final class StandardResourcePeriodAggregator {

    private StandardResourcePeriodAggregator() {}

    public static void aggregate(
            OntologyGraph.Builder builder,
            Map<String, PhysicalResourcePeriod> prpByKey,
            Set<String> standardResourceIds,
            List<Period> periods) {
        for (String standardResourceId : standardResourceIds) {
            for (Period period : periods) {
                StandardResourcePeriod srp = new StandardResourcePeriod(
                        OntologyIds.srpId(standardResourceId, period.getSequenceNr()),
                        standardResourceId,
                        period.getId());
                double totalAvailable = 0;
                for (PhysicalResourcePeriod prp : prpByKey.values()) {
                    if (standardResourceId.equals(prp.getStandardResourceId())
                            && period.getId().equals(prp.getPeriodId())) {
                        totalAvailable += prp.getAvailableCapacity();
                    }
                }
                srp.setTotalCapacity(totalAvailable);
                srp.setCalendarDowntime(0);
                srp.setTechnicalDowntime(0);
                srp.recalculateCapacityFields();
                builder.standardResourcePeriod(srp);
            }
        }
    }
}
