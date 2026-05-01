package com.sov.imhub.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "bad request"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "not found"));
    }

    /**
     * 并发或绕开预校验时的唯一键冲突（如 uk_bot_command），避免直接暴露 SQL 报错。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        Throwable c = e.getMostSpecificCause();
        String msg = c != null && c.getMessage() != null ? c.getMessage() : e.getMessage();
        if (msg != null && msg.contains("uk_bot_command")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error",
                            "该机器人下已存在相同命令。请换一个命令名，或在列表中点「编辑」修改已有查询。"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "数据冲突（如重复记录），请检查输入后重试。"));
    }
}
