package com.plantops.openapi.sdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** CLI entry: regenerate {@code docs/api/openapi.yaml} from §6 (TODO-02). */
public final class GenerateOpenApiFromSdd {

    private GenerateOpenApiFromSdd() {}

    public static void main(String[] args) throws IOException {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path contracts = repoRoot.resolve("docs/sdd/core/06-api-contracts.md");
        Path output = repoRoot.resolve("docs/api/openapi.yaml");

        List<SddApiOperation> operations = SddApiContractParser.parse(contracts);
        Map<String, Object> document = OpenApiSkeletonGenerator.generate(operations);

        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper(
                YAMLFactory.builder().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());
        String yaml = mapper.writeValueAsString(document);
        String header =
                """
                # Generated from docs/sdd/core/06-api-contracts.md — do not edit by hand.
                # Regenerate: ./mvnw -q exec:java -Dexec.mainClass=com.plantops.openapi.sdd.GenerateOpenApiFromSdd

                """;
        Files.writeString(output, header + yaml);
        System.out.println("Wrote " + output + " (" + operations.size() + " operations)");
    }
}
