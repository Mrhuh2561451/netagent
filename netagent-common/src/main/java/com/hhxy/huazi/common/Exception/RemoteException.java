package com.hhxy.huazi.common.exception;

import com.hhxy.huazi.common.errorcode.BaseErrorCode;
import com.hhxy.huazi.common.errorcode.IErrorCode;

import java.io.Serial;

/**
 * 远程服务调用异常，例如模型接口或外部 MCP 服务不可用。
 * 原始消息与异常链用于日志排查，全局处理器对外返回通用提示。
 */
public class RemoteException extends AbstractException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RemoteException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable cause, IErrorCode errorCode) {
        super(message, cause, errorCode);
    }
}
