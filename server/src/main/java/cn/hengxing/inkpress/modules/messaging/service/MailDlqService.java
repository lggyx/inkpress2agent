package cn.hengxing.inkpress.modules.messaging.service;

import cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M7 死信队列管理：消费失败的消息经 DLX 落入 inkpress.mail.dlq。
 * list 为窥视（basicGet 后 nack requeue 放回，不出队）；republish 取出后按原路由键重投回业务交换机。
 */
@Service
public class MailDlqService {

    private final RabbitTemplate rabbitTemplate;

    public MailDlqService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** DLQ 当前积压条数 */
    public long count() {
        return rabbitTemplate.execute((Channel ch) -> ch.queueDeclarePassive(MailRabbitConfig.MAIL_DLQ).getMessageCount());
    }

    /** 窥视 DLQ 前 limit 条（不出队），附带死信原因 */
    public List<Map<String, Object>> list(int limit) {
        return rabbitTemplate.execute((Channel ch) -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                GetResponse resp = ch.basicGet(MailRabbitConfig.MAIL_DLQ, false);
                if (resp == null) {
                    break;
                }
                Map<String, Object> headers = safeHeaders(resp.getProps().getHeaders());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("body", new String(resp.getBody(), StandardCharsets.UTF_8));
                item.put("routingKey", resp.getEnvelope().getRoutingKey());
                item.put("redelivered", resp.getEnvelope().isRedeliver());
                item.put("death", firstDeath(headers));
                out.add(item);
                // 放回队列，仅窥视不消费
                ch.basicNack(resp.getEnvelope().getDeliveryTag(), false, true);
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
                String routingKey = originalRoutingKey(safeHeaders(resp.getProps().getHeaders()));
                ch.basicPublish(MailRabbitConfig.MAIL_EXCHANGE, routingKey,
                        resp.getProps(), resp.getBody());
                ch.basicAck(resp.getEnvelope().getDeliveryTag(), false);
                count++;
            }
            return count;
        });
        return n == null ? 0 : n;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDeath(Map<String, Object> headers) {
        Object deaths = headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reason", String.valueOf(death.get("reason")));
            out.put("queue", String.valueOf(death.get("queue")));
            out.put("time", String.valueOf(death.get("time")));
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String originalRoutingKey(Map<String, Object> headers) {
        Object deaths = headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Object keys = ((Map<String, Object>) death).get("routing-keys");
            if (keys instanceof List<?> keyList && !keyList.isEmpty()) {
                return String.valueOf(keyList.get(0));
            }
        }
        // 无 x-death（异常入队）时回退到评论路由
        return MailRabbitConfig.RK_COMMENT;
    }

    private Map<String, Object> safeHeaders(Map<String, Object> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        headers.forEach((k, v) -> out.put(k, v instanceof Date d ? d.toInstant().toString() : String.valueOf(v)));
        return out;
    }
}
