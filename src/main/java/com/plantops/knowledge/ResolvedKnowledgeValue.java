package com.plantops.knowledge;

/** 单条解析结果及其来源层。 */
public record ResolvedKnowledgeValue(String key, String value, KnowledgeLayer layer) {}
