package cn.hengxing.inkpress.modules.messaging.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.messaging.entity.Notification;
import cn.hengxing.inkpress.modules.messaging.service.NotificationService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** SSE 长连接：实时接收通知（30s 心跳） */
    @GetMapping("/stream")
    public SseEmitter stream(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return notificationService.subscribe(user.id());
    }

    @GetMapping
    public Result<List<Notification>> list(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(user.id(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unread(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return Result.ok(Map.of("count", notificationService.unread(user.id())));
    }

    @PostMapping("/read")
    public Result<Void> read(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                             @RequestBody(required = false) Map<String, List<Long>> body) {
        notificationService.markRead(user.id(), body == null ? null : body.get("ids"));
        return Result.ok();
    }
}
