package cn.hengxing.inkpress.modules.article.mapper;

import cn.hengxing.inkpress.modules.article.entity.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

public interface ArticleMapper extends BaseMapper<Article> {

    /** 互动构成（开题模块六）：全站已发布文章的点赞/评论累计与文章数，供看板聚合展示 */
    @Select("SELECT COALESCE(SUM(like_count),0) AS likes, COALESCE(SUM(comment_count),0) AS comments, "
            + "COUNT(*) AS published FROM article WHERE status='PUBLISHED' AND deleted=0")
    Map<String, Object> interactionTotals();
}
