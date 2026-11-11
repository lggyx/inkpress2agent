package cn.hengxing.inkpress.modules.messaging.service;

import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.messaging.entity.EmailDigestLog;
import cn.hengxing.inkpress.modules.messaging.mapper.EmailDigestLogMapper;
import cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M7 邮件异步投递：站内事件 → RabbitMQ（inkpress.mail 交换机）→ 消费者发信。
 * SMTP 未配置（smtp.local）时降级为日志输出，保证本地链路可验证；
 * 真实发送失败抛 AmqpRejectAndDontRequeueException，由 DLX 落入 DLQ 待管理端处理。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String KIND_COMMENT = "comment";
    private static final String KIND_DIGEST = "digest";

    private final RabbitTemplate rabbitTemplate;
    private final UserMapper userMapper;
    private final EmailDigestLogMapper digestLogMapper;
    private final JavaMailSender mailSender;
    private final String smtpHost;
    private final String from;
    private final String publicBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public MailService(RabbitTemplate rabbitTemplate, UserMapper userMapper,
                       EmailDigestLogMapper digestLogMapper, JavaMailSender mailSender,
                       @Value("${spring.mail.host:}") String smtpHost,
                       @Value("${inkpress.mail.from:inkpress@noreply.local}") String from,
                       @Value("${inkpress.mail.public-base-url:http://127.0.0.1:8912}") String publicBaseUrl) {
        this.rabbitTemplate = rabbitTemplate;
        this.userMapper = userMapper;
        this.digestLogMapper = digestLogMapper;
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 评论类即时邮件提醒（无需订阅）：入队失败不阻塞站内通知，由调用方吞异常 */
    public void publishCommentAlert(Long recipientId, String title, String content, String refUrl) {
        User user = userMapper.selectById(recipientId);
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        String unsubUrl = publicBaseUrl + "/api/unsubscribe?token=" + ensureUnsubToken(user);
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", KIND_COMMENT);
        msg.put("to", user.getEmail());
        msg.put("nickname", user.getNickname());
        msg.put("subject", title);
        msg.put("content", content == null ? "" : content);
        msg.put("url", publicBaseUrl + (refUrl == null ? "" : refUrl));
        msg.put("unsubUrl", unsubUrl);
        rabbitTemplate.convertAndSend(MailRabbitConfig.MAIL_EXCHANGE, MailRabbitConfig.RK_COMMENT, msg);
    }

    /** 一键退订令牌：首次使用时生成并落库，之后在所有邮件中复用 */
    public String ensureUnsubToken(User user) {
        if (user.getDigestUnsubToken() != null && !user.getDigestUnsubToken().isBlank()) {
            return user.getDigestUnsubToken();
        }
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        user.setDigestUnsubToken(token);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return token;
    }

    /** 按退订令牌关闭每周精选订阅（幂等）；返回是否命中用户 */
    public boolean unsubscribeByToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getDigestUnsubToken, token));
        if (user == null) {
            return false;
        }
        user.setDigestSubscribed(0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return true;
    }

    @RabbitListener(queues = MailRabbitConfig.MAIL_QUEUE)
    public void onMessage(Map<String, Object> msg) {
        String kind = str(msg, "kind");
        if (KIND_COMMENT.equals(kind)) {
            send(str(msg, "to"), str(msg, "subject"), renderCommentBody(msg));
            return;
        }
        if (KIND_DIGEST.equals(kind)) {
            handleDigest(msg);
            return;
        }
        // 未知类型直接丢弃（ack），避免毒消息循环；已定义类型消费失败统一拒绝进 DLQ
        log.warn("未知邮件消息类型 kind={}，已丢弃", kind);
    }

    /** 每周精选消费：按 (user_id, week_start) 去重，同周重复入队（手动触发/重投）只投一封 */
    private void handleDigest(Map<String, Object> msg) {
        Long userId = Long.valueOf(str(msg, "userId"));
        LocalDate weekStart = LocalDate.parse(str(msg, "weekStart"));
        try {
            digestLogMapper.insert(new EmailDigestLog(userId, weekStart));
        } catch (DuplicateKeyException e) {
            log.info("每周精选本周已投递 userId={} weekStart={}，跳过", userId, weekStart);
            return;
        }
        send(str(msg, "to"), "InkPress 每周精选（" + weekStart + " 这一周）", renderDigestBody(msg));
    }

    private String renderDigestBody(Map<String, Object> msg) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) msg.getOrDefault("items", List.of());
        StringBuilder sb = new StringBuilder();
        sb.append("你好 ").append(str(msg, "nickname")).append("：\n\n");
        sb.append("本周精选热文 Top").append(items.size()).append("（按浏览量排序）：\n\n");
        int i = 1;
        for (Map<String, Object> item : items) {
            sb.append(i++).append(". ").append(str(item, "title"))
                    .append("（").append(str(item, "views")).append(" 次浏览）\n   ")
                    .append(str(item, "url")).append('\n');
        }
        if (items.isEmpty()) {
            sb.append("本周暂无新发布的热门文章。\n");
        }
        sb.append("\n——\n不想再收到每周精选？一键退订：\n").append(str(msg, "unsubUrl")).append('\n');
        return sb.toString();
    }

    private String renderCommentBody(Map<String, Object> msg) {
        return "你好 " + str(msg, "nickname") + "：\n\n"
                + str(msg, "subject") + "\n"
                + str(msg, "content") + "\n\n"
                + "查看全文：" + str(msg, "url") + "\n\n"
                + "——\n本邮件为评论提醒（无需订阅）。点击以下链接可同时关闭每周精选订阅：\n"
                + str(msg, "unsubUrl") + "\n";
    }

    private void send(String to, String subject, String body) {
        if (smtpHost == null || smtpHost.isBlank() || "smtp.local".equals(smtpHost)) {
            // 开发期未配置 SMTP：降级日志输出，保持链路可验证且不进 DLQ
            log.info("[MAIL-DEV] to={} subject={}\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("邮件发送失败 to={} subject={}", to, subject, e);
            throw new AmqpRejectAndDontRequeueException("邮件发送失败", e);
        }
    }

    private String str(Map<String, Object> msg, String key) {
        Object v = msg.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
