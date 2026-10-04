package com.hhxy.huazi.user.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.hhxy.huazi.common.context.LoginInfo;
import com.hhxy.huazi.common.context.UserContext;
import com.hhxy.huazi.common.exception.ClientException;
import com.hhxy.huazi.user.entity.User;
import com.hhxy.huazi.user.mapper.UserMapper;
import com.hhxy.huazi.user.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.DispatcherType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
@RequiredArgsConstructor
@Component
public class UserContextInterceptor implements AsyncHandlerInterceptor{

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 异步调度请求跳过（SSE 完成回调会触发 asyncDispatch，此时 SaToken 上下文已丢失）
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        // 预检请求放行，避免 CORS 阻断
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String loginId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectById(loginId);
        if (user == null) {
            throw new ClientException("用户不存在或已删除");
        }

        UserContext.set(LoginInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .avatar(StrUtil.isBlank(user.getAvatar())
                        ? AuthServiceImpl.DEFAULT_AVATAR : user.getAvatar())
                .build());

        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContext.clear();
    }

}
