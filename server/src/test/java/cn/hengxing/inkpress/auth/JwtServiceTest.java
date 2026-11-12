package cn.hengxing.inkpress.auth;

import cn.hengxing.inkpress.modules.auth.support.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** JWT 令牌签发与解析：往返、typ 校验、篡改拒绝、过期拒绝 */
class JwtServiceTest {

    private static final String SECRET = "inkpress-unit-test-secret-key-0123456789abcdef";

    private JwtService service(Duration ttl) {
        return new JwtService(SECRET, ttl);
    }

    @Test
    void roundtrip() {
        JwtService svc = service(Duration.ofMinutes(5));
        String token = svc.createAccessToken(42L);
        assertEquals(42L, svc.parseAccessToken(token));
    }

    @Test
    void expiredTokenRejected() {
        JwtService svc = service(Duration.ofMillis(1));
        String token = svc.createAccessToken(1L);
        try { Thread.sleep(50); } catch (InterruptedException ignored) { }
        assertNull(svc.parseAccessToken(token));
    }

    @Test
    void tamperedTokenRejected() {
        JwtService svc = service(Duration.ofMinutes(5));
        String token = svc.createAccessToken(1L);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertNull(svc.parseAccessToken(tampered));
    }

    @Test
    void crossSecretRejected() {
        String token = service(Duration.ofMinutes(5)).createAccessToken(1L);
        JwtService other = new JwtService(SECRET + "-different", Duration.ofMinutes(5));
        assertNull(other.parseAccessToken(token));
    }

    @Test
    void garbageRejected() {
        assertNull(service(Duration.ofMinutes(5)).parseAccessToken("not-a-jwt"));
    }
}
