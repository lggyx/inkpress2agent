package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.article.dto.CategoryRequest;
import cn.hengxing.inkpress.modules.article.entity.Category;
import cn.hengxing.inkpress.modules.article.entity.Comment;
import cn.hengxing.inkpress.modules.article.entity.Tag;
import cn.hengxing.inkpress.modules.article.mapper.TagMapper;
import cn.hengxing.inkpress.modules.article.service.CategoryService;
import cn.hengxing.inkpress.modules.article.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CategoryTagCommentController {

    private final CategoryService categoryService;
    private final CommentService commentService;
    private final TagMapper tagMapper;

    public CategoryTagCommentController(CategoryService categoryService,
                                        CommentService commentService, TagMapper tagMapper) {
        this.categoryService = categoryService;
        this.commentService = commentService;
        this.tagMapper = tagMapper;
    }

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping("/studio/categories")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Map<String, Long>> saveCategory(@Valid @RequestBody CategoryRequest req) {
        return Result.ok(Map.of("id", categoryService.save(req)));
    }

    @DeleteMapping("/studio/categories/{id}")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }

    @GetMapping("/tags")
    public Result<List<Tag>> tags() {
        return Result.ok(tagMapper.selectList(null));
    }

    @GetMapping("/studio/comments/pending")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<List<Comment>> pendingComments() {
        return Result.ok(commentService.pendingAudit());
    }

    @PostMapping("/studio/comments/{id}/approve")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Void> approve(@PathVariable Long id) {
        commentService.approve(id);
        return Result.ok();
    }

    @DeleteMapping("/studio/comments/{id}")
    @PreAuthorize("hasRole('BLOGGER')")
    public Result<Void> remove(@PathVariable Long id) {
        commentService.remove(id);
        return Result.ok();
    }
}
