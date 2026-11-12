package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 索引定时对账（开题 7.2(4) 数据一致性兜底）：每 10 分钟扫描 updated_at 近 15 分钟内
 * 有变更的文章，公开的重建索引、非公开的移除索引——覆盖 MQ 丢消息/消费失败造成的两边不一致。
 */
@Component
public class SearchIndexReconciler {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexReconciler.class);
    /** 对账窗口略大于调度间隔，避免边界遗漏 */
    private static final int WINDOW_MINUTES = 15;

    private final ArticleMapper articleMapper;
    private final SearchService searchService;

    public SearchIndexReconciler(ArticleMapper articleMapper, SearchService searchService) {
        this.articleMapper = articleMapper;
        this.searchService = searchService;
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 90_000L)
    public void reconcile() {
        try {
            if (!searchService.esUp()) {
                return; // ES 宕机时跳过，等恢复后的下一轮再修
            }
            LocalDateTime since = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
            List<Article> changed = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .ge(Article::getUpdatedAt, since));
            int upsert = 0;
            int removed = 0;
            for (Article a : changed) {
                boolean publicVisible = a.getDeleted() != 1 && "PUBLISHED".equals(a.getStatus());
                if (publicVisible) {
                    searchService.indexArticle(a);
                    upsert++;
                } else {
                    searchService.removeArticle(a.getId());
                    removed++;
                }
            }
            if (upsert + removed > 0) {
                log.info("索引对账完成 upsert={} removed={}", upsert, removed);
            }
        } catch (Exception e) {
            log.warn("索引对账异常：{}", e.getMessage());
        }
    }
}
