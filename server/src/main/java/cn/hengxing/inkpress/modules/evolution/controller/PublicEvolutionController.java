package cn.hengxing.inkpress.modules.evolution.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.evolution.service.EvolutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 公开进化摘要（AI 线 / /ai/evolution 数据源）：仅聚合指标，无任何用户隐私字段 */
@RestController
@RequestMapping("/api/evolution")
public class PublicEvolutionController {

    private final EvolutionService evolutionService;

    public PublicEvolutionController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @GetMapping("/public-summary")
    public Result<Map<String, Object>> publicSummary() {
        return Result.ok(evolutionService.publicSummary());
    }
}
