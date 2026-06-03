package com.plantops.config;

import com.plantops.scenario.batch.BatchRemainderMode;
import com.plantops.scenario.batch.BatchSplitMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BatchSplitConfigService {

    public static final String PARAM_MODE = "batch_split_mode";
    public static final String PARAM_FIXED_QTY = "batch_fixed_qty";
    public static final String PARAM_REMAINDER_MODE = "batch_remainder_mode";
    public static final String PARAM_AUTO_ON_DISPATCH = "batch_auto_on_dispatch";
    public static final String PARAM_KITTING_CREATE_SHORT = "batch_kitting_create_short_batch";
    public static final String PARAM_MIN_QTY = "batch_min_qty";
    public static final String PARAM_MAX_QTY = "batch_max_qty";

    @Inject
    ParameterRegistry parameters;

    public BatchSplitMode mode() {
        return BatchSplitMode.parse(parameters.get(PARAM_MODE));
    }

    public int fixedQty() {
        return Math.max(1, parameters.getInt(PARAM_FIXED_QTY, 1));
    }

    public BatchRemainderMode remainderMode() {
        return BatchRemainderMode.parse(parameters.get(PARAM_REMAINDER_MODE));
    }

    public boolean autoOnDispatch() {
        return "true".equalsIgnoreCase(parameters.get(PARAM_AUTO_ON_DISPATCH));
    }

    public boolean kittingCreateShortBatch() {
        String raw = parameters.get(PARAM_KITTING_CREATE_SHORT);
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(raw.trim());
    }

    public int minQty() {
        return Math.max(1, parameters.getInt(PARAM_MIN_QTY, 1));
    }

    public int maxQty() {
        int max = parameters.getInt(PARAM_MAX_QTY, 500);
        return Math.max(minQty(), max);
    }
}
