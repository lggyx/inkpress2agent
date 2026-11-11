package cn.hengxing.inkpress.modules.messaging.service;

import cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * M7 死信队列管理：消费失败的消息经 DLX 落入 inkpress.mail.dlq。
 * list 以临时消费者批量收齐（不出队，收完统一 nack requeue，避免同条消息被重复窥视）；
 * republish 取出后按原路由键重投回业务交换机。
 */
@Service
public class MailDlqService {

    private static final long PEEK_TIMEOUT_MS = 500L;

    private final RabbitTemplate rabbitTemplate;

    public MailDlqService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** DLQ 当前积压条数 */
    public long count() {
        return rabbitTemplate.execute((Channel ch) ->
                ch.queueDeclarePassive(MailRabbitConfig.MAIL_DLQ).getMessageCount());
    }

    /** 窥视 DLQ 前 limit 条（统一 nack 放回，不出队），附带死信原因 */
    public List<Map<String, Object>> list(int limit) {
        return rabbitTemplate.execute((Channel ch) -> {
            LinkedBlockingQueue<Delivery> deliveries = new LinkedBlockingQueue<>();
            String tag = ch.basicConsume(MailRabbitConfig.MAIL_DLQ, false,
                    (consumerTag, message) -> deliveries.add(message),
                    consumerTag -> {
                    });
            List<Map<String, Object>> out = new ArrayList<>();
            List<Long> pendingTags = new ArrayList<>();
            try {
                while (out.size() < limit) {
                    Delivery d = deliveries.poll(PEEK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (d == null) {
                        break;
                    }
                    pendingTags.add(d.getEnvelope().getDeliveryTag());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("body", new String(d.getBody(), StandardCharsets.UTF_8));
                    item.put("routingKey", d.getEnvelope().getRoutingKey());
                    item.put("redelivered", d.getEnvelope().isRedeliver());
                    item.put("death", firstDeath(d.getProperties().getHeaders()));
                    out.add(item);
                }
            } finally {
                ch.basicCancel(tag);
                for (Long deliveryTag : pendingTags) {
                    // 放回队列，仅窥视不消费
                    ch.basicNack(deliveryTag, false, true);
                }
            }
            return out;
        });
    }

    /** 取出最多 max 条并按原路由键重投回业务交换机；返回重投条数 */
    public int republish(int max) {
        Integer n = rabbitTemplate.execute((Channel ch) -> {
            int count = 0;
            for (int i = 0; i < max; i++) {
                GetResponse resp = ch.basicGet(MailRabbitConfig.MAIL_DLQ, false);
                if (resp == null) {
                    break;
                }
                String routingKey = originalRoutingKey(resp.getProps().getHeaders());
                ch.basicPublish(MailRabbitConfig.MAIL_EXCHANGE, routingKey,
                        resp.getProps(), resp.getBody());
                ch.basicAck(resp.getEnvelope().getDeliveryTag(), false);
                count++;
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    /** x-death 首条：死因/来源队列/时间（header 值保持原始类型解析） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDeath(Map<String, Object> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object deaths = headers == null ? null : headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Map<String, Object> raw = (Map<String, Object>) death;
            out.put("reason", String.valueOf(raw.get("reason")));
            out.put("queue", String.valueOf(raw.get("queue")));
            Object time = raw.get("time");
            out.put("time", time instanceof Date d ? d.toInstant().toString() : String.valueOf(time));
        }
        return out;
    }

    /** 从 x-death 取原始路由键；无 x-death（异常入队）时回退 mail.comment */
    @SuppressWarnings("unchecked")
    private String originalRoutingKey(Map<String, Object> headers) {
        Object deaths = headers == null ? null : headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Object keys = ((Map<String, Object>) death).get("routing-keys");
            if (keys instanceof List<?> keyList && !keyList.isEmpty()) {
                return String.valueOf(keyList.get(0));
            }
        }
        return MailRabbitConfig.RK_COMMENT;
    }
}
