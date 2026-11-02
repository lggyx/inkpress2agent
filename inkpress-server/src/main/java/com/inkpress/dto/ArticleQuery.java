package com.inkpress.dto;

import lombok.Data;

/**
 * 文章分页查询条件
 */
@Data
public class ArticleQuery {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    /** 标题关键词 */
    private String keyword;

    private Long categoryId;

    private Long tagId;

    /** 状态过滤（管理端使用），空则查全部 */
    private String status;
}
