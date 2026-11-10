package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private Long categoryId;
    private String title;
    private String contentMd;
    private String contentHtml;
    private String coverUrl;
    private String seoDescription;
    private String geoSummary;
    /** DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED / OFFLINE */
    private String status;
    /** HUMAN / AGENT */
    private String source;
    private Integer pinned;
    private Integer deleted;
    private Long viewCount;
    private Long likeCount;
    private Integer commentCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getAuthorId() { return authorId; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getContentMd() { return contentMd; }
    public String getContentHtml() { return contentHtml; }
    public String getCoverUrl() { return coverUrl; }
    public String getSeoDescription() { return seoDescription; }
    public String getGeoSummary() { return geoSummary; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public Integer getPinned() { return pinned; }
    public Integer getDeleted() { return deleted; }
    public Long getViewCount() { return viewCount; }
    public Long getLikeCount() { return likeCount; }
    public Integer getCommentCount() { return commentCount; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public void setTitle(String title) { this.title = title; }
    public void setContentMd(String contentMd) { this.contentMd = contentMd; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }
    public void setGeoSummary(String geoSummary) { this.geoSummary = geoSummary; }
    public void setStatus(String status) { this.status = status; }
    public void setSource(String source) { this.source = source; }
    public void setPinned(Integer pinned) { this.pinned = pinned; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
