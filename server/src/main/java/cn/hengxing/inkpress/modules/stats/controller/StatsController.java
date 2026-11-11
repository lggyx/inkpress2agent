package cn.hengxing.inkpress.modules.stats.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.stats.service.StatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M6 查询：公开热门榜（首页模块）+ 管理端画像总览（可视化数据源） */
@RestController
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** 首页热门榜：近 7 天发布热文 Top5（游客可读） */
    @GetMapping("/api/stats/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.hotArticles(days));
    }

    /** 访问画像总览：PV/UV/地域/设备/日趋势/热读/出站平台（管理端可视化） */
    @GetMapping("/api/admin/stats/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.overview(days));
    }
}
