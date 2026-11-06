package com.inkpress.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.config.AiProperties;
import com.inkpress.service.AiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 动态向量化提供者：每次调用按当前生效配置（数据库 > 环境变量）解析具体实现，
 * 使管理端切换 本地特征哈希 / 远程 Embedding 后无需重启即生效。
 * 委托实例按配置签名缓存，避免每次调用重复创建 HttpClient；
 * 向量化配置变化时由 AiConfigChangedEvent 触发索引重建（见 ArticleVectorServiceImpl）。
 */
@Slf4j
@Component
@Primary
public class DynamicEmbeddingProvider implements EmbeddingProvider {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    /** 缓存最近一次使用的委托实例与配置签名 */
    private volatile String cachedSignature;
    private volatile EmbeddingProvider cachedDelegate;

    public DynamicEmbeddingProvider(AiConfigService aiConfigService, ObjectMapper objectMapper) {
        this.aiConfigService = aiConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public double[] embed(String text) {
        return delegate().embed(text);
    }

    @Override
    public String name() {
        return delegate().name();
    }

    private EmbeddingProvider delegate() {
        AiProperties cfg = aiConfigService.getEffective();
        // 签名含 base-url 与密钥：远程方案换厂商/换钥也要换委托实例
        String signature = aiConfigService.embeddingSignature(cfg)
                + "|" + cfg.getBaseUrl() + "|" + cfg.getApiKey();
        EmbeddingProvider current = cachedDelegate;
        if (current == null || !signature.equals(cachedSignature)) {
            synchronized (this) {
                current = cachedDelegate;
                if (current == null || !signature.equals(cachedSignature)) {
                    current = create(cfg);
                    cachedDelegate = current;
                    cachedSignature = signature;
                    log.info("向量化实现已切换：{}", current.name());
                }
            }
        }
        return current;
    }

    private EmbeddingProvider create(AiProperties cfg) {
        String provider = cfg.getEmbedding().getProvider() == null
                ? "auto" : cfg.getEmbedding().getProvider().toLowerCase();
        return switch (provider) {
            case "local" -> new HashingEmbeddingProvider(cfg);
            case "remote" -> new OpenAiCompatibleEmbeddingProvider(cfg, objectMapper);
            default -> cfg.enabled()
                    ? new OpenAiCompatibleEmbeddingProvider(cfg, objectMapper)
                    : new HashingEmbeddingProvider(cfg);
        };
    }
}
