package com.hhxy.huazi.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.hhxy.huazi.common.Result;
import com.hhxy.huazi.common.errorcode.BaseErrorCode;
import com.hhxy.huazi.common.errorcode.IErrorCode;
import com.hhxy.huazi.common.exception.AbstractException;
import com.hhxy.huazi.common.exception.ClientException;
import com.hhxy.huazi.common.exception.RemoteException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器，作用于 Spring MVC 请求处理链。
 * 业务错误码写入 Result，HTTP 状态码单独设置；未知异常详情不返回客户端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private String maxFileSize = "1MB";

    @Value("${spring.servlet.multipart.max-request-size:10MB}")
    private String maxRequestSize = "10MB";

    /**
     * 统一接住客户端、服务端和远程调用异常。
     */
    @ExceptionHandler(AbstractException.class)
    public ResponseEntity<Result<Void>> handleApplicationException(
            AbstractException ex, HttpServletRequest request) {
        if (ex instanceof ClientException) {
            log.warn("客户端请求错误: {} {}, code={}", request.getMethod(), request.getRequestURI(), ex.getErrorCode());
            return error(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getErrorMessage());
        }

        log.error("业务处理异常: {} {}, code={}", request.getMethod(), request.getRequestURI(), ex.getErrorCode(), ex);
        if (ex instanceof RemoteException) {
            return error(HttpStatus.BAD_GATEWAY, ex.getErrorCode(), BaseErrorCode.REMOTE_ERROR.message());
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getErrorCode(), BaseErrorCode.SERVICE_ERROR.message());
    }

    /**
     * 同时覆盖表单绑定和 MethodArgumentNotValidException 请求体校验异常。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleValidationException(BindException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("请求参数校验失败");
        return error(HttpStatus.BAD_REQUEST, BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("请求参数校验失败");
        return error(HttpStatus.BAD_REQUEST, BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleMalformedRequest() {
        return error(HttpStatus.BAD_REQUEST, BaseErrorCode.CLIENT_ERROR.code(), "请求内容格式或参数类型不正确");
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin() {
        return error(HttpStatus.UNAUTHORIZED, BaseErrorCode.NOT_LOGIN_ERROR);
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public ResponseEntity<Result<Void>> handlePermissionDenied() {
        return error(HttpStatus.FORBIDDEN, BaseErrorCode.PERMISSION_DENIED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadSizeExceeded() {
        String message = "上传大小超过限制：单个文件最大 " + maxFileSize + "，单次请求最大 " + maxRequestSize;
        return error(HttpStatus.PAYLOAD_TOO_LARGE, BaseErrorCode.UPLOAD_SIZE_EXCEEDED.code(), message);
    }

    /**
     * 保留 Spring MVC 已确定的 HTTP 状态及响应头，其余异常统一兜底。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            boolean clientError = errorResponse.getStatusCode().is4xxClientError();
            if (!clientError) {
                log.error("请求处理异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
            }
            Result<Void> result = clientError
                    ? Result.error(BaseErrorCode.CLIENT_ERROR.code(), "请求无法处理，请检查请求地址、方法和参数", null)
                    : Result.error(BaseErrorCode.SERVICE_ERROR);
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(result);
        }

        log.error("请求处理异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, BaseErrorCode.SERVICE_ERROR);
    }

    private ResponseEntity<Result<Void>> error(HttpStatus status, IErrorCode errorCode) {
        return error(status, errorCode.code(), errorCode.message());
    }

    private ResponseEntity<Result<Void>> error(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(Result.error(code, message, null));
    }
}
