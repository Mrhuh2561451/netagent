package com.hhxy.huazi.user.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.cors.CorsUtils;
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
                    ServletRequestAttributes attributes =
                            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                    if (attributes != null) {
                        HttpServletRequest request = attributes.getRequest();
                        if (request.getDispatcherType() == DispatcherType.ASYNC || CorsUtils.isPreFlightRequest(request)) {
                            return;
                        }
                    }

                    SaRouter.match("/**")
                            .check(r -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error")
                .order(0);

        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/error")
                .order(2);
    }

}
