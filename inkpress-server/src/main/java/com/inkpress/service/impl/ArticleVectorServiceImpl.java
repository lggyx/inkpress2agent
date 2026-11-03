package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.ai.EmbeddingProvider;
import com.inkpress.common.Constants;
import com.inkpress.entity.Article;
import com.inkpress.entity.ArticleTag;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.mapper.ArticleTagMapper;
import com.inkpress.mapper.CategoryMapper;
import com.inkpress.service.ArticleVectorService;
import com.inkpress.vo.ArticleVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义向量服务实现。
 * 设计要点：
 * 1. 向量存 Redis（inkpress:emb:{id}），语料规模（个人博客数百篇）下全内存余弦计算毫秒级完成，
 *    无需引入独立向量数据库；
 * 2. 相关文章 = 语义相似度（0.85 权重）+ 标签重合度（0.15 权重）的混合排序；
 * 3. 启动时异步全量重建，发布/更新时增量索引，保证索引最终一致。
 */
@Slf4j
@Service
public class ArticleVectorServiceImpl implements ArticleVectorService {

    private static final String KEY_PREFIX = "inkpress:emb:";
    private static final Duration KEY_TTL = Duration.ofDays(30);
    /** 拼入向量的正文字符上限，控制成本与时延 */
    private static final int CONTENT_SNIPPET = 2000;

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingProvider embeddingProvider;

    public ArticleVectorServiceImpl(ArticleMapper articleMapper,
                                    ArticleTagMapper articleTagMapper,
                                    CategoryMapper categoryMapper,
                                    StringRedisTemplate stringRedisTemplate,
                                    ObjectMapper objectMapper,
                                    EmbeddingProvider embeddingProvider) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.categoryMapper = categoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.embeddingProvider = embeddingProvider;
    }

    /** 启动后异步全量重建索引，不阻塞应用启动 */
    @PostConstruct
    public void init() {
        rebuildAllAsync();
    }

    @Async("taskExecutor")
    public void rebuildAllAsync() {
        try {
            rebuildAll();
        } catch (Exception e) {
            log.warn("向量索引全量重建失败（不影响主流程）", e);
        }
    }

    @Override
    public void rebuildAll() {
        long start = System.currentTimeMillis();
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSummary,
                        Article::getContent, Article::getStatus)
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED));
        int indexed = 0;
        for (Article article : articles) {
            try {
                indexArticle(article);
                indexed++;
            } catch (Exception e) {
                log.warn("文章 {} 向量化失败: {}", article.getId(), e.getMessage());
            }
        }
        log.info("向量索引重建完成：{} 篇，耗时 {} ms，实现 {}",
                indexed, System.currentTimeMillis() - start, embeddingProvider.name());
    }

    @Override
    public void indexArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !Constants.ARTICLE_PUBLISHED.equals(article.getStatus())) {
            stringRedisTemplate.delete(KEY_PREFIX + articleId);
            return;
        }
        indexArticle(article);
    }

    private void indexArticle(Article article) {
        String text = article.getTitle() + "\n" + nullToEmpty(article.getSummary()) + "\n"
                + truncate(nullToEmpty(article.getContent()), CONTENT_SNIPPET);
        double[] vector = embeddingProvider.embed(text);
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + article.getId(),
                    objectMapper.writeValueAsString(vector), KEY_TTL);
        } catch (Exception e) {
            log.warn("文章 {} 向量写入 Redis 失败", article.getId(), e);
        }
    }

    @Override
    public List<ArticleVO> related(Long articleId, int topK) {
        double[] base = loadVector(articleId);
        if (base == null) {
            // 当前文章尚未索引：即时向量化一次
            Article current = articleMapper.selectById(articleId);
            if (current == null) {
                return List.of();
            }
            indexArticle(current);
            base = loadVector(articleId);
            if (base == null) {
                return List.of();
            }
        }
        Set<Long> selfTags = tagSet(articleId);
        List<Article> candidates = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSummary, Article::getCover,
                        Article::getViews, Article::getLikes, Article::getIsTop, Article::getStatus,
                        Article::getCategoryId, Article::getPublishedAt, Article::getCreatedAt)
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED)
                .ne(Article::getId, articleId));

        record Scored(Article article, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Article candidate : candidates) {
            double[] vector = loadVector(candidate.getId());
            if (vector == null || vector.length != base.length) {
                continue;
            }
            double semantic = cosine(base, vector);
            double overlap = tagOverlap(selfTags, candidate.getId());
            scored.add(new Scored(candidate, semantic * 0.85 + overlap * 0.15));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<ArticleVO> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            Article article = scored.get(i).article();
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(article, vo);
            vo.setFirstImage(null);
            if (article.getCategoryId() != null) {
                var category = categoryMapper.selectById(article.getCategoryId());
                vo.setCategoryName(category == null ? null : category.getName());
            }
            vo.setTags(articleTagMapper.selectTagNames(article.getId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> semanticSearch(String query, int topK) {
        double[] queryVector = embeddingProvider.embed(query);
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSummary, Article::getCover,
                        Article::getViews, Article::getLikes, Article::getIsTop, Article::getStatus,
                        Article::getCategoryId, Article::getPublishedAt, Article::getCreatedAt)
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Article article : articles) {
            double[] vector = loadVector(article.getId());
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = cosine(queryVector, vector);
            if (score < 0.02) {
                continue;
            }
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(article, vo);
            vo.setFirstImage(null);
            if (article.getCategoryId() != null) {
                var category = categoryMapper.selectById(article.getCategoryId());
                vo.setCategoryName(category == null ? null : category.getName());
            }
            vo.setTags(articleTagMapper.selectTagNames(article.getId()));
            Map<String, Object> item = new HashMap<>();
            item.put("article", vo);
            item.put("score", Math.round(score * 1000) / 1000.0);
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        return result.subList(0, Math.min(topK, result.size()));
    }

    @Override
    public String providerName() {
        return embeddingProvider.name();
    }

    private double[] loadVector(Long articleId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + articleId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<Long> tagSet(Long articleId) {
        List<ArticleTag> relations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        Set<Long> tags = new HashSet<>();
        relations.forEach(r -> tags.add(r.getTagId()));
        return tags;
    }

    private double tagOverlap(Set<Long> baseTags, Long otherId) {
        if (baseTags.isEmpty()) {
            return 0;
        }
        Set<Long> other = tagSet(otherId);
        if (other.isEmpty()) {
            return 0;
        }
        long common = baseTags.stream().filter(other::contains).count();
        return (double) common / Math.max(baseTags.size(), other.size());
    }

    /** 余弦相似度（向量已 L2 归一化时等价于点积，此处仍按通用公式实现） */
    private double cosine(double[] a, double[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double norm = Math.sqrt(normA) * Math.sqrt(normB);
        return norm > 0 ? dot / norm : 0;
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
