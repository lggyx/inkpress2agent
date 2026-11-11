package cn.hengxing.inkpress.modules.graph.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article_link")
public class ArticleLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long targetId;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public Long getTargetId() { return targetId; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
