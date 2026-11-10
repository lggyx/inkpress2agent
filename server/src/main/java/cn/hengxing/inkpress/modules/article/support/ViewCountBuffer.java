package cn.hengxing.inkpress.modules.article.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 浏览量 Redis 计数：每 60s 把 Redis 增量刷回 MySQL（article.view_count 与按天统计在 M6 落库） */
@Component
public class ViewCountBuffer {

    private static final String KEY_PREFIX = "article:views:";

    private final StringRedisTemplate redis;

    public ViewCountBuffer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void increment(Long articleId) {
        try {
            redis.opsForValue().increment(KEY_PREFIX + articleId);
        } catch (Exception ignored) {
            // Redis 不可用时丢弃本次计数，不阻塞阅读主链路
        }
    }

    /** 读取并清空增量（供后续统计落库任务消费，M6 阶段接入按天表） */
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
        for (String key : keys) {
            String v = redis.opsForValue().get(key);
            redis.delete(key);
            // TODO(M6)：写入 article_daily_stats 并累加 article.view_count
        }
    }
}
