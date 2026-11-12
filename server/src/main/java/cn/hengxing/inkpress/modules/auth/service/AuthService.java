package cn.hengxing.inkpress.modules.auth.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.auth.dto.AuthResponse;
import cn.hengxing.inkpress.modules.auth.entity.EmailCode;
import cn.hengxing.inkpress.modules.auth.entity.RefreshToken;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.entity.UserRole;
import cn.hengxing.inkpress.modules.auth.mapper.EmailCodeMapper;
import cn.hengxing.inkpress.modules.auth.mapper.RefreshTokenMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserRoleMapper;
import cn.hengxing.inkpress.modules.auth.support.JwtService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final EmailCodeMapper emailCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final Duration refreshTtl;

    public AuthService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                       RefreshTokenMapper refreshTokenMapper, EmailCodeMapper emailCodeMapper,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       JavaMailSender mailSender,
                       @Value("${inkpress.jwt.refresh-token-ttl}") Duration refreshTtl) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.emailCodeMapper = emailCodeMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailSender = mailSender;
        this.refreshTtl = refreshTtl;
    }

    /** 注册：创建未激活用户并发放邮箱验证码；首个用户自动授予 BLOGGER+ADMIN */
    @Transactional
    public void register(String email, String password, String nickname) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setStatus("UNVERIFIED");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BizException.of(1001, "该邮箱已注册");
        }
        boolean first = userMapper.selectCount(null) == 1;
        grantRole(user.getId(), UserRole.READER, null);
        if (first) {
            grantRole(user.getId(), UserRole.BLOGGER, user.getId());
            grantRole(user.getId(), UserRole.ADMIN, user.getId());
        }
        sendEmailCode(email, EmailCode.PURPOSE_VERIFY, "InkPress 邮箱验证码");
    }

    public void verifyEmail(String email, String code) {
        consumeCode(email, EmailCode.PURPOSE_VERIFY, code);
        User user = findByEmail(email);
        user.setStatus("ACTIVE");
        userMapper.updateById(user);
    }

    public AuthResponse login(String email, String password, String ip, String userAgent) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BizException.of(1002, "邮箱或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BizException.of(1003, "邮箱尚未验证，请先完成激活");
        }
        return issueTokens(user, ip, userAgent);
    }

    /** 旋转宽限期：旧刷新令牌标记为"宽限作废"（revoked_at = now + 宽限），宽限窗口内的重复刷新仍放行，
     *  解决多标签/并发请求共用一枚刷新令牌的竞态互踢；窗口外即真作废（保留旋转的安全语义） */
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(60);

    @Transactional
    public AuthResponse refresh(String refreshToken, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken stored = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getTokenHash, sha256(refreshToken)));
        boolean revoked = stored != null && stored.getRevokedAt() != null && stored.getRevokedAt().isBefore(now);
        if (stored == null || revoked || stored.getExpiresAt().isBefore(now)) {
            throw BizException.of(1004, "登录已过期，请重新登录");
        }
        // 旋转：进入宽限期（窗口内可重复刷新），窗口外自动失效
        stored.setRevokedAt(now.plus(REFRESH_GRACE));
        refreshTokenMapper.updateById(stored);
        User user = userMapper.selectById(stored.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw BizException.of(1004, "账号不可用");
        }
        return issueTokens(user, ip, userAgent);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        RefreshToken stored = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getTokenHash, sha256(refreshToken)));
        if (stored != null && stored.getRevokedAt() == null) {
            stored.setRevokedAt(LocalDateTime.now());
            refreshTokenMapper.updateById(stored);
        }
    }

    public void forgotPassword(String email) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email.toLowerCase()));
        // 用户不存在也返回成功，避免邮箱枚举
        if (user == null) {
            return;
        }
        sendEmailCode(email, EmailCode.PURPOSE_RESET, "InkPress 密码重置验证码");
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        consumeCode(email, EmailCode.PURPOSE_RESET, code);
        User user = findByEmail(email);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        // 重置密码后吊销全部刷新令牌与设备令牌
        revokeAllSessions(user.getId());
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw BizException.of(1005, "原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        revokeAllSessions(userId);
    }

    private void revokeAllSessions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<RefreshToken> tokens = refreshTokenMapper.selectList(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getUserId, userId).isNull(RefreshToken::getRevokedAt));
        for (RefreshToken t : tokens) {
            t.setRevokedAt(now);
            refreshTokenMapper.updateById(t);
        }
    }

    private AuthResponse issueTokens(User user, String ip, String userAgent) {
        String access = jwtService.createAccessToken(user.getId());
        String refresh = newToken();
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(sha256(refresh));
        stored.setIp(ip);
        stored.setUserAgent(userAgent);
        stored.setExpiresAt(LocalDateTime.now().plus(refreshTtl));
        refreshTokenMapper.insert(stored);
        List<String> roles = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRole).toList();
        AuthResponse.UserInfo info = new AuthResponse.UserInfo(
                user.getId(), user.getEmail(), user.getNickname(), user.getAvatarUrl(), roles);
        return new AuthResponse(access, refresh, 7200L, info);
    }

    private void grantRole(Long userId, String role, Long grantedBy) {
        UserRole ur = new UserRole(userId, role, grantedBy);
        userRoleMapper.insert(ur);
    }

    private User findByEmail(String email) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email.toLowerCase()));
        if (user == null) {
            throw BizException.of(1002, "邮箱或密码错误");
        }
        return user;
    }

    /** 生成 6 位数字验证码并入库；SMTP 未配置时降级为日志输出（开发期可见） */
    private void sendEmailCode(String email, String purpose, String subject) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailCode ec = new EmailCode();
        ec.setEmail(email.toLowerCase());
        ec.setPurpose(purpose);
        ec.setCodeHash(sha256(code));
        ec.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        emailCodeMapper.insert(ec);
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject(subject);
            msg.setText("您的验证码是：" + code + "，10 分钟内有效。");
            mailSender.send(msg);
        } catch (Exception e) {
            // 开发期未配置 SMTP 时输出到日志，保证注册流程可验证
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .warn("SMTP 未配置，验证码发送失败 email={} code={}", email, code);
        }
    }

    private void consumeCode(String email, String purpose, String code) {
        EmailCode ec = emailCodeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email.toLowerCase())
                .eq(EmailCode::getPurpose, purpose)
                .isNull(EmailCode::getConsumedAt)
                .orderByDesc(EmailCode::getId)
                .last("LIMIT 1"));
        if (ec == null || !ec.getCodeHash().equals(sha256(code)) || ec.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BizException.of(1006, "验证码错误或已过期");
        }
        ec.setConsumedAt(LocalDateTime.now());
        emailCodeMapper.updateById(ec);
    }

    private String newToken() {
        byte[] buf = new byte[48];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
