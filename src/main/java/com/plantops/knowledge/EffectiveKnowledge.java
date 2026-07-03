package com.plantops.knowledge;

import java.util.Map;

/** 合并后的 Effective Knowledge 快照（只读）。 */
public record EffectiveKnowledge(
        String workspaceId,
        String industryId,
        String standardPackId,
        String industryPackVersion,
        Map<String, ResolvedKnowledgeValue> valuesByKey) {

    public String getString(String key) {
        ResolvedKnowledgeValue value = valuesByKey.get(key);
        return value != null ? value.value() : null;
    }
}
