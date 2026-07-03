package com.plantops.testsupport.blindrebuild;

import java.util.List;

/**
 * Spec-only rebuild target for TODO-05 blind exercise.
 *
 * @param moduleId stable id (e.g. {@code sch-p0-projection})
 * @param title human label
 * @param pilotPack relative path under {@code docs/testing/blind-rebuild-pilots/}
 * @param specAnchors SDD paths relative to {@code docs/sdd/}
 * @param acceptanceIds §8 AC ids that must pass after rebuild
 * @param gateTestClasses fully-qualified JUnit classes run as automated gates
 */
public record BlindRebuildModulePack(
        String moduleId,
        String title,
        String pilotPack,
        List<String> specAnchors,
        List<String> acceptanceIds,
        List<String> gateTestClasses) {}
