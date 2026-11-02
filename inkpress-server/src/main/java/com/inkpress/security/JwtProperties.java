package com.inkpress.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "inkpress.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求长度不小于 32 字节 */
    private String secret;

    /** 令牌有效期（小时），默认 7 天 */
    private long expireHours = 168;
}
