package cn.hengxing.inkpress.modules.stats.mapper;

import cn.hengxing.inkpress.modules.stats.entity.OutboundClick;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OutboundClickMapper extends BaseMapper<OutboundClick> {

    /** 近 N 天出站平台分布 Top N */
    @Select("SELECT target_host AS host, COUNT(*) AS cnt FROM outbound_click "
            + "WHERE created_at >= #{since} GROUP BY target_host ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> hostTop(LocalDateTime since, int limit);
}
