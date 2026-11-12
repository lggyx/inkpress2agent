package cn.hengxing.inkpress.auth;

import cn.hengxing.inkpress.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工具函数：SHA-256 十六进制摘要（刷新令牌/设备令牌/验证码的哈希基础） */
class Sha256Test {

    @Test
    void matchesReferenceDigest() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("inkpress".getBytes(StandardCharsets.UTF_8));
        String expected = HexFormat.of().formatHex(digest);
        assertEquals(expected, AuthService.sha256("inkpress"));
    }

    @Test
    void deterministicAndSensitive() {
        assertEquals(AuthService.sha256("abc"), AuthService.sha256("abc"));
        assertNotEquals(AuthService.sha256("abc"), AuthService.sha256("abd"));
    }

    @Test
    void emptyStringStable() {
        // e3b0c442... 是 SHA-256 对空串的标准摘要前缀
        assertTrue(AuthService.sha256("").startsWith("e3b0c442"));
    }
}
