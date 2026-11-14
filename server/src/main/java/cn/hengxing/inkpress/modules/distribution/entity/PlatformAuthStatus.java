package cn.hengxing.inkpress.modules.distribution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 平台授权状态（distributor CLI auth status 回报；管理端可见） */
@TableName("platform_auth_status")
public class PlatformAuthStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String authType;
    private Boolean loggedIn;
    private String note;
    private String deviceName;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public Boolean getLoggedIn() { return loggedIn; }
    public void setLoggedIn(Boolean loggedIn) { this.loggedIn = loggedIn; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
