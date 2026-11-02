package com.inkpress.controller;

import com.inkpress.aspect.OperationLog;
import com.inkpress.common.ApiResponse;
import com.inkpress.entity.Tag;
import com.inkpress.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "标签")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "标签列表（公开）")
    @GetMapping("/tags")
    public ApiResponse<List<Tag>> list() {
        return ApiResponse.ok(tagService.listAll());
    }

    @Operation(summary = "新增标签")
    @OperationLog(module = "标签管理", operation = "新增标签")
    @PostMapping("/admin/tags")
    public ApiResponse<Void> save(@RequestBody Tag tag) {
        tagService.save(tag);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新标签")
    @OperationLog(module = "标签管理", operation = "更新标签")
    @PutMapping("/admin/tags")
    public ApiResponse<Void> update(@RequestBody Tag tag) {
        tagService.update(tag);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除标签")
    @OperationLog(module = "标签管理", operation = "删除标签")
    @DeleteMapping("/admin/tags/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}
