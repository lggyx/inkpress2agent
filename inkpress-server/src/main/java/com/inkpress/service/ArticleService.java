package com.inkpress.service;

import com.inkpress.common.PageResult;
import com.inkpress.dto.ArticleQuery;
import com.inkpress.dto.ArticleSaveDTO;
import com.inkpress.vo.ArchiveVO;
import com.inkpress.vo.ArticleDetailVO;
import com.inkpress.vo.ArticleVO;

import java.util.List;
import java.util.Map;

public interface ArticleService {

    /** 前台分页：仅已发布文章 */
    PageResult<ArticleVO> frontPage(ArticleQuery query);

    /** 管理端分页：包含草稿 */
    PageResult<ArticleVO> adminPage(ArticleQuery query);

    /** 前台详情：仅已发布，同时进行浏览计数 */
    ArticleDetailVO getDetail(Long id);

    /** 管理端详情：草稿可见 */
    ArticleDetailVO getAdminDetail(Long id);

    /** 热门文章（Redis 缓存 5 分钟） */
    List<ArticleVO> hot(int limit);

    /** 归档时间线，按年份分组 */
    List<ArchiveVO> archives();

    /** 点赞（同一访客对同一文章仅一次），返回最新点赞数 */
    Long like(Long id, String viewer);

    /**
     * 获取相邻文章（按发布时间排序）
     * @return {"prev": {id,title}|null, "next": {id,title}|null}
     */
    Map<String, Object> adjacent(Long id);

    /** 新增或更新文章，返回文章 ID */
    Long save(ArticleSaveDTO dto);

    /** 发布 / 下架 */
    void updateStatus(Long id, String status);

    /** 置顶 / 取消置顶 */
    void updateTop(Long id, Boolean isTop);

    void delete(Long id);

    /** 将 Redis 中的浏览量计数回写数据库（定时任务调用） */
    void flushViews();
}
