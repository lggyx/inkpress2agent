package com.inkpress.service;

import com.inkpress.common.PageResult;
import com.inkpress.dto.CommentDTO;
import com.inkpress.vo.CommentVO;

import java.util.List;

public interface CommentService {

    /** 某篇文章的已通过评论（前台） */
    List<CommentVO> listByArticle(Long articleId);

    /**
     * 发表评论：登录用户自动带出昵称，游客需填写昵称；是否需审核由配置决定
     * @return 评论落库后的状态（PENDING 待审核 / APPROVED 直接展示）
     */
    String create(CommentDTO dto);

    PageResult<CommentVO> adminPage(long pageNum, long pageSize, String status);

    /** 审核：APPROVED / REJECTED */
    void audit(Long id, String status);

    void delete(Long id);
}
