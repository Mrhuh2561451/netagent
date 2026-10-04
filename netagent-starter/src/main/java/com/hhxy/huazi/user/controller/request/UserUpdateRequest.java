package com.hhxy.huazi.user.controller.request;

import lombok.Data;

@Data
public class UserUpdateRequest {
    /**
     * 用户名
     */
    private String username;

    /**
     * 新密码（可选）
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
