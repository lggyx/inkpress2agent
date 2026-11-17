package cn.hengxing.inkpress.modules.evolution;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.service.AiChatService;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.evolution.entity.AiFeedback;
import cn.hengxing.inkpress.modules.evolution.entity.EvolutionRun;
import cn.hengxing.inkpress.modules.evolution.entity.WritingMemory;
import cn.hengxing.inkpress.modules.evolution.mapper.AiFeedbackMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.EvolutionRunMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.MemorySnapshotMapper;
import cn.hengxing.inkpress.modules.evolution.mapper.WritingMemoryMapper;
import cn.hengxing.inkpress.modules.evolution.service.EvolutionService;
import cn.hengxing.inkpress.modules.evolution.service.WritingMemoryService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 进化闭环编排层单测（Mockito）：startRun 自动收尾、recordFeedback 校验与挂载、
 * finishRun 指标核算 / AI 蒸馏 / 降级入库、publicSummary 聚合。
 * mapper 与协作服务全部 mock，不依赖数据库与 AI 网关。
 */
class EvolutionOrchestrationTest {

    private static final long USER = 9L;
    private static final long RUN = 7L;

    private final ObjectMapper om = new ObjectMapper();
    private EvolutionRunMapper runMapper;
    private AiFeedbackMapper feedbackMapper;
    private WritingMemoryMapper memoryMapper;
    private WritingMemoryService memoryService;
    private AiChatService aiChatService;
    private EvolutionService service;

    @BeforeAll
    static void initEntityMeta() {
        // 纯单测环境没有 MyBatis 容器，Lambda 查询的列缓存需手动注册
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, EvolutionRun.class);
        TableInfoHelper.initTableInfo(assistant, AiFeedback.class);
        TableInfoHelper.initTableInfo(assistant, WritingMemory.class);
    }

    @BeforeEach
    void setUp() {
        runMapper = mock(EvolutionRunMapper.class);
        feedbackMapper = mock(AiFeedbackMapper.class);
        memoryMapper = mock(WritingMemoryMapper.class);
        memoryService = mock(WritingMemoryService.class);
        aiChatService = mock(AiChatService.class);
        service = new EvolutionService(runMapper, feedbackMapper,
                mock(MemorySnapshotMapper.class), memoryMapper,
                memoryService, aiChatService, mock(ArticleMapper.class));
    }

    private static EvolutionRun run(long userId, long id) {
        EvolutionRun r = new EvolutionRun();
        r.setId(id);
        r.setUserId(userId);
        r.setStatus(EvolutionService.STATUS_RUNNING);
        r.setStartedAt(LocalDateTime.now());
        return r;
    }

    private static AiFeedback fb(String verdict, String text) {
        AiFeedback f = new AiFeedback();
        f.setRunId(RUN);
        f.setUserId(USER);
        f.setSuggestionType("REPLACE");
        f.setSuggestionText(text);
        f.setVerdict(verdict);
        return f;
    }

    @Test
    void startRunAutoClosesStaleRunningRun() throws Exception {
        EvolutionRun stale = run(USER, RUN);
        when(runMapper.selectOne(any())).thenReturn(stale);
        when(runMapper.selectById(RUN)).thenReturn(stale);
        when(feedbackMapper.selectList(any())).thenReturn(List.of());
        when(aiChatService.complete(anyString(), anyString())).thenThrow(new RuntimeException("AI 未配置"));

        EvolutionRun fresh = service.startRun(USER, 33L);

        assertEquals(EvolutionService.STATUS_RUNNING, fresh.getStatus());
        assertEquals(USER, fresh.getUserId().longValue());
        assertEquals(33L, fresh.getArticleId().longValue());
        assertNotNull(fresh.getStartedAt());
        // 残留 RUNNING 被自动收尾
        assertEquals(EvolutionService.STATUS_FINISHED, stale.getStatus());
        assertNotNull(stale.getFinishedAt());
        verify(runMapper).updateById(stale);
        ArgumentCaptor<EvolutionRun> ins = ArgumentCaptor.forClass(EvolutionRun.class);
        verify(runMapper).insert(ins.capture());
        assertEquals(EvolutionService.STATUS_RUNNING, ins.getValue().getStatus());
        // 降级路径：无纠正原话时不产生记忆
        Map<String, Object> metrics = om.readValue(stale.getMetricsJson(), Map.class);
        assertEquals(0, ((Number) metrics.get("memoryLearned")).intValue());
        assertEquals(false, metrics.get("distilled"));
    }

    @Test
    void recordFeedbackRejectsUnknownTypeAndVerdict() {
        BizException badType = assertThrows(BizException.class,
                () -> service.recordFeedback(USER, null, null, "BAZ", "内容", "UP"));
        assertEquals(3201, badType.getCode());
        BizException badVerdict = assertThrows(BizException.class,
                () -> service.recordFeedback(USER, null, null, "CHAT", "内容", "SIDE"));
        assertEquals(3202, badVerdict.getCode());
    }

    @Test
    void recordFeedbackRejectsForeignRun() {
        EvolutionRun foreign = run(2L, RUN);
        when(runMapper.selectById(RUN)).thenReturn(foreign);
        BizException e = assertThrows(BizException.class,
                () -> service.recordFeedback(USER, RUN, null, "CHAT", "内容", "UP"));
        assertEquals(3203, e.getCode());
    }

    @Test
    void recordFeedbackAttachesToCurrentRunAndCapsText() {
        EvolutionRun current = run(USER, RUN);
        when(runMapper.selectOne(any())).thenReturn(current);
        String huge = "超长建议".repeat(3000);

        service.recordFeedback(USER, null, 88L, "append", huge, "applied");

        ArgumentCaptor<AiFeedback> cap = ArgumentCaptor.forClass(AiFeedback.class);
        verify(feedbackMapper).insert(cap.capture());
        AiFeedback saved = cap.getValue();
        assertEquals(RUN, saved.getRunId().longValue());
        assertEquals("APPEND", saved.getSuggestionType());
        assertEquals("APPLIED", saved.getVerdict());
        assertEquals(8000, saved.getSuggestionText().length());
    }

    @Test
    void finishRunComputesMetricsAndDistillsByAi() throws Exception {
        EvolutionRun run = run(USER, RUN);
        when(runMapper.selectById(RUN)).thenReturn(run);
        List<AiFeedback> fbs = List.of(
                fb("APPLIED", "定稿内容"),
                fb("APPLIED", "完全不同的另外一段话内容"),
                fb("UP", null),
                fb("DOWN", null));
        when(feedbackMapper.selectList(any())).thenReturn(fbs);
        when(aiChatService.complete(eq("memory_distill"), anyString()))
                .thenReturn("[{\"category\":\"TONE\",\"content\":\"句子宜短\"}]");

        EvolutionRun finished = service.finishRun(USER, RUN, "开头。\n定稿内容。结尾。", List.of());

        assertEquals(EvolutionService.STATUS_FINISHED, finished.getStatus());
        assertNotNull(finished.getFinishedAt());
        Map<String, Object> m = om.readValue(finished.getMetricsJson(), Map.class);
        assertEquals(4, ((Number) m.get("suggestionsTotal")).intValue());
        assertEquals(2, ((Number) m.get("applied")).intValue());
        assertEquals(1, ((Number) m.get("verbatim")).intValue());
        assertEquals(1, ((Number) m.get("thumbsUp")).intValue());
        assertEquals(1, ((Number) m.get("thumbsDown")).intValue());
        int avg = ((Number) m.get("avgEditDistanceBp")).intValue();
        assertTrue(avg > 0 && avg <= 10000, "第二条建议未被逐字包含，平均距离应为正");
        assertEquals(1, ((Number) m.get("memoryLearned")).intValue());
        assertEquals(true, m.get("distilled"));
        // 逐字包含的标记 1/0，落回反馈行
        assertEquals(1, fbs.get(0).getFinalVerbatim().intValue());
        assertEquals(0, fbs.get(0).getEditDistance().intValue());
        assertTrue(fbs.get(1).getEditDistance() > 0);
        verify(feedbackMapper).updateById(fbs.get(0));
        verify(feedbackMapper).updateById(fbs.get(1));
        verify(memoryService).insert(eq(USER), eq("TONE"), eq("句子宜短"), eq("DISTILL"), eq(RUN));
    }

    @Test
    void finishRunDistillFallsBackToCorrectionRawText() throws Exception {
        EvolutionRun run = run(USER, RUN);
        when(runMapper.selectById(RUN)).thenReturn(run);
        when(feedbackMapper.selectList(any())).thenReturn(List.of());
        when(aiChatService.complete(anyString(), anyString())).thenThrow(new RuntimeException("网关超时"));

        EvolutionRun finished = service.finishRun(USER, RUN, "定稿正文", List.of("", "句子太长了", "  ", "少用感叹号"));

        assertEquals(EvolutionService.STATUS_FINISHED, finished.getStatus());
        verify(memoryService).insert(eq(USER), eq("TONE"), eq("句子太长了"), eq("CORRECTION"), eq(RUN));
        verify(memoryService).insert(eq(USER), eq("TONE"), eq("少用感叹号"), eq("CORRECTION"), eq(RUN));
        Map<String, Object> m = om.readValue(finished.getMetricsJson(), Map.class);
        assertEquals(false, m.get("distilled"));
        assertEquals(2, ((Number) m.get("memoryLearned")).intValue());
    }

    @Test
    void finishRunRejectsAlreadyFinishedRun() {
        EvolutionRun run = run(USER, RUN);
        run.setStatus(EvolutionService.STATUS_FINISHED);
        when(runMapper.selectById(RUN)).thenReturn(run);
        BizException e = assertThrows(BizException.class,
                () -> service.finishRun(USER, RUN, "定稿正文", List.of()));
        assertEquals(3204, e.getCode());
    }

    @Test
    void publicSummaryAggregatesCountsAndLastMetrics() throws Exception {
        when(runMapper.selectCount(any())).thenReturn(4L);
        when(memoryMapper.selectCount(any())).thenReturn(2L);
        EvolutionRun last = run(USER, 12L);
        last.setStatus(EvolutionService.STATUS_FINISHED);
        last.setMetricsJson("{\"applied\":1,\"verbatim\":1,\"distilled\":true}");
        last.setFinishedAt(LocalDateTime.of(2026, 11, 14, 13, 39));
        when(runMapper.selectOne(any())).thenReturn(last);

        Map<String, Object> m = service.publicSummary();

        assertEquals(4L, m.get("finishedRuns"));
        assertEquals(2L, m.get("activeMemories"));
        assertEquals(12L, m.get("lastRunId"));
        assertEquals("2026-11-14T13:39", m.get("lastFinishedAt"));
        assertEquals(1, ((com.fasterxml.jackson.databind.JsonNode) m.get("lastMetrics")).path("applied").asInt());
    }
}
