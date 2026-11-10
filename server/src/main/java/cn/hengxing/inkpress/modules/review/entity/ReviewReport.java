package cn.hengxing.inkpress.modules.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("review_report")
public class ReviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer readability;
    private Integer structure;
    private Integer keywordDensity;
    private Integer seo;
    private Integer totalScore;
    private String suggestion;
    private String confidence;
    private String triggerType;
    private String model;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public Integer getReadability() { return readability; }
    public Integer getStructure() { return structure; }
    public Integer getKeywordDensity() { return keywordDensity; }
    public Integer getSeo() { return seo; }
    public Integer getTotalScore() { return totalScore; }
    public String getSuggestion() { return suggestion; }
    public String getConfidence() { return confidence; }
    public String getTriggerType() { return triggerType; }
    public String getModel() { return model; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setReadability(Integer readability) { this.readability = readability; }
    public void setStructure(Integer structure) { this.structure = structure; }
    public void setKeywordDensity(Integer keywordDensity) { this.keywordDensity = keywordDensity; }
    public void setSeo(Integer seo) { this.seo = seo; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public void setModel(String model) { this.model = model; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
