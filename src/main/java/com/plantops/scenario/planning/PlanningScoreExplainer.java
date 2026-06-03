package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.analysis.ConstraintAnalysis;
import ai.timefold.solver.core.api.score.analysis.MatchAnalysis;
import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.stream.ConstraintJustification;
import ai.timefold.solver.core.api.score.stream.DefaultConstraintJustification;
import com.plantops.api.dto.planning.PlanningConstraintMatchDto;
import com.plantops.api.dto.planning.PlanningConstraintMatchTotalDto;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将 Timefold 2.0 {@link ScoreAnalysis} 转为 REST DTO（含匹配样本截断）。
 */
@ApplicationScoped
public class PlanningScoreExplainer {

    static final int MAX_MATCHES_PER_CONSTRAINT = 15;
    static final int MAX_TOTAL_MATCHES = 150;

    public PlanningScoreExplanationDto explainMasterPlan(String planVersionId, ScoreAnalysis<HardSoftScore> analysis) {
        return toDto(planVersionId, "MASTER_PLAN", null, analysis);
    }

    public PlanningScoreExplanationDto explainDetailSchedule(
            String planVersionId,
            String masterPlanVersionId,
            ScoreAnalysis<HardSoftScore> analysis) {
        return toDto(planVersionId, "DETAIL_SCHEDULE", masterPlanVersionId, analysis);
    }

    private static PlanningScoreExplanationDto toDto(
            String planVersionId,
            String planType,
            String masterPlanVersionId,
            ScoreAnalysis<HardSoftScore> analysis) {
        HardSoftScore score = analysis.score();
        boolean truncated = false;
        int matchBudget = MAX_TOTAL_MATCHES;
        List<PlanningConstraintMatchTotalDto> totals = new ArrayList<>();

        List<ConstraintAnalysis<HardSoftScore>> sorted = new ArrayList<>(analysis.constraintAnalyses());
        sorted.sort(Comparator
                .comparing((ConstraintAnalysis<HardSoftScore> t) -> t.score().hardScore())
                .thenComparing(t -> t.score().softScore()));

        for (ConstraintAnalysis<HardSoftScore> total : sorted) {
            HardSoftScore totalScore = total.score();
            if (totalScore.hardScore() == 0 && totalScore.softScore() == 0) {
                continue;
            }
            int matchCount = total.matchCount();
            List<PlanningConstraintMatchDto> samples = new ArrayList<>();
            boolean sampleTruncated = false;
            int perConstraintLimit = Math.min(MAX_MATCHES_PER_CONSTRAINT, matchBudget);
            int added = 0;
            for (MatchAnalysis<HardSoftScore> match : total.matches()) {
                if (added >= perConstraintLimit) {
                    sampleTruncated = true;
                    truncated = true;
                    break;
                }
                HardSoftScore matchScore = match.score();
                if (matchScore.hardScore() == 0 && matchScore.softScore() == 0) {
                    continue;
                }
                samples.add(new PlanningConstraintMatchDto(
                        match.justification().toString(),
                        (int) matchScore.hardScore(),
                        (int) matchScore.softScore(),
                        stringifyJustification(match.justification())));
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
                    total.constraintId(),
                    "",
                    total.constraintId(),
                    (int) totalScore.hardScore(),
                    (int) totalScore.softScore(),
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
                (int) score.hardScore(),
                (int) score.softScore(),
                analysis.summarize(),
                totals,
                truncated);
    }

    static List<String> stringifyJustification(ConstraintJustification justification) {
        if (justification instanceof DefaultConstraintJustification defaultJustification) {
            return stringifyIndicted(defaultJustification.getFacts());
        }
        return List.of(justification != null ? justification.toString() : "null");
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
