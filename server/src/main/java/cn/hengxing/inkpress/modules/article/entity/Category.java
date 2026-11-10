package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getName() { return name; }
    public Integer getSort() { return sort; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setName(String name) { this.name = name; }
    public void setSort(Integer sort) { this.sort = sort; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
