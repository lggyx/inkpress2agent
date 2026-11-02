package com.inkpress.controller;

import com.inkpress.aspect.OperationLog;
import com.inkpress.common.ApiResponse;
import com.inkpress.entity.Category;
import com.inkpress.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "分类")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类列表（公开）")
    @GetMapping("/categories")
    public ApiResponse<List<Category>> list() {
        return ApiResponse.ok(categoryService.listAll());
    }

    @Operation(summary = "新增分类")
    @OperationLog(module = "分类管理", operation = "新增分类")
    @PostMapping("/admin/categories")
    public ApiResponse<Void> save(@RequestBody Category category) {
        categoryService.save(category);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新分类")
    @OperationLog(module = "分类管理", operation = "更新分类")
    @PutMapping("/admin/categories")
    public ApiResponse<Void> update(@RequestBody Category category) {
        categoryService.update(category);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除分类")
    @OperationLog(module = "分类管理", operation = "删除分类")
    @DeleteMapping("/admin/categories/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }
}
