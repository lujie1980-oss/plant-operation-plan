package com.plantops.ontology.period;

import com.plantops.ontology.OntologyIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodSequenceSpec {

    /** 段：count 个长度为 lengthDays 的桶。 */
    public record Segment(int count, int lengthDays) {}

    private static final Pattern SEGMENT = Pattern.compile("(\\d+)x(\\d+)([dwm])");

    private final List<Segment> segments;

    private PeriodSequenceSpec(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public static PeriodSequenceSpec defaultSpec() {
        return new PeriodSequenceSpec(List.of(new Segment(OntologyIds.DEFAULT_PERIOD_COUNT, 1)));
    }

    /** "14x1d,4x1w,2x1m" → segments；d=1天 w=7天 m=30天。 */
    public static PeriodSequenceSpec parse(String text) {
        List<Segment> segments = new ArrayList<>();
        for (String token : text.split(",")) {
            Matcher m = SEGMENT.matcher(token.trim().toLowerCase());
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid period segment: " + token);
            }
            int unitDays = switch (m.group(3)) {
                case "d" -> 1;
                case "w" -> 7;
                case "m" -> 30;
                default -> throw new IllegalArgumentException(token);
            };
            segments.add(new Segment(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) * unitDays));
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
        List<Period> periods = new ArrayList<>();
        LocalDate cursor = planningStart;
        int seq = 0;
        for (Segment segment : segments) {
            for (int i = 0; i < segment.count(); i++) {
                LocalDate end = cursor.plusDays(segment.lengthDays() - 1L);
                periods.add(new Period(OntologyIds.periodId(seq), seq, cursor, end));
                cursor = end.plusDays(1);
                seq++;
            }
        }
        return periods;
    }
}
