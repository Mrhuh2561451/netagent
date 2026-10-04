package com.hhxy.huazi.user.controller.request;

import lombok.Data;

@Data
public class UserCreateRequest {
    /**
     * 用户名
     */
    private String username;

    /**
     * 登录密码
     */
    private String password;

    /**
     * 角色（admin/user）
     */
    private String role;

    /**
     * 头像地址
     */
    private String avatar;
}
