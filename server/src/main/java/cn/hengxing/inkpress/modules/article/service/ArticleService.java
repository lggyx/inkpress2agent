package cn.hengxing.inkpress.modules.article.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.dto.ArticleResponse;
import cn.hengxing.inkpress.modules.article.dto.ArticleUpsertRequest;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleTag;
import cn.hengxing.inkpress.modules.article.entity.Category;
import cn.hengxing.inkpress.modules.article.entity.Tag;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.ArticleTagMapper;
import cn.hengxing.inkpress.modules.article.mapper.CategoryMapper;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.article.support.MarkdownRenderer;
import cn.hengxing.inkpress.modules.article.support.ViewCountBuffer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final MarkdownRenderer markdownRenderer;
    private final ViewCountBuffer viewCountBuffer;
    private final cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService;
    private final cn.hengxing.inkpress.modules.search.service.SearchIndexPublisher searchIndexPublisher;
    private final cn.hengxing.inkpress.modules.review.service.ReviewService reviewService;

    public ArticleService(ArticleMapper articleMapper, ArticleTagMapper articleTagMapper,
                          TagMapper tagMapper, CategoryMapper categoryMapper,
                          MarkdownRenderer markdownRenderer, ViewCountBuffer viewCountBuffer,
                          cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService,
                          cn.hengxing.inkpress.modules.search.service.SearchIndexPublisher searchIndexPublisher,
                          cn.hengxing.inkpress.modules.review.service.ReviewService reviewService) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
        this.categoryMapper = categoryMapper;
        this.markdownRenderer = markdownRenderer;
        this.viewCountBuffer = viewCountBuffer;
        this.notificationService = notificationService;
        this.searchIndexPublisher = searchIndexPublisher;
        this.reviewService = reviewService;
    }

    /** 创建或更新草稿；博主本人稿件可直接发布，agent 来稿强制 PENDING_REVIEW */
    @Transactional
    public ArticleResponse upsert(Long authorId, ArticleUpsertRequest req, boolean agentSource) {
        Article article;
        if (req.id() == null) {
            article = new Article();
            article.setAuthorId(authorId);
            article.setSource(agentSource ? "AGENT" : "HUMAN");
            article.setStatus("DRAFT");
            article.setPinned(0);
            article.setDeleted(0);
            article.setViewCount(0L);
            article.setLikeCount(0L);
            article.setCommentCount(0);
        } else {
            article = mustGet(req.id());
            if (!article.getAuthorId().equals(authorId)) {
                throw BizException.of(2001, "无权操作该文章");
            }
        }
        article.setCategoryId(req.categoryId());
        article.setTitle(req.title());
        article.setContentMd(req.contentMd());
        article.setContentHtml(markdownRenderer.render(req.contentMd()));
        article.setCoverUrl(req.coverUrl());
        article.setSeoDescription(req.seoDescription());
        article.setGeoSummary(req.geoSummary());

        boolean publish = "PUBLISH".equals(req.action());
        if (publish) {
            // agent 来稿不允许直接发布，强制进入人工复审队列
            if ("AGENT".equals(article.getSource()) || agentSource) {
                article.setStatus("PENDING_REVIEW");
            } else {
                // M9 低分闸口：最新 AI 评审总分低于阈值需显式确认
                Integer score = reviewService.latestScore(article.getId());
                if (score != null && score < cn.hengxing.inkpress.modules.review.service.ReviewService.LOW_SCORE_THRESHOLD
                        && !Boolean.TRUE.equals(req.confirmLowScore())) {
                    throw BizException.of(2005, "AI 评审分数偏低（" + score + " 分），请阅读评审报告后确认发布");
                }
                article.setStatus("PUBLISHED");
                if (article.getPublishedAt() == null) {
                    article.setPublishedAt(LocalDateTime.now());
                }
            }
        } else if ("DRAFT".equals(article.getStatus()) || "REJECTED".equals(article.getStatus())) {
            article.setStatus("DRAFT");
        }
        // PENDING_REVIEW / PUBLISHED 稿件的再编辑保持原状态（复审中/已发布）

        if (req.id() == null) {
            articleMapper.insert(article);
        } else {
            articleMapper.updateById(article);
        }
        replaceTags(article.getId(), req.tags());
        if ("PUBLISHED".equals(article.getStatus())) {
            // 索引同步走 RabbitMQ（事务提交后投递）；定时对账兜底最终一致
            searchIndexPublisher.publishUpsert(article.getId());
        }
        return toResponse(article, req.tags());
    }

    @PreAuthorize("hasRole('BLOGGER')")
    @Transactional
    public void review(Long articleId, boolean pass, Long reviewerId) {
        Article article = mustGet(articleId);
        if (!"PENDING_REVIEW".equals(article.getStatus())) {
            throw BizException.of(2002, "该文章不在待复审状态");
        }
        article.setStatus(pass ? "PUBLISHED" : "REJECTED");
        if (pass && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        if (pass) {
            searchIndexPublisher.publishUpsert(article.getId());
        }
        notificationService.notifyUser(article.getAuthorId(),
                cn.hengxing.inkpress.modules.messaging.entity.Notification.TYPE_REVIEW,
                "稿件复审结果：" + (pass ? "已通过并发布" : "被退回"),
                "《" + article.getTitle() + "》" + (pass ? " 已通过人工复审，现已发布。" : " 未通过人工复审，请在博主端修改后重新提交。"),
                "/studio/articles");
    }

    @PreAuthorize("hasRole('BLOGGER')")
    public IPage<ArticleResponse> studioPage(int page, int size, String status) {
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>()
                .eq(Article::getDeleted, 0)
                .eq(status != null && !status.isBlank(), Article::getStatus, status)
                .orderByDesc(Article::getPinned)
                .orderByDesc(Article::getUpdatedAt);
        return articleMapper.selectPage(Page.of(page, size), qw).convert(a -> toResponse(a, null));
    }

    public IPage<ArticleResponse> publicPage(int page, int size, Long categoryId) {
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                .orderByDesc(Article::getPinned)
                .orderByDesc(Article::getPublishedAt);
        IPage<Article> p = articleMapper.selectPage(Page.of(page, size), qw);
        // 注意：IPage.convert 会原地替换 records（映射为 null 会让 getRecords 全变 null），这里手工组装响应页
        Page<ArticleResponse> out = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(toResponses(p.getRecords()));
        return out;
    }

    /** 列表页批量装配：整页只发 3 条聚合查询（分类/文章-标签关联/标签），消除逐行 N+1 */
    private List<ArticleResponse> toResponses(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        // 1) 分类批量
        List<Long> categoryIds = articles.stream().map(Article::getCategoryId)
                .filter(id -> id != null).distinct().toList();
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName));
        // 2) 文章-标签关联批量
        List<Long> articleIds = articles.stream().map(Article::getId).toList();
        List<ArticleTag> links = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getArticleId, articleIds));
        Map<Long, List<Long>> tagIdsByArticle = new java.util.LinkedHashMap<>();
        for (ArticleTag link : links) {
            tagIdsByArticle.computeIfAbsent(link.getArticleId(), k -> new ArrayList<>()).add(link.getTagId());
        }
        // 3) 标签批量
        List<Long> allTagIds = links.stream().map(ArticleTag::getTagId).distinct().toList();
        Map<Long, String> tagNames = allTagIds.isEmpty() ? Map.of()
                : tagMapper.selectBatchIds(allTagIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Tag::getId, Tag::getName));

        List<ArticleResponse> out = new ArrayList<>(articles.size());
        for (Article a : articles) {
            String categoryName = a.getCategoryId() == null ? null : categoryNames.get(a.getCategoryId());
            List<String> tags = tagIdsByArticle.getOrDefault(a.getId(), List.of()).stream()
                    .map(id -> tagNames.getOrDefault(id, ""))
                    .filter(s -> !s.isBlank())
                    .toList();
            out.add(new ArticleResponse(a.getId(), a.getCategoryId(), categoryName, a.getTitle(),
                    a.getContentMd(), a.getContentHtml(), a.getCoverUrl(), a.getSeoDescription(),
                    a.getGeoSummary(), a.getStatus(), a.getSource(), a.getPinned() == 1,
                    a.getViewCount(), a.getLikeCount(), a.getCommentCount(), tags,
                    a.getPublishedAt(), a.getUpdatedAt()));
        }
        return out;
    }

    public ArticleResponse publicDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus()) || article.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在或未发布");
        }
        viewCountBuffer.increment(id);
        return toResponse(article, tagsOf(id));
    }

    @PreAuthorize("hasRole('BLOGGER')")
    public ArticleResponse studioDetail(Long id, Long authorId) {
        Article article = mustGet(id);
        if (!article.getAuthorId().equals(authorId)) {
            throw BizException.of(2001, "无权操作该文章");
        }
        return toResponse(article, tagsOf(id));
    }

    @PreAuthorize("hasRole('BLOGGER')")
    @Transactional
    public void setPinned(Long id, boolean pinned) {
        Article article = mustGet(id);
        article.setPinned(pinned ? 1 : 0);
        articleMapper.updateById(article);
    }

    @PreAuthorize("hasRole('BLOGGER')")
    @Transactional
    public void offline(Long id) {
        Article article = mustGet(id);
        article.setStatus("OFFLINE");
        articleMapper.updateById(article);
        searchIndexPublisher.publishDelete(id);
    }

    @PreAuthorize("hasRole('BLOGGER')")
    @Transactional
    public void softDelete(Long id) {
        Article article = mustGet(id);
        article.setDeleted(1);
        articleMapper.updateById(article);
        searchIndexPublisher.publishDelete(id);
    }

    public Article mustGet(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在");
        }
        return article;
    }

    private void replaceTags(Long articleId, List<String> tags) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (tags == null) {
            return;
        }
        for (String name : tags.stream().distinct().toList()) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tagMapper.insert(tag);
            }
            articleTagMapper.insert(new ArticleTag(articleId, tag.getId()));
        }
    }

    private List<String> tagsOf(Long articleId) {
        List<ArticleTag> links = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> ids = links.stream().map(ArticleTag::getTagId).toList();
        List<String> result = new ArrayList<>();
        for (Tag t : tagMapper.selectBatchIds(ids)) {
            result.add(t.getName());
        }
        return result;
    }

    private ArticleResponse toResponse(Article a, List<String> tags) {
        String categoryName = null;
        if (a.getCategoryId() != null) {
            Category c = categoryMapper.selectById(a.getCategoryId());
            categoryName = c == null ? null : c.getName();
        }
        List<String> finalTags = tags == null ? tagsOf(a.getId()) : tags;
        return new ArticleResponse(a.getId(), a.getCategoryId(), categoryName, a.getTitle(),
                a.getContentMd(), a.getContentHtml(), a.getCoverUrl(), a.getSeoDescription(),
                a.getGeoSummary(), a.getStatus(), a.getSource(), a.getPinned() == 1,
                a.getViewCount(), a.getLikeCount(), a.getCommentCount(), finalTags,
                a.getPublishedAt(), a.getUpdatedAt());
    }
}
