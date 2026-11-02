package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkpress.common.BizException;
import com.inkpress.common.PageResult;
import com.inkpress.entity.User;
import com.inkpress.mapper.UserMapper;
import com.inkpress.service.UserService;
import com.inkpress.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getNickname, keyword))
                .orderByDesc(User::getCreatedAt);
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, UserVO::from);
    }

    @Override
    public void delete(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw BizException.of("不能删除当前登录账号");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(com.inkpress.common.ErrorCode.NOT_FOUND);
        }
        if (com.inkpress.common.Constants.ROLE_ADMIN.equals(user.getRole())) {
            throw BizException.of("管理员账号不能删除");
        }
        userMapper.deleteById(id);
    }

    @Override
    public void resetPassword(Long id, String password) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(com.inkpress.common.ErrorCode.NOT_FOUND);
        }
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(update);
    }
}
