package com.inkpress.controller;

import com.inkpress.ai.ChatStreamHandler;
import com.inkpress.ai.PromptInjectionDetector;
import com.inkpress.common.ApiResponse;
import com.inkpress.dto.AiContentDTO;
import com.inkpress.entity.Article;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.security.RateLimit;
import com.inkpress.service.AiAuditService;
import com.inkpress.service.AiService;
import com.inkpress.service.ArticleVectorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能检索与 RAG 问答接口。
 * 相关文章/语义搜索为公开 GET；问答为公开 SSE 流（未配置 Key 时返回 JSON 错误）。
 */
@Tag(name = "智能检索与问答")
@RestController
@RequestMapping("/api")
public class IntelligentController {

    /** RAG 检索条数与单篇资料截断长度 */
    private static final int RAG_TOP_K = 4;
    private static final int RAG_SNIPPET = 600;

    private final ArticleVectorService vectorService;
    private final AiService aiService;
    private final AiAuditService aiAuditService;
    private final ArticleMapper articleMapper;
    private final ThreadPoolTaskExecutor aiExecutor;

    public IntelligentController(ArticleVectorService vectorService,
                                 AiService aiService,
                                 AiAuditService aiAuditService,
                                 ArticleMapper articleMapper,
                                 @org.springframework.beans.factory.annotation.Qualifier("aiExecutor")
                                 ThreadPoolTaskExecutor aiExecutor) {
        this.vectorService = vectorService;
        this.aiService = aiService;
        this.aiAuditService = aiAuditService;
        this.articleMapper = articleMapper;
        this.aiExecutor = aiExecutor;
    }

    @Operation(summary = "相关文章推荐（语义+标签混合排序）")
    @GetMapping("/articles/{id}/related")
    public ApiResponse<List<Map<String, Object>>> related(@org.springframework.web.bind.annotation.PathVariable Long id,
                                                          @RequestParam(defaultValue = "4") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 8);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        vectorService.related(id, safeLimit).forEach(vo -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", vo.getId());
            item.put("title", vo.getTitle());
            item.put("summary", vo.getSummary());
            item.put("categoryName", vo.getCategoryName());
            item.put("views", vo.getViews());
            items.add(item);
        });
        return ApiResponse.ok(items);
    }

    @Operation(summary = "语义搜索（自然语言查询）")
    @GetMapping("/search/semantic")
    public ApiResponse<List<Map<String, Object>>> semanticSearch(@RequestParam String q,
                                                                 @RequestParam(defaultValue = "8") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return ApiResponse.ok(vectorService.semanticSearch(q, safeLimit));
    }

    @Operation(summary = "站内知识问答（RAG，SSE 流式，首事件为引用来源 JSON）")
    @RateLimit(times = 10, windowSeconds = 300, message = "问答请求过于频繁，请 5 分钟后再试")
    @PostMapping(value = "/ai/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@Valid @RequestBody AiContentDTO dto) {
        aiService.ensureAvailable();
        long start = System.currentTimeMillis();
        // 提示注入检测：游客输入直接拼入 RAG 提示词，是 OWASP LLM01 的直接暴露面
        int risk = PromptInjectionDetector.inspect(dto.getContent());
        String guarded = risk > 0 ? dto.getContent() + PromptInjectionDetector.guardSuffix() : dto.getContent();
        // 检索 Top-K 文章，拼装带编号的资料块
        List<Map<String, Object>> hits = vectorService.semanticSearch(guarded, RAG_TOP_K);
        if (hits.isEmpty()) {
            SseEmitter empty = new SseEmitter(0L);
            try {
                empty.send(SseEmitter.event().data(
                        "{\"type\":\"sources\",\"items\":[]}"));
                empty.send(SseEmitter.event().data("站内暂无与该问题相关的文章，无法回答。"));
            } catch (Exception ignored) {
            }
            aiAuditService.record("/api/ai/ask", dto.getContent().length(), risk,
                    "OK", 0, System.currentTimeMillis() - start);
            empty.complete();
            return empty;
        }
        StringBuilder knowledge = new StringBuilder();
        List<Map<String, Object>> sources = new java.util.ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> hit = hits.get(i);
            @SuppressWarnings("unchecked")
            Map<String, Object> articleMap = (Map<String, Object>) hit.get("article");
            Long articleId = ((Number) articleMap.get("id")).longValue();
            Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getTitle, Article::getContent)
                    .eq(Article::getId, articleId));
            if (article == null) {
                continue;
            }
            String content = article.getContent() == null ? "" : article.getContent();
            knowledge.append("[").append(i + 1).append("] ").append(article.getTitle()).append("\n")
                    .append(content, 0, Math.min(RAG_SNIPPET, content.length()))
                    .append("\n\n");
            Map<String, Object> source = new HashMap<>();
            source.put("index", i + 1);
            source.put("id", article.getId());
            source.put("title", article.getTitle());
            sources.add(source);
        }

        SseEmitter emitter = new SseEmitter(0L);
        String sourcesJson;
        try {
            sourcesJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of("type", "sources", "items", sources));
        } catch (Exception e) {
            sourcesJson = "{\"type\":\"sources\",\"items\":[]}";
        }
        final String firstEvent = sourcesJson;
        final String question = guarded;
        aiExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().data(firstEvent));
            } catch (Exception e) {
                emitter.complete();
                return;
            }
            aiService.ask(question, knowledge.toString(), new ChatStreamHandler() {
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
                    aiAuditService.record("/api/ai/ask", dto.getContent().length(), risk,
                            "OK", 0, System.currentTimeMillis() - start);
                    emitter.complete();
                }

                @Override
                public void onError(Throwable t) {
                    aiAuditService.record("/api/ai/ask", dto.getContent().length(), risk,
                            "ERROR", 0, System.currentTimeMillis() - start);
                    emitter.completeWithError(t);
                }
            });
        });
        return emitter;
    }
}
