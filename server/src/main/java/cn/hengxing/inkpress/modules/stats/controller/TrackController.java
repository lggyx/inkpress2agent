package cn.hengxing.inkpress.modules.stats.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.stats.entity.OutboundClick;
import cn.hengxing.inkpress.modules.stats.entity.VisitLog;
import cn.hengxing.inkpress.modules.stats.mapper.OutboundClickMapper;
import cn.hengxing.inkpress.modules.stats.mapper.VisitLogMapper;
import cn.hengxing.inkpress.modules.stats.support.RegionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/** M6 采集：匿名访客浏览上报（游客可用）+ /r 出站跳转追踪引流 */
@RestController
public class TrackController {

    private final VisitLogMapper visitLogMapper;
    private final OutboundClickMapper outboundClickMapper;
    private final RegionService regionService;

    public TrackController(VisitLogMapper visitLogMapper, OutboundClickMapper outboundClickMapper,
                           RegionService regionService) {
        this.visitLogMapper = visitLogMapper;
        this.outboundClickMapper = outboundClickMapper;
        this.regionService = regionService;
    }

    public record VisitRequest(String visitorId, String path, Long articleId) {
    }

    /** 浏览上报：匿名 visitor_id 由前端 localStorage 生成；地域/设备由服务端解析 */
    @PostMapping("/api/track/visit")
    public Object visit(@RequestBody VisitRequest req, HttpServletRequest request) {
        if (req.visitorId() == null || req.visitorId().length() < 8 || req.visitorId().length() > 64) {
            throw BizException.of(2501, "visitorId 非法");
        }
        String path = req.path() == null ? "/" : req.path();
        if (path.length() > 300) {
            path = path.substring(0, 300);
        }
        VisitLog log = new VisitLog();
        log.setVisitorId(req.visitorId());
        String ip = clientIp(request);
        log.setIp(ip);
        log.setRegion(regionService.regionOf(ip));
        log.setDevice(deviceOf(request.getHeader("User-Agent")));
        log.setPath(path);
        log.setArticleId(req.articleId());
        visitLogMapper.insert(log);
        return Map.of("code", 0, "message", "ok");
    }

    /**
     * 出站跳转：GET /r?to=<目标URL>&aid=<文章ID> → 记录点击后 302。
     * 仅放行绝对 http(s) 地址，拦截 javascript:/data: 等危险 scheme。
     */
    @GetMapping("/r")
    public ResponseEntity<String> outbound(@RequestParam("to") String to,
                                           @RequestParam(value = "aid", required = false) Long articleId,
                                           HttpServletRequest request) {
        URI uri;
        try {
            uri = URI.create(to);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("scheme");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body("invalid redirect target");
        }
        OutboundClick click = new OutboundClick();
        click.setVisitorId(visitIdFallback(request));
        click.setArticleId(articleId);
        click.setTargetHost(uri.getHost() == null ? "unknown" : uri.getHost());
        click.setTargetUrl(to.length() > 500 ? to.substring(0, 500) : to);
        outboundClickMapper.insert(click);
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        return ip == null || ip.isBlank() ? request.getRemoteAddr() : ip;
    }

    private String deviceOf(String ua) {
        return ua != null && ua.contains("Mobile") ? "MOBILE" : "PC";
    }

    /** /r 跳转无法强制带 visitor_id：缺省用 IP 代访客身份（仅供画像参考） */
    private String visitIdFallback(HttpServletRequest request) {
        return "ip:" + clientIp(request);
    }
}
