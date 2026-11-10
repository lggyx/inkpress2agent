package cn.hengxing.inkpress.modules.article.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String contentMd,
        String contentHtml,
        String coverUrl,
        String seoDescription,
        String geoSummary,
        String status,
        String source,
        Boolean pinned,
        Long viewCount,
        Long likeCount,
        Integer commentCount,
        List<String> tags,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
