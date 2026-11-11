package cn.hengxing.inkpress.modules.messaging.service;

import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M7 每周精选（订阅制，默认关闭）：定时聚合近 7 天发布热文 Top5，
 * 逐订阅者入队异步投递；消费端按 (user_id, week_start) 去重，重复触发不会重发。
 */
@Service
public class DigestService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MailService mailService;
    private final String publicBaseUrl;

    public DigestService(ArticleMapper articleMapper, UserMapper userMapper,
                         RabbitTemplate rabbitTemplate, MailService mailService,
                         @Value("${inkpress.mail.public-base-url:http://127.0.0.1:8912}") String publicBaseUrl) {
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.mailService = mailService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** 每周一 09:00（北京时间）自动投递；可用 inkpress.mail.digest-cron 覆盖 */
    @Scheduled(cron = "${inkpress.mail.digest-cron:0 0 9 * * MON}", zone = "Asia/Shanghai")
    public void weeklyDigest() {
        triggerNow();
    }

    /** 管理端手动触发本期精选；返回入队统计 */
    public Map<String, Object> triggerNow() {
        LocalDate weekStart = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime windowStart = LocalDateTime.now(ZONE).minusDays(7);
        List<Article> tops = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "PUBLISHED")
                .eq(Article::getDeleted, 0)
                .ge(Article::getPublishedAt, windowStart)
                .orderByDesc(Article::getViewCount)
                .last("LIMIT 5"));
        List<User> subscribers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getDigestSubscribed, 1)
                .eq(User::getStatus, "ACTIVE"));
        for (User subscriber : subscribers) {
            rabbitTemplate.convertAndSend(MailRabbitConfig.MAIL_EXCHANGE, MailRabbitConfig.RK_DIGEST,
                    buildMessage(subscriber, weekStart, tops));
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("weekStart", weekStart.toString());
        stats.put("subscribers", subscribers.size());
        stats.put("articles", tops.size());
        return stats;
    }

    private Map<String, Object> buildMessage(User subscriber, LocalDate weekStart, List<Article> tops) {
        String unsubToken = mailService.ensureUnsubToken(subscriber);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Article a : tops) {
            Map<String, Object> item = new HashMap<>();
            item.put("title", a.getTitle());
            item.put("url", publicBaseUrl + "/article/" + a.getId());
            item.put("views", a.getViewCount() == null ? 0L : a.getViewCount());
            items.add(item);
        }
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "digest");
        msg.put("userId", subscriber.getId());
        msg.put("to", subscriber.getEmail());
        msg.put("nickname", subscriber.getNickname());
        msg.put("weekStart", weekStart.toString());
        msg.put("items", items);
        msg.put("unsubUrl", publicBaseUrl + "/api/unsubscribe?token=" + unsubToken);
        return msg;
    }
}
