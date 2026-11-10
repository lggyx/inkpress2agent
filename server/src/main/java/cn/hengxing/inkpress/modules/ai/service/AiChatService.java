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

    /** SSE 流式对话：按提示词模板渲染后转发模型输出，逐 token 推给前端 */
    public SseEmitter chatStream(String category, String content) {
        requireConfigured();
        SseEmitter emitter = new SseEmitter(180_000L);
        String prompt = configService.render(category, content);
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", configService.get(AiConfigService.KEY_MODEL),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", true));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        executor.submit(() -> streamInto(emitter, request));
        return emitter;
    }

    /** 摘要与标签：summary 模板 → JSON 解析为 seoDescription/geoSummary/tags */
    public Map<String, Object> generateSummary(String content) {
        requireConfigured();
        String raw = callOnce(configService.render("summary", content));
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

    /** 管理端测试连接：GET /models */
    public boolean testConnection() {
        if (!configService.isConfigured()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY))
                    .GET().build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 一次性补全（非流式）：按模板渲染后调用，返回文本。供评审等场景复用 */
    public String complete(String category, String content) {
        requireConfigured();
        return callOnce(configService.render(category, content));
    }

    private void streamInto(SseEmitter emitter, HttpRequest request) {
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
                    if (line.isBlank()) {
                        continue;
                    }
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    try {
                        JsonNode choices = objectMapper.readTree(data).path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            String token = choices.get(0).path("delta").path("content").asText(null);
                            if (token != null && !token.isEmpty()) {
                                emitter.send(SseEmitter.event().name("token").data(token));
                            }
                        }
                    } catch (Exception ignored) {
                        // 跳过脏帧
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
