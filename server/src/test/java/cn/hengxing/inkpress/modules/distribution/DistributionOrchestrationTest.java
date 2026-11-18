package cn.hengxing.inkpress.modules.distribution;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.distribution.entity.DistributionRecord;
import cn.hengxing.inkpress.modules.distribution.mapper.DistributionRecordMapper;
import cn.hengxing.inkpress.modules.distribution.service.DistributionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分发编排层单测（Mockito）：创建的发布状态校验/熔断/进行中去重、filled/report 状态机、
 * 作品数据回流（仅 PUBLISHED 可回填、字段缺省语义）。
 */
class DistributionOrchestrationTest {

    private DistributionRecordMapper recordMapper;
    private ArticleMapper articleMapper;
    private DistributionService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, DistributionRecord.class);
    }

    @BeforeEach
    void setUp() {
        recordMapper = mock(DistributionRecordMapper.class);
        articleMapper = mock(ArticleMapper.class);
        service = new DistributionService(recordMapper, articleMapper);
        doAnswer(inv -> {
            DistributionRecord r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        }).when(recordMapper).insert(any(DistributionRecord.class));
    }

    private static Article publishedArticle(long id) {
        Article a = new Article();
        a.setId(id);
        a.setStatus("PUBLISHED");
        a.setDeleted(0);
        return a;
    }

    private static DistributionRecord rec(String status) {
        DistributionRecord r = new DistributionRecord();
        r.setId(1L);
        r.setArticleId(9L);
        r.setPlatform("juejin");
        r.setStatus(status);
        r.setAttemptCount(0);
        return r;
    }

    @Test
    void createRejectsUnpublishedArticle() {
        Article draft = publishedArticle(9L);
        draft.setStatus("DRAFT");
        when(articleMapper.selectById(9L)).thenReturn(draft);

        assertEquals(2003, assertThrows(BizException.class,
                () -> service.create(9L, "juejin")).getCode());
    }

    @Test
    void createTripsCircuitBreakerAfterThreeConsecutiveFailures() {
        when(articleMapper.selectById(9L)).thenReturn(publishedArticle(9L));
        when(recordMapper.selectList(any())).thenReturn(List.of(
                rec("FAILED"), rec("FAILED"), rec("FAILED")));

        BizException e = assertThrows(BizException.class, () -> service.create(9L, "juejin"));
        assertEquals(4002, e.getCode());
        verify(recordMapper, never()).insert(any(DistributionRecord.class));
    }

    @Test
    void createRejectsDuplicateInflightTask() {
        when(articleMapper.selectById(9L)).thenReturn(publishedArticle(9L));
        when(recordMapper.selectList(any())).thenReturn(List.of(rec("FAILED"), rec("PUBLISHED")));
        when(recordMapper.selectOne(any())).thenReturn(rec("PENDING"));

        assertEquals(4001, assertThrows(BizException.class,
                () -> service.create(9L, "juejin")).getCode());
    }

    @Test
    void createInsertsPendingRecordAfterChecksPass() {
        when(articleMapper.selectById(9L)).thenReturn(publishedArticle(9L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectOne(any())).thenReturn(null);

        Long id = service.create(9L, "juejin");

        assertEquals(50L, id);
        ArgumentCaptor<DistributionRecord> cap = ArgumentCaptor.forClass(DistributionRecord.class);
        verify(recordMapper).insert(cap.capture());
        assertEquals("PENDING", cap.getValue().getStatus());
        assertEquals("juejin", cap.getValue().getPlatform());
    }

    @Test
    void markFilledBumpsAttemptAndKeepsDraftUrl() {
        DistributionRecord r = rec("PENDING");
        when(recordMapper.selectById(1L)).thenReturn(r);

        service.markFilled(1L, "https://juejin.cn/editor/draft", "{}");

        assertEquals("FILLED", r.getStatus());
        assertEquals("https://juejin.cn/editor/draft", r.getDraftUrl());
        assertEquals(1, r.getAttemptCount());
    }

    @Test
    void reportFillsPublishedUrlOnlyWhenProvided() {
        DistributionRecord r = rec("FILLED");
        r.setPublishedUrl("https://juejin.cn/post/old");
        when(recordMapper.selectById(1L)).thenReturn(r);

        service.report(1L, true, "", "{}");
        assertEquals("PUBLISHED", r.getStatus());
        assertEquals("https://juejin.cn/post/old", r.getPublishedUrl(), "空外链不覆盖既有值");
    }

    @Test
    void saveWorkStatsOnlyForPublishedRecord() {
        DistributionRecord filled = rec("FILLED");
        when(recordMapper.selectById(1L)).thenReturn(filled);
        assertEquals(4003, assertThrows(BizException.class,
                () -> service.saveWorkStats(1L, 10L, 2L, 1L)).getCode());

        DistributionRecord published = rec("PUBLISHED");
        when(recordMapper.selectById(1L)).thenReturn(published);
        service.saveWorkStats(1L, 120L, 34L, null);

        assertEquals(120L, published.getWorkViews());
        assertEquals(34L, published.getWorkLikes());
        assertNull(published.getWorkComments(), "缺省字段不覆盖");
        verify(recordMapper).updateById(published);
    }

    @Test
    void publishedWorksQueriesPublishedWithUrl() {
        when(recordMapper.selectList(any())).thenReturn(List.of());
        service.publishedWorks();
        verify(recordMapper, times(1)).selectList(any());
    }
}
