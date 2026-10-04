package com.hhxy.huazi.common.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginInfo {
    private String userId;
    private String username;
    private String role;
    private String avatar;
}
