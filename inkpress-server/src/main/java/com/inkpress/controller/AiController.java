package com.inkpress.controller;

import com.inkpress.ai.ChatMessage;
import com.inkpress.ai.ChatStreamHandler;
import com.inkpress.common.ApiResponse;
import com.inkpress.common.BizException;
import com.inkpress.dto.AiContentDTO;
import com.inkpress.dto.AiPolishDTO;
import com.inkpress.ai.PromptInjectionDetector;
import com.inkpress.security.RateLimit;
import com.inkpress.service.AiAuditService;
import com.inkpress.service.AiService;
import com.inkpress.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "AI 写作辅助")
@RestController
@RequestMapping("/api/admin/ai")
public class AiController {

    private final AiService aiService;
    private final TagService tagService;
    private final AiAuditService aiAuditService;
    private final ThreadPoolTaskExecutor aiExecutor;

    public AiController(AiService aiService, TagService tagService, AiAuditService aiAuditService,
                        @org.springframework.beans.factory.annotation.Qualifier("aiExecutor")
                        ThreadPoolTaskExecutor aiExecutor) {
        this.aiService = aiService;
        this.tagService = tagService;
        this.aiAuditService = aiAuditService;
        this.aiExecutor = aiExecutor;
    }

    @Operation(summary = "AI 润色（SSE 流式输出）")
    @RateLimit(times = 20, windowSeconds = 300, message = "AI 调用过于频繁，请 5 分钟后再试")
    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter polish(@Valid @RequestBody AiPolishDTO dto) {
        // 未配置 API Key 时直接返回 JSON 错误而非 SSE 流
        aiService.ensureAvailable();
        long start = System.currentTimeMillis();
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        String guarded = risk > 0 ? dto.getContent() + PromptInjectionDetector.guardSuffix() : dto.getContent();
        StringBuilder acc = new StringBuilder();
        SseEmitter emitter = new SseEmitter(0L);
        aiExecutor.execute(() -> aiService.polish(guarded, dto.getInstruction(), new ChatStreamHandler() {
            @Override
            public void onDelta(String delta) {
                acc.append(delta);
                try {
                    emitter.send(SseEmitter.event().data(delta));
                } catch (Exception e) {
                    // 客户端已断开，结束推送
                    emitter.complete();
                }
            }

            @Override
            public void onDone() {
                aiAuditService.record("/api/admin/ai/polish", dto.getContent().length(),
                        risk, "OK", acc.length(), System.currentTimeMillis() - start);
                emitter.complete();
            }

            @Override
            public void onError(Throwable t) {
                aiAuditService.record("/api/admin/ai/polish", dto.getContent().length(),
                        risk, "ERROR", 0, System.currentTimeMillis() - start);
                emitter.completeWithError(t);
            }
        }));
        return emitter;
    }

    @Operation(summary = "AI 续写（SSE 流式输出）")
    @RateLimit(times = 20, windowSeconds = 300, message = "AI 调用过于频繁，请 5 分钟后再试")
    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueWriting(@Valid @RequestBody AiPolishDTO dto) {
        aiService.ensureAvailable();
        long start = System.currentTimeMillis();
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        String guarded = risk > 0 ? dto.getContent() + PromptInjectionDetector.guardSuffix() : dto.getContent();
        SseEmitter emitter = new SseEmitter(0L);
        aiExecutor.execute(() -> aiService.continueWriting(guarded, dto.getInstruction(), new ChatStreamHandler() {
            @Override
            public void onDelta(String delta) {
                try {
                    emitter.send(SseEmitter.event().data(delta));
                } catch (Exception e) {
                    emitter.complete();
                }
            }

            @Override
            public void onDone() {
                aiAuditService.record("/api/admin/ai/continue", dto.getContent().length(),
                        risk, "OK", 0, System.currentTimeMillis() - start);
                emitter.complete();
            }

            @Override
            public void onError(Throwable t) {
                aiAuditService.record("/api/admin/ai/continue", dto.getContent().length(),
                        risk, "ERROR", 0, System.currentTimeMillis() - start);
                emitter.completeWithError(t);
            }
        }));
        return emitter;
    }

    @Operation(summary = "生成文章摘要")
    @RateLimit(times = 30, windowSeconds = 300, message = "AI 调用过于频繁，请 5 分钟后再试")
    @PostMapping("/summary")
    public ApiResponse<String> summary(@Valid @RequestBody AiContentDTO dto) {
        long start = System.currentTimeMillis();
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        try {
            aiService.ensureAvailable();
            String result = aiService.summary(dto.getContent());
            aiAuditService.record("/api/admin/ai/summary", dto.getContent().length(), risk,
                    "OK", result == null ? 0 : result.length(), System.currentTimeMillis() - start);
            return ApiResponse.ok(result);
        } catch (BizException e) {
            // 失败路径同样落审计：未配置/超时/调用失败都可追溯
            aiAuditService.record("/api/admin/ai/summary", dto.getContent().length(), risk,
                    "ERROR", 0, System.currentTimeMillis() - start);
            throw e;
        }
    }

    @Operation(summary = "推荐标签")
    @RateLimit(times = 30, windowSeconds = 300, message = "AI 调用过于频繁，请 5 分钟后再试")
    @PostMapping("/tags")
    public ApiResponse<List<String>> suggestTags(@Valid @RequestBody AiContentDTO dto) {
        long start = System.currentTimeMillis();
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        List<String> candidates = tagService.listAll().stream()
                .map(com.inkpress.entity.Tag::getName)
                .toList();
        List<String> result = aiService.suggestTags(dto.getContent(), candidates);
        aiAuditService.record("/api/admin/ai/tags", dto.getContent().length(), risk,
                "OK", result.size(), System.currentTimeMillis() - start);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "生成标题候选（5 个）")
    @RateLimit(times = 30, windowSeconds = 300, message = "AI 调用过于频繁，请 5 分钟后再试")
    @PostMapping("/titles")
    public ApiResponse<List<String>> suggestTitles(@Valid @RequestBody AiContentDTO dto) {
        long start = System.currentTimeMillis();
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        List<String> result = aiService.suggestTitles(dto.getContent());
        aiAuditService.record("/api/admin/ai/titles", dto.getContent().length(), risk,
                "OK", result.size(), System.currentTimeMillis() - start);
        return ApiResponse.ok(result);
    }
}
