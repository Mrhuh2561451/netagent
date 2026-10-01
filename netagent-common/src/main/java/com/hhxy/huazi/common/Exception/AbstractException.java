package com.hhxy.huazi.common.exception;

import com.hhxy.huazi.common.errorcode.IErrorCode;
import lombok.Getter;

import java.io.Serial;
import java.util.Objects;

/**
 * 三类业务异常的共同父类，统一保存错误码、消息及原始异常。
 * 空消息回退到默认值，并与 getMessage() 保持一致。
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int errorCode;
    private final String errorMessage;

    protected AbstractException(String message, Throwable cause, IErrorCode errorCode) {
        super(resolveMessage(message, errorCode), cause);
        this.errorCode = errorCode.code();
        this.errorMessage = getMessage();
    }

    private static String resolveMessage(String message, IErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "错误码不能为空");
        return message == null || message.isBlank() ? errorCode.message() : message;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{code=" + errorCode + ", message='" + errorMessage + "'}";
    }
}
