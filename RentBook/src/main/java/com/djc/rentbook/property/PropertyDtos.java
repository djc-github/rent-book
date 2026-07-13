package com.djc.rentbook.property;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PropertyDtos {
    private PropertyDtos() {}

    public record PropertyCreateRequest(
            String name,
            @NotBlank String address,
            String district,
            String landlordName,
            String landlordPhone,
            String manager,
            String notes
    ) {}

    public record RoomCreateRequest(
            @NotNull Long propertyId,
            @NotBlank String roomNo,
            String floor,
            @PositiveOrZero BigDecimal area,
            @PositiveOrZero BigDecimal rentAmount,
            @PositiveOrZero BigDecimal depositAmount,
            String status,
            Integer payCycleMonths,
            LocalDate nextDueDate,
            String orientation,
            String tags,
            String notes
    ) {}

    public record RoomStatusRequest(@NotBlank String status) {}

    public record RoomRentRequest(
            @PositiveOrZero BigDecimal rentAmount,
            @PositiveOrZero BigDecimal depositAmount,
            @NotNull Integer payCycleMonths,
            @NotNull LocalDate leaseStartDate,
            @NotNull LocalDate leaseEndDate,
            @NotNull LocalDate nextDueDate,
            String notes
    ) {}

    public record RoomCollectRentRequest(
            Integer months,
            LocalDate paidDate,
            BigDecimal amount,
            String method,
            String notes
    ) {}

    public record PropertyDetail(Map<String, Object> property, List<Map<String, Object>> rooms) {}
}
