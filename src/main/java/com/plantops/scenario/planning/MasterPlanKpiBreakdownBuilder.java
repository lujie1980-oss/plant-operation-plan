package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiBreakdownDto;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiDomainScoreDto;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiItemDto;
import com.plantops.api.dto.planning.PlanningConstraintMatchTotalDto;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds §15 {@code kpiBreakdown} from score-explanation or raw score string. */
public final class MasterPlanKpiBreakdownBuilder {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);

    private MasterPlanKpiBreakdownBuilder() {}

    public static KpiBreakdownDto fromExplanation(PlanningScoreExplanationDto explanation) {
        if (explanation == null) {
            return empty();
        }
        return fromConstraintTotals(explanation.constraintTotals());
    }

    public static KpiBreakdownDto fromConstraintTotals(List<PlanningConstraintMatchTotalDto> totals) {
        Map<String, KpiDomainScoreDto> domains = new LinkedHashMap<>();
        domains.put("delivery", domain("delivery"));
        domains.put("material", domain("material"));
        domains.put("capacity", domain("capacity"));
        domains.put("supply", domain("supply"));
        domains.put("preference", domain("preference"));

        List<KpiItemDto> scoring = new ArrayList<>();
        List<KpiItemDto> constraint = new ArrayList<>();
        Map<String, KpiItemDto> scoringMerged = new LinkedHashMap<>();
        Map<String, KpiItemDto> constraintMerged = new LinkedHashMap<>();

        if (totals != null) {
            for (PlanningConstraintMatchTotalDto total : totals) {
                if (total == null) {
                    continue;
                }
                var entry = MasterPlanKpiConstraintCatalog.resolve(total.constraintName());
                if (entry.isEmpty()) {
                    continue;
                }
                MasterPlanKpiConstraintCatalog.Entry catalog = entry.get();
                KpiDomainScoreDto current = domains.get(catalog.domain());
                domains.put(catalog.domain(), new KpiDomainScoreDto(
                        catalog.domain(),
                        current.hard() + total.hardScore(),
                        current.soft() + total.softScore()));

                KpiItemDto item = new KpiItemDto(
                        catalog.kpiId(),
                        catalog.displayName(),
                        total.constraintName(),
                        total.hardScore(),
                        total.softScore());
                if (catalog.layer() == MasterPlanKpiConstraintCatalog.Layer.SCORING) {
                    mergeItem(scoringMerged, item);
                } else {
                    mergeItem(constraintMerged, item);
                }
            }
        }
        scoring.addAll(scoringMerged.values());
        constraint.addAll(constraintMerged.values());

        return new KpiBreakdownDto(
                domains.get("delivery"),
                domains.get("material"),
                domains.get("capacity"),
                domains.get("supply"),
                domains.get("preference"),
                List.copyOf(scoring),
                List.copyOf(constraint));
    }

    public static KpiBreakdownDto fromScoreString(String score) {
        ParsedScore parsed = parseScore(score);
        if (parsed == null) {
            return empty();
        }
        KpiDomainScoreDto aggregate = new KpiDomainScoreDto("total", parsed.hard(), parsed.soft());
        return new KpiBreakdownDto(
                aggregate,
                domain("material"),
                domain("capacity"),
                domain("supply"),
                domain("preference"),
                List.of(),
                List.of());
    }

    public static Integer totalKpiFromScore(String score) {
        ParsedScore parsed = parseScore(score);
        return parsed == null ? null : parsed.hard() + parsed.soft();
    }

    public static String scoreSummary(String score, PlanningScoreExplanationDto explanation) {
        if (explanation != null && explanation.summary() != null && !explanation.summary().isBlank()) {
            return explanation.summary();
        }
        if (score == null || score.isBlank()) {
            return "N/A";
        }
        ParsedScore parsed = parseScore(score);
        if (parsed == null) {
            return score;
        }
        return String.format(
                Locale.ROOT,
                "hard=%d soft=%d (%s)",
                parsed.hard(),
                parsed.soft(),
                score.trim());
    }

    private static void mergeItem(Map<String, KpiItemDto> merged, KpiItemDto item) {
        KpiItemDto existing = merged.get(item.kpiId());
        if (existing == null) {
            merged.put(item.kpiId(), item);
            return;
        }
        merged.put(item.kpiId(), new KpiItemDto(
                item.kpiId(),
                item.name(),
                item.constraintId(),
                existing.hard() + item.hard(),
                existing.soft() + item.soft()));
    }

    private static KpiDomainScoreDto domain(String name) {
        return new KpiDomainScoreDto(name, 0, 0);
    }

    private static KpiBreakdownDto empty() {
        return fromConstraintTotals(List.of());
    }

    private static ParsedScore parseScore(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        Matcher matcher = HARD_SOFT_SCORE_PATTERN.matcher(score.trim());
        if (!matcher.find()) {
            return null;
        }
        return new ParsedScore(
                Integer.parseInt(matcher.group("hard")),
                Integer.parseInt(matcher.group("soft")));
    }

    private record ParsedScore(int hard, int soft) {}
}
