package cn.hengxing.inkpress.modules.graph;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleTag;
import cn.hengxing.inkpress.modules.article.entity.Tag;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.graph.entity.ArticleLink;
import cn.hengxing.inkpress.modules.graph.mapper.ArticleLinkMapper;
import cn.hengxing.inkpress.modules.graph.service.GraphService;
import cn.hengxing.inkpress.modules.stats.entity.VisitLog;
import cn.hengxing.inkpress.modules.stats.mapper.VisitLogMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图谱编排层单测（Mockito）：标签共现边构建与未发布过滤、手动关联置顶与标签重叠/访客
 * 偏好加权的排序语义、手动关联的作者与目标校验。
 */
class GraphOrchestrationTest {

    private ArticleMapper articleMapper;
    private ArticleTagMapper articleTagMapper;
    private TagMapper tagMapper;
    private ArticleLinkMapper articleLinkMapper;
    private VisitLogMapper visitLogMapper;
    private GraphService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
        TableInfoHelper.initTableInfo(assistant, ArticleTag.class);
        TableInfoHelper.initTableInfo(assistant, ArticleLink.class);
        TableInfoHelper.initTableInfo(assistant, VisitLog.class);
    }

    @BeforeEach
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        articleTagMapper = mock(ArticleTagMapper.class);
        tagMapper = mock(TagMapper.class);
        articleLinkMapper = mock(ArticleLinkMapper.class);
        visitLogMapper = mock(VisitLogMapper.class);
        service = new GraphService(articleMapper, articleTagMapper, tagMapper,
                articleLinkMapper, visitLogMapper);
        when(tagMapper.selectList(isNull())).thenReturn(List.of(
                tag(10L, "Java"), tag(11L, "Elasticsearch"), tag(12L, "Redis")));
    }

    private static Article publishedArticle(long id, String title, long views) {
        Article a = new Article();
        a.setId(id);
        a.setAuthorId(9L);
        a.setTitle(title);
        a.setStatus("PUBLISHED");
        a.setDeleted(0);
        a.setViewCount(views);
        return a;
    }

    private static ArticleTag membership(long articleId, long tagId) {
        return new ArticleTag(articleId, tagId);
    }

    private static Tag tag(long id, String name) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static ArticleLink link(long from, long to) {
        ArticleLink l = new ArticleLink();
        l.setArticleId(from);
        l.setTargetId(to);
        return l;
    }

    @Test
    void graphBuildsCooccurrenceLinksAndFiltersUnpublished() {
        when(articleMapper.selectList(any())).thenReturn(
                List.of(publishedArticle(1L, "文章一", 100), publishedArticle(2L, "文章二", 50)));
        // 文章 3 未发布（不在文章级 selectList 结果里），其挂载关系必须被过滤
        when(articleTagMapper.selectList(isNull())).thenReturn(List.of(
                membership(1, 10), membership(1, 11), membership(2, 10), membership(2, 12), membership(3, 10)));
        when(articleLinkMapper.selectList(isNull())).thenReturn(List.of(link(1, 2)));

        Map<String, Object> g = service.graph();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) g.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) g.get("links");
        assertEquals(5, nodes.size(), "2 文章节点 + 3 标签节点");
        assertTrue(nodes.stream().anyMatch(n -> "t:10".equals(n.get("id"))));
        assertTrue(links.stream().anyMatch(l -> "t:10".equals(l.get("source"))
                && "t:11".equals(l.get("target")) && Long.valueOf(1).equals(l.get("weight"))));
        assertTrue(links.stream().anyMatch(l -> "t:10".equals(l.get("source"))
                && "t:12".equals(l.get("target"))));
        assertTrue(links.stream().anyMatch(l -> "manual".equals(l.get("kind"))
                && "a:1".equals(l.get("source")) && "a:2".equals(l.get("target"))));
    }

    @Test
    void relatedRanksManualAboveSharedTagsAboveViews() {
        when(articleMapper.selectById(1L)).thenReturn(publishedArticle(1L, "当前文章", 10));
        // 候选：2（手动关联+标签重叠）、3（手动关联但无标签重叠）、4（无关联但浏览量极高）
        when(articleMapper.selectList(any())).thenReturn(List.of(
                publishedArticle(2L, "文章二", 500),
                publishedArticle(3L, "文章三", 50),
                publishedArticle(4L, "文章四", 9000)));
        // tagIdsOf 调用顺序确定：self → 候选2 → 候选3 → 候选4，按序给出各文章的标签关联
        when(articleTagMapper.selectList(any())).thenReturn(
                List.of(membership(1, 10), membership(1, 11)),
                List.of(membership(2, 10), membership(2, 11)),
                List.of(membership(3, 12)),
                List.of(membership(4, 10)));
        // 手动关联：1→2 与 3→1（双向）
        when(articleLinkMapper.selectList(any())).thenAnswer(inv -> List.of(link(1, 2), link(3, 1)));

        List<Map<String, Object>> related = service.related(1L, null, 3);

        assertEquals(List.of(2L, 3L, 4L),
                related.stream().map(m -> ((Number) m.get("id")).longValue()).toList(),
                "手动关联置顶（10700 > 10050），无关联的高浏览量文章（9100）排末位");
        assertEquals(2, ((Number) related.get(0).get("sharedTags")).intValue());
        assertEquals(true, related.get(0).get("manual"));
    }

    @Test
    void relatedBoostsByVisitorTagAffinity() {
        when(articleMapper.selectById(1L)).thenReturn(publishedArticle(1L, "当前文章", 10));
        // 两个候选同分（标签重叠 1 + 浏览量 100），仅访客近 30 天偏好区分先后
        when(articleMapper.selectList(any())).thenReturn(List.of(
                publishedArticle(2L, "候选一", 100), publishedArticle(3L, "候选二", 100)));
        // tagIdsOf 调用顺序确定：self → 访客读过的候选一（affinity 聚合）→ 候选一 → 候选二
        when(articleTagMapper.selectList(any())).thenReturn(
                List.of(membership(1, 10)),
                List.of(membership(2, 10), membership(2, 11)),
                List.of(membership(2, 10), membership(2, 11)),
                List.of(membership(3, 10)));
        when(articleLinkMapper.selectList(any())).thenAnswer(inv -> List.of());
        // 访客读过候选一 4 次（标签 10/11 各 +4）→ 候选一偏好加权更高
        List<VisitLog> visits = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            VisitLog v = new VisitLog();
            v.setVisitorId("visitor-abcdef");
            v.setArticleId(2L);
            visits.add(v);
        }
        when(visitLogMapper.selectList(any())).thenReturn(visits);

        List<Map<String, Object>> related = service.related(1L, "visitor-abcdef", 10);

        assertEquals(2L, ((Number) related.get(0).get("id")).longValue(), "访客偏好应把读过的标签候选提前");
    }

    @Test
    void relatedRejectsUnpublishedSelf() {
        Article draft = publishedArticle(9L, "草稿", 0);
        draft.setStatus("DRAFT");
        when(articleMapper.selectById(9L)).thenReturn(draft);
        assertEquals(2003, assertThrows(BizException.class,
                () -> service.related(9L, null, 5)).getCode());
    }

    @Test
    void addManualLinkValidatesOwnerTargetAndDuplicate() {
        Article own = publishedArticle(1L, "我的文章", 0);
        when(articleMapper.selectById(1L)).thenReturn(own);

        assertEquals(2601, assertThrows(BizException.class,
                () -> service.addManualLink(9L, 1L, 1L)).getCode(), "不能关联自身");
        assertEquals(2601, assertThrows(BizException.class,
                () -> service.addManualLink(9L, 1L, null)).getCode(), "目标非法");

        Article foreign = publishedArticle(2L, "别人的", 0);
        when(articleMapper.selectById(2L)).thenReturn(foreign);
        assertEquals(2603, assertThrows(BizException.class,
                () -> service.addManualLink(8L, 2L, 1L)).getCode(), "仅作者可管理关联");

        doThrow(new DuplicateKeyException("uk")).when(articleLinkMapper).insert(any(ArticleLink.class));
        assertEquals(2602, assertThrows(BizException.class,
                () -> service.addManualLink(9L, 1L, 2L)).getCode(), "重复关联");

        verify(articleLinkMapper).insert(any(ArticleLink.class));
    }
}
