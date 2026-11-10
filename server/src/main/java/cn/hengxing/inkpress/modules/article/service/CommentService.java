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

import java.util.List;
import java.util.Map;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final SensitiveWordService sensitiveWordService;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper,
                          SensitiveWordService sensitiveWordService) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.sensitiveWordService = sensitiveWordService;
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
            // TODO(M7)：通知文章作者 / 被回复者
        }
        return Map.of("id", comment.getId(), "status", comment.getStatus());
    }

    /** 前台可见评论：仅已过审的顶层评论（回复随父级带出由前端组装） */
    public List<Comment> publishedOfArticle(Long articleId) {
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "PUBLISHED")
                .orderByAsc(Comment::getId));
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
