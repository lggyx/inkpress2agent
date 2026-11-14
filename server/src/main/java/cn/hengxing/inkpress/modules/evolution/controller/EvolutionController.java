package cn.hengxing.inkpress.modules.evolution.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import cn.hengxing.inkpress.modules.evolution.entity.AiFeedback;
import cn.hengxing.inkpress.modules.evolution.entity.EvolutionRun;
import cn.hengxing.inkpress.modules.evolution.entity.MemorySnapshot;
import cn.hengxing.inkpress.modules.evolution.service.EvolutionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 进化闭环 API（BoHack 日新赛道）：现场两次运行的可量化对比、可见的学习记忆、可演示的回退。
 * 全部挂在 /api/studio 下沿用 BLOGGER 门禁。
 */
@RestController
@RequestMapping("/api/studio/evolution")
@PreAuthorize("hasRole('BLOGGER')")
public class EvolutionController {

    public record RunStartRequest(Long articleId) {
    }

    public record RunFinishRequest(Long runId, String finalContent, List<String> corrections) {
    }

    public record FeedbackRequest(Long runId, Long articleId, @NotBlank String type,
                                  String text, @NotBlank String verdict) {
    }

    public record SnapshotRequest(String label) {
    }

    public record RollbackRequest(Long snapshotId) {
    }

    private final EvolutionService evolutionService;

    public EvolutionController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @PostMapping("/run/start")
    public Result<EvolutionRun> startRun(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                         @RequestBody(required = false) RunStartRequest req) {
        return Result.ok(evolutionService.startRun(user.id(), req == null ? null : req.articleId()));
    }

    @PostMapping("/run/finish")
    public Result<EvolutionRun> finishRun(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                          @RequestBody RunFinishRequest req) {
        List<String> corrections = req.corrections() == null ? List.of() : req.corrections();
        return Result.ok(evolutionService.finishRun(user.id(), req.runId(), req.finalContent(), corrections));
    }

    @GetMapping("/run/active")
    public Result<EvolutionRun> activeRun(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return Result.ok(evolutionService.currentRun(user.id()));
    }

    @PostMapping("/feedback")
    public Result<AiFeedback> feedback(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                       @RequestBody FeedbackRequest req) {
        return Result.ok(evolutionService.recordFeedback(user.id(), req.runId(), req.articleId(),
                req.type(), req.text(), req.verdict()));
    }

    @GetMapping("/runs")
    public Result<List<EvolutionRun>> runs(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return Result.ok(evolutionService.listRuns(user.id()));
    }

    @GetMapping("/memory")
    public Result<List<EvolutionService.WritingMemoryLike>> memory(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return Result.ok(evolutionService.listMemory(user.id()));
    }

    @PostMapping("/snapshot")
    public Result<MemorySnapshot> snapshot(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                           @RequestBody(required = false) SnapshotRequest req) {
        return Result.ok(evolutionService.snapshot(user.id(), req == null ? null : req.label()));
    }

    @GetMapping("/snapshots")
    public Result<List<MemorySnapshot>> snapshots(@AuthenticationPrincipal JwtAuthFilter.LoginUser user) {
        return Result.ok(evolutionService.listSnapshots(user.id()));
    }

    @PostMapping("/rollback")
    public Result<Map<String, Object>> rollback(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                                @RequestBody RollbackRequest req) {
        return Result.ok(evolutionService.rollback(user.id(), req.snapshotId()));
    }
}
