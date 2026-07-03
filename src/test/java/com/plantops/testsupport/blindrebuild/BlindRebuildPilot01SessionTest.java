package com.plantops.testsupport.blindrebuild;

import com.plantops.testsupport.SpecRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO-05 M1: documents that pilot {@code sch-p0-projection} gate command is wired and
 * implementation passes the same gates a blind rebuild must pass.
 */
@SpecRef("AC-BR-02")
class BlindRebuildPilot01SessionTest {

  @Test
  void pilot01GateCommandIncludesModuleAndGlobalGates() {
    String command = BlindRebuildGateSuiteTest.gateTestCommand("sch-p0-projection");
    assertTrue(command.contains("DetailScheduleLegacyProjectorTest"));
    assertTrue(command.contains("SpecRefCoverageTest"));
    assertTrue(command.contains("OpenApiSpecCoverageTest"));
    assertTrue(command.contains("BlindRebuildGateSuiteTest"));
  }
}
