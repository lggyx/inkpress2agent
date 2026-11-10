package cn.hengxing.inkpress.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceLoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        @NotBlank @Size(max = 64) String deviceName) {
}
