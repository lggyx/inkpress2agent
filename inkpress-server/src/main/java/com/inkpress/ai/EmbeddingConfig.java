package com.inkpress.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量化实现装配：按 inkpress.ai.embedding.provider 选择。
 * auto：配置了 api-key 用远程 Embedding API，否则本地哈希向量；
 * remote 强制远程（未配置密钥时调用会报业务错误）；local 强制本地。
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingProvider embeddingProvider(AiProperties properties, ObjectMapper objectMapper) {
        String provider = properties.getEmbedding().getProvider() == null
                ? "auto" : properties.getEmbedding().getProvider().toLowerCase();
        return switch (provider) {
            case "local" -> new HashingEmbeddingProvider(properties);
            case "remote" -> new OpenAiCompatibleEmbeddingProvider(properties, objectMapper);
            default -> properties.enabled()
                    ? new OpenAiCompatibleEmbeddingProvider(properties, objectMapper)
                    : new HashingEmbeddingProvider(properties);
        };
    }
}
