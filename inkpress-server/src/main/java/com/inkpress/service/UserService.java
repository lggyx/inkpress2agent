package com.inkpress.service;

import com.inkpress.common.PageResult;
import com.inkpress.vo.UserVO;

public interface UserService {

    PageResult<UserVO> page(long pageNum, long pageSize, String keyword);

    void delete(Long id, Long currentUserId);

    void resetPassword(Long id, String password);
}
