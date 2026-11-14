package cn.hengxing.inkpress.modules.ai.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.evolution.service.WritingMemoryService;
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
    private final WritingMemoryService memoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiChatService(AiConfigService configService, WritingMemoryService memoryService) {
        this.configService = configService;
        this.memoryService = memoryService;
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

    /** SSE 流式多轮对话：前端直传 messages 历史（studio 工作台用），assistant 模板作为系统提示 */
    public SseEmitter chatStreamMessages(List<Map<String, String>> messages) {
        return chatStreamMessages(messages, "assistant");
    }

    /** 按 persona 路由系统提示模板：assistant=工作台写作助手，article_qa=文章页读者问答助手 */
    public SseEmitter chatStreamMessages(List<Map<String, String>> messages, String category) {
        return chatStreamMessages(messages, category, null);
    }

    /** 多轮对话 + 作者写作记忆注入：工作台（assistant）会话时把该作者的 ACTIVE 记忆并入 system 提示；
     *  读者侧（article_qa）与匿名调用不注入，行为与未开启进化闭环时一致 */
    public SseEmitter chatStreamMessages(List<Map<String, String>> messages, String category, Long userId) {
        requireConfigured(AiConfigService.CAP_TEXT);
        SseEmitter emitter = new SseEmitter(180_000L);
        String system = configService.mustTemplate(category).getContent();
        if (userId != null && "assistant".equals(category)) {
            String memoryBlock = memoryService.activeBlock(userId);
            if (memoryBlock != null && !memoryBlock.isBlank()) {
                system = system + "\n\n" + memoryBlock;
            }
        }
        HttpRequest request = anthropic()
                ? anthropicMessagesRequest(messages, system, true)
                : openaiMessagesRequest(messages, system, true);
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

    /** OpenAI 多轮：messages 直传，assistant 模板作 system 消息前置 */
    private HttpRequest openaiMessagesRequest(List<Map<String, String>> messages, String system, boolean stream) {
        List<Map<String, String>> full = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            full.add(Map.of("role", "system", "content", system));
        }
        for (Map<String, String> m : messages) {
            full.add(Map.of("role", strOrDefault(m.get("role"), "user"), "content", strOrDefault(m.get("content"), "")));
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", text().model(),
                    "messages", full,
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

    private static String strOrDefault(String v, String dft) {
        return v == null || v.isBlank() ? dft : v;
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

    /** Anthropic 多轮：messages 直传（roles 必须交替 user/assistant），system 参数单列 */
    private HttpRequest anthropicMessagesRequest(List<Map<String, String>> messages, String system, boolean stream) {
        List<Map<String, Object>> full = new ArrayList<>();
        String lastRole = null;
        for (Map<String, String> m : messages) {
            String role = strOrDefault(m.get("role"), "user");
            if (role.equals("system")) continue;
            // Anthropic 要求相邻消息角色交替，相同角色合并内容
            if (role.equals(lastRole) && !full.isEmpty()) {
                Map<String, Object> prev = full.get(full.size() - 1);
                prev.put("content", prev.get("content") + "\n\n" + m.get("content"));
                continue;
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("role", role);
            row.put("content", m.get("content"));
            full.add(row);
            lastRole = role;
        }
        String body;
        try {
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("model", text().model());
            root.put("max_tokens", 4096);
            root.put("stream", stream);
            root.put("messages", full);
            if (system != null && !system.isBlank()) {
                root.put("system", system);
            }
            body = objectMapper.writeValueAsString(root);
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
