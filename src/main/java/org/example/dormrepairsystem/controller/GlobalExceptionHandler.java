package org.example.dormrepairsystem.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 请求参数格式错误（请求体无法解析、参数类型不匹配、缺少必填参数）
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        log.warn("请求参数错误：{}", e.getMessage());
        return build("请求参数格式错误", HttpStatus.BAD_REQUEST);
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return build("请求方法不被支持", HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理运行时异常
     *
     * 只把异常细节写进服务端日志，返回给客户端的是通用提示，
     * 避免把 SQL 语句、类名之类的内部信息暴露出去。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常：", e);
        return build("请求处理失败，请稍后重试", HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理其余所有异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常：", e);
        return build("系统内部错误，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> build(String message, HttpStatus status) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return new ResponseEntity<>(result, status);
    }
}
