package com.hhxy.huazi.common.exception;

import com.hhxy.huazi.common.errorcode.BaseErrorCode;
import com.hhxy.huazi.common.errorcode.IErrorCode;

import java.io.Serial;

/**
 * 客户端异常，例如参数不合法、用户名已存在。
 * 消息会返回给客户端，不应包含内部实现细节或敏感信息。
 */
public class ClientException extends AbstractException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable cause, IErrorCode errorCode) {
        super(message, cause, errorCode);
    }
}
