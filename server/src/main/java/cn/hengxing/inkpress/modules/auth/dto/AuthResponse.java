package cn.hengxing.inkpress.modules.auth.dto;

import java.util.List;

public record AuthResponse(String accessToken, String refreshToken, Long accessTokenExpiresIn, UserInfo user) {

    public record UserInfo(Long id, String email, String nickname, String avatarUrl, List<String> roles) {
    }
}
