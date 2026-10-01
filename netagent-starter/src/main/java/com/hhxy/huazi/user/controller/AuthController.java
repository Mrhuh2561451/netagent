package com.hhxy.huazi.user.controller;

import com.hhxy.huazi.common.Result;
import com.hhxy.huazi.user.controller.request.LoginRequest;
import com.hhxy.huazi.user.controller.vo.LoginVO;
import com.hhxy.huazi.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口
     */
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@RequestBody LoginRequest requestParam) {
        return Result.success(authService.login(requestParam));
    }

    /**
     * 用户登出接口，清除用户的认证信息和会话
     */
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

}
