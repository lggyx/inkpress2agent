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

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final MarkdownRenderer markdownRenderer;
    private final ViewCountBuffer viewCountBuffer;
    private final cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService;

    public ArticleService(ArticleMapper articleMapper, ArticleTagMapper articleTagMapper,
                          TagMapper tagMapper, CategoryMapper categoryMapper,
                          MarkdownRenderer markdownRenderer, ViewCountBuffer viewCountBuffer,
                          cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.tagMapper = tagMapper;
        this.categoryMapper = categoryMapper;
        this.markdownRenderer = markdownRenderer;
        this.viewCountBuffer = viewCountBuffer;
        this.notificationService = notificationService;
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
        return articleMapper.selectPage(Page.of(page, size), qw).convert(a -> toResponse(a, null));
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
    }

    @PreAuthorize("hasRole('BLOGGER')")
    @Transactional
    public void softDelete(Long id) {
        Article article = mustGet(id);
        article.setDeleted(1);
        articleMapper.updateById(article);
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
