package cn.hengxing.inkpress.modules.stats.mapper;

import cn.hengxing.inkpress.modules.stats.entity.VisitLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface VisitLogMapper extends BaseMapper<VisitLog> {

    /** 近 N 天 UV（去重访客数） */
    @Select("SELECT COUNT(DISTINCT visitor_id) FROM visit_log WHERE created_at >= #{since}")
    long countUvSince(java.time.LocalDateTime since);

    /** 近 N 天地域分布 Top N */
    @Select("SELECT IFNULL(region, '未知') AS region, COUNT(*) AS cnt FROM visit_log "
            + "WHERE created_at >= #{since} GROUP BY region ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> regionTop(java.time.LocalDateTime since, int limit);

    /** 近 N 天设备分布 */
    @Select("SELECT IFNULL(device, '未知') AS device, COUNT(*) AS cnt FROM visit_log "
            + "WHERE created_at >= #{since} GROUP BY device ORDER BY cnt DESC")
    List<Map<String, Object>> deviceTop(java.time.LocalDateTime since);

    /** 近 N 天按天 PV 趋势 */
    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM visit_log "
            + "WHERE created_at >= #{since} GROUP BY day ORDER BY day")
    List<Map<String, Object>> dailyPv(java.time.LocalDateTime since);

    /** 近 N 天热读文章（按访问次数） */
    @Select("SELECT article_id AS articleId, COUNT(*) AS cnt FROM visit_log "
            + "WHERE created_at >= #{since} AND article_id IS NOT NULL GROUP BY article_id "
            + "ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> articleTop(java.time.LocalDateTime since, int limit);
}
