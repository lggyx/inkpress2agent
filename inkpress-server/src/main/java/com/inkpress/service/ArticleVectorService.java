package com.inkpress.service;

import com.inkpress.vo.ArticleVO;

import java.util.List;
import java.util.Map;

/**
 * 站内语义向量服务：文章向量化索引、余弦相似度检索、相关文章混合排序。
 * 向量存储于 Redis（JSON 数组），文章发布/更新时增量重建，应用启动时全量重建。
 */
public interface ArticleVectorService {

    /** 重建全部文章向量（应用启动时异步执行） */
    void rebuildAll();

    /** 增量索引单篇文章（发布/更新后调用） */
    void indexArticle(Long articleId);

    /**
     * 相关文章推荐：语义余弦相似度为主，标签重合度加权
     *
     * @param articleId 当前文章
     * @param topK      返回条数
     */
    List<ArticleVO> related(Long articleId, int topK);

    /**
     * 语义搜索：自然语言查询向量化后全库余弦检索
     *
     * @param query 查询文本
     * @param topK  返回条数
     * @return 文章列表（带相似度得分，score 字段）
     */
    List<Map<String, Object>> semanticSearch(String query, int topK);

    /** 当前使用的向量化实现标识 */
    String providerName();
}
