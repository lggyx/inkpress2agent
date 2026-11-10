package cn.hengxing.inkpress.modules.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ArticleUpsertRequest(
        Long id,
        Long categoryId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String contentMd,
        String coverUrl,
        String seoDescription,
        String geoSummary,
        List<String> tags,
        /** DRAFT 保存草稿 / PUBLISH 直接发布（博主本人稿件） */
        String action,
        /** AI 评审低分时，前端确认后带 true 放行发布 */
        Boolean confirmLowScore) {
}
