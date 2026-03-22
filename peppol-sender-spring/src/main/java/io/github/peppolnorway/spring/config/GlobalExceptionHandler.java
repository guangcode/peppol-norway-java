package io.github.peppolnorway.spring.config;

import io.github.peppolnorway.exception.InvoiceValidationException;
import io.github.peppolnorway.spring.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * 将未捕获的异常统一转换为标准 {@link ApiResponse} 格式返回，
 * 避免 Spring 默认的 HTML 错误页面暴露给 API 消费者。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvoiceValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(InvoiceValidationException ex) {
        log.warn("发票校验失败：{}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数错误：{}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("INVALID_ARGUMENT", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("系统内部异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR",
                        "系统内部错误，请联系管理员。详情：" + ex.getMessage()));
    }
}
