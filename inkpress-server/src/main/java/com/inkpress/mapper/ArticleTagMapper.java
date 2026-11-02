package com.inkpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkpress.entity.ArticleTag;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /** 查询文章关联的标签名称 */
    @Select("SELECT t.name FROM tag t JOIN article_tag rel ON rel.tag_id = t.id WHERE rel.article_id = #{articleId} ORDER BY t.id")
    List<String> selectTagNames(@Param("articleId") Long articleId);
}
