package com.inkpress.event;

import com.inkpress.service.ArticleVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AI 配置变更监听：向量化方案/维度/密钥变化时，旧向量与新配置不兼容
 * （本地 512 维 vs 远程 1024+ 维），异步重建索引保证检索一致性。
 * 独立成组件（而非挂在 ArticleVectorServiceImpl 上）是为了让 @Async 代理正常生效——
 * 同类自调用会绕过代理，导致重建阻塞管理端保存请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigChangedListener {

    private final ArticleVectorService articleVectorService;

    @Async("taskExecutor")
    @EventListener
    public void onAiConfigChanged(AiConfigChangedEvent event) {
        if (!event.embeddingChanged()) {
            return;
        }
        log.info("检测到向量化配置变化，异步重建向量索引");
        try {
            articleVectorService.rebuildAll();
        } catch (Exception e) {
            log.warn("向量索引重建失败（不影响主流程）", e);
        }
    }
}
