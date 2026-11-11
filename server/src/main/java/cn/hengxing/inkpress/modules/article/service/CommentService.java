package cn.hengxing.inkpress.modules.article.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.Comment;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.CommentMapper;
import cn.hengxing.inkpress.modules.article.support.SensitiveWordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final SensitiveWordService sensitiveWordService;
    private final cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService;
    private final cn.hengxing.inkpress.modules.auth.mapper.UserMapper userMapper;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper,
                          SensitiveWordService sensitiveWordService,
                          cn.hengxing.inkpress.modules.messaging.service.NotificationService notificationService,
                          cn.hengxing.inkpress.modules.auth.mapper.UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
    }

    /** 发表评论（需登录）：敏感词命中转人工审核，未命中直发 */
    @Transactional
    public Map<String, Object> post(Long articleId, Long userId, Long parentId, String content) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus()) || article.getDeleted() == 1) {
            throw BizException.of(2003, "文章不存在或未发布");
        }
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setParentId(parentId == null ? 0L : parentId);
        comment.setRootId(resolveRootId(parentId));
        comment.setContent(content);
        comment.setStatus(sensitiveWordService.firstHit(content) == null ? "PUBLISHED" : "PENDING_AUDIT");
        commentMapper.insert(comment);
        if ("PUBLISHED".equals(comment.getStatus())) {
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("id", articleId).setSql("comment_count = comment_count + 1"));
            notifyRelated(article, userId, comment);
        }
        return Map.of("id", comment.getId(), "status", comment.getStatus());
    }

    private void notifyRelated(Article article, Long commenterId, Comment comment) {
        cn.hengxing.inkpress.modules.auth.entity.User commenter = userMapper.selectById(commenterId);
        String name = commenter == null ? "有人" : commenter.getNickname();
        if (comment.getParentId() == 0) {
            if (!article.getAuthorId().equals(commenterId)) {
                notificationService.notifyUser(article.getAuthorId(),
                        cn.hengxing.inkpress.modules.messaging.entity.Notification.TYPE_COMMENT,
                        name + " 评论了你的文章《" + article.getTitle() + "》",
                        abbreviate(comment.getContent()), "/article/" + article.getId());
            }
        } else {
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null && !parent.getUserId().equals(commenterId)) {
                notificationService.notifyUser(parent.getUserId(),
                        cn.hengxing.inkpress.modules.messaging.entity.Notification.TYPE_REPLY,
                        name + " 回复了你的评论",
                        abbreviate(comment.getContent()), "/article/" + article.getId());
            }
        }
    }

    private String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 80 ? content : content.substring(0, 80) + "…";
    }

    /** 前台可见评论（含昵称批量解析）：仅已过审的顶层评论（回复随父级带出由前端组装） */
    public List<Map<String, Object>> publishedOfArticle(Long articleId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "PUBLISHED")
                .orderByAsc(Comment::getId));
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, String> nicknames = userMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        cn.hengxing.inkpress.modules.auth.entity.User::getId,
                        u -> u.getNickname() == null || u.getNickname().isBlank() ? "用户" + u.getId() : u.getNickname()));
        List<Map<String, Object>> out = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("userId", c.getUserId());
            item.put("nickname", nicknames.getOrDefault(c.getUserId(), "用户" + c.getUserId()));
            item.put("parentId", c.getParentId());
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());
            out.add(item);
        }
        return out;
    }

    /** 博主端：待审核评论列表 */
    public List<Comment> pendingAudit() {
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, "PENDING_AUDIT")
                .orderByAsc(Comment::getId));
    }

    @Transactional
    public void approve(Long id) {
        Comment comment = mustGet(id);
        if (!"PENDING_AUDIT".equals(comment.getStatus())) {
            throw BizException.of(2201, "评论不在待审核状态");
        }
        comment.setStatus("PUBLISHED");
        commentMapper.updateById(comment);
        articleMapper.update(null, new UpdateWrapper<Article>()
                .eq("id", comment.getArticleId()).setSql("comment_count = comment_count + 1"));
    }

    @Transactional
    public void remove(Long id) {
        Comment comment = mustGet(id);
        if ("PUBLISHED".equals(comment.getStatus())) {
            articleMapper.update(null, new UpdateWrapper<Article>()
                    .eq("id", comment.getArticleId()).setSql("comment_count = comment_count - 1"));
        }
        comment.setStatus("REMOVED");
        commentMapper.updateById(comment);
    }

    private Long resolveRootId(Long parentId) {
        if (parentId == null || parentId == 0) {
            return 0L;
        }
        Comment parent = commentMapper.selectById(parentId);
        if (parent == null) {
            throw BizException.of(2202, "被回复的评论不存在");
        }
        return parent.getRootId() == 0 ? parent.getId() : parent.getRootId();
    }

    private Comment mustGet(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BizException.of(2202, "评论不存在");
        }
        return comment;
    }
}
