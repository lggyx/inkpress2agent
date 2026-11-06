package com.inkpress.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.config.AiProperties;
import com.inkpress.service.AiConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议适配器（默认实现）。
 * 通过 base-url / api-key / model 三个配置项即可切换 DeepSeek、智谱、通义千问、Kimi、Ollama 等服务。
 * 配置经 AiConfigService 实时解析（数据库 > 环境变量），管理端保存后无需重启即生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleAdapter implements ChatModelAdapter {

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String complete(List<ChatMessage> messages) {
        try {
            HttpResponse<String> response = httpClient.send(buildRequest(messages, false),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("AI 接口返回异常: {} {}", response.statusCode(), abbreviate(response.body()));
                throw new BizException(ErrorCode.AI_CALL_FAILED);
            }
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                throw new BizException(ErrorCode.AI_RESPONSE_INVALID);
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("AI 接口调用失败", e);
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    @Override
    public void stream(List<ChatMessage> messages, ChatStreamHandler handler) {
        try {
            HttpResponse<InputStream> response = httpClient.send(buildRequest(messages, true),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("AI 流式接口返回异常: {} {}", response.statusCode(), abbreviate(body));
                handler.onError(new BizException(ErrorCode.AI_CALL_FAILED));
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    String delta = objectMapper.readTree(payload)
                            .path("choices").path(0).path("delta").path("content").asText(null);
                    if (delta != null && !delta.isEmpty()) {
                        handler.onDelta(delta);
                    }
                }
            }
            handler.onDone();
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private HttpRequest buildRequest(List<ChatMessage> messages, boolean stream) {
        // 每次调用实时解析配置：管理端修改后即时生效，无需重启
        AiProperties properties = aiConfigService.getEffective();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", properties.getTemperature());
        body.put("stream", stream);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.substring(0, Math.min(300, text.length()));
    }
}
