package com.inkpress.controller;

import com.inkpress.aspect.OperationLog;
import com.inkpress.common.ApiResponse;
import com.inkpress.common.PageResult;
import com.inkpress.dto.CommentDTO;
import com.inkpress.dto.StatusDTO;
import com.inkpress.security.RateLimit;
import com.inkpress.service.CommentService;
import com.inkpress.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "评论")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "文章的已通过评论（前台）")
    @GetMapping("/articles/{articleId}/comments")
    public ApiResponse<List<CommentVO>> listByArticle(@PathVariable Long articleId) {
        return ApiResponse.ok(commentService.listByArticle(articleId));
    }

    @Operation(summary = "发表评论（游客或登录用户），返回落库状态 PENDING/APPROVED")
    @RateLimit(times = 5, windowSeconds = 300, message = "评论提交过于频繁，请 5 分钟后再试")
    @PostMapping("/comments")
    public ApiResponse<String> create(@Valid @RequestBody CommentDTO dto) {
        return ApiResponse.ok(commentService.create(dto));
    }

    @Operation(summary = "管理端评论分页")
    @GetMapping("/admin/comments")
    public ApiResponse<PageResult<CommentVO>> adminPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(commentService.adminPage(pageNum, pageSize, status));
    }

    @Operation(summary = "评论审核")
    @OperationLog(module = "评论管理", operation = "评论审核")
    @PutMapping("/admin/comments/{id}/status")
    public ApiResponse<Void> audit(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        commentService.audit(id, dto.getStatus());
        return ApiResponse.ok();
    }

    @Operation(summary = "删除评论")
    @OperationLog(module = "评论管理", operation = "删除评论")
    @DeleteMapping("/admin/comments/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ApiResponse.ok();
    }
}
