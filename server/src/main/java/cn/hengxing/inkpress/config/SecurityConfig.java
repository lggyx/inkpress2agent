package cn.hengxing.inkpress.config;

import cn.hengxing.inkpress.common.TraceIdFilter;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                           TraceIdFilter traceIdFilter,
                                           cn.hengxing.inkpress.common.RateLimitFilter rateLimitFilter,
                                           cn.hengxing.inkpress.modules.auth.support.DeviceTokenAuthFilter deviceTokenAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE（SseEmitter）异步完成时的 ASYNC 再派发不重复鉴权：
                        // 否则过滤器链在响应已提交后抛 AccessDenied，流式连接被拦腰掐断（nginx premature close）
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/actuator/health").permitAll()
                        // 一键退订：邮件链接直达，免登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/unsubscribe").permitAll()
                        // M6 统计采集与公开榜：游客可上报/可读；画像一键清除为匿名隐私权利
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/track/visit", "/api/track/clear").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/r", "/api/stats/hot", "/api/graph", "/rss.xml").permitAll()
                        // 游客可读：公开文章、搜索、分类、标签、媒体资产、AI 线公开摘要
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/articles/**", "/api/search", "/api/categories", "/api/tags",
                                "/api/media/**", "/api/evolution/public-summary").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(deviceTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 个人站部署形态：来源不固定（局域网 IP/公网域名），放行任意来源（带凭据时框架自动回显具体来源而非 "*"）
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
