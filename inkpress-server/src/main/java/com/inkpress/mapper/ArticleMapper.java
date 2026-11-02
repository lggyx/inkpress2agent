package com.inkpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkpress.entity.Article;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ArticleMapper extends BaseMapper<Article> {

    @Update("UPDATE article SET likes = likes + 1 WHERE id = #{id}")
    int incrementLikes(@Param("id") Long id);

    @Update("UPDATE article SET views = views + #{delta} WHERE id = #{id}")
    int incrementViews(@Param("id") Long id, @Param("delta") long delta);

    /** 近期每日新增文章数，用于看板趋势图 */
    @Select("SELECT DATE(created_at) AS `date`, COUNT(*) AS cnt FROM article "
            + "WHERE created_at >= #{start} GROUP BY DATE(created_at)")
    List<Map<String, Object>> selectDailyCreated(@Param("start") LocalDateTime start);

    @Select("SELECT COALESCE(SUM(views), 0) FROM article")
    Long sumViews();

    @Select("SELECT COALESCE(SUM(likes), 0) FROM article")
    Long sumLikes();
}
