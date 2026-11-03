package com.inkpress.controller;

import com.inkpress.ai.ChatMessage;
import com.inkpress.ai.ChatStreamHandler;
import com.inkpress.common.ApiResponse;
import com.inkpress.dto.AiContentDTO;
import com.inkpress.dto.AiPolishDTO;
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
    private final ThreadPoolTaskExecutor aiExecutor;

    public AiController(AiService aiService, TagService tagService,
                        @org.springframework.beans.factory.annotation.Qualifier("aiExecutor")
                        ThreadPoolTaskExecutor aiExecutor) {
        this.aiService = aiService;
        this.tagService = tagService;
        this.aiExecutor = aiExecutor;
    }

    @Operation(summary = "AI 润色（SSE 流式输出）")
    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter polish(@Valid @RequestBody AiPolishDTO dto) {
        // 未配置 API Key 时直接返回 JSON 错误而非 SSE 流
        aiService.ensureAvailable();
        SseEmitter emitter = new SseEmitter(0L);
        aiExecutor.execute(() -> aiService.polish(dto.getContent(), dto.getInstruction(), new ChatStreamHandler() {
            @Override
            public void onDelta(String delta) {
                try {
                    emitter.send(SseEmitter.event().data(delta));
                } catch (Exception e) {
                    // 客户端已断开，结束推送
                    emitter.complete();
                }
            }

            @Override
            public void onDone() {
                emitter.complete();
            }

            @Override
            public void onError(Throwable t) {
                emitter.completeWithError(t);
            }
        }));
        return emitter;
    }

    @Operation(summary = "AI 续写（SSE 流式输出）")
    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter continueWriting(@Valid @RequestBody AiPolishDTO dto) {
        aiService.ensureAvailable();
        SseEmitter emitter = new SseEmitter(0L);
        aiExecutor.execute(() -> aiService.continueWriting(dto.getContent(), dto.getInstruction(), new ChatStreamHandler() {
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
                emitter.complete();
            }

            @Override
            public void onError(Throwable t) {
                emitter.completeWithError(t);
            }
        }));
        return emitter;
    }

    @Operation(summary = "生成文章摘要")
    @PostMapping("/summary")
    public ApiResponse<String> summary(@Valid @RequestBody AiContentDTO dto) {
        return ApiResponse.ok(aiService.summary(dto.getContent()));
    }

    @Operation(summary = "推荐标签")
    @PostMapping("/tags")
    public ApiResponse<List<String>> suggestTags(@Valid @RequestBody AiContentDTO dto) {
        List<String> candidates = tagService.listAll().stream()
                .map(com.inkpress.entity.Tag::getName)
                .toList();
        return ApiResponse.ok(aiService.suggestTags(dto.getContent(), candidates));
    }

    @Operation(summary = "生成标题候选（5 个）")
    @PostMapping("/titles")
    public ApiResponse<List<String>> suggestTitles(@Valid @RequestBody AiContentDTO dto) {
        return ApiResponse.ok(aiService.suggestTitles(dto.getContent()));
    }
}
