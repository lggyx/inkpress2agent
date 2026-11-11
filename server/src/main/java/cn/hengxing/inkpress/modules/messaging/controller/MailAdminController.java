package cn.hengxing.inkpress.modules.messaging.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.messaging.service.DigestService;
import cn.hengxing.inkpress.modules.messaging.service.MailDlqService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 邮件管理端：每周精选手动触发 + 死信队列（DLQ）可查可重投 */
@RestController
@RequestMapping("/api/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
public class MailAdminController {

    private final DigestService digestService;
    private final MailDlqService mailDlqService;

    public MailAdminController(DigestService digestService, MailDlqService mailDlqService) {
        this.digestService = digestService;
        this.mailDlqService = mailDlqService;
    }

    /** 立即投递本期每周精选（消费端按周去重，重复调用不会重发） */
    @PostMapping("/digest/send")
    public Result<Map<String, Object>> sendDigest() {
        return Result.ok(digestService.triggerNow());
    }

    /** DLQ 积压条数 */
    @GetMapping("/dlq/count")
    public Result<Map<String, Long>> dlqCount() {
        return Result.ok(Map.of("count", mailDlqService.count()));
    }

    /** DLQ 消息列表（窥视不出队，含死信原因） */
    @GetMapping("/dlq")
    public Result<Map<String, Object>> dlqList(@RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> messages = mailDlqService.list(Math.min(Math.max(limit, 1), 100));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", mailDlqService.count());
        data.put("messages", messages);
        return Result.ok(data);
    }

    /** 重投：取出 DLQ 消息按原路由键回投业务交换机 */
    @PostMapping("/dlq/republish")
    public Result<Map<String, Integer>> dlqRepublish(@RequestBody(required = false) Map<String, Integer> body) {
        int max = body == null || body.get("max") == null ? 10 : Math.min(Math.max(body.get("max"), 1), 100);
        return Result.ok(Map.of("republished", mailDlqService.republish(max)));
    }
}
