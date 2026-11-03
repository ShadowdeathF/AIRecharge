package com.orionkey.finance;

import com.orionkey.common.ApiResponse;
import com.orionkey.exception.BusinessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="com.orionkey.finance")
public class FinanceExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> business(BusinessException e){return ResponseEntity.status(e.getHttpStatus()).body(ApiResponse.error(e.getCode(),e.getMessage(),e.getParams()));}
    @ExceptionHandler({DataIntegrityViolationException.class,ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<?>> conflict(Exception e){return ResponseEntity.status(409).body(ApiResponse.error(10001,"记录已变更或业务单号重复，请刷新后核对；不要重复提交"));}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<?>> invalid(Exception e){return ResponseEntity.badRequest().body(ApiResponse.error(10001,"输入格式无效，请核对金额、日期与必填信息"));}
}
