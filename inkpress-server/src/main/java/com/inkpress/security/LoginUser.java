package com.inkpress.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录用户信息：由 JWT 解析得出，作为 SecurityContext 中的主体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private String nickname;
    private String role;

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
