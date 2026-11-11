package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.article.dto.ArticleResponse;
import cn.hengxing.inkpress.modules.article.dto.ArticleUpsertRequest;
import cn.hengxing.inkpress.modules.article.service.ArticleService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/studio/articles")
@PreAuthorize("hasRole('BLOGGER')")
public class StudioArticleController {

    private final ArticleService articleService;
    private final cn.hengxing.inkpress.modules.review.service.ReviewService reviewService;
    private final cn.hengxing.inkpress.modules.graph.service.GraphService graphService;

    public StudioArticleController(ArticleService articleService,
                                   cn.hengxing.inkpress.modules.review.service.ReviewService reviewService,
                                   cn.hengxing.inkpress.modules.graph.service.GraphService graphService) {
        this.articleService = articleService;
        this.reviewService = reviewService;
        this.graphService = graphService;
    }

    @PostMapping
    public Result<ArticleResponse> upsert(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                          @Valid @RequestBody ArticleUpsertRequest req) {
        return Result.ok(articleService.upsert(user.id(), req, false));
    }

    @GetMapping
    public Result<IPage<ArticleResponse>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String status) {
        return Result.ok(articleService.studioPage(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<ArticleResponse> detail(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                          @PathVariable Long id) {
        return Result.ok(articleService.studioDetail(id, user.id()));
    }

    @PostMapping("/{id}/pin")
    public Result<Void> pin(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        articleService.setPinned(id, Boolean.TRUE.equals(body.get("pinned")));
        return Result.ok();
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        articleService.offline(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.softDelete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/review")
    public Result<Void> review(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                               @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        articleService.review(id, Boolean.TRUE.equals(body.get("pass")), user.id());
        return Result.ok();
    }

    /** M9：手动触发 AI 评审 */
    @PostMapping("/{id}/ai-review")
    public Result<cn.hengxing.inkpress.modules.review.entity.ReviewReport> aiReview(@PathVariable Long id) {
        return Result.ok(reviewService.run(id, "MANUAL"));
    }

    @GetMapping("/{id}/ai-review/latest")
    public Result<cn.hengxing.inkpress.modules.review.entity.ReviewReport> latestReview(@PathVariable Long id) {
        return Result.ok(reviewService.latest(id));
    }

    /** M8：手动关联文章（图谱边 + 相关阅读置顶），仅文章作者 */
    @PostMapping("/{id}/links")
    public Result<Void> addLink(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                @PathVariable Long id, @RequestBody Map<String, Long> body) {
        graphService.addManualLink(user.id(), id, body.get("targetId"));
        return Result.ok();
    }

    @GetMapping("/{id}/links")
    public Result<List<Map<String, Object>>> links(@PathVariable Long id) {
        return Result.ok(graphService.linksOf(id));
    }

    @DeleteMapping("/{id}/links/{targetId}")
    public Result<Void> removeLink(@PathVariable Long id, @PathVariable Long targetId) {
        graphService.removeManualLink(id, targetId);
        return Result.ok();
    }
}
