package cn.hengxing.inkpress.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** polish/correct/summary/continue/title/review */
    private String category;
    private String name;
    private String content;
    private Integer enabled;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getContent() { return content; }
    public Integer getEnabled() { return enabled; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setCategory(String category) { this.category = category; }
    public void setName(String name) { this.name = name; }
    public void setContent(String content) { this.content = content; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
