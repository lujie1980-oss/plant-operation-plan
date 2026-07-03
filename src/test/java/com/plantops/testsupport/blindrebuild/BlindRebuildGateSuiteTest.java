package com.plantops.testsupport.blindrebuild;

import com.plantops.testsupport.SpecRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO-05: validates blind-rebuild module packs (spec anchors exist, AC ids known, gate tests on
 * classpath). Does not run gate tests — use {@link #gateTestCommand(String)} in CI or after rebuild.
 */
@SpecRef("AC-BR-01")
class BlindRebuildGateSuiteTest {

  private static final Pattern AC_ID = Pattern.compile("\\b(AC(?:-[A-Z0-9]+)+)\\b");

  @Test
  void modulePacksReferenceExistingSpecsAndAcceptanceIds() throws IOException {
    Set<String> knownAc = loadAcceptanceIds();
    assertFalse(knownAc.isEmpty());

    List<String> violations = new ArrayList<>();
    for (BlindRebuildModulePack pack : BlindRebuildRegistry.MODULE_PACKS) {
      Path pilot = Path.of("docs/testing/blind-rebuild-pilots", pack.pilotPack());
      if (!Files.isRegularFile(pilot)) {
        violations.add(pack.moduleId() + ": missing pilot pack " + pilot);
      }
      for (String anchor : pack.specAnchors()) {
        String pathPart = anchor.contains("#") ? anchor.substring(0, anchor.indexOf('#')) : anchor;
        Path spec = Path.of("docs/sdd", pathPart);
        if (!Files.isRegularFile(spec)) {
          violations.add(pack.moduleId() + ": missing spec anchor " + spec);
        }
      }
      for (String acId : pack.acceptanceIds()) {
        if (!knownAc.contains(acId)) {
          violations.add(pack.moduleId() + ": unknown acceptance id " + acId);
        }
      }
      for (String gateClass : pack.gateTestClasses()) {
        try {
          Class.forName(gateClass);
        } catch (ClassNotFoundException e) {
          violations.add(pack.moduleId() + ": gate test class not found " + gateClass);
        }
      }
    }
    assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
  }

  @Test
  void globalGateTestsExistOnClasspath() {
    List<String> missing = new ArrayList<>();
    for (String gateClass : BlindRebuildRegistry.GLOBAL_GATE_TESTS) {
      try {
        Class.forName(gateClass);
      } catch (ClassNotFoundException e) {
        missing.add(gateClass);
      }
    }
    assertTrue(missing.isEmpty(), () -> "Missing global gate tests: " + missing);
  }

  /** Maven {@code -Dtest=} argument for a module pack plus global gates. */
  static String gateTestCommand(String moduleId) {
    BlindRebuildModulePack pack =
        BlindRebuildRegistry.MODULE_PACKS.stream()
            .filter(p -> p.moduleId().equals(moduleId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown module: " + moduleId));

    LinkedHashSet<String> classes = new LinkedHashSet<>(BlindRebuildRegistry.GLOBAL_GATE_TESTS);
    classes.add(BlindRebuildGateSuiteTest.class.getName());
    classes.addAll(pack.gateTestClasses());
    return String.join(",", classes);
  }

  @Test
  void registryProvidesGateCommandsForAllModules() {
    for (BlindRebuildModulePack pack : BlindRebuildRegistry.MODULE_PACKS) {
      String cmd = gateTestCommand(pack.moduleId());
      assertFalse(cmd.isBlank());
      assertTrue(cmd.contains("SpecRefCoverageTest"));
    }
  }

  private static Set<String> loadAcceptanceIds() throws IOException {
    String text = Files.readString(Path.of("docs/sdd/core/08-acceptance.md"));
    Set<String> ids = new LinkedHashSet<>();
    Matcher matcher = AC_ID.matcher(text);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    return ids;
  }
}
