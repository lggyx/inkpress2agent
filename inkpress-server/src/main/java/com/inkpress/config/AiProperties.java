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

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
