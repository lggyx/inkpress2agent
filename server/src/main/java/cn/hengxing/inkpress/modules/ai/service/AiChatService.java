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

/**
 * OpenAI 兼容 / Anthropic 双协议大模型代理：SSE 流式对话、摘要/标签生成、连接测试。
 * 一律使用 AiConfigService 的能力槽 provider（文本对话=ai.text，视觉理解=ai.vision，双协议同构）。
 */
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
        requireConfigured(AiConfigService.CAP_TEXT);
        SseEmitter emitter = new SseEmitter(180_000L);
        String prompt = configService.render(category, content);
        HttpRequest request = anthropic() ? anthropicRequest(prompt, true) : openaiRequest(prompt, true);
        executor.submit(() -> streamInto(emitter, request));
        return emitter;
    }

    /** OpenAI 兼容请求：POST {base}/chat/completions，Bearer 鉴权 */
    private HttpRequest openaiRequest(String prompt, boolean stream) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", text().model(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", stream));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return HttpRequest.newBuilder(URI.create(text().baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + text().apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /** Anthropic 请求：POST {base}/v1/messages（base 以 /v1 结尾则拼 /messages）；鉴权双发 x-api-key 与 Bearer（AUTH_TOKEN 型网关只认后者）；max_tokens 必填 */
    private HttpRequest anthropicRequest(String prompt, boolean stream) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", text().model(),
                    "max_tokens", 4096,
                    "stream", stream,
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return anthropicBuilder(anthropicUrl("/messages"))
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private AiConfigService.AiProvider text() {
        return configService.provider(AiConfigService.CAP_TEXT);
    }

    private boolean anthropic() {
        return AiConfigService.PROTOCOL_ANTHROPIC.equals(text().protocol());
    }

    private boolean anthropic(AiConfigService.AiProvider p) {
        return AiConfigService.PROTOCOL_ANTHROPIC.equals(p.protocol());
    }

    /** Anthropic 端点拼接：base 已含 /v1 则只拼子路径 */
    private static String anthropicUrl(String base, String sub) {
        return base.endsWith("/v1") ? base + sub : base + "/v1" + sub;
    }

    private String anthropicUrl(String sub) {
        return anthropicUrl(text().baseUrl(), sub);
    }

    private HttpRequest.Builder anthropicBuilder(String url, AiConfigService.AiProvider p) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", p.apiKey())
                .header("Authorization", "Bearer " + p.apiKey())
                .header("anthropic-version", "2023-06-01");
    }

    private HttpRequest.Builder anthropicBuilder(String url) {
        return anthropicBuilder(url, text());
    }

    /** 分能力测试连接：OpenAI 走 GET {base}/models；Anthropic 走 GET {base}/v1/models，网关不支持（404/405）时降级为 1 token messages ping */
    public boolean testConnection(String capability) {
        if (!configService.configured(capability)) {
            return false;
        }
        AiConfigService.AiProvider p = configService.provider(capability);
        try {
            if (anthropic(p)) {
                HttpResponse<String> resp = http.send(anthropicBuilder(anthropicUrl(p.baseUrl(), "/models"), p)
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return true;
                }
                if (resp.statusCode() != 404 && resp.statusCode() != 405) {
                    return false;
                }
                String ping = objectMapper.writeValueAsString(Map.of(
                        "model", p.model(),
                        "max_tokens", 1,
                        "messages", List.of(Map.of("role", "user", "content", "ping"))));
                HttpResponse<String> pingResp = http.send(anthropicBuilder(anthropicUrl(p.baseUrl(), "/messages"), p)
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(ping, StandardCharsets.UTF_8)).build(),
                        HttpResponse.BodyHandlers.ofString());
                return pingResp.statusCode() == 200;
            }
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(p.baseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + p.apiKey())
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 一次性补全（非流式）：按模板渲染后调用，返回文本。供评审等场景复用（双协议统一入口） */
    public String complete(String category, String content) {
        requireConfigured(AiConfigService.CAP_TEXT);
        return callModel(configService.render(category, content));
    }

    /** 非流式统一入口：按协议分发 */
    private String callModel(String prompt) {
        if (anthropic()) {
            return anthropicComplete(prompt);
        }
        return openaiComplete(prompt);
    }

    /** OpenAI 非流式补全 */
    private String openaiComplete(String prompt) {
        try {
            HttpResponse<String> resp = http.send(openaiRequest(prompt, false), HttpResponse.BodyHandlers.ofString());
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

    /** Anthropic 非流式补全 */
    private String anthropicComplete(String prompt) {
        try {
            HttpResponse<String> resp = http.send(anthropicRequest(prompt, false), HttpResponse.BodyHandlers.ofString());
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

    /** Anthropic 流帧：type=content_block_delta 时 delta.text；error 帧丢弃 */
    private String anthropicToken(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            String type = node.path("type").asText("");
            if ("content_block_delta".equals(type)) {
                return node.path("delta").path("text").asText(null);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 摘要与标签：summary 模板 → JSON 解析为 seoDescription/geoSummary/tags（双协议） */
    public Map<String, Object> generateSummary(String content) {
        requireConfigured(AiConfigService.CAP_TEXT);
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

    private void requireConfigured(String capability) {
        if (!configService.configured(capability)) {
            throw BizException.of(3004, "AI 能力未配置（" + capability + "），请管理员在系统设置中填写");
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end > start ? raw.substring(start, end + 1) : raw;
    }
}
