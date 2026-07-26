package com.djc.rentbook.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("address", "房源地址"),
            Map.entry("propertyId", "所属房源"),
            Map.entry("roomNo", "房号"),
            Map.entry("status", "房间状态"),
            Map.entry("rentAmount", "月租金"),
            Map.entry("depositAmount", "押金"),
            Map.entry("leaseStartDate", "租期开始日期"),
            Map.entry("leaseEndDate", "租期结束日期"),
            Map.entry("nextDueDate", "下次收租日"),
            Map.entry("payCycleMonths", "几个月一收"),
            Map.entry("months", "收租月数"),
            Map.entry("paidDate", "收款日期"),
            Map.entry("amount", "收款金额"),
            Map.entry("method", "收款方式"),
            Map.entry("expectedNextDueDate", "原下次收租日"),
            Map.entry("moveOutDate", "实际退租日期"),
            Map.entry("settlementDate", "结算日期"),
            Map.entry("rentRefundAmount", "实际退还租金"),
            Map.entry("depositDeductionAmount", "押金扣款"),
            Map.entry("reason", "原因"),
            Map.entry("notes", "补充说明")
    );

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                .map(this::fieldValidationMessage)
                .collect(Collectors.joining("；"))
                : ex.getMessage();
        log.warn("Bad request: {}", message);
        return new ApiResponse<>(false, null, message, OffsetDateTime.now());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> database(DataAccessException ex) {
        log.error("Database operation failed", ex);
        return new ApiResponse<>(false, null, "操作没有保存成功，请稍后再试", OffsetDateTime.now());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> dataConflict(DataIntegrityViolationException ex) {
        String detail = rootMessage(ex);
        String message = detail.contains("uk_rent_payment_room_period_no_overlap")
                || detail.contains("已经登记过收租")
                ? "这段租期已经登记过收租，请刷新后核对"
                : "页面内容已经变化，请刷新后重新操作";
        log.warn("Database integrity conflict: {}", detail);
        return new ApiResponse<>(false, null, message, OffsetDateTime.now());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> uploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Uploaded file is too large: {}", ex.getMessage());
        return new ApiResponse<>(false, null, "图片太大，请压缩后再上传", OffsetDateTime.now());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> notFound(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        return new ApiResponse<>(false, null, "当前功能暂时不可用，请刷新后再试", OffsetDateTime.now());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unexpected(Exception ex) {
        log.error("Unexpected server error", ex);
        return new ApiResponse<>(false, null, "系统开小差了，请稍后再试", OffsetDateTime.now());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private String fieldValidationMessage(FieldError error) {
        String label = FIELD_LABELS.getOrDefault(error.getField(), "填写内容");
        String detail = switch (error.getCode() == null ? "" : error.getCode()) {
            case "NotBlank", "NotNull" -> "不能为空";
            case "Positive" -> "必须大于0";
            case "PositiveOrZero" -> "不能小于0";
            case "Min" -> "不能小于1";
            case "PastOrPresent" -> "不能晚于今天";
            case "Size" -> "内容过长";
            case "Digits" -> "金额格式不正确";
            default -> "填写有误";
        };
        return label + "：" + detail;
    }
}
