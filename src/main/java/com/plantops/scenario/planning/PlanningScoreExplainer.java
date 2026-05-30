package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import com.plantops.api.dto.planning.PlanningConstraintMatchDto;
import com.plantops.api.dto.planning.PlanningConstraintMatchTotalDto;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将 Timefold {@link ScoreExplanation} 转为 REST DTO（含匹配样本截断）。
 */
@ApplicationScoped
public class PlanningScoreExplainer {

    static final int MAX_MATCHES_PER_CONSTRAINT = 15;
    static final int MAX_TOTAL_MATCHES = 150;

    public PlanningScoreExplanationDto explainMasterPlan(
            String planVersionId,
            ScoreExplanation<MasterPlanSchedule, HardSoftScore> explanation) {
        return toDto(planVersionId, "MASTER_PLAN", null, explanation);
    }

    public PlanningScoreExplanationDto explainDetailSchedule(
            String planVersionId,
            String masterPlanVersionId,
            ScoreExplanation<DetailSchedule, HardSoftScore> explanation) {
        return toDto(planVersionId, "DETAIL_SCHEDULE", masterPlanVersionId, explanation);
    }

    private static <Solution_> PlanningScoreExplanationDto toDto(
            String planVersionId,
            String planType,
            String masterPlanVersionId,
            ScoreExplanation<Solution_, HardSoftScore> explanation) {
        HardSoftScore score = explanation.getScore();
        boolean truncated = false;
        int matchBudget = MAX_TOTAL_MATCHES;
        List<PlanningConstraintMatchTotalDto> totals = new ArrayList<>();

        Map<String, ConstraintMatchTotal<HardSoftScore>> map = explanation.getConstraintMatchTotalMap();
        List<ConstraintMatchTotal<HardSoftScore>> sorted = new ArrayList<>(map.values());
        sorted.sort(Comparator
                .comparing((ConstraintMatchTotal<HardSoftScore> t) -> t.getScore().hardScore())
                .thenComparing(t -> t.getScore().softScore()));

        for (ConstraintMatchTotal<HardSoftScore> total : sorted) {
            HardSoftScore totalScore = total.getScore();
            if (totalScore.hardScore() == 0 && totalScore.softScore() == 0) {
                continue;
            }
            int matchCount = total.getConstraintMatchCount();
            List<PlanningConstraintMatchDto> samples = new ArrayList<>();
            boolean sampleTruncated = false;
            int perConstraintLimit = Math.min(MAX_MATCHES_PER_CONSTRAINT, matchBudget);
            int added = 0;
            for (ConstraintMatch<HardSoftScore> match : total.getConstraintMatchSet()) {
                if (added >= perConstraintLimit) {
                    sampleTruncated = true;
                    truncated = true;
                    break;
                }
                HardSoftScore matchScore = match.getScore();
                if (matchScore.hardScore() == 0 && matchScore.softScore() == 0) {
                    continue;
                }
                samples.add(new PlanningConstraintMatchDto(
                        match.getIdentificationString(),
                        matchScore.hardScore(),
                        matchScore.softScore(),
                        stringifyIndicted(match.getIndictedObjectList())));
                added++;
                matchBudget--;
                if (matchBudget <= 0) {
                    sampleTruncated = matchCount > samples.size();
                    truncated = true;
                    break;
                }
            }
            if (matchCount > samples.size()) {
                sampleTruncated = true;
            }
            totals.add(new PlanningConstraintMatchTotalDto(
                    total.getConstraintId(),
                    total.getConstraintPackage(),
                    total.getConstraintName(),
                    totalScore.hardScore(),
                    totalScore.softScore(),
                    matchCount,
                    samples,
                    sampleTruncated));
            if (matchBudget <= 0) {
                break;
            }
        }

        return new PlanningScoreExplanationDto(
                LocalDateTime.now(),
                planVersionId,
                planType,
                masterPlanVersionId,
                score.toString(),
                score.hardScore(),
                score.softScore(),
                explanation.getSummary(),
                totals,
                truncated);
    }

    static List<String> stringifyIndicted(List<Object> indicted) {
        if (indicted == null || indicted.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(indicted.size());
        for (Object obj : indicted) {
            out.add(describeIndicted(obj));
        }
        return out;
    }

    static String describeIndicted(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof OrderAllocation allocation) {
            return allocation.getId();
        }
        if (obj instanceof OperationAssignment assignment) {
            return assignment.getOperationId();
        }
        if (obj instanceof TimeSlot slot) {
            return slot.getId();
        }
        return obj.toString();
    }
}
