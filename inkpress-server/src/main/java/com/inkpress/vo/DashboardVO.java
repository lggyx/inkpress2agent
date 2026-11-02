package com.inkpress.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 看板统计数据
 */
@Data
public class DashboardVO {

    private Long articleCount;
    private Long publishedCount;
    private Long commentCount;
    private Long userCount;
    private Long totalViews;
    private Long totalLikes;

    /** 近 7 天文章/评论趋势 */
    private List<TrendItem> trend;

    /** 分类文章数分布 */
    private List<Map<String, Object>> categoryDistribution;

    @Data
    @AllArgsConstructor
    public static class TrendItem {
        private String date;
        private Long articles;
        private Long comments;
    }
}
