package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.inkpress.common.Constants;
import com.inkpress.entity.Article;
import com.inkpress.mapper.ArticleMapper;
import com.inkpress.mapper.CategoryMapper;
import com.inkpress.mapper.CommentMapper;
import com.inkpress.mapper.UserMapper;
import com.inkpress.service.DashboardService;
import com.inkpress.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板统计：总量指标、近 7 天趋势、分类分布
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public DashboardVO stats() {
        DashboardVO vo = new DashboardVO();
        vo.setArticleCount(articleMapper.selectCount(null));
        vo.setPublishedCount(articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, Constants.ARTICLE_PUBLISHED)));
        vo.setCommentCount(commentMapper.selectCount(null));
        vo.setUserCount(userMapper.selectCount(null));
        vo.setTotalViews(articleMapper.sumViews());
        vo.setTotalLikes(articleMapper.sumLikes());

        LocalDate start = LocalDate.now().minusDays(6);
        Map<String, Long> articleMap = toCountMap(articleMapper.selectDailyCreated(start.atStartOfDay()));
        Map<String, Long> commentMap = toCountMap(commentMapper.selectDailyCreated(start.atStartOfDay()));
        List<DashboardVO.TrendItem> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String day = start.plusDays(i).toString();
            trend.add(new DashboardVO.TrendItem(day,
                    articleMap.getOrDefault(day, 0L),
                    commentMap.getOrDefault(day, 0L)));
        }
        vo.setTrend(trend);
        vo.setCategoryDistribution(categoryMapper.selectDistribution());
        return vo;
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("date");
            Object cnt = row.get("cnt");
            if (date == null || !(cnt instanceof Number number)) {
                continue;
            }
            String key;
            if (date instanceof java.sql.Date sqlDate) {
                key = sqlDate.toLocalDate().toString();
            } else if (date instanceof LocalDate localDate) {
                key = localDate.toString();
            } else if (date instanceof LocalDateTime dateTime) {
                key = dateTime.toLocalDate().toString();
            } else {
                key = date.toString();
            }
            map.put(key, number.longValue());
        }
        return map;
    }
}
