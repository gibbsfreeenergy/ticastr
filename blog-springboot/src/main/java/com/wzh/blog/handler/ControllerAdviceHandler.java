package com.wzh.blog.handler;

import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.ConflictException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.vo.Result;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.wzh.blog.enums.StatusCodeEnum.SYSTEM_ERROR;
import static com.wzh.blog.enums.StatusCodeEnum.VALID_ERROR;
import static com.wzh.blog.enums.StatusCodeEnum.NOT_FOUND;
import static com.wzh.blog.enums.StatusCodeEnum.CONFLICT;


/**
 * 全局异常处理
 *
 * @author yezhqiu
 * @date 2021/06/11
 **/
@Log4j2
@RestControllerAdvice
public class ControllerAdviceHandler {

    /**
     * 处理服务异常
     *
     * @param e 异常
     * @return 接口异常信息
     */
    @ExceptionHandler(value = BizException.class)
    public ResponseEntity<Result<?>> errorHandler(BizException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常
     *
     * @param e 异常
     * @return 接口异常信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> errorHandler(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(VALID_ERROR.getDesc());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(VALID_ERROR.getCode(), message));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Result<?>> errorHandler(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(NOT_FOUND.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Result<?>> errorHandler(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(CONFLICT.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> errorHandler(DataIntegrityViolationException exception) {
        log.warn("Database constraint rejected an API operation", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.fail(CONFLICT.getCode(), "数据已存在或仍被其他数据引用"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> errorHandler(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(VALID_ERROR.getCode(), VALID_ERROR.getDesc()));
    }

    /**
     * 处理系统异常
     *
     * @param e 异常
     * @return 接口异常信息
     */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<Result<?>> errorHandler(Exception e) {
        log.error("Unhandled API exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(SYSTEM_ERROR.getCode(), SYSTEM_ERROR.getDesc()));
    }

}
