package com.plantops.scenario;

import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.masterdata.ChangeoverExcelImportService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeoverDurationServiceTest {

    @Test
    void ruleMatches_wildcardBothSidesRequiresDifferentValues() {
        ChangeoverMatrixEntity rule = rule("*", "*");
        assertTrue(ChangeoverDurationService.ruleMatches(rule, "A", "B"));
        assertTrue(!ChangeoverDurationService.ruleMatches(rule, "A", "A"));
    }

    @Test
    void ruleMatches_specificTargetBranch() {
        ChangeoverMatrixEntity rule = rule("*", "1分支");
        assertTrue(ChangeoverDurationService.ruleMatches(rule, "2分支", "1分支"));
        assertTrue(!ChangeoverDurationService.ruleMatches(rule, "2分支", "3分支"));
    }

    @Test
    void parseDurationMinutes_fromExcelTime() {
        assertEquals(15, ChangeoverExcelImportService.parseDurationMinutes("00:15:00"));
        assertEquals(30, ChangeoverExcelImportService.parseDurationMinutes("00:30:00"));
        assertEquals(5, ChangeoverExcelImportService.parseDurationMinutes("00:05:00"));
    }

    @Test
    void formatDurationMinutes_forExcelExport() {
        assertEquals("00:15:00", ChangeoverExcelImportService.formatDurationMinutes(15));
        assertEquals("00:30:00", ChangeoverExcelImportService.formatDurationMinutes(30));
        assertEquals("01:05:00", ChangeoverExcelImportService.formatDurationMinutes(65));
    }

    @Test
    void attributeKey_parseChineseLabels() {
        assertEquals("wireMaterial", ChangeoverAttributeKey.normalizeCode("线材"));
        assertEquals("keyMaterial", ChangeoverAttributeKey.normalizeCode("关键物料"));
        assertEquals("maleFemaleEnd", ChangeoverAttributeKey.normalizeCode("公母端"));
        assertEquals("totalBranch", ChangeoverAttributeKey.normalizeCode("分支"));
        assertEquals("productCode", ChangeoverAttributeKey.normalizeCode("料号"));
    }

    private static ChangeoverMatrixEntity rule(String from, String to) {
        ChangeoverMatrixEntity e = new ChangeoverMatrixEntity();
        e.fromAttributeValue = from;
        e.toAttributeValue = to;
        return e;
    }
}
