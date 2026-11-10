package cn.hengxing.inkpress.modules.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 32) String nickname,
        @Size(max = 500) String bio) {
}
