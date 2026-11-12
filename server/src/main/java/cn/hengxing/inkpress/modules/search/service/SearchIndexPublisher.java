package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.search.support.SearchRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 索引同步发布器：文章增改删后经 RabbitMQ 通知索引更新。
 * 事务内调用时注册 afterCommit 再投递，避免消费者读到未提交数据（经典竞态）。
 */
@Service
public class SearchIndexPublisher {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public SearchIndexPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 发布/更新索引消息；无事务时立即投递，有事务时等提交后再投 */
    public void publishUpsert(Long articleId) {
        send(SearchRabbitConfig.RK_UPSERT, articleId);
    }

    public void publishDelete(Long articleId) {
        send(SearchRabbitConfig.RK_DELETE, articleId);
    }

    private void send(String routingKey, Long articleId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("action", "delete".equals(routingKey) ? "delete" : "upsert");
        msg.put("articleId", articleId);
        Runnable deliver = () -> {
            try {
                rabbitTemplate.convertAndSend(SearchRabbitConfig.SEARCH_EXCHANGE, routingKey, msg);
            } catch (Exception e) {
                // MQ 不可用不阻塞主流程：定时对账任务会按 updated_at 增量修复索引
                log.warn("索引同步消息投递失败 articleId={} rk={} err={}", articleId, routingKey, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
        } else {
            deliver.run();
        }
    }
}
