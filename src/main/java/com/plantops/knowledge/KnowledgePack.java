package com.plantops.knowledge;

import java.util.Map;

/** 已加载的 Standard / Industry pack 快照。 */
public record KnowledgePack(
        String packId,
        String version,
        String extendsPackId,
        KnowledgeLayer layer,
        Map<String, String> flatParameters) {}
