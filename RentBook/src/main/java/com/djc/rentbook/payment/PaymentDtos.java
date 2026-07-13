package com.djc.rentbook.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentRequest(
            @NotNull Long contractId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotNull LocalDate paidDate,
            @Positive BigDecimal amount,
            String method,
            String receiptNo,
            String notes
    ) {}

    public record PaymentPage(
            List<Map<String, Object>> rows,
            String nextCursor,
            boolean hasMore
    ) {}
}
