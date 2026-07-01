package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;

import java.util.List;
import java.util.Map;

/** 子 shift-SRP → 父日-SRP 产能与占用汇总（ADR-16 · TODO-23 S2）。 */
public final class StandardResourcePeriodRollup {

    private StandardResourcePeriodRollup() {}

    public static void rollupParentCapacities(
            Map<String, StandardResourcePeriod> srpByKey, List<Period> periods, PeriodIndex periodIndex) {
        for (Period parent : periods) {
            if (parent.isLeaf()) {
                continue;
            }
            List<Period> children = periodIndex.childrenOf(parent.getId());
            if (children.isEmpty()) {
                continue;
            }
            String resourceId = resourceIdForPeriod(children.get(0), srpByKey);
            if (resourceId == null) {
                continue;
            }
            StandardResourcePeriod parentSrp =
                    srpByKey.get(OntologyIds.srpId(resourceId, parent.getSequenceNr()));
            if (parentSrp == null) {
                continue;
            }
            double total = 0;
            double calendarDowntime = 0;
            double reserved = 0;
            for (Period child : children) {
                StandardResourcePeriod childSrp =
                        srpByKey.get(OntologyIds.srpId(resourceId, child.getSequenceNr()));
                if (childSrp == null) {
                    continue;
                }
                total += childSrp.getTotalCapacity();
                calendarDowntime += childSrp.getCalendarDowntime();
                reserved += childSrp.getReservedCapacity();
            }
            parentSrp.setTotalCapacity(total);
            parentSrp.setCalendarDowntime(calendarDowntime);
            parentSrp.setReservedCapacity(reserved);
        }
    }

    public static void rollupParentReserved(OntologyGraph graph) {
        if (graph == null || graph.periodsOrdered().isEmpty()) {
            return;
        }
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        for (Period parent : graph.periodsOrdered()) {
            if (parent.isLeaf()) {
                continue;
            }
            List<Period> children = periodIndex.childrenOf(parent.getId());
            if (children.isEmpty()) {
                continue;
            }
            String resourceId = resourceIdForPeriod(children.get(0), graph);
            if (resourceId == null) {
                continue;
            }
            StandardResourcePeriod parentSrp = graph.srp(OntologyIds.srpId(resourceId, parent.getSequenceNr()));
            if (parentSrp == null) {
                continue;
            }
            double reserved = 0;
            for (Period child : children) {
                StandardResourcePeriod childSrp =
                        graph.srp(OntologyIds.srpId(resourceId, child.getSequenceNr()));
                if (childSrp != null) {
                    reserved += childSrp.getReservedCapacity();
                }
            }
            parentSrp.setReservedCapacity(reserved);
            parentSrp.recalculateCapacityFields();
        }
    }

    private static String resourceIdForPeriod(Period period, Map<String, StandardResourcePeriod> srpByKey) {
        for (StandardResourcePeriod srp : srpByKey.values()) {
            if (period.getId().equals(srp.getPeriodId())) {
                return srp.getStandardResourceId();
            }
        }
        return null;
    }

    private static String resourceIdForPeriod(Period period, OntologyGraph graph) {
        for (StandardResourcePeriod srp : graph.srpById().values()) {
            if (period.getId().equals(srp.getPeriodId())) {
                return srp.getStandardResourceId();
            }
        }
        return null;
    }
}
