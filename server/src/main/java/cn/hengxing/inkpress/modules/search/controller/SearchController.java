package cn.hengxing.inkpress.modules.search.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.search.service.SearchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 公开搜索：mode=bm25/vector/hybrid，ES 宕机自动降级 LIKE 并标注 degraded */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "hybrid") String mode,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(searchService.search(keyword, mode, page, size));
    }

    /**
     * 语料规模统计（公开）：文章数与检索单元（chunk）数。
     * chunk 口径即路演物料「3000+ 语料全部可检索」的可验证数字；ES 宕机时 chunks 为 -1 且 degraded=true。
     */
    @GetMapping("/search/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(searchService.corpusStats());
    }

    @GetMapping("/admin/search/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Boolean>> status() {
        return Result.ok(Map.of("esUp", searchService.esUp()));
    }

    /** 全量重建索引（发布后同步索引失败时可手动补偿） */
    @PostMapping("/admin/search/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> reindex() {
        int count = searchService.reindexAll();
        return Result.ok(Map.of("indexed", count));
    }
}
