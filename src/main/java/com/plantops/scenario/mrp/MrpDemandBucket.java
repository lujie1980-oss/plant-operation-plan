package com.plantops.scenario.mrp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MrpDemandBucket {

    public record Key(String productCode, LocalDate needDate) {
    }

    public record PegLine(
            String salesOrderNo,
            int salesOrderLineNo,
            String finishedProductCode,
            BigDecimal qty) {
    }

    public final int bomLevel;
    public BigDecimal grossQty = BigDecimal.ZERO;
    public final List<PegLine> pegLines = new ArrayList<>();

    public MrpDemandBucket(int bomLevel) {
        this.bomLevel = bomLevel;
    }

    public void addGross(BigDecimal qty, PegLine peg) {
        if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
            grossQty = grossQty.add(qty);
        }
        if (peg != null) {
            mergePeg(peg);
        }
    }

    public void addPeg(PegLine peg) {
        if (peg != null) {
            mergePeg(peg);
        }
    }

    private void mergePeg(PegLine peg) {
        for (PegLine existing : pegLines) {
            if (existing.salesOrderNo().equals(peg.salesOrderNo())
                    && existing.salesOrderLineNo() == peg.salesOrderLineNo()
                    && Objects.equals(existing.finishedProductCode(), peg.finishedProductCode())) {
                pegLines.remove(existing);
                pegLines.add(new PegLine(
                        peg.salesOrderNo(),
                        peg.salesOrderLineNo(),
                        peg.finishedProductCode(),
                        existing.qty().add(peg.qty())));
                return;
            }
        }
        pegLines.add(peg);
    }

    static Map<Key, MrpDemandBucket> mergeMaps(Map<Key, MrpDemandBucket> target, Map<Key, MrpDemandBucket> source) {
        for (var entry : source.entrySet()) {
            MrpDemandBucket bucket = target.computeIfAbsent(entry.getKey(), k -> new MrpDemandBucket(entry.getValue().bomLevel));
            MrpDemandBucket src = entry.getValue();
            bucket.grossQty = bucket.grossQty.add(src.grossQty);
            for (PegLine peg : src.pegLines) {
                bucket.mergePeg(peg);
            }
        }
        return target;
    }

    public static void addTo(Map<Key, MrpDemandBucket> map, Key key, int level, BigDecimal qty, PegLine peg) {
        MrpDemandBucket bucket = map.computeIfAbsent(key, k -> new MrpDemandBucket(level));
        bucket.addGross(qty, peg);
    }
}
