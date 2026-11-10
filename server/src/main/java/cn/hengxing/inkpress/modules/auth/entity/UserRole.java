package cn.hengxing.inkpress.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_role")
public class UserRole {

    public static final String READER = "READER";
    public static final String BLOGGER = "BLOGGER";
    public static final String ADMIN = "ADMIN";

    private Long userId;
    private String role;
    private Long grantedBy;
    private LocalDateTime createdAt;

    public UserRole() {
    }

    public UserRole(Long userId, String role, Long grantedBy) {
        this.userId = userId;
        this.role = role;
        this.grantedBy = grantedBy;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Long getGrantedBy() {
        return grantedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setGrantedBy(Long grantedBy) {
        this.grantedBy = grantedBy;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
