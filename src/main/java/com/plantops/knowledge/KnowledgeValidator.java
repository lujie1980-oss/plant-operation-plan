package com.plantops.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 导入 overlay 时校验：不得覆盖 hard RULE（TODO-15 K2）。 */
@ApplicationScoped
public class KnowledgeValidator {

    private static final Set<String> HARD_RULE_IDS = loadHardRuleIds();

    public void validateOverlayKey(String overlayKey) {
        if (overlayKey == null || overlayKey.isBlank()) {
            throw new BadRequestException("overlay_key 不能为空");
        }
        String trimmed = overlayKey.trim();
        if (HARD_RULE_IDS.contains(trimmed)) {
            throw new BadRequestException("不可覆盖 hard 规则: " + trimmed);
        }
        if (trimmed.matches("^RULE-[A-Z0-9-]+$")) {
            throw new BadRequestException("不可直接覆盖 RULE 标识: " + trimmed);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> loadHardRuleIds() {
        Set<String> hard = new HashSet<>();
        try (InputStream in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("knowledge/standard/catalog.yaml")) {
            if (in == null) {
                return hard;
            }
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            Map<String, Object> root = yaml.readValue(in, Map.class);
            Object rulesObj = root.get("rules");
            if (!(rulesObj instanceof List<?> rules)) {
                return hard;
            }
            for (Object item : rules) {
                if (!(item instanceof Map<?, ?> rule)) {
                    continue;
                }
                Object id = rule.get("id");
                Object overridable = rule.get("overridable");
                if (id != null && "none".equals(String.valueOf(overridable))) {
                    hard.add(String.valueOf(id));
                }
            }
        } catch (Exception ignored) {
            // catalog 缺失时仅做 RULE-* 前缀拦截
        }
        return hard;
    }
}
