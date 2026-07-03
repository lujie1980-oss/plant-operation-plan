package com.plantops.openapi.sdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.plantops.testsupport.SpecRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI guard (TODO-02): committed {@code docs/api/openapi.yaml} matches §6 generator output.
 */
@SpecRef("AC-API-01")
class OpenApiSpecCoverageTest {

    private static final Pattern API_ID = Pattern.compile("\\b(API-[A-Z]+-\\d+)\\b");

    private static ObjectMapper yamlMapper() {
        return new ObjectMapper(
                YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());
    }

    @Test
    void openapiYamlMatchesSection6Generator() throws Exception {
        Path contracts = Path.of("docs/sdd/core/06-api-contracts.md");
        Path openapi = Path.of("docs/api/openapi.yaml");

        List<SddApiOperation> operations = SddApiContractParser.parse(contracts);
        assertFalse(operations.isEmpty(), "§6 parser must emit operations");

        Map<String, Object> expected = OpenApiSkeletonGenerator.generate(operations);
        ObjectMapper mapper = yamlMapper();

        assertTrue(Files.exists(openapi), "Run GenerateOpenApiFromSdd to create docs/api/openapi.yaml");
        String committed = Files.readString(openapi);
        String committedBody = stripGeneratorHeader(committed);

        @SuppressWarnings("unchecked")
        Map<String, Object> actual = mapper.readValue(committedBody, Map.class);
        assertEquals(expected, actual, "docs/api/openapi.yaml is stale — run GenerateOpenApiFromSdd");
    }

    @Test
    void everySection6ApiIdAppearsInOpenapi() throws Exception {
        Set<String> specIds = loadApiIdsFromSection6();
        List<SddApiOperation> operations =
                SddApiContractParser.parse(Path.of("docs/sdd/core/06-api-contracts.md"));
        Set<String> parsedIds = new LinkedHashSet<>();
        for (SddApiOperation op : operations) {
            parsedIds.add(op.apiId());
        }
        for (String id : specIds) {
            assertTrue(parsedIds.contains(id), "§6 " + id + " missing from parser output");
        }
    }

    private static Set<String> loadApiIdsFromSection6() throws Exception {
        String text = Files.readString(Path.of("docs/sdd/core/06-api-contracts.md"));
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = API_ID.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String stripGeneratorHeader(String yaml) {
        int idx = yaml.indexOf("openapi:");
        return idx >= 0 ? yaml.substring(idx) : yaml;
    }
}
