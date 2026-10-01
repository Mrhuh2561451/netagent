

package com.hhxy.huazi.common.exception;

import com.hhxy.huazi.common.errorcode.BaseErrorCode;
import com.hhxy.huazi.common.errorcode.IErrorCode;

import java.io.Serial;

/**
 * 服务端异常，表示本系统处理失败。
 * 原始消息与异常链用于日志排查，全局处理器对外返回通用提示。
 */
public class ServiceException extends AbstractException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable cause, IErrorCode errorCode) {
        super(message, cause, errorCode);
    }
}
