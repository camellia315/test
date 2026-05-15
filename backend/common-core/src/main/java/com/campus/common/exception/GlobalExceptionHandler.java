package com.campus.common.exception;

import com.campus.common.api.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ApiResponse.fail(413, "上传文件过大，单张图片请控制在 5MB 以内。");
    }

    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMultipartException(MultipartException ex) {
        String message = ex.getMessage();
        if (message != null && (message.toLowerCase().contains("size") || message.contains("too large"))) {
            return ApiResponse.fail(413, "上传文件过大，单张图片请控制在 5MB 以内。");
        }
        return ApiResponse.fail(400, "上传请求无效，请重试。");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        if (isDbConnectionError(ex)) {
            return ApiResponse.fail(500, "数据库连接失败，请检查 MySQL 用户名、密码和服务状态。");
        }
        return ApiResponse.fail(500, ex.getMessage());
    }

    private boolean isDbConnectionError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if ("org.springframework.jdbc.CannotGetJdbcConnectionException".equals(className)
                    || (message != null && message.contains("Access denied for user"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
