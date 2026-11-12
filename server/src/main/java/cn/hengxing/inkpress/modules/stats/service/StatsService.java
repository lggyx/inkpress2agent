package cn.hengxing.inkpress.modules.stats.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.stats.entity.VisitLog;
import cn.hengxing.inkpress.modules.stats.mapper.OutboundClickMapper;
import cn.hengxing.inkpress.modules.stats.mapper.VisitLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** M6 统计画像：公开周热榜 + 管理端访问画像聚合（查询时聚合，无预聚合表） */
@Service
public class StatsService {

    private final VisitLogMapper visitLogMapper;
    private final OutboundClickMapper outboundClickMapper;
    private final ArticleMapper articleMapper;

    public StatsService(VisitLogMapper visitLogMapper, OutboundClickMapper outboundClickMapper,
                        ArticleMapper articleMapper) {
        this.visitLogMapper = visitLogMapper;
        this.outboundClickMapper = outboundClickMapper;
        this.articleMapper = articleMapper;
    }

    /** 首页热门榜：近 N 天发布文章按浏览量 Top5（M6 定案周榜取前 5） */
    public List<Map<String, Object>> hotArticles(int days) {
        int n = Math.min(Math.max(days, 1), 30);
        LocalDateTime since = LocalDateTime.now().minusDays(n);
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, "PUBLISHED")
                        .eq(Article::getDeleted, 0)
                        .ge(Article::getPublishedAt, since)
                        .orderByDesc(Article::getViewCount)
                        .last("LIMIT 5"))
                .stream().map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("title", a.getTitle());
                    item.put("viewCount", a.getViewCount());
                    item.put("likeCount", a.getLikeCount());
                    return item;
                }).toList();
    }

    /** 管理端画像总览：PV/UV/地域/设备/日趋势/热读文章/出站平台 */
    public Map<String, Object> overview(int days) {
        int n = Math.min(Math.max(days, 1), 30);
        LocalDateTime since = LocalDateTime.now().minusDays(n);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", n);
        out.put("pv", visitLogMapper.selectCount(
                new LambdaQueryWrapper<VisitLog>().ge(VisitLog::getCreatedAt, since)));
        out.put("uv", visitLogMapper.countUvSince(since));
        out.put("regions", visitLogMapper.regionTop(since, 10));
        out.put("devices", visitLogMapper.deviceTop(since));
        out.put("dailyPv", visitLogMapper.dailyPv(since));
        out.put("topArticles", withTitles(visitLogMapper.articleTop(since, 5)));
        out.put("outboundTop", outboundClickMapper.hostTop(since, 10));
        // 互动构成（全站累计，非窗口内）：点赞/评论/文章数
        out.put("interaction", articleMapper.interactionTotals());
        return out;
    }

    /** 访问 Top 文章 ID 补齐标题 */
    private List<Map<String, Object>> withTitles(List<Map<String, Object>> rows) {
        List<Long> ids = rows.stream()
                .map(r -> ((Number) r.get("articleId")).longValue())
                .toList();
        Map<Long, Article> articles = ids.isEmpty() ? Map.of()
                : articleMapper.selectList(new LambdaQueryWrapper<Article>().in(Article::getId, ids))
                .stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("articleId")).longValue();
            Map<String, Object> item = new LinkedHashMap<>(row);
            Article a = articles.get(id);
            item.put("title", a == null ? "(已删除)" : a.getTitle());
            out.add(item);
        }
        return out;
    }
}
