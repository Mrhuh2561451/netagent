package com.hhxy.huazi.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.hhxy.huazi.common.exception.ClientException;

public class UserContext {

    private static final TransmittableThreadLocal<LoginInfo> CONTEXT = new TransmittableThreadLocal<>();

    private UserContext() {
    }
    public static void set(LoginInfo loginInfo) {
        CONTEXT.set(loginInfo);
    }

    public static LoginInfo get() {
        return CONTEXT.get();
    }

    public static LoginInfo requireUser() {
        LoginInfo user = get();
        if (user == null) {
            throw new ClientException("未获取到当前登录用户");
        }
        return user;
    }
    public static String getUserId() {
        LoginInfo user = get();
        return user == null ? null : user.getUserId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
