package com.hhxy.huazi.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hhxy.huazi.common.exception.ClientException;
import com.hhxy.huazi.user.controller.request.LoginRequest;
import com.hhxy.huazi.user.controller.vo.LoginVO;
import com.hhxy.huazi.user.entity.User;
import com.hhxy.huazi.user.mapper.UserMapper;
import com.hhxy.huazi.user.service.AuthService;
import com.hhxy.huazi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;

    private final UserMapper userMapper;

    public static final String DEFAULT_AVATAR = "https://ts1.tc.mm.bing.net/th/id/OIP-C.nKetvjjSggVKwC55M-AzUwAAAA?w=193&h=193&c=8&rs=1&qlt=90&o=6&dpr=2&pid=ImgAns&rm=2";
    @Override
    public LoginVO login(LoginRequest requestParam) {
        String username = requestParam.getUsername();
        String password = requestParam.getPassword();
        //校验用户名和密码是否为kong
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password) ) {
            throw new ClientException("用户名或密码为空");
        }
        User user = findByUsername(username);
        if (user == null || !user.getPassword().equals(password)) {
            throw new ClientException("用户名或密码错误");
        }
        if (StrUtil.isBlank(user.getId())){
            throw new ClientException("用户信息异常");
        }
        String avatar = StrUtil.isBlank(user.getAvatar()) ? DEFAULT_AVATAR : user.getAvatar();
        StpUtil.login(user.getId());
        return new LoginVO(user.getId(), user.getRole(), StpUtil.getTokenValue(),avatar);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getUsername, username)
                        .eq(User::getDeleted, 0)
        );
    }
}
