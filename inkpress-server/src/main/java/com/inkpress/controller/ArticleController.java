package com.inkpress.controller;

import com.inkpress.aspect.OperationLog;
import com.inkpress.common.ApiResponse;
import com.inkpress.common.IpUtils;
import com.inkpress.common.PageResult;
import com.inkpress.dto.ArticleQuery;
import com.inkpress.dto.ArticleSaveDTO;
import com.inkpress.dto.StatusDTO;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.ArticleService;
import com.inkpress.vo.ArchiveVO;
import com.inkpress.vo.ArticleDetailVO;
import com.inkpress.vo.ArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "文章")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    // ==================== 前台公开接口 ====================

    @Operation(summary = "前台文章分页（仅已发布）")
    @GetMapping("/articles/page")
    public ApiResponse<PageResult<ArticleVO>> page(ArticleQuery query) {
        return ApiResponse.ok(articleService.frontPage(query));
    }

    @Operation(summary = "热门文章")
    @GetMapping("/articles/hot")
    public ApiResponse<List<ArticleVO>> hot(@RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(articleService.hot(limit));
    }

    @Operation(summary = "归档时间线")
    @GetMapping("/articles/archives")
    public ApiResponse<List<ArchiveVO>> archives() {
        return ApiResponse.ok(articleService.archives());
    }

    @Operation(summary = "文章详情（公开，带浏览计数）")
    @GetMapping("/articles/{id}")
    public ApiResponse<ArticleDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(articleService.getDetail(id));
    }

    @Operation(summary = "相邻文章（上一篇/下一篇，按发布时间）")
    @GetMapping("/articles/{id}/adjacent")
    public ApiResponse<Map<String, Object>> adjacent(@PathVariable Long id) {
        return ApiResponse.ok(articleService.adjacent(id));
    }

    @Operation(summary = "点赞")
    @PostMapping("/articles/{id}/like")
    public ApiResponse<Long> like(@PathVariable Long id, HttpServletRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        String viewer = loginUser != null ? "u:" + loginUser.getUserId() : "ip:" + IpUtils.getIp(request);
        return ApiResponse.ok(articleService.like(id, viewer));
    }

    // ==================== 管理端接口（ADMIN） ====================

    @Operation(summary = "管理端文章分页（含草稿）")
    @GetMapping("/admin/articles/page")
    public ApiResponse<PageResult<ArticleVO>> adminPage(ArticleQuery query) {
        return ApiResponse.ok(articleService.adminPage(query));
    }

    @Operation(summary = "管理端文章详情")
    @GetMapping("/admin/articles/{id}")
    public ApiResponse<ArticleDetailVO> adminDetail(@PathVariable Long id) {
        return ApiResponse.ok(articleService.getAdminDetail(id));
    }

    @Operation(summary = "保存文章（新增或更新）")
    @OperationLog(module = "文章管理", operation = "保存文章")
    @PostMapping("/admin/articles")
    public ApiResponse<Long> save(@Valid @RequestBody ArticleSaveDTO dto) {
        return ApiResponse.ok(articleService.save(dto));
    }

    @Operation(summary = "修改文章状态（发布/下架）")
    @OperationLog(module = "文章管理", operation = "修改文章状态")
    @PutMapping("/admin/articles/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        articleService.updateStatus(id, dto.getStatus());
        return ApiResponse.ok();
    }

    @Operation(summary = "修改置顶状态")
    @OperationLog(module = "文章管理", operation = "修改置顶状态")
    @PutMapping("/admin/articles/{id}/top")
    public ApiResponse<Void> updateTop(@PathVariable Long id, @RequestParam boolean isTop) {
        articleService.updateTop(id, isTop);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除文章")
    @OperationLog(module = "文章管理", operation = "删除文章")
    @DeleteMapping("/admin/articles/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return ApiResponse.ok();
    }
}
