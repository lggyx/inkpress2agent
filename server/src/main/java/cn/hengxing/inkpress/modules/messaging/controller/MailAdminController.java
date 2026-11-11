package cn.hengxing.inkpress.modules.messaging.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.messaging.service.DigestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 邮件管理端：每周精选手动触发（定时为周一 09:00，测试/补发用） */
@RestController
@RequestMapping("/api/admin/mail")
@PreAuthorize("hasRole('ADMIN')")
public class MailAdminController {

    private final DigestService digestService;

    public MailAdminController(DigestService digestService) {
        this.digestService = digestService;
    }

    /** 立即投递本期每周精选（消费端按周去重，重复调用不会重发） */
    @PostMapping("/digest/send")
    public Result<Map<String, Object>> sendDigest() {
        return Result.ok(digestService.triggerNow());
    }
}
