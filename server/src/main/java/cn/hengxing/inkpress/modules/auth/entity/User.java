package cn.hengxing.inkpress.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String bio;
    /** UNVERIFIED / ACTIVE / DISABLED */
    private String status;
    /** 每周精选订阅开关（默认关闭，仅注册用户可开） */
    private Integer digestSubscribed;
    /** 一键退订令牌原文（低敏感，仅能关闭订阅；首封邮件/首次订阅时生成） */
    private String digestUnsubToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public String getStatus() {
        return status;
    }

    public Integer getDigestSubscribed() {
        return digestSubscribed;
    }

    public String getDigestUnsubToken() {
        return digestUnsubToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDigestSubscribed(Integer digestSubscribed) {
        this.digestSubscribed = digestSubscribed;
    }

    public void setDigestUnsubToken(String digestUnsubToken) {
        this.digestUnsubToken = digestUnsubToken;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
