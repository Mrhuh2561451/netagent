package com.hhxy.huazi.common;

import com.hhxy.huazi.common.errorcode.IErrorCode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;//成功

    public static final int ERROR = 500;//失败

    private int code;

    private String msg;

    private T data;

    //成功
    public static <T> Result<T> success() {
        return success(null);
    }

    //成功带data
    public static <T> Result<T> success(T data) {
        return success(data, "success");
    }

    //成功带data+msg
    public static <T> Result<T> success(T data, String msg) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    /**
     * 按业务错误码和数据构造错误响应，无数据时传 null。
     * 使用三个参数，避免改变 error(data, msg) 中整数数据的含义。
     */
    public static <T> Result<T> error(int code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    /**
     * 使用错误码定义中的默认消息；null 保持普通空数据错误响应的语义。
     */
    public static <T> Result<T> error(IErrorCode errorCode) {
        if (errorCode == null) {
            return error(ERROR, "error", null);
        }
        return error(errorCode.code(), errorCode.message(), null);
    }

    // 失败 data
    public static <T> Result<T> error(T data) {
        return error(data, "error");
    }

    // 失败带data+msg
    public static <T> Result<T> error(T data, String msg) {
        return error(ERROR, msg, data);
    }
}
