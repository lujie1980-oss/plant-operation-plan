package com.plantops.api.dto.batch;

import java.util.List;

public record BulkBatchSplitResultDto(
        int attempted,
        int succeeded,
        int skipped,
        List<String> failures) {
}
