package com.plantops.api.dto.masterdata;

import java.util.List;

public record MasterDataImportResult(
        int rowsImported,
        List<String> errors
) {
}
