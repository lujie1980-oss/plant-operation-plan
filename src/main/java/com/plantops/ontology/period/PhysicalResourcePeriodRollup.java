package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;

import java.util.List;
import java.util.Map;

/** 子 shift-PRP → 父日-PRP 产能汇总（ADR-17 · ADR-16）。 */
public final class PhysicalResourcePeriodRollup {

    private PhysicalResourcePeriodRollup() {}

    public static void rollupParentCapacities(
            Map<String, PhysicalResourcePeriod> prpByKey, List<Period> periods, PeriodIndex periodIndex) {
        for (Period parent : periods) {
            if (parent.isLeaf()) {
                continue;
            }
            List<Period> children = periodIndex.childrenOf(parent.getId());
            if (children.isEmpty()) {
                continue;
            }
            String physicalResourceId = physicalResourceIdForPeriod(children.get(0), prpByKey);
            if (physicalResourceId == null) {
                continue;
            }
            PhysicalResourcePeriod parentPrp =
                    prpByKey.get(OntologyIds.prpId(physicalResourceId, parent.getId()));
            if (parentPrp == null) {
                continue;
            }
            double total = 0;
            double calendarDowntime = 0;
            double schedulerFeedback = 0;
            for (Period child : children) {
                PhysicalResourcePeriod childPrp =
                        prpByKey.get(OntologyIds.prpId(physicalResourceId, child.getId()));
                if (childPrp == null) {
                    continue;
                }
                total += childPrp.getTotalCapacity();
                calendarDowntime += childPrp.getCalendarDowntime();
                schedulerFeedback += childPrp.getSchedulerFeedbackMinutes();
            }
            parentPrp.setTotalCapacity(total);
            parentPrp.setCalendarDowntime(calendarDowntime);
            parentPrp.setSchedulerFeedbackMinutes(schedulerFeedback);
        }
    }

    private static String physicalResourceIdForPeriod(
            Period period, Map<String, PhysicalResourcePeriod> prpByKey) {
        for (PhysicalResourcePeriod prp : prpByKey.values()) {
            if (period.getId().equals(prp.getPeriodId())) {
                return prp.getPhysicalResourceId();
            }
        }
        return null;
    }
}
