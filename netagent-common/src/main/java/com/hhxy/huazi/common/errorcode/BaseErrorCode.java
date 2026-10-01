package com.hhxy.huazi.common.errorcode;

/**
 * 使用整数业务码并补充认证和上传错误。
 */
public enum BaseErrorCode implements IErrorCode {

    // 客户端错误
    CLIENT_ERROR(40001, "用户请求错误"),
    USER_REGISTER_ERROR(40010, "用户注册错误"),
    USER_NAME_VERIFY_ERROR(40011, "用户名校验失败"),
    USER_NAME_EXIST_ERROR(40012, "用户名已存在"),
    USER_NAME_SENSITIVE_ERROR(40013, "用户名包含敏感词"),
    USER_NAME_SPECIAL_CHARACTER_ERROR(40014, "用户名包含特殊字符"),
    PASSWORD_VERIFY_ERROR(40015, "密码校验失败"),
    PASSWORD_SHORT_ERROR(40016, "密码长度不够"),
    PHONE_VERIFY_ERROR(40017, "手机号格式校验失败"),
    IDEMPOTENT_TOKEN_NULL_ERROR(40020, "幂等 Token 为空"),
    IDEMPOTENT_TOKEN_DELETE_ERROR(40021, "幂等 Token 已被使用或失效"),
    SEARCH_AMOUNT_EXCEEDS_LIMIT(40030, "查询数据量超过最大限制"),
    NOT_LOGIN_ERROR(40101, "未登录或登录已过期"),
    PERMISSION_DENIED(40301, "权限不足"),
    UPLOAD_SIZE_EXCEEDED(41301, "上传文件或请求大小超过限制"),

    // 服务端错误
    SERVICE_ERROR(50001, "系统繁忙，请稍后重试"),
    SERVICE_TIMEOUT_ERROR(50003, "系统处理超时，请稍后重试"),

    // 远程服务调用错误
    REMOTE_ERROR(50002, "外部服务暂时不可用，请稍后重试");

    private final int code;
    private final String message;

    BaseErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
