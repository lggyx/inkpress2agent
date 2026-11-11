package cn.hengxing.inkpress.modules.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("outbound_click")
public class OutboundClick {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String visitorId;
    private Long articleId;
    private String targetHost;
    private String targetUrl;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getVisitorId() { return visitorId; }
    public Long getArticleId() { return articleId; }
    public String getTargetHost() { return targetHost; }
    public String getTargetUrl() { return targetUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
