package com.inkpress.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务配置。
 * 适配 OpenAI 兼容协议，常见厂商只需替换 base-url / api-key / model：
 * <pre>
 * 智谱     https://open.bigmodel.cn/api/paas/v4                 glm-4-flash
 * DeepSeek https://api.deepseek.com/v1                          deepseek-chat
 * Kimi     https://api.moonshot.cn/v1                           moonshot-v1-8k
 * 通义     https://dashscope.aliyuncs.com/compatible-mode/v1    qwen-turbo
 * Ollama   http://localhost:11434/v1                            llama3.1
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "inkpress.ai")
public class AiProperties {

    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature = 0.7;
    private Integer timeoutSeconds = 60;
    /** 向量化配置：本地哈希向量（无需外部服务）或远程 Embedding API */
    private Embedding embedding = new Embedding();

    @Data
    public static class Embedding {
        /** auto：有 api-key 用远程，否则本地；local：仅本地；remote：仅远程 */
        private String provider = "auto";
        private String model = "embedding-3";
        private Integer localDim = 512;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
