package com.hhxy.huazi.user.service;


import com.hhxy.huazi.user.controller.request.LoginRequest;
import com.hhxy.huazi.user.controller.vo.LoginVO;

public interface AuthService {
    LoginVO login(LoginRequest requestParam);

    void logout();
}
