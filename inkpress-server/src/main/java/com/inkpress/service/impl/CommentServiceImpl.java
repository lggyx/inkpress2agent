package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkpress.common.BizException;
import com.inkpress.common.Constants;
import com.inkpress.common.ErrorCode;
import com.inkpress.common.PageResult;
import com.inkpress.dto.CommentDTO;
import com.inkpress.entity.Article;
import com.inkpress.entity.Comment;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.mapper.CommentMapper;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.CommentService;
import com.inkpress.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    /** 是否开启评论审核 */
    @Value("${inkpress.comment.audit:true}")
    private boolean auditEnabled;

    @Override
    public List<CommentVO> listByArticle(Long articleId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, Constants.COMMENT_APPROVED)
                .orderByAsc(Comment::getCreatedAt));
        return comments.stream().map(comment -> toVO(comment, false)).toList();
    }

    @Override
    public void create(CommentDTO dto) {
        Article article = articleMapper.selectById(dto.getArticleId());
        if (article == null || !Constants.ARTICLE_PUBLISHED.equals(article.getStatus())) {
            throw BizException.of("文章不存在或未发布");
        }
        if (dto.getParentId() != null) {
            Comment parent = commentMapper.selectById(dto.getParentId());
            if (parent == null || !parent.getArticleId().equals(dto.getArticleId())) {
                throw BizException.of("回复的评论不存在");
            }
        }
        Comment comment = new Comment();
        comment.setArticleId(dto.getArticleId());
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        LoginUser loginUser = SecurityUtils.getLoginUser();
        String status;
        if (loginUser != null) {
            comment.setUserId(loginUser.getUserId());
            comment.setNickname(loginUser.getNickname());
            status = loginUser.isAdmin() || !auditEnabled
                    ? Constants.COMMENT_APPROVED : Constants.COMMENT_PENDING;
        } else {
            if (!StringUtils.hasText(dto.getNickname())) {
                throw BizException.of("游客评论请填写昵称");
            }
            comment.setNickname(dto.getNickname().trim());
            comment.setEmail(dto.getEmail());
            status = auditEnabled ? Constants.COMMENT_PENDING : Constants.COMMENT_APPROVED;
        }
        comment.setStatus(status);
        commentMapper.insert(comment);
    }

    @Override
    public PageResult<CommentVO> adminPage(long pageNum, long pageSize, String status) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(StringUtils.hasText(status), Comment::getStatus, status)
                .orderByDesc(Comment::getCreatedAt);
        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, comment -> toVO(comment, true));
    }

    @Override
    public void audit(Long id, String status) {
        if (!Constants.COMMENT_APPROVED.equals(status) && !Constants.COMMENT_REJECTED.equals(status)) {
            throw BizException.of("非法的评论状态");
        }
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Comment update = new Comment();
        update.setId(id);
        update.setStatus(status);
        commentMapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    private CommentVO toVO(Comment comment, boolean withArticleTitle) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        if (withArticleTitle && comment.getArticleId() != null) {
            Article article = articleMapper.selectById(comment.getArticleId());
            vo.setArticleTitle(article == null ? null : article.getTitle());
        }
        return vo;
    }
}
