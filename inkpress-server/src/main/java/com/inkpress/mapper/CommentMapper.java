package com.inkpress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkpress.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CommentMapper extends BaseMapper<Comment> {

    /** 近期每日新增评论数，用于看板趋势图 */
    @Select("SELECT DATE(created_at) AS `date`, COUNT(*) AS cnt FROM comment "
            + "WHERE created_at >= #{start} GROUP BY DATE(created_at)")
    List<Map<String, Object>> selectDailyCreated(@Param("start") LocalDateTime start);
}
