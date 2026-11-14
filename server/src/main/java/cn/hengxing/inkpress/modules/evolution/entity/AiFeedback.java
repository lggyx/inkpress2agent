package cn.hengxing.inkpress.modules.evolution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_feedback")
public class AiFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long userId;
    private Long articleId;
    private String suggestionType;
    private String suggestionText;
    private String verdict;
    private Integer finalVerbatim;
    private Integer editDistance;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getSuggestionType() { return suggestionType; }
    public void setSuggestionType(String suggestionType) { this.suggestionType = suggestionType; }
    public String getSuggestionText() { return suggestionText; }
    public void setSuggestionText(String suggestionText) { this.suggestionText = suggestionText; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public Integer getFinalVerbatim() { return finalVerbatim; }
    public void setFinalVerbatim(Integer finalVerbatim) { this.finalVerbatim = finalVerbatim; }
    public Integer getEditDistance() { return editDistance; }
    public void setEditDistance(Integer editDistance) { this.editDistance = editDistance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
