package cn.hengxing.inkpress.modules.messaging.service;

import cn.hengxing.inkpress.modules.messaging.entity.Notification;
import cn.hengxing.inkpress.modules.messaging.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 站内通知：落库 + SSE 实时推送（按用户维护长连接，30s 心跳） */
@Service
public class NotificationService {

    private static final long HEARTBEAT_MS = 30_000L;

    private final NotificationMapper notificationMapper;
    private final MailService mailService;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationMapper notificationMapper, MailService mailService) {
        this.notificationMapper = notificationMapper;
        this.mailService = mailService;
    }

    public void notifyUser(Long userId, String type, String title, String content, String refUrl) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefUrl(refUrl);
        notificationMapper.insert(n);
        push(userId, n);
        // 评论类即时邮件提醒（无需订阅）：入队/投递失败不影响站内通知主链路
        if (Notification.TYPE_COMMENT.equals(type) || Notification.TYPE_REPLY.equals(type)) {
            try {
                mailService.publishCommentAlert(userId, title, content, refUrl);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                        .warn("评论邮件提醒入队失败 userId={}", userId, e);
            }
        }
    }

    public List<Notification> list(Long userId, int page, int size) {
        return notificationMapper.selectPage(
                        com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(page, size),
                        new LambdaQueryWrapper<Notification>()
                                .eq(Notification::getUserId, userId)
                                .orderByDesc(Notification::getId))
                .getRecords();
    }

    public long unread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId).isNull(Notification::getReadAt));
    }

    public void markRead(Long userId, List<Long> ids) {
        UpdateWrapper<Notification> uw = new UpdateWrapper<Notification>()
                .eq("user_id", userId).isNull("read_at").set("read_at", LocalDateTime.now());
        if (ids != null && !ids.isEmpty()) {
            uw.in("id", ids);
        }
        notificationMapper.update(null, uw);
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {
        }
        return emitter;
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        emitters.forEach((uid, list) -> list.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("hb"));
            } catch (Exception ex) {
                remove(uid, e);
            }
        }));
    }

    private void push(Long userId, Notification n) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "id", n.getId(), "type", n.getType(), "title", n.getTitle(),
                "content", n.getContent() == null ? "" : n.getContent(),
                "refUrl", n.getRefUrl() == null ? "" : n.getRefUrl(),
                "createdAt", n.getCreatedAt().toString());
        list.forEach(e -> {
            try {
                e.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception ex) {
                remove(userId, e);
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
