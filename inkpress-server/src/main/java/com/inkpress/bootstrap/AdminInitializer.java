package com.inkpress.bootstrap;

import com.inkpress.common.Constants;
import com.inkpress.entity.User;
import com.inkpress.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时创建初始管理员账号（数据库无用户时执行）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${INKPRESS_ADMIN_PASSWORD:admin123}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("站长");
        admin.setRole(Constants.ROLE_ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已创建初始管理员账号：admin / {}（请尽快在后台修改密码）", adminPassword);
    }
}
