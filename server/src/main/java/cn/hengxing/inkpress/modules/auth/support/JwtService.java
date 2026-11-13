package cn.hengxing.inkpress.modules.auth.support;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private static final String DEV_DEFAULT_SECRET = "inkpress-local-dev-secret-change-me";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(@Value("${inkpress.jwt.secret}") String secret,
                      @Value("${inkpress.jwt.access-token-ttl}") Duration accessTtl,
                      @Value("${inkpress.mail.public-base-url:http://127.0.0.1:8912}") String publicBaseUrl) {
        // 启动强校验：对外部署（public-base-url 非 loopback）拒绝默认密钥——
        // application.yml 注释声称"本地缺省时启动报错提示"，此前并无实现，默认密钥会静默生效
        boolean loopback = publicBaseUrl.contains("//127.0.0.1") || publicBaseUrl.contains("//localhost")
                || publicBaseUrl.contains("//[::1]");
        if (!loopback && DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "拒绝启动：检测到对外部署（inkpress.public-base-url=" + publicBaseUrl
                            + "）仍在使用本地默认 JWT 密钥。请通过环境变量 INKPRESS_JWT_SECRET 注入强随机密钥"
                            + "（建议 openssl rand -hex 32 生成）。");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            org.slf4j.LoggerFactory.getLogger(JwtService.class)
                    .warn("使用本地默认 JWT 密钥（仅限本机开发）；对外部署必须设置 INKPRESS_JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("typ", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 access token，非法返回 null */
    public Long parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!"access".equals(claims.get("typ"))) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
