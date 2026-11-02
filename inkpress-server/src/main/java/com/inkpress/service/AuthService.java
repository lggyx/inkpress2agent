package com.inkpress.service;

import com.inkpress.dto.LoginDTO;
import com.inkpress.dto.RegisterDTO;
import com.inkpress.vo.LoginVO;
import com.inkpress.vo.UserVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    void register(RegisterDTO dto);

    UserVO currentUser();
}
