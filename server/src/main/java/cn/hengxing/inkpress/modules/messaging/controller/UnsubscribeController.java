package cn.hengxing.inkpress.modules.messaging.controller;

import cn.hengxing.inkpress.modules.messaging.service.MailService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 邮件一键退订：免登录直达（每封邮件正文附带链接），幂等 */
@RestController
public class UnsubscribeController {

    private final MailService mailService;

    public UnsubscribeController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping(value = "/api/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public String unsubscribe(@RequestParam(required = false) String token) {
        boolean hit = mailService.unsubscribeByToken(token);
        String message = hit
                ? "已为您退订每周精选邮件。站内通知与评论提醒不受影响。"
                : "链接无效或已退订，无需重复操作。";
        return "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>退订邮件</title></head>"
                + "<body style=\"font-family:system-ui,sans-serif;max-width:32rem;margin:4rem auto;"
                + "padding:0 1rem;color:#333\"><h2>InkPress 邮件退订</h2><p>" + message + "</p>"
                + "<p style=\"color:#888;font-size:.9rem\">若为误操作，可登录后在个人中心重新开启订阅。</p>"
                + "</body></html>";
    }
}
