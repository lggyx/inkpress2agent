package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.common.BizException;
import com.inkpress.common.Constants;
import com.inkpress.common.ErrorCode;
import com.inkpress.common.PageResult;
import com.inkpress.dto.ArticleQuery;
import com.inkpress.dto.ArticleSaveDTO;
import com.inkpress.entity.Article;
import com.inkpress.entity.ArticleTag;
import com.inkpress.entity.Category;
import com.inkpress.entity.Tag;
import com.inkpress.entity.User;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.mapper.ArticleTagMapper;
import com.inkpress.mapper.CategoryMapper;
import com.inkpress.mapper.CommentMapper;
import com.inkpress.mapper.TagMapper;
import com.inkpress.mapper.UserMapper;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.ArticleService;
import com.inkpress.vo.ArchiveVO;
import com.inkpress.vo.ArticleDetailVO;
import com.inkpress.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章业务：前台浏览、管理端维护、点赞与浏览计数、归档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final Duration HOT_CACHE_TTL = Duration.ofMinutes(5);

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<ArticleVO> frontPage(ArticleQuery query) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED)
                .like(StringUtils.hasText(query.getKeyword()), Article::getTitle, query.getKeyword())
                .eq(query.getCategoryId() != null, Article::getCategoryId, query.getCategoryId())
                // tagId 为 Long 类型，拼接安全
                .inSql(query.getTagId() != null, Article::getId,
                        "SELECT article_id FROM article_tag WHERE tag_id = " + query.getTagId())
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishedAt);
        Page<Article> page = articleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public PageResult<ArticleVO> adminPage(ArticleQuery query) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(StringUtils.hasText(query.getStatus()), Article::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getKeyword()), Article::getTitle, query.getKeyword())
                .eq(query.getCategoryId() != null, Article::getCategoryId, query.getCategoryId())
                .orderByDesc(Article::getUpdatedAt);
        Page<Article> page = articleMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public ArticleDetailVO getDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || !Constants.ARTICLE_PUBLISHED.equals(article.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 浏览量：Redis 原子自增，由定时任务回写数据库，避免高频写库
        stringRedisTemplate.opsForValue().increment(Constants.KEY_ARTICLE_VIEWS + id);
        stringRedisTemplate.opsForSet().add(Constants.KEY_ARTICLE_VIEWS_DIRTY, id.toString());
        return toDetail(article);
    }

    @Override
    public ArticleDetailVO getAdminDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toDetail(article);
    }

    @Override
    public List<ArticleVO> hot(int limit) {
        String cached = stringRedisTemplate.opsForValue().get(Constants.KEY_HOT_ARTICLES);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<ArticleVO>>() {
                });
            } catch (JsonProcessingException ignored) {
                // 缓存内容损坏时回源数据库
            }
        }
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED)
                .orderByDesc(Article::getViews)
                .last("LIMIT " + limit));
        List<ArticleVO> result = articles.stream().map(this::toVO).toList();
        try {
            stringRedisTemplate.opsForValue().set(Constants.KEY_HOT_ARTICLES,
                    objectMapper.writeValueAsString(result), HOT_CACHE_TTL);
        } catch (JsonProcessingException ignored) {
        }
        return result;
    }

    @Override
    public List<ArchiveVO> archives() {
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getPublishedAt, Article::getCreatedAt)
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED)
                .orderByDesc(Article::getPublishedAt));
        Map<String, List<ArchiveVO.Item>> byYear = new LinkedHashMap<>();
        for (Article article : articles) {
            LocalDateTime time = article.getPublishedAt() == null
                    ? article.getCreatedAt() : article.getPublishedAt();
            byYear.computeIfAbsent(String.valueOf(time.getYear()), k -> new ArrayList<>())
                    .add(new ArchiveVO.Item(article.getId(), article.getTitle(), time));
        }
        return byYear.entrySet().stream()
                .map(entry -> new ArchiveVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public Long like(Long id, String viewer) {
        Article article = articleMapper.selectById(id);
        if (article == null || !Constants.ARTICLE_PUBLISHED.equals(article.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 同一访客（登录用户或 IP）对同一文章只计一次
        Long added = stringRedisTemplate.opsForSet().add(Constants.KEY_ARTICLE_LIKED + id, viewer);
        if (added != null && added == 1) {
            articleMapper.incrementLikes(id);
        }
        return articleMapper.selectById(id).getLikes();
    }

    @Override
    @Transactional
    public Long save(ArticleSaveDTO dto) {
        LoginUser current = SecurityUtils.getLoginUser();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!Constants.ARTICLE_PUBLISHED.equals(dto.getStatus())
                && !Constants.ARTICLE_DRAFT.equals(dto.getStatus())) {
            throw BizException.of("非法的文章状态");
        }
        Article article;
        if (dto.getId() == null) {
            article = new Article();
            article.setAuthorId(current.getUserId());
            article.setViews(0L);
            article.setLikes(0L);
        } else {
            article = articleMapper.selectById(dto.getId());
            if (article == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
        }
        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus());
        article.setIsTop(Boolean.TRUE.equals(dto.getIsTop()));
        boolean publishing = Constants.ARTICLE_PUBLISHED.equals(dto.getStatus());
        if (dto.getId() == null) {
            article.setPublishedAt(publishing ? LocalDateTime.now() : null);
            articleMapper.insert(article);
        } else {
            if (publishing && article.getPublishedAt() == null) {
                article.setPublishedAt(LocalDateTime.now());
            }
            articleMapper.updateById(article);
        }
        saveArticleTags(article.getId(), dto.getTags());
        return article.getId();
    }

    @Override
    public void updateStatus(Long id, String status) {
        if (!Constants.ARTICLE_PUBLISHED.equals(status) && !Constants.ARTICLE_DRAFT.equals(status)) {
            throw BizException.of("非法的文章状态");
        }
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        article.setStatus(status);
        if (Constants.ARTICLE_PUBLISHED.equals(status) && article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
    }

    @Override
    public void updateTop(Long id, Boolean isTop) {
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .set(Article::getIsTop, Boolean.TRUE.equals(isTop)));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        articleMapper.deleteById(id);
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        commentMapper.delete(new LambdaQueryWrapper<com.inkpress.entity.Comment>()
                .eq(com.inkpress.entity.Comment::getArticleId, id));
    }

    /** 浏览量回写：每分钟将 Redis 计数合并进数据库 */
    @Override
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flushViews() {
        Set<String> dirtyIds = stringRedisTemplate.opsForSet().members(Constants.KEY_ARTICLE_VIEWS_DIRTY);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            return;
        }
        for (String idStr : dirtyIds) {
            String value = stringRedisTemplate.opsForValue().get(Constants.KEY_ARTICLE_VIEWS + idStr);
            if (value != null) {
                long delta = Long.parseLong(value);
                if (delta > 0) {
                    articleMapper.incrementViews(Long.valueOf(idStr), delta);
                }
                stringRedisTemplate.delete(Constants.KEY_ARTICLE_VIEWS + idStr);
            }
            stringRedisTemplate.opsForSet().remove(Constants.KEY_ARTICLE_VIEWS_DIRTY, idStr);
        }
    }

    private void saveArticleTags(Long articleId, List<String> tags) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<String> names = tags.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        for (String name : names) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tagMapper.insert(tag);
            }
            articleTagMapper.insert(new ArticleTag(articleId, tag.getId()));
        }
    }

    private ArticleVO toVO(Article article) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getName());
        }
        vo.setTags(articleTagMapper.selectTagNames(article.getId()));
        return vo;
    }

    private ArticleDetailVO toDetail(Article article) {
        ArticleDetailVO vo = new ArticleDetailVO();
        BeanUtils.copyProperties(article, vo);
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getName());
        }
        vo.setTags(articleTagMapper.selectTagNames(article.getId()));
        if (article.getAuthorId() != null) {
            User author = userMapper.selectById(article.getAuthorId());
            vo.setAuthorName(author == null ? null : author.getNickname());
        }
        return vo;
    }
}
