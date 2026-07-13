package com.djc.rentbook.contract;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ContractDtos {
    private ContractDtos() {}

    public record ContractRequest(
            @NotNull Long roomId,
            @NotNull Long tenantId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Positive BigDecimal rentAmount,
            @PositiveOrZero BigDecimal depositAmount,
            @Positive Integer payCycleMonths,
            @NotNull LocalDate nextDueDate,
            String contractNo,
            String notes
    ) {}

    public record TerminateRequest(
            @NotNull LocalDate endDate,
            String status,
            String roomStatus,
            String notes
    ) {}

    public record TransferRequest(
            @NotNull Long newTenantId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal rentAmount,
            BigDecimal depositAmount,
            LocalDate nextDueDate,
            String notes
    ) {}

    public record ChangeRoomRequest(
            @NotNull Long newRoomId,
            BigDecimal rentAmount,
            BigDecimal depositAmount,
            LocalDate nextDueDate,
            String notes
    ) {}

    public record RenewRequest(
            @NotNull LocalDate endDate,
            @NotNull LocalDate nextDueDate,
            @Positive Integer payCycleMonths,
            BigDecimal rentAmount,
            BigDecimal depositAmount,
            String notes
    ) {}
}
