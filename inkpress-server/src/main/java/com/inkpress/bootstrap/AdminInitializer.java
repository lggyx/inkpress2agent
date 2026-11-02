package com.inkpress.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        // 判据是"不存在管理员"而非"表为空"：否则先注册了普通用户后系统将永远没有管理员
        Long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, Constants.ROLE_ADMIN));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        User admin = new User();
        // 仅当用户表为空时固定 id=1，与种子文章的 author_id=1 对应；否则使用自增主键避免冲突
        Long totalCount = userMapper.selectCount(null);
        if (totalCount == null || totalCount == 0) {
            admin.setId(1L);
        }
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("站长");
        admin.setRole(Constants.ROLE_ADMIN);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已创建初始管理员账号：admin / {}（请尽快在后台修改密码）", adminPassword);
    }
}
