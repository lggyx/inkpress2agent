package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long userId;
    private Long parentId;
    private Long rootId;
    private String content;
    /** PUBLISHED / PENDING_AUDIT / REMOVED */
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public Long getUserId() { return userId; }
    public Long getParentId() { return parentId; }
    public Long getRootId() { return rootId; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setRootId(Long rootId) { this.rootId = rootId; }
    public void setContent(String content) { this.content = content; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
