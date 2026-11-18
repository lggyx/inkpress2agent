package cn.hengxing.inkpress.modules.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("distribution_record")
public class DistributionRecord {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    /** juejin / csdn / zhihu / wechat */
    private String platform;
    private String status;
    private String draftUrl;
    private String publishedUrl;
    private Integer attemptCount;
    private String detail;
    /** 作品数据回流（WorkProvider）：阅读/点赞/评论 + 最近回流时间 */
    private Long workViews;
    private Long workLikes;
    private Long workComments;
    private LocalDateTime workFetchedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public String getPlatform() { return platform; }
    public String getStatus() { return status; }
    public String getDraftUrl() { return draftUrl; }
    public String getPublishedUrl() { return publishedUrl; }
    public Integer getAttemptCount() { return attemptCount; }
    public String getDetail() { return detail; }
    public Long getWorkViews() { return workViews; }
    public Long getWorkLikes() { return workLikes; }
    public Long getWorkComments() { return workComments; }
    public LocalDateTime getWorkFetchedAt() { return workFetchedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setStatus(String status) { this.status = status; }
    public void setDraftUrl(String draftUrl) { this.draftUrl = draftUrl; }
    public void setPublishedUrl(String publishedUrl) { this.publishedUrl = publishedUrl; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public void setDetail(String detail) { this.detail = detail; }
    public void setWorkViews(Long workViews) { this.workViews = workViews; }
    public void setWorkLikes(Long workLikes) { this.workLikes = workLikes; }
    public void setWorkComments(Long workComments) { this.workComments = workComments; }
    public void setWorkFetchedAt(LocalDateTime workFetchedAt) { this.workFetchedAt = workFetchedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
