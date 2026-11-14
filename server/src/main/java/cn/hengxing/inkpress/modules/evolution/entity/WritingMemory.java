package cn.hengxing.inkpress.modules.evolution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("writing_memory")
public class WritingMemory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String category;
    private String content;
    private String source;
    private String status;
    private Long originRunId;
    private LocalDateTime lastHitAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getOriginRunId() { return originRunId; }
    public void setOriginRunId(Long originRunId) { this.originRunId = originRunId; }
    public LocalDateTime getLastHitAt() { return lastHitAt; }
    public void setLastHitAt(LocalDateTime lastHitAt) { this.lastHitAt = lastHitAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
