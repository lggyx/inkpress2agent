package cn.hengxing.inkpress.modules.article.support;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.ArticleDailyStats;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/** 浏览量 Redis 计数：每 60s 把 Redis 增量刷回 MySQL（article.view_count 与按天统计在 M6 落库） */
@Component
public class ViewCountBuffer {

    private static final String KEY_PREFIX = "article:views:";

    private final StringRedisTemplate redis;
    private final cn.hengxing.inkpress.modules.article.mapper.ArticleDailyStatsMapper statsMapper;
    private final cn.hengxing.inkpress.modules.article.mapper.ArticleMapper articleMapper;

    public ViewCountBuffer(StringRedisTemplate redis,
                           cn.hengxing.inkpress.modules.article.mapper.ArticleDailyStatsMapper statsMapper,
                           cn.hengxing.inkpress.modules.article.mapper.ArticleMapper articleMapper) {
        this.redis = redis;
        this.statsMapper = statsMapper;
        this.articleMapper = articleMapper;
    }

    public void increment(Long articleId) {
        try {
            redis.opsForValue().increment(KEY_PREFIX + articleId);
        } catch (Exception ignored) {
            // Redis 不可用时丢弃本次计数，不阻塞阅读主链路
        }
    }

    /** 读取并清空增量：累加 article.view_count 并按天落库 article_daily_stats（M6 统计基座） */
    @Scheduled(fixedDelay = 60_000)
    public void flush() {
        Set<String> keys;
        try {
            keys = redis.keys(KEY_PREFIX + "*");
        } catch (Exception e) {
            return;
        }
        if (keys == null || keys.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (String key : keys) {
            String v = redis.opsForValue().get(key);
            redis.delete(key);
            if (v == null) {
                continue;
            }
            long articleId;
            try {
                articleId = Long.parseLong(key.substring(KEY_PREFIX.length()));
            } catch (NumberFormatException e) {
                continue;
            }
            long delta = Long.parseLong(v);
            applyToStats(articleId, delta, today);
        }
    }

    private void applyToStats(long articleId, long delta, LocalDate today) {
        try {
            ArticleDailyStats row = statsMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDailyStats>()
                    .eq(ArticleDailyStats::getArticleId, articleId)
                    .eq(ArticleDailyStats::getStatDate, today));
            if (row == null) {
                row = new ArticleDailyStats();
                row.setArticleId(articleId);
                row.setStatDate(today);
                row.setViews(delta);
                row.setLikes(0L);
                row.setComments(0L);
                statsMapper.insert(row);
            } else {
                row.setViews(row.getViews() + delta);
                statsMapper.updateById(row);
            }
            articleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Article>()
                    .eq("id", articleId).setSql("view_count = view_count + " + delta));
        } catch (Exception ignored) {
            // 落库失败不影响主链路（下一周期重试前提已 delete，允许少量丢计数）
        }
    }
}
