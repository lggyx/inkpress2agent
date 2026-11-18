package cn.hengxing.inkpress.modules.stats;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.stats.mapper.OutboundClickMapper;
import cn.hengxing.inkpress.modules.stats.mapper.VisitLogMapper;
import cn.hengxing.inkpress.modules.stats.service.StatsService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 统计画像编排层单测（Mockito）：周热榜字段映射、管理端总览聚合与已删文章标题兜底。 */
class StatsOrchestrationTest {

    private VisitLogMapper visitLogMapper;
    private OutboundClickMapper outboundClickMapper;
    private ArticleMapper articleMapper;
    private StatsService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
    }

    @BeforeEach
    void setUp() {
        visitLogMapper = mock(VisitLogMapper.class);
        outboundClickMapper = mock(OutboundClickMapper.class);
        articleMapper = mock(ArticleMapper.class);
        service = new StatsService(visitLogMapper, outboundClickMapper, articleMapper);
    }

    private static Article hotArticle(long id, String title, long views, long likes) {
        Article a = new Article();
        a.setId(id);
        a.setTitle(title);
        a.setStatus("PUBLISHED");
        a.setDeleted(0);
        a.setViewCount(views);
        a.setLikeCount(likes);
        a.setPublishedAt(LocalDateTime.of(2026, 11, 10, 9, 0));
        return a;
    }

    @Test
    void hotArticlesMapsTopFiveFields() {
        when(articleMapper.selectList(any())).thenReturn(List.of(
                hotArticle(1L, "热文一", 1200, 34),
                hotArticle(2L, "热文二", 800, 12)));

        List<Map<String, Object>> hot = service.hotArticles(7);

        assertEquals(2, hot.size());
        assertEquals(1L, hot.get(0).get("id"));
        assertEquals("热文一", hot.get(0).get("title"));
        assertEquals(1200L, hot.get(0).get("viewCount"));
        assertEquals(34L, hot.get(0).get("likeCount"));
    }

    @Test
    void overviewAggregatesAndBackfillsDeletedArticleTitle() {
        when(visitLogMapper.selectCount(any())).thenReturn(7L);
        when(visitLogMapper.countUvSince(any())).thenReturn(3L);
        when(visitLogMapper.regionTop(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(Map.of("region", "华东", "pv", 4)));
        when(visitLogMapper.deviceTop(any())).thenReturn(List.of());
        when(visitLogMapper.dailyPv(any())).thenReturn(List.of());
        when(visitLogMapper.articleTop(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(Map.of("articleId", 1L, "pv", 5), Map.of("articleId", 2L, "pv", 2)));
        when(outboundClickMapper.hostTop(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(articleMapper.interactionTotals()).thenReturn(Map.of("likes", 1));
        // 文章 2 已删除（查不到）
        when(articleMapper.selectList(any())).thenReturn(List.of(hotArticle(1L, "热文一", 10, 1)));

        Map<String, Object> overview = service.overview(30);

        assertEquals(30, overview.get("days"));
        assertEquals(7L, overview.get("pv"));
        assertEquals(3L, overview.get("uv"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) overview.get("topArticles");
        assertEquals("热文一", top.get(0).get("title"));
        assertEquals("(已删除)", top.get(1).get("title"), "已删文章 ID 补标题时兜底");
        @SuppressWarnings("unchecked")
        Map<String, Object> interaction = (Map<String, Object>) overview.get("interaction");
        assertEquals(1, ((Number) interaction.get("likes")).intValue());
    }
}
