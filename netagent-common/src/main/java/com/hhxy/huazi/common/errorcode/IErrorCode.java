package com.hhxy.huazi.common.errorcode;

/**
 * 统一错误码约定，业务模块可通过实现该接口扩展自己的错误码。
 */
public interface IErrorCode {

    /**
     * 响应体中的业务错误码，不等同于 HTTP 状态码。
     */
    int code();

    /**
     * 默认错误信息。
     */
    String message();
}
