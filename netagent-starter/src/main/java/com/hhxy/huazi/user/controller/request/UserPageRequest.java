package com.hhxy.huazi.user.controller.request;

import lombok.Data;

@Data
public class UserPageRequest {
    /**
     * 当前页，从 1 开始
     */
    private long current = 1;

    /**
     * 每页条数，范围 1～100
     */
    private long size = 10;

    /**
     * 关键词（支持匹配用户名/角色）
     */
    private String keyword;
}
