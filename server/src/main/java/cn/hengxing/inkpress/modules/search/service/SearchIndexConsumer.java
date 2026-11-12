package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.search.support.SearchRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 索引同步消费者：消费 search.queue 的 upsert/delete 消息。
 * 消费失败抛 AmqpRejectAndDontRequeueException 拒绝不回队，由 DLX 落入 search.dlq；
 * 另有定时对账任务按 updated_at 增量兜底，保证最终一致。
 */
@Component
public class SearchIndexConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexConsumer.class);

    private final ArticleMapper articleMapper;
    private final SearchService searchService;

    public SearchIndexConsumer(ArticleMapper articleMapper, SearchService searchService) {
        this.articleMapper = articleMapper;
        this.searchService = searchService;
    }

    @RabbitListener(queues = SearchRabbitConfig.SEARCH_QUEUE)
    public void onMessage(Map<String, Object> msg) {
        String action = String.valueOf(msg.getOrDefault("action", ""));
        Long articleId = Long.valueOf(String.valueOf(msg.get("articleId")));
        try {
            if ("delete".equals(action)) {
                searchService.removeArticle(articleId);
                return;
            }
            Article a = articleMapper.selectById(articleId);
            boolean publicVisible = a != null && a.getDeleted() != 1
                    && "PUBLISHED".equals(a.getStatus());
            if (publicVisible) {
                searchService.indexArticle(a);
            } else {
                // 非公开文章（草稿/下架/删除/待复审）不进索引，已索引的移除
                searchService.removeArticle(articleId);
            }
        } catch (Exception e) {
            log.error("索引同步消费失败 action={} articleId={}", action, articleId, e);
            throw new AmqpRejectAndDontRequeueException("索引同步失败", e);
        }
    }
}
