package cn.hengxing.inkpress.modules.messaging.support;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M7 邮件异步投递拓扑：业务交换机 mail.# → mail.queue（失败进死信交换机 → mail.dlq 管理端可查可重投）。
 * 消息体为 JSON Map；消费端抛异常统一拒绝不回队（default-requeue-rejected=false），由 DLX 落入 DLQ。
 */
@Configuration
public class MailRabbitConfig {

    public static final String MAIL_EXCHANGE = "inkpress.mail";
    public static final String MAIL_DLX = "inkpress.mail.dlx";
    public static final String MAIL_QUEUE = "inkpress.mail.queue";
    public static final String MAIL_DLQ = "inkpress.mail.dlq";

    public static final String RK_COMMENT = "mail.comment";
    public static final String RK_DIGEST = "mail.digest";

    @Bean
    public TopicExchange mailExchange() {
        return new TopicExchange(MAIL_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mailDeadLetterExchange() {
        return new TopicExchange(MAIL_DLX, true, false);
    }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(MAIL_QUEUE)
                .deadLetterExchange(MAIL_DLX)
                .deadLetterRoutingKey("dead")
                .build();
    }

    @Bean
    public Binding mailQueueBinding() {
        return BindingBuilder.bind(mailQueue()).to(mailExchange()).with("mail.#");
    }

    @Bean
    public Queue mailDeadLetterQueue() {
        return QueueBuilder.durable(MAIL_DLQ).build();
    }

    @Bean
    public Binding mailDlqBinding() {
        return BindingBuilder.bind(mailDeadLetterQueue()).to(mailDeadLetterExchange()).with("#");
    }

    @Bean
    public MessageConverter mailMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
