package com.djc.rentbook.property;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
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
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal rentAmount,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
            String status,
            Integer payCycleMonths,
            LocalDate nextDueDate,
            String orientation,
            String tags,
            String notes
    ) {}

    public record RoomStatusRequest(@NotBlank String status) {}

    public record RoomRentRequest(
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal rentAmount,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
            @NotNull @Min(1) Integer payCycleMonths,
            @NotNull LocalDate leaseStartDate,
            @NotNull LocalDate leaseEndDate,
            @NotNull LocalDate nextDueDate,
            String notes
    ) {}

    public record RoomCollectRentRequest(
            @Min(1) Integer months,
            @PastOrPresent LocalDate paidDate,
            @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            String method,
            String notes
    ) {}

    public record PropertyDetail(Map<String, Object> property, List<Map<String, Object>> rooms) {}
}
