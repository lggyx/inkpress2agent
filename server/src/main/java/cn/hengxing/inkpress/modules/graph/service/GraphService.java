package cn.hengxing.inkpress.modules.graph.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleTag;
import cn.hengxing.inkpress.modules.article.entity.Tag;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.graph.entity.ArticleLink;
import cn.hengxing.inkpress.modules.graph.mapper.ArticleLinkMapper;
import cn.hengxing.inkpress.modules.stats.entity.VisitLog;
import cn.hengxing.inkpress.modules.stats.mapper.VisitLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * M8 图谱：边 = 标签共现（同篇文章的两个标签）+ 手动关联（article_link）。
 * 相关阅读 = 标签重叠为主、手动关联置顶；携带 visitorId 时按其近 30 天阅读偏好重排序（候选集不变）。
 */
@Service
public class GraphService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final ArticleLinkMapper articleLinkMapper;
    private final VisitLogMapper visitLogMapper;

    public GraphService(ArticleMapper articleMapper, ArticleTagMapper articleTagMapper,
                        TagMapper tagMapper, ArticleLinkMapper articleLinkMapper,
                        VisitLogMapper visitLogMapper) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
        this.articleLinkMapper = articleLinkMapper;
        this.visitLogMapper = visitLogMapper;
    }

    /** 力导向图谱数据：文章节点 + 标签节点，共现/手动两类边 */
    public Map<String, Object> graph() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .select(Article::getId, Article::getTitle, Article::getViewCount));
        List<ArticleTag> memberships = articleTagMapper.selectList(null);
        Map<Long, String> tagNames = tagMapper.selectList(null).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));

        // 只保留已发布文章的挂载关系
        Set<Long> publishedIds = articles.stream().map(Article::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> tagsOfArticle = new HashMap<>();
        Map<Long, Integer> tagUsage = new TreeMap<>();
        for (ArticleTag mt : memberships) {
            if (!publishedIds.contains(mt.getArticleId())) {
                continue;
            }
            tagsOfArticle.computeIfAbsent(mt.getArticleId(), k -> new ArrayList<>()).add(mt.getTagId());
            tagUsage.merge(mt.getTagId(), 1, Integer::sum);
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Article a : articles) {
            nodes.add(node("a:" + a.getId(), "article", a.getTitle(),
                    a.getViewCount() == null ? 0 : a.getViewCount()));
        }
        for (Map.Entry<Long, Integer> e : tagUsage.entrySet()) {
            nodes.add(node("t:" + e.getKey(), "tag",
                    tagNames.getOrDefault(e.getKey(), "#" + e.getKey()), e.getValue()));
        }

        // 标签共现：同篇文章内标签两两配对计数（tag id 升序排列保证无向键唯一）
        Map<String, Integer> cooccur = new TreeMap<>();
        for (List<Long> tags : tagsOfArticle.values()) {
            List<Long> sorted = tags.stream().distinct().sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    cooccur.merge(sorted.get(i) + ":" + sorted.get(j), 1, Integer::sum);
                }
            }
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cooccur.entrySet()) {
            String[] ids = e.getKey().split(":");
            links.add(link("t:" + ids[0], "t:" + ids[1], e.getValue(), "cooccur"));
        }
        for (ArticleLink l : articleLinkMapper.selectList(null)) {
            if (publishedIds.contains(l.getArticleId()) && publishedIds.contains(l.getTargetId())) {
                links.add(link("a:" + l.getArticleId(), "a:" + l.getTargetId(), 1, "manual"));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", nodes);
        out.put("links", links);
        return out;
    }

    /** 相关阅读：候选 = 已发布文章（除自身）；手动关联 > 标签重叠 > 访客偏好 > 浏览量 */
    public List<Map<String, Object>> related(Long articleId, String visitorId, int limit) {
        Article self = articleMapper.selectById(articleId);
        if (self == null || !"PUBLISHED".equals(self.getStatus()) || self.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在或未发布");
        }
        int n = Math.min(Math.max(limit, 1), 20);
        Set<Long> selfTags = new HashSet<>(tagIdsOf(articleId));
        Set<Long> manualIds = manualNeighborIds(articleId);
        Map<Long, Integer> affinity = visitorTagAffinity(visitorId);

        List<Article> candidates = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .ne(Article::getId, articleId));
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Article c : candidates) {
            Set<Long> tags = new HashSet<>(tagIdsOf(c.getId()));
            long shared = tags.stream().filter(selfTags::contains).count();
            long affinityScore = tags.stream().mapToInt(t -> affinity.getOrDefault(t, 0)).sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("title", c.getTitle());
            item.put("viewCount", c.getViewCount());
            item.put("sharedTags", shared);
            item.put("manual", manualIds.contains(c.getId()));
            item.put("_score", (manualIds.contains(c.getId()) ? 10_000 : 0)
                    + shared * 100 + affinityScore + c.getViewCount());
            scored.add(item);
        }
        scored.sort(Comparator.comparingLong(m -> -(Long) m.get("_score")));
        scored.forEach(m -> m.remove("_score"));
        return scored.subList(0, Math.min(n, scored.size()));
    }

    /** 手动关联：仅文章作者可操作，目标文章须存在且非自身 */
    public void addManualLink(Long userId, Long articleId, Long targetId) {
        Article self = mustOwned(userId, articleId);
        if (targetId == null || targetId.equals(articleId)) {
            throw BizException.of(2601, "关联目标非法");
        }
        Article target = articleMapper.selectById(targetId);
        if (target == null || target.getDeleted() == 1) {
            throw BizException.of(2601, "关联目标文章不存在");
        }
        ArticleLink link = new ArticleLink();
        link.setArticleId(self.getId());
        link.setTargetId(targetId);
        link.setCreatedBy(userId);
        try {
            articleLinkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            throw BizException.of(2602, "该关联已存在");
        }
    }

    public void removeManualLink(Long articleId, Long targetId) {
        articleLinkMapper.delete(new LambdaQueryWrapper<ArticleLink>()
                .eq(ArticleLink::getArticleId, articleId)
                .eq(ArticleLink::getTargetId, targetId));
    }

    private Article mustOwned(Long userId, Long articleId) {
        Article a = articleMapper.selectById(articleId);
        if (a == null || a.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在");
        }
        if (!a.getAuthorId().equals(userId)) {
            throw BizException.of(2603, "仅文章作者可管理关联");
        }
        return a;
    }

    private Set<Long> tagIdsOf(Long articleId) {
        return articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .eq(ArticleTag::getArticleId, articleId))
                .stream().map(ArticleTag::getTagId).collect(Collectors.toSet());
    }

    /** 双向手动邻居 */
    private Set<Long> manualNeighborIds(Long articleId) {
        Set<Long> out = new HashSet<>();
        for (ArticleLink l : articleLinkMapper.selectList(new LambdaQueryWrapper<ArticleLink>()
                .eq(ArticleLink::getArticleId, articleId))) {
            out.add(l.getTargetId());
        }
        for (ArticleLink l : articleLinkMapper.selectList(new LambdaQueryWrapper<ArticleLink>()
                .eq(ArticleLink::getTargetId, articleId))) {
            out.add(l.getArticleId());
        }
        return out;
    }

    /** 访客近 30 天阅读偏好：读过的文章 → 标签出现次数（候选集不变，仅用于重排序） */
    private Map<Long, Integer> visitorTagAffinity(String visitorId) {
        if (visitorId == null || visitorId.length() < 8) {
            return Map.of();
        }
        List<VisitLog> logs = visitLogMapper.selectList(new LambdaQueryWrapper<VisitLog>()
                .eq(VisitLog::getVisitorId, visitorId)
                .isNotNull(VisitLog::getArticleId)
                .ge(VisitLog::getCreatedAt, LocalDateTime.now().minusDays(30))
                .last("LIMIT 200"));
        if (logs.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> articleWeights = new HashMap<>();
        for (VisitLog log : logs) {
            articleWeights.merge(log.getArticleId(), 1, Integer::sum);
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : articleWeights.entrySet()) {
            for (Long tagId : tagIdsOf(e.getKey())) {
                out.merge(tagId, Math.min(e.getValue(), 5), Integer::sum);
            }
        }
        return out;
    }

    private Map<String, Object> node(String id, String type, String name, long weight) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("name", name);
        n.put("weight", weight);
        return n;
    }

    private Map<String, Object> link(String source, String target, long weight, String kind) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("source", source);
        l.put("target", target);
        l.put("weight", weight);
        l.put("kind", kind);
        return l;
    }
}
