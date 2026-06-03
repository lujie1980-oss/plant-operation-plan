package com.plantops.scenario.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 固定批量拆批数量计算（纯函数）。 */
public final class BatchFixedQuantitySplitter {

    private BatchFixedQuantitySplitter() {
    }

    public static List<BigDecimal> split(
            BigDecimal totalQty,
            BigDecimal batchSize,
            BatchRemainderMode remainderMode) {
        if (totalQty == null || totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        if (batchSize == null || batchSize.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of(totalQty);
        }
        BigDecimal total = totalQty.setScale(4, RoundingMode.HALF_UP);
        BigDecimal size = batchSize.setScale(4, RoundingMode.HALF_UP);
        BigDecimal[] div = total.divideAndRemainder(size);
        int fullCount = div[0].intValue();
        BigDecimal remainder = div[1];

        return switch (remainderMode) {
            case FLOOR -> repeat(size, fullCount);
            case CEIL -> {
                if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                    yield concat(repeat(size, fullCount), List.of(remainder));
                }
                yield repeat(size, fullCount);
            }
            case SEPARATE_TAIL -> {
                if (fullCount == 0 && remainder.compareTo(BigDecimal.ZERO) > 0) {
                    yield List.of(remainder);
                }
                if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                    yield concat(repeat(size, fullCount), List.of(remainder));
                }
                yield repeat(size, fullCount);
            }
            case MERGE_TAIL -> {
                if (fullCount == 0) {
                    yield List.of(total);
                }
                if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                    yield concat(repeat(size, fullCount - 1), List.of(size.add(remainder)));
                }
                yield repeat(size, fullCount);
            }
        };
    }

    private static List<BigDecimal> repeat(BigDecimal size, int count) {
        List<BigDecimal> out = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            out.add(size);
        }
        return out;
    }

    @SafeVarargs
    private static List<BigDecimal> concat(List<BigDecimal>... parts) {
        List<BigDecimal> out = new ArrayList<>();
        for (List<BigDecimal> part : parts) {
            out.addAll(part);
        }
        return out;
    }
}
