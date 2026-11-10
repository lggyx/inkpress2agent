package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article_like")
public class ArticleLike {

    private Long articleId;
    private Long userId;
    private LocalDateTime createdAt;

    public ArticleLike() {
    }

    public ArticleLike(Long articleId, Long userId) {
        this.articleId = articleId;
        this.userId = userId;
    }

    public Long getArticleId() { return articleId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
