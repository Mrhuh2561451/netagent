package com.hhxy.huazi.user.controller.request;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    /**
     * 当前密码
     */
    private String currentPassword;

    /**
     * 新密码
     */
    private String newPassword;
}
