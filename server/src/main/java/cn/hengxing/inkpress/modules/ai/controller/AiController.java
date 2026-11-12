package cn.hengxing.inkpress.modules.ai.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.ai.service.AiChatService;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.ai.entity.PromptTemplate;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AiController {

    public record ChatRequest(@NotBlank String category, @NotBlank String content) {
    }

    public record SummaryRequest(@NotBlank String content) {
    }

    public record ImageRequest(@NotBlank String prompt, String size) {
    }

    /** 单能力槽配置更新：protocol/baseUrl/model；apiKey 约定 null 不改、空串清除；dimensions 仅向量槽 */
    public record CapabilityUpdate(String capability, String protocol, String baseUrl, String model,
                                   String apiKey, String dimensions) {
    }

    public record ConfigUpdateRequest(List<CapabilityUpdate> updates) {
    }

    public record TemplateUpdateRequest(@NotBlank String category, @NotBlank String name,
                                        @NotBlank String content, boolean enabled) {
    }

    private final AiChatService aiChatService;
    private final AiConfigService aiConfigService;
    private final cn.hengxing.inkpress.modules.ai.service.ImageGenerationService imageGenerationService;
    private final cn.hengxing.inkpress.modules.search.service.EmbeddingService embeddingService;

    public AiController(AiChatService aiChatService, AiConfigService aiConfigService,
                        cn.hengxing.inkpress.modules.ai.service.ImageGenerationService imageGenerationService,
                        cn.hengxing.inkpress.modules.search.service.EmbeddingService embeddingService) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.imageGenerationService = imageGenerationService;
        this.embeddingService = embeddingService;
    }

    /** SSE 流式 AI 对话：{category,content} 模板模式 或 {messages:[...],persona?} 多轮直传（studio 工作台/文章页问答） */
    @PostMapping(path = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        Object messages = body.get("messages");
        if (messages instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, String>> hist = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object role = m.get("role");
                    Object content = m.get("content");
                    Map<String, String> row = new java.util.LinkedHashMap<>();
                    row.put("role", role == null ? "user" : String.valueOf(role));
                    row.put("content", content == null ? "" : String.valueOf(content));
                    hist.add(row);
                }
            }
            // persona=reader → 文章页问答助手模板；缺省 assistant（工作台写作助手）
            String category = "reader".equals(String.valueOf(body.getOrDefault("persona", "")))
                    ? "article_qa" : "assistant";
            return aiChatService.chatStreamMessages(hist, category);
        }
        String category = body.get("category") == null ? "" : String.valueOf(body.get("category"));
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        if (category.isBlank() || content.isBlank()) {
            throw cn.hengxing.inkpress.common.BizException.of(400, "缺少 category/content 或 messages");
        }
        return aiChatService.chatStream(category, content);
    }

    @PostMapping("/ai/summary")
    public Result<Map<String, Object>> summary(@RequestBody SummaryRequest req) {
        return Result.ok(aiChatService.generateSummary(req.content()));
    }

    /** AI 配图：生成图片落媒体流，返回站内 /api/media/{id}（可作封面/插图 URL） */
    @PostMapping("/ai/images")
    public Result<Map<String, Object>> image(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                             @jakarta.validation.Valid @RequestBody ImageRequest req) {
        return Result.ok(imageGenerationService.generateAndStore(user.id(), req.prompt(), req.size()));
    }

    @GetMapping("/ai/status")
    public Result<Map<String, Boolean>> status() {
        return Result.ok(Map.of("configured", aiConfigService.isConfigured()));
    }

    @GetMapping("/admin/ai-config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> config() {
        return Result.ok(aiConfigService.view());
    }

    @PutMapping("/admin/ai-config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateConfig(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                     @RequestBody ConfigUpdateRequest req) {
        List<Map<String, String>> updates = new ArrayList<>();
        if (req.updates() != null) {
            for (CapabilityUpdate u : req.updates()) {
                Map<String, String> m = new java.util.LinkedHashMap<>();
                m.put("capability", u.capability());
                m.put("protocol", u.protocol());
                m.put("baseUrl", u.baseUrl());
                m.put("model", u.model());
                m.put("apiKey", u.apiKey());
                m.put("dimensions", u.dimensions());
                updates.add(m);
            }
        }
        aiConfigService.update(user.id(), updates);
        return Result.ok();
    }

    /** 分能力测试连接：body 传 {capability}，缺省测文本对话；embedding 槽实发最小 embeddings 调用 */
    @PostMapping("/admin/ai-config/test")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Boolean>> test(@RequestBody(required = false) Map<String, String> body) {
        String cap = body == null || body.get("capability") == null
                ? AiConfigService.CAP_TEXT : body.get("capability");
        boolean ok = AiConfigService.CAP_EMBEDDING.equals(cap)
                ? embeddingService.testEmbedding()
                : aiChatService.testConnection(cap);
        return Result.ok(Map.of("ok", ok));
    }

    @GetMapping("/admin/prompt-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<PromptTemplate>> templates() {
        return Result.ok(aiConfigService.templates());
    }

    @PutMapping("/admin/prompt-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateTemplate(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                       @RequestBody TemplateUpdateRequest req) {
        aiConfigService.updateTemplate(user.id(), req.category(), req.name(), req.content(), req.enabled());
        return Result.ok();
    }
}
