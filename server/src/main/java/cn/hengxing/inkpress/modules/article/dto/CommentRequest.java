package cn.hengxing.inkpress.modules.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(Long articleId, Long parentId, @NotBlank @Size(max = 2000) String content) {
}
