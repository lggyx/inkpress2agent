package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.article.dto.ArticleResponse;
import cn.hengxing.inkpress.modules.article.dto.CommentRequest;
import cn.hengxing.inkpress.modules.article.entity.Comment;
import cn.hengxing.inkpress.modules.article.service.ArticleService;
import cn.hengxing.inkpress.modules.article.service.CommentService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class PublicArticleController {

    private final ArticleService articleService;
    private final CommentService commentService;

    public PublicArticleController(ArticleService articleService, CommentService commentService) {
        this.articleService = articleService;
        this.commentService = commentService;
    }

    @GetMapping
    public Result<IPage<ArticleResponse>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Long categoryId) {
        return Result.ok(articleService.publicPage(page, size, categoryId));
    }

    @GetMapping("/{id}")
    public Result<ArticleResponse> detail(@PathVariable Long id) {
        return Result.ok(articleService.publicDetail(id));
    }

    @GetMapping("/{id}/comments")
    public Result<List<Comment>> comments(@PathVariable Long id) {
        return Result.ok(commentService.publishedOfArticle(id));
    }

    @PostMapping("/{id}/comments")
    public Result<Map<String, Object>> comment(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                               @PathVariable Long id,
                                               @Valid @RequestBody CommentRequest req) {
        return Result.ok(commentService.post(id, user.id(), req.parentId(), req.content()));
    }
}
