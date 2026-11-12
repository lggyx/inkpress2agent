package cn.hengxing.inkpress.modules.search.support;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M5 检索索引异步同步拓扑：业务交换机 search.# → search.queue（失败进 search.dlx → search.dlq）。
 * 消息体为 JSON Map {action: upsert|delete, articleId}；与邮件队列同一 Jackson 转换器风格。
 */
@Configuration
public class SearchRabbitConfig {

    public static final String SEARCH_EXCHANGE = "inkpress.search";
    public static final String SEARCH_DLX = "inkpress.search.dlx";
    public static final String SEARCH_QUEUE = "inkpress.search.queue";
    public static final String SEARCH_DLQ = "inkpress.search.dlq";

    public static final String RK_UPSERT = "search.upsert";
    public static final String RK_DELETE = "search.delete";

    @Bean
    public TopicExchange searchExchange() {
        return new TopicExchange(SEARCH_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange searchDeadLetterExchange() {
        return new TopicExchange(SEARCH_DLX, true, false);
    }

    @Bean
    public Queue searchQueue() {
        return QueueBuilder.durable(SEARCH_QUEUE)
                .deadLetterExchange(SEARCH_DLX)
                .deadLetterRoutingKey("dead")
                .build();
    }

    @Bean
    public Binding searchQueueBinding() {
        return BindingBuilder.bind(searchQueue()).to(searchExchange()).with("search.#");
    }

    @Bean
    public Queue searchDeadLetterQueue() {
        return QueueBuilder.durable(SEARCH_DLQ).build();
    }

    @Bean
    public Binding searchDlqBinding() {
        return BindingBuilder.bind(searchDeadLetterQueue()).to(searchDeadLetterExchange()).with("#");
    }
}
