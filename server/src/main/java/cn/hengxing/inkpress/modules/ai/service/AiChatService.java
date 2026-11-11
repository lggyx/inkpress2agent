package cn.hengxing.inkpress.modules.ai.service;

import cn.hengxing.inkpress.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OpenAI 兼容大模型代理：SSE 流式对话、摘要/标签生成、连接测试 */
@Service
public class AiChatService {

    private final AiConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiChatService(AiConfigService configService) {
        this.configService = configService;
    }

    /** SSE 流式对话：按提示词模板渲染后转发模型输出，逐 token 推给前端（双协议） */
    public SseEmitter chatStream(String category, String content) {
        requireConfigured();
        SseEmitter emitter = new SseEmitter(180_000L);
        String prompt = configService.render(category, content);
        HttpRequest request = anthropic() ? anthropicStreamRequest(prompt) : openaiStreamRequest(prompt);
        executor.submit(() -> streamInto(emitter, request));
        return emitter;
    }

    /** OpenAI 兼容：POST {base}/chat/completions，流式，Bearer 鉴权 */
    private HttpRequest openaiStreamRequest(String prompt) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", configService.get(AiConfigService.KEY_MODEL),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", true));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /** Anthropic：POST {base}/v1/messages（base 以 /v1 结尾则拼 /messages），x-api-key + anthropic-version，max_tokens 必填 */
    private HttpRequest anthropicStreamRequest(String prompt) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", configService.get(AiConfigService.KEY_MODEL),
                    "max_tokens", 4096,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return HttpRequest.newBuilder(URI.create(anthropicMessagesUrl()))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("x-api-key", configService.get(AiConfigService.KEY_API_KEY))
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private boolean anthropic() {
        return AiConfigService.PROTOCOL_ANTHROPIC.equals(configService.protocol());
    }

    /** Anthropic messages 端点：base 已含 /v1 则只拼 /messages */
    private String anthropicMessagesUrl() {
        String base = baseUrl();
        return base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages";
    }

    /** Anthropic models 端点（测试连接用），同 /v1 规则 */
    private String anthropicModelsUrl() {
        String base = baseUrl();
        return base.endsWith("/v1") ? base + "/models" : base + "/v1/models";
    }

    /** 摘要与标签：summary 模板 → JSON 解析为 seoDescription/geoSummary/tags（双协议） */
    public Map<String, Object> generateSummary(String content) {
        requireConfigured();
        String raw = callModel(configService.render("summary", content));
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                node.get("tags").forEach(t -> tags.add(t.asText()));
            }
            return Map.of(
                    "seoDescription", node.path("seoDescription").asText(""),
                    "geoSummary", node.path("geoSummary").asText(""),
                    "tags", tags);
        } catch (Exception e) {
            throw BizException.of(3002, "AI 返回格式异常，请重试");
        }
    }

    /** 管理端测试连接：OpenAI 走 GET /models（Bearer）；Anthropic 走 GET /v1/models（x-api-key） */
    public boolean testConnection() {
        if (!configService.isConfigured()) {
            return false;
        }
        try {
            HttpRequest.Builder builder;
            if (anthropic()) {
                builder = HttpRequest.newBuilder(URI.create(anthropicModelsUrl()))
                        .timeout(Duration.ofSeconds(15))
                        .header("x-api-key", configService.get(AiConfigService.KEY_API_KEY))
                        .header("anthropic-version", "2023-06-01");
            } else {
                builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/models"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY));
            }
            HttpResponse<String> resp = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 一次性补全（非流式）：按模板渲染后调用，返回文本。供评审等场景复用（双协议统一入口） */
    public String complete(String category, String content) {
        requireConfigured();
        return callModel(configService.render(category, content));
    }

    /** 非流式统一入口：按协议分发 */
    private String callModel(String prompt) {
        if (anthropic()) {
            return anthropicComplete(prompt);
        }
        return callOnce(prompt);
    }

    /** Anthropic 非流式补全 */
    private String anthropicComplete(String prompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", configService.get(AiConfigService.KEY_MODEL),
                    "max_tokens", 4096,
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(anthropicMessagesUrl()))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", configService.get(AiConfigService.KEY_API_KEY))
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw BizException.of(3003, "模型服务返回 " + resp.statusCode());
            }
            // Anthropic 响应：{content:[{type:"text",text:"..."}]}
            JsonNode contentArr = objectMapper.readTree(resp.body()).path("content");
            for (JsonNode block : contentArr) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText("");
                }
            }
            return "";
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(3003, "AI 调用失败：" + e.getMessage());
        }
    }

    private void streamInto(SseEmitter emitter, HttpRequest request) {
        boolean anthropic = anthropic();
        try {
            HttpResponse<java.io.InputStream> resp =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                emitter.send(SseEmitter.event().name("error").data("模型服务返回 " + resp.statusCode()));
                emitter.complete();
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String token = anthropic ? anthropicToken(data) : openaiToken(data);
                    if (token != null && !token.isEmpty()) {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    }
                }
            }
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("AI 调用失败：" + e.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.complete();
        }
    }

    /** OpenAI 流帧：choices[0].delta.content */
    private String openaiToken(String data) {
        try {
            JsonNode choices = objectMapper.readTree(data).path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("delta").path("content").asText(null);
            }
            return null;
        } catch (Exception ignored) {
            return null; // 跳过脏帧
        }
    }

    /** Anthropic 流帧：type=content_block_delta 时 delta.text；error 事件透传文案 */
    private String anthropicToken(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            String type = node.path("type").asText("");
            if ("content_block_delta".equals(type)) {
                return node.path("delta").path("text").asText(null);
            }
            if ("error".equals(type)) {
                return null; // 错误帧丢弃，等待流自然结束
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String callOnce(String prompt) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", configService.get(AiConfigService.KEY_MODEL),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw BizException.of(3003, "模型服务返回 " + resp.statusCode());
            }
            JsonNode choices = objectMapper.readTree(resp.body()).path("choices");
            return choices.get(0).path("message").path("content").asText("");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(3003, "AI 调用失败：" + e.getMessage());
        }
    }

    private void requireConfigured() {
        if (!configService.isConfigured()) {
            throw BizException.of(3004, "AI 服务未配置，请管理员在系统设置中填写");
        }
    }

    private String baseUrl() {
        String base = configService.get(AiConfigService.KEY_BASE_URL);
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : raw;
    }
}
