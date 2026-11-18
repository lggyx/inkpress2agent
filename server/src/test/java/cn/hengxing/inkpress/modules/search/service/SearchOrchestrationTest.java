package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检索编排层单测（Mockito）：chunk 聚合、RRF 融合次序、向量不可用降级、
 * ES 宕机/异常回退 LIKE、语料统计降级——EsClient 与 mapper 全 mock，不依赖 ES。
 */
class SearchOrchestrationTest {

    private EsClient esClient;
    private EmbeddingService embedding;
    private ChunkingService chunking;
    private ArticleMapper articleMapper;
    private ArticleTagMapper articleTagMapper;
    private SearchService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeAll
    static void initEntityMeta() {
        // 纯单测环境没有 MyBatis 容器，Lambda 查询的列缓存需手动注册
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Article.class);
        TableInfoHelper.initTableInfo(assistant, cn.hengxing.inkpress.modules.article.entity.ArticleTag.class);
        TableInfoHelper.initTableInfo(assistant, cn.hengxing.inkpress.modules.article.entity.Tag.class);
    }

    @BeforeEach
    void setUp() {
        esClient = mock(EsClient.class);
        embedding = mock(EmbeddingService.class);
        chunking = mock(ChunkingService.class);
        articleMapper = mock(ArticleMapper.class);
        articleTagMapper = mock(ArticleTagMapper.class);
        service = new SearchService(esClient, embedding, chunking,
                articleMapper, articleTagMapper, mock(TagMapper.class));
    }

    private static Article article(long id, String title) {
        Article a = new Article();
        a.setId(id);
        a.setTitle(title);
        a.setStatus("PUBLISHED");
        a.setDeleted(0);
        return a;
    }

    private JsonNode json(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("records");
    }

    @Test
    void blankKeywordReturnsEmptyWithoutQueryingEs() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        Map<String, Object> r = service.search("  ", "bm25", 1, 10);
        assertEquals(0, r.get("total"));
        assertTrue(records(r).isEmpty());
        assertEquals(false, r.get("degraded"));
        verify(esClient, never()).search(anyString());
        verify(esClient, never()).chunkSearch(anyString());
    }

    @Test
    void esDownFallsBackToMysqlLike() throws Exception {
        when(esClient.healthy()).thenReturn(false);
        Page<Article> p = new Page<>(1, 10);
        p.setTotal(2);
        p.setRecords(new ArrayList<>(List.of(article(1L, "标题一"), article(2L, "标题二"))));
        doReturn(p).when(articleMapper).selectPage(any(), any());

        Map<String, Object> r = service.search("向量检索", "hybrid", 1, 10);

        assertEquals("like", r.get("mode"));
        assertEquals(true, r.get("degraded"));
        assertEquals(2L, r.get("total"));
        assertEquals(1L, records(r).get(0).get("id"));
        verify(esClient, never()).search(anyString());
    }

    @Test
    void chunkBm25AggregatesByArticleKeepingFirstHighlight() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        when(esClient.chunkCount()).thenReturn(120L);
        when(esClient.chunkSearch(anyString())).thenReturn(json("{\"hits\":{\"hits\":[" +
                "{\"_id\":\"89-0\",\"_source\":{\"title\":\"chunk 索引设计\"},\"highlight\":{\"content\":[\"…<em>chunk</em> 检索单元…\"]}}," +
                "{\"_id\":\"89-2\",\"_source\":{\"title\":\"chunk 索引设计\"},\"highlight\":{\"content\":[\"第二处命中\"]}}," +
                "{\"_id\":\"90-1\",\"_source\":{\"title\":\"RRF 融合\"},\"highlight\":{\"title\":[\"<em>RRF</em> 融合\"]}}]}}"));

        Map<String, Object> r = service.search("chunk", "bm25", 1, 10);

        assertEquals("bm25", r.get("mode"));
        assertEquals(false, r.get("degraded"));
        assertEquals(2, r.get("total"));
        List<Map<String, Object>> recs = records(r);
        assertEquals(89L, recs.get(0).get("id"));
        assertEquals("…<em>chunk</em> 检索单元…", recs.get(0).get("highlight"), "同一文章多次命中保留首个（最佳块）");
        assertEquals(90L, recs.get(1).get("id"));
        assertEquals("<em>RRF</em> 融合", recs.get(1).get("highlight"));
        verify(embedding, never()).embed(anyString());
        verify(esClient, never()).search(anyString());
    }

    @Test
    void chunkHybridMergesKeywordAndVectorArmsWithRrf() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        when(esClient.chunkCount()).thenReturn(50L);
        // 关键词臂排名：A 第 1，B 第 2；向量臂：B 第 1，C 第 2
        when(esClient.chunkSearch(anyString())).thenReturn(json("{\"hits\":{\"hits\":[" +
                "{\"_id\":\"1-0\",\"_source\":{\"title\":\"文章A\"}}," +
                "{\"_id\":\"2-0\",\"_source\":{\"title\":\"文章B\"}}]}}"));
        when(embedding.embed(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(esClient.search(anyString())).thenReturn(json("{\"hits\":{\"hits\":[" +
                "{\"_id\":\"2\",\"_source\":{\"title\":\"文章B\"}}," +
                "{\"_id\":\"3\",\"_source\":{\"title\":\"文章C\"}}]}}"));

        Map<String, Object> r = service.search("融合", "hybrid", 1, 10);

        assertEquals("hybrid", r.get("mode"));
        assertEquals(false, r.get("degraded"));
        assertEquals(3, r.get("total"));
        // RRF(k=60)：B=1/62+1/61 ≈ 0.0325 > A=1/61 ≈ 0.0164 > C=1/62 ≈ 0.0161
        List<Map<String, Object>> recs = records(r);
        assertEquals(2L, recs.get(0).get("id"), "两臂都命中的文章应排第一");
        assertEquals(1L, recs.get(1).get("id"));
        assertEquals(3L, recs.get(2).get("id"));
    }

    @Test
    void vectorModeFallsBackToChunkBm25WhenEmbeddingUnavailable() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        when(esClient.chunkCount()).thenReturn(50L);
        when(embedding.embed(anyString())).thenReturn(null);
        when(esClient.chunkSearch(anyString())).thenReturn(json("{\"hits\":{\"hits\":[" +
                "{\"_id\":\"5-0\",\"_source\":{\"title\":\"标题五\"}}]}}"));

        Map<String, Object> r = service.search("问题", "vector", 1, 10);

        assertEquals("bm25", r.get("mode"), "向量不可用降级后应如实标注实际执行的模式");
        assertEquals(false, r.get("degraded"));
        assertEquals(1, r.get("total"));
        verify(esClient, never()).search(anyString());
    }

    @Test
    void esFailureDuringSearchFallsBackToLike() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        when(esClient.chunkCount()).thenReturn(50L);
        doThrow(new IOException("连接被重置")).when(esClient).chunkSearch(anyString());
        Page<Article> p = new Page<>(1, 10);
        p.setTotal(1);
        p.setRecords(new ArrayList<>(List.of(article(9L, "标题九"))));
        doReturn(p).when(articleMapper).selectPage(any(), any());

        Map<String, Object> r = service.search("关键词", "bm25", 1, 10);

        assertEquals("like", r.get("mode"));
        assertEquals(true, r.get("degraded"));
        assertEquals(1L, r.get("total"));
    }

    @Test
    void corpusStatsReadsEsWhenHealthy() throws Exception {
        when(esClient.healthy()).thenReturn(true);
        when(esClient.docCount()).thenReturn(49L);
        when(esClient.chunkCount()).thenReturn(4806L);

        Map<String, Object> m = service.corpusStats();

        assertEquals(49L, m.get("articles"));
        assertEquals(4806L, m.get("chunks"));
        assertEquals(false, m.get("degraded"));
    }

    @Test
    void corpusStatsFallsBackToMysqlWhenEsDown() throws Exception {
        when(esClient.healthy()).thenReturn(false);
        when(articleMapper.selectCount(any())).thenReturn(49L);

        Map<String, Object> m = service.corpusStats();

        assertEquals(49L, m.get("articles"), "ES 宕机时文章数走 MySQL");
        assertEquals(-1L, m.get("chunks"), "单元数无法统计标 -1");
        assertEquals(true, m.get("degraded"));
    }

    // ---------- 索引写入与向量维度守门 ----------

    @Test
    void initCreatesIndexWithConfiguredDims() {
        when(embedding.dims()).thenReturn(2048);
        service.init();
        verify(esClient).ensureIndex(2048);
        verify(esClient).ensureChunkIndex();
    }

    private Article publishedArticle(long id, String title) {
        Article a = article(id, title);
        a.setContentMd("# 标题\n\n正文内容。");
        return a;
    }

    @Test
    void indexArticleIncludesEmbeddingWhenDimsMatch() throws Exception {
        // 维度守门：索引 mapping 与向量同为 3 维即写入
        when(esClient.indexDims()).thenReturn(3);
        when(embedding.embed(anyString())).thenReturn(new ArrayList<>(java.util.Collections.nCopies(3, 0.5)));
        when(embedding.dims()).thenReturn(3);
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        when(chunking.chunk(anyString())).thenReturn(List.of("检索单元一"));

        service.indexArticle(publishedArticle(1L, "标题一"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> doc = ArgumentCaptor.forClass(Map.class);
        verify(esClient).index(eq("1"), doc.capture());
        assertTrue(doc.getValue().containsKey("embedding"), "维度与索引 mapping 一致时应写入向量");
        verify(esClient).chunkDeleteByArticle(1L);
        verify(esClient).chunkBulkIndex(any());
    }

    @Test
    void indexArticleDropsEmbeddingWithWarnWhenDimsMismatch() throws Exception {
        when(esClient.indexDims()).thenReturn(768);
        when(embedding.embed(anyString())).thenReturn(new ArrayList<>(java.util.Collections.nCopies(1024, 0.1)));
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        when(chunking.chunk(anyString())).thenReturn(List.of("检索单元一"));

        service.indexArticle(publishedArticle(2L, "标题二"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> doc = ArgumentCaptor.forClass(Map.class);
        verify(esClient).index(eq("2"), doc.capture());
        assertFalse(doc.getValue().containsKey("embedding"), "维度不符必须跳过向量，避免整篇被 ES 拒收");
        // 文本仍要正常入索引（降级不是丢文档）
        assertEquals("标题二", doc.getValue().get("title"));
    }

    @Test
    void indexArticleSkipsVectorWhenSlotUnconfigured() throws Exception {
        when(embedding.embed(anyString())).thenReturn(null);
        when(articleTagMapper.selectList(any())).thenReturn(List.of());
        when(chunking.chunk(anyString())).thenReturn(List.of());

        service.indexArticle(publishedArticle(3L, "标题三"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> doc = ArgumentCaptor.forClass(Map.class);
        verify(esClient).index(eq("3"), doc.capture());
        assertFalse(doc.getValue().containsKey("embedding"));
        // 空块列表不应触发 bulk 写入
        verify(esClient, never()).chunkBulkIndex(any());
    }
}
