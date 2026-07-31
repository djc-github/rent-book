package com.djc.rentbook.payment;

import com.djc.rentbook.common.ApiResponse;
import com.djc.rentbook.mutation.MutationOperation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PaymentDtos.PaymentPage> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.list(from, to, propertyId, minAmount, maxAmount, cursor, limit));
    }

    @PostMapping
    @MutationOperation(module = "收租", action = "新增收租记录")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody PaymentDtos.PaymentRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @DeleteMapping("/{id}")
    @MutationOperation(module = "收租", action = "撤销收租记录")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
