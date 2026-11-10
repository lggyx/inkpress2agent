package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleTag;
import cn.hengxing.inkpress.modules.article.entity.Tag;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.search.dto.SearchHit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索：BM25（IK 分词）+ dense_vector kNN（远程 Embedding），RRF(k=60) 融合；
 * ES 宕机或向量不可用时自动降级（LIKE 关键词 / 纯 BM25），结果标注 degraded。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int RRF_K = 60;
    private static final int FETCH_SIZE = 30;

    private final EsClient esClient;
    private final EmbeddingService embeddingService;
    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;

    public SearchService(EsClient esClient, EmbeddingService embeddingService,
                         ArticleMapper articleMapper, ArticleTagMapper articleTagMapper, TagMapper tagMapper) {
        this.esClient = esClient;
        this.embeddingService = embeddingService;
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        esClient.ensureIndex();
    }

    public boolean esUp() {
        return esClient.healthy();
    }

    // ---------- 索引维护 ----------

    public void indexArticle(Article article) {
        try {
            List<String> tags = tagsOf(article.getId());
            Map<String, Object> doc = new HashMap<>();
            doc.put("title", article.getTitle());
            doc.put("content", stripMd(article.getContentMd()));
            doc.put("tags", tags);
            doc.put("publishedAt", article.getPublishedAt() == null ? null : article.getPublishedAt().toString());
            List<Double> vector = embeddingService.embed(article.getTitle() + "\n" + article.getContentMd());
            if (vector != null && vector.size() == 1024) {
                doc.put("embedding", vector);
            }
            esClient.index(String.valueOf(article.getId()), doc);
        } catch (Exception e) {
            log.warn("index article {} failed: {}", article.getId(), e.getMessage());
        }
    }

    public void removeArticle(Long id) {
        try {
            esClient.delete(String.valueOf(id));
        } catch (Exception e) {
            log.warn("remove article {} from index failed: {}", id, e.getMessage());
        }
    }

    public int reindexAll() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED").eq(Article::getDeleted, 0));
        for (Article a : articles) {
            indexArticle(a);
        }
        return articles.size();
    }

    // ---------- 检索 ----------

    /** mode: bm25 / vector / hybrid（默认 hybrid） */
    public Map<String, Object> search(String keyword, String mode, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return Map.of("total", 0, "records", List.of(), "degraded", !esUp(), "mode", mode);
        }
        if (!esUp()) {
            return mysqlLikeSearch(keyword, page, size);
        }
        String effectiveMode = ("vector".equals(mode) || "hybrid".equals(mode)) ? mode : "bm25";
        if ("vector".equals(effectiveMode) && embeddingService.embed(keyword) == null) {
            effectiveMode = "bm25"; // 向量不可用降级
        }
        try {
            List<SearchHit> hits = switch (effectiveMode) {
                case "bm25" -> bm25(keyword);
                case "vector" -> vector(keyword);
                default -> hybrid(keyword);
            };
            int from = Math.max(0, (page - 1) * size);
            int to = Math.min(hits.size(), from + size);
            List<Map<String, Object>> records = new ArrayList<>();
            for (SearchHit h : hits.subList(from, to)) {
                records.add(h.toMap());
            }
            return Map.of("total", hits.size(), "records", records, "degraded", false, "mode", effectiveMode);
        } catch (Exception e) {
            log.warn("ES search failed, fallback to LIKE: {}", e.getMessage());
            return mysqlLikeSearch(keyword, page, size);
        }
    }

    private List<SearchHit> bm25(String keyword) throws Exception {
        String body = """
                {"size":%d,"query":{"multi_match":{"query":"%s","fields":["title^3","content"]}},
                 "highlight":{"fields":{"title":{},"content":{"fragment_size":120,"number_of_fragments":2}}}}
                """.formatted(FETCH_SIZE, escape(keyword));
        return parseHits(esClient.search(body));
    }

    private List<SearchHit> vector(String keyword) throws Exception {
        List<Double> v = embeddingService.embed(keyword);
        if (v == null) {
            return bm25(keyword);
        }
        StringBuilder vec = new StringBuilder();
        for (int i = 0; i < v.size(); i++) {
            vec.append(i == 0 ? "" : ",").append(v.get(i));
        }
        String body = """
                {"size":%d,"knn":{"field":"embedding","query_vector":[%s],"k":%d,"num_candidates":100}}
                """.formatted(FETCH_SIZE, vec, FETCH_SIZE);
        return parseHits(esClient.search(body));
    }

    private List<SearchHit> hybrid(String keyword) throws Exception {
        List<SearchHit> keywordHits = bm25(keyword);
        List<SearchHit> vectorHits = vector(keyword);
        // RRF 融合：score = Σ 1/(k + rank)
        Map<Long, SearchHit> merged = new LinkedHashMap<>();
        Map<Long, Double> score = new HashMap<>();
        applyRanks(merged, score, keywordHits);
        applyRanks(merged, score, vectorHits);
        List<SearchHit> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Double.compare(score.getOrDefault(b.id(), 0.0), score.getOrDefault(a.id(), 0.0)));
        return result;
    }

    private void applyRanks(Map<Long, SearchHit> merged, Map<Long, Double> score, List<SearchHit> hits) {
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            merged.putIfAbsent(h.id(), h);
            score.merge(h.id(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    private List<SearchHit> parseHits(JsonNode root) {
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode h : root.path("hits").path("hits")) {
            Long id = Long.valueOf(h.path("_id").asText());
            String title = h.path("_source").path("title").asText("");
            String highlight = "";
            JsonNode hl = h.path("highlight");
            if (hl.has("title") && hl.path("title").size() > 0) {
                highlight = hl.path("title").get(0).asText();
            } else if (hl.has("content") && hl.path("content").size() > 0) {
                highlight = hl.path("content").get(0).asText();
            }
            hits.add(new SearchHit(id, title, highlight));
        }
        return hits;
    }

    private Map<String, Object> mysqlLikeSearch(String keyword, int page, int size) {
        Page<Article> p = articleMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getStatus, Article::getDeleted)
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .and(w -> w.like(Article::getTitle, keyword).or().like(Article::getContentMd, keyword))
                .orderByDesc(Article::getPublishedAt));
        List<Map<String, Object>> records = new ArrayList<>();
        for (Article a : p.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("highlight", "");
            records.add(item);
        }
        return Map.of("total", p.getTotal(), "records", records, "degraded", true, "mode", "like");
    }

    private List<String> tagsOf(Long articleId) {
        List<ArticleTag> links = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> ids = links.stream().map(ArticleTag::getTagId).toList();
        List<String> tags = new ArrayList<>();
        for (Tag t : tagMapper.selectBatchIds(ids)) {
            tags.add(t.getName());
        }
        return tags;
    }

    private String stripMd(String md) {
        return md == null ? "" : md.replaceAll("[#>*`\\-\\[\\]()!]", " ");
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
