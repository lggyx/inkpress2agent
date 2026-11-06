package com.inkpress.event;

import com.inkpress.dto.AiConfigDTO;

/**
 * AI 配置变更事件。
 * 发布方：AiConfigServiceImpl（保存/恢复默认后）；
 * 监听方：ArticleVectorServiceImpl（向量化相关配置变化时异步重建索引，
 * 避免旧维度向量与新配置混用导致检索结果失真）。
 */
public record AiConfigChangedEvent(AiConfigDTO dto, boolean embeddingChanged) {
}
