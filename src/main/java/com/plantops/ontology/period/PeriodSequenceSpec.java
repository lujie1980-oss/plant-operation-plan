package com.plantops.ontology.period;

import com.plantops.ontology.OntologyIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodSequenceSpec {

    public sealed interface Segment permits DaySegment, ShiftSegment {}

    /** 连续 count 个、每个 lengthDays 天的日/周/月桶。 */
    public record DaySegment(int count, int lengthDays) implements Segment {}

    /** 连续 dayCount 个日历日，每日 shiftsPerDay 个班次 leaf Period。 */
    public record ShiftSegment(int dayCount, int shiftsPerDay) implements Segment {}

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)x(\\d+)(shift|[dwm])");

    private final List<Segment> segments;

    private PeriodSequenceSpec(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public List<Segment> segments() {
        return segments;
    }

    public static PeriodSequenceSpec defaultSpec() {
        return new PeriodSequenceSpec(List.of(new DaySegment(OntologyIds.DEFAULT_PERIOD_COUNT, 1)));
    }

    /**
     * 解析序列语法，例如 {@code 14x3shift,4x1d,2x1w}（ADR-16 · §5.8.1）。
     */
    public static PeriodSequenceSpec parse(String text) {
        List<Segment> segments = new ArrayList<>();
        for (String token : text.split(",")) {
            Matcher m = SEGMENT.matcher(token.trim().toLowerCase());
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid period segment: " + token);
            }
            int count = Integer.parseInt(m.group(1));
            int multiplier = Integer.parseInt(m.group(2));
            if (count < 1 || multiplier < 1) {
                throw new IllegalArgumentException("Invalid period segment: " + token);
            }
            String unit = m.group(3);
            if ("shift".equals(unit)) {
                segments.add(new ShiftSegment(count, multiplier));
            } else {
                int unitDays = switch (unit) {
                    case "d" -> 1;
                    case "w" -> 7;
                    case "m" -> 30;
                    default -> throw new IllegalArgumentException(token);
                };
                segments.add(new DaySegment(count, multiplier * unitDays));
            }
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Empty spec");
        }
        return new PeriodSequenceSpec(segments);
    }

    public static PeriodSequenceSpec parseOrDefault(String text) {
        if (text == null || text.isBlank()) {
            return defaultSpec();
        }
        try {
            return parse(text);
        } catch (IllegalArgumentException ex) {
            return defaultSpec();
        }
    }

    public List<Period> expand(LocalDate planningStart) {
        return PeriodExpander.expand(this, planningStart);
    }
}
