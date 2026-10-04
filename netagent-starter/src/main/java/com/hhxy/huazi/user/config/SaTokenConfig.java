package com.hhxy.huazi.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer{

    private final UserContextInterceptor userContextInterceptor;

    /**
     * 注册 Sa-Token 拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**").excludePathPatterns("/auth/**");
        // 注册用户上下文拦截器
        registry.addInterceptor(userContextInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除认证相关路径和错误页面
                .excludePathPatterns("/auth/**", "/error");
    }

}
