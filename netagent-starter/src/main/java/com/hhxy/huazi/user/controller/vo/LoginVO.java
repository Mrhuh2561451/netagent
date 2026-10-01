package com.hhxy.huazi.user.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {

    // 用户ID
    private String userId;

    // 角色
    private String role;

    // token
    private String token;

    // 头像
    private String avatar;
}
