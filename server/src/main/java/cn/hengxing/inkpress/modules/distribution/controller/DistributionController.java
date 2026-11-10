package cn.hengxing.inkpress.modules.distribution.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.distribution.entity.DistributionRecord;
import cn.hengxing.inkpress.modules.distribution.service.DistributionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DistributionController {

    public record CreateRequest(@NotBlank Long articleId, @NotBlank String platform) {
    }

    public record FillRequest(@NotBlank Long id, String draftUrl, String detail) {
    }

    public record ReportRequest(@NotBlank Long id, boolean success, String publishedUrl, String detail) {
    }

    private final DistributionService distributionService;
    private final cn.hengxing.inkpress.modules.distribution.service.PlatformConvertService platformConvertService;

    public DistributionController(DistributionService distributionService,
                                  cn.hengxing.inkpress.modules.distribution.service.PlatformConvertService platformConvertService) {
        this.distributionService = distributionService;
        this.platformConvertService = platformConvertService;
    }

    /** 博主端发起分发 */
    @PostMapping("/studio/distribution")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Map<String, Long>> create(@RequestBody CreateRequest req) {
        return Result.ok(Map.of("id", distributionService.create(req.articleId(), req.platform())));
    }

    /** 博主端查看文章的分发记录 */
    @GetMapping("/studio/distribution/{articleId}")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<List<DistributionRecord>> ofArticle(@PathVariable Long articleId) {
        return Result.ok(distributionService.listByArticle(articleId));
    }

    /** 排版转换预览：Markdown → 平台 HTML（深度主题） */
    @PostMapping("/studio/distribution/convert")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Map<String, String>> convert(@RequestBody Map<String, String> req) {
        return Result.ok(Map.of("html",
                platformConvertService.convert(req.get("platform"), req.get("contentMd"))));
    }

    /** 本机 agent（设备令牌）拉取待分发任务与正文 */
    @GetMapping("/distribution/pending")
    public Result<List<Map<String, Object>>> pending() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DistributionRecord r : distributionService.pending()) {
            Article article = distributionService.articleOf(r);
            if (article == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("recordId", r.getId());
            item.put("platform", r.getPlatform());
            item.put("articleId", article.getId());
            item.put("title", article.getTitle());
            item.put("contentMd", article.getContentMd());
            item.put("coverUrl", article.getCoverUrl() == null ? "" : article.getCoverUrl());
            result.add(item);
        }
        return Result.ok(result);
    }

    /** agent 回调：草稿已填充 */
    @PostMapping("/distribution/filled")
    public Result<Void> filled(@RequestBody FillRequest req) {
        distributionService.markFilled(req.id(), req.draftUrl(), req.detail());
        return Result.ok();
    }

    /** agent 回调：发布结果与外链回填 */
    @PostMapping("/distribution/report")
    public Result<Void> report(@RequestBody ReportRequest req) {
        distributionService.report(req.id(), req.success(), req.publishedUrl(), req.detail());
        return Result.ok();
    }
}
