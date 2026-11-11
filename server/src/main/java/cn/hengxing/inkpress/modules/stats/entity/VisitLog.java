package cn.hengxing.inkpress.modules.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("visit_log")
public class VisitLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String visitorId;
    private String ip;
    private String region;
    private String device;
    private String path;
    private Long articleId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getVisitorId() { return visitorId; }
    public String getIp() { return ip; }
    public String getRegion() { return region; }
    public String getDevice() { return device; }
    public String getPath() { return path; }
    public Long getArticleId() { return articleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public void setIp(String ip) { this.ip = ip; }
    public void setRegion(String region) { this.region = region; }
    public void setDevice(String device) { this.device = device; }
    public void setPath(String path) { this.path = path; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
