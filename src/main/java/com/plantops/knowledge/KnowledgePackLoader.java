package com.plantops.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从 classpath `knowledge/**` 加载 YAML pack 并扁平化为 dot-key 参数表。 */
@ApplicationScoped
public class KnowledgePackLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public KnowledgePack loadStandardPack() {
        Map<String, String> flat = new LinkedHashMap<>();
        mergeYamlResource(flat, "knowledge/standard/defaults/parameters.yaml");
        mergeYamlResource(flat, "knowledge/standard/defaults/demand-supply-rules.yaml");
        mergeYamlResource(flat, "knowledge/standard/pack.yaml");
        return new KnowledgePack("plantops-standard-v1", "1.0.0-SNAPSHOT", null, KnowledgeLayer.STANDARD, Map.copyOf(flat));
    }

    public KnowledgePack loadIndustryPack(String industryId) {
        String path = "knowledge/industries/" + industryFolder(industryId) + "/pack.yaml";
        return loadPack(path, KnowledgeLayer.INDUSTRY, industryId);
    }

    private KnowledgePack loadPack(String classpathPath, KnowledgeLayer layer, String defaultPackId) {
        Map<String, String> flat = new LinkedHashMap<>();
        mergeYamlResource(flat, classpathPath);
        if (flat.isEmpty()) {
            throw new IllegalStateException("Knowledge pack not found or empty: " + classpathPath);
        }
        String packId = flat.getOrDefault("pack_id", flat.getOrDefault("industry_id", defaultPackId));
        String version = flat.getOrDefault("version", "1.0.0");
        String extendsPack = flat.get("extends");
        flat.remove("pack_id");
        flat.remove("industry_id");
        flat.remove("version");
        flat.remove("extends");
        flat.remove("description");
        return new KnowledgePack(packId, version, extendsPack, layer, Map.copyOf(flat));
    }

    private static void mergeYamlResource(Map<String, String> flat, String classpathPath) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathPath)) {
            if (in == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = YAML.readValue(in, Map.class);
            flatten("", root, flat);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load knowledge resource: " + classpathPath, e);
        }
    }

    private static String industryFolder(String industryId) {
        return switch (industryId) {
            case "DISCRETE_ASSEMBLY" -> "discrete-assembly";
            default -> industryId.toLowerCase().replace('_', '-');
        };
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> node, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            if (isPackMetadata(key) && prefix.isEmpty()) {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?>)) {
                    out.put(key, String.valueOf(value));
                }
                continue;
            }
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(fullKey, (Map<String, Object>) nested, out);
            } else if (value != null) {
                out.put(fullKey, String.valueOf(value));
                if ("parameters".equals(prefix) || fullKey.startsWith("parameters.")) {
                    String shortKey =
                            fullKey.startsWith("parameters.") ? fullKey.substring("parameters.".length()) : key;
                    out.putIfAbsent(shortKey, String.valueOf(value));
                }
            }
        }
    }

    private static boolean isPackMetadata(String key) {
        return switch (key) {
            case "pack_id", "industry_id", "version", "extends", "description", "sdd_version", "artifacts",
                    "knowledge_types", "domains" -> true;
            default -> false;
        };
    }
}
