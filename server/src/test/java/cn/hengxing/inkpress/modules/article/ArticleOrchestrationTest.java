package cn.hengxing.inkpress.modules.article;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.dto.ArticleUpsertRequest;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.CategoryMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.article.service.ArticleService;
import cn.hengxing.inkpress.modules.article.support.MarkdownRenderer;
import cn.hengxing.inkpress.modules.article.support.ViewCountBuffer;
import cn.hengxing.inkpress.modules.ai.service.AiChatService;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.messaging.service.NotificationService;
import cn.hengxing.inkpress.modules.review.service.ReviewService;
import cn.hengxing.inkpress.modules.search.service.SearchIndexPublisher;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章编排层单测（Mockito）：upsert 的 agent 强制复审 / 低分闸口 / 发布链路、
 * 复审状态机、上下架与删除的索引同步、公开详情的浏览计数——mapper 全 mock，不依赖 DB。
 */
class ArticleOrchestrationTest {

    private static final long AUTHOR = 9L;

    private ArticleMapper articleMapper;
    private ArticleTagMapper articleTagMapper;
    private TagMapper tagMapper;
    private MarkdownRenderer markdownRenderer;
    private ViewCountBuffer viewCountBuffer;
    private NotificationService notificationService;
    private SearchIndexPublisher searchIndexPublisher;
    private ReviewService reviewService;
    private AiConfigService aiConfigService;
    private ArticleService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
        TableInfoHelper.initTableInfo(assistant, cn.hengxing.inkpress.modules.article.entity.ArticleTag.class);
        TableInfoHelper.initTableInfo(assistant, cn.hengxing.inkpress.modules.article.entity.Tag.class);
    }

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        articleTagMapper = mock(ArticleTagMapper.class);
        tagMapper = mock(TagMapper.class);
        markdownRenderer = mock(MarkdownRenderer.class);
        viewCountBuffer = mock(ViewCountBuffer.class);
        notificationService = mock(NotificationService.class);
        searchIndexPublisher = mock(SearchIndexPublisher.class);
        reviewService = mock(ReviewService.class);
        aiConfigService = mock(AiConfigService.class);
        service = new ArticleService(articleMapper, articleTagMapper, tagMapper,
                mock(CategoryMapper.class), markdownRenderer, viewCountBuffer,
                notificationService, searchIndexPublisher, reviewService,
                mock(AiChatService.class), aiConfigService);
        when(markdownRenderer.render(anyString())).thenReturn("<p>渲染后 HTML</p>");
        when(aiConfigService.isConfigured()).thenReturn(false);
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        // insert 时补一个自增 id，走通 replaceTags 等后续编排
        doAnswer(inv -> {
            Article a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        }).when(articleMapper).insert(any(Article.class));
    }

    private static ArticleUpsertRequest req(Long id, String action, Boolean confirmLowScore) {
        return new ArticleUpsertRequest(id, null, "测试标题", "正文内容",
                null, null, null, List.of("Java", "ES"), action, confirmLowScore);
    }

    @Test
    void upsertAgentSourceForcedToPendingReview() {
        ArgumentCaptor<Article> cap = ArgumentCaptor.forClass(Article.class);

        service.upsert(AUTHOR, req(null, "PUBLISH", null), true);

        verify(articleMapper).insert(cap.capture());
        assertEquals("PENDING_REVIEW", cap.getValue().getStatus());
        assertEquals("AGENT", cap.getValue().getSource());
        verify(searchIndexPublisher, never()).publishUpsert(any());
    }

    @Test
    void upsertHumanPublishBlockedByLowScoreGate() {
        when(reviewService.latestScore(any())).thenReturn(50);

        BizException e = assertThrows(BizException.class,
                () -> service.upsert(AUTHOR, req(null, "PUBLISH", null), false));

        assertEquals(2005, e.getCode());
        verify(articleMapper, never()).insert(any(Article.class));
        verify(searchIndexPublisher, never()).publishUpsert(any());
    }

    @Test
    void upsertHumanPublishWithConfirmedLowScoreSucceeds() {
        when(reviewService.latestScore(any())).thenReturn(50);
        ArgumentCaptor<Article> cap = ArgumentCaptor.forClass(Article.class);

        service.upsert(AUTHOR, req(null, "PUBLISH", true), false);

        verify(articleMapper).insert(cap.capture());
        assertEquals("PUBLISHED", cap.getValue().getStatus());
        assertNotNull(cap.getValue().getPublishedAt());
        verify(searchIndexPublisher).publishUpsert(any());
        // 标签替换：旧关联先清空，两个新标签各自落库（insert 有 T/Collection 两个重载，显式给类型）
        verify(articleTagMapper).delete(any());
        verify(tagMapper, times(2)).insert(any(cn.hengxing.inkpress.modules.article.entity.Tag.class));
        verify(articleTagMapper, times(2)).insert(any(cn.hengxing.inkpress.modules.article.entity.ArticleTag.class));
    }

    @Test
    void upsertUpdateForeignArticleRejected() {
        Article existing = article(1L);
        existing.setAuthorId(2L);
        when(articleMapper.selectById(1L)).thenReturn(existing);

        BizException e = assertThrows(BizException.class,
                () -> service.upsert(AUTHOR, req(1L, "DRAFT", null), false));

        assertEquals(2001, e.getCode());
        verify(articleMapper, never()).updateById(any(Article.class));
    }

    @Test
    void upsertDraftSaveDoesNotPublish() {
        ArgumentCaptor<Article> cap = ArgumentCaptor.forClass(Article.class);

        service.upsert(AUTHOR, req(null, "DRAFT", null), false);

        verify(articleMapper).insert(cap.capture());
        assertEquals("DRAFT", cap.getValue().getStatus());
        verify(searchIndexPublisher, never()).publishUpsert(any());
    }

    @Test
    void reviewPassPublishesAndNotifies() {
        Article existing = article(1L);
        existing.setStatus("PENDING_REVIEW");
        when(articleMapper.selectById(1L)).thenReturn(existing);

        service.review(1L, true, 2L);

        assertEquals("PUBLISHED", existing.getStatus());
        assertNotNull(existing.getPublishedAt());
        verify(searchIndexPublisher).publishUpsert(1L);
        verify(notificationService).notifyUser(eq(AUTHOR), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void reviewRejectsNonPendingArticle() {
        Article existing = article(1L);
        existing.setStatus("PUBLISHED");
        when(articleMapper.selectById(1L)).thenReturn(existing);

        BizException e = assertThrows(BizException.class, () -> service.review(1L, true, 2L));
        assertEquals(2002, e.getCode());
    }

    @Test
    void offlineAndSoftDeleteSyncIndexRemoval() {
        Article existing = article(1L);
        when(articleMapper.selectById(1L)).thenReturn(existing);

        service.offline(1L);
        assertEquals("OFFLINE", existing.getStatus());
        verify(searchIndexPublisher).publishDelete(1L);

        service.softDelete(1L);
        assertEquals(1, existing.getDeleted());
        verify(searchIndexPublisher, times(2)).publishDelete(1L);
    }

    @Test
    void publicDetailRejectsUnpublishedAndCountsViews() {
        Article draft = article(1L);
        draft.setStatus("DRAFT");
        when(articleMapper.selectById(1L)).thenReturn(draft);
        assertEquals(2003, assertThrows(BizException.class, () -> service.publicDetail(1L)).getCode());

        Article published = article(1L);
        published.setStatus("PUBLISHED");
        when(articleMapper.selectById(1L)).thenReturn(published);
        service.publicDetail(1L);
        verify(viewCountBuffer).increment(1L);
    }

    private static Article article(long id) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(AUTHOR);
        a.setStatus("DRAFT");
        a.setDeleted(0);
        a.setPinned(0);
        a.setViewCount(0L);
        a.setLikeCount(0L);
        a.setCommentCount(0);
        return a;
    }
}
