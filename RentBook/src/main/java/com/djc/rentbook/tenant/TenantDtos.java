package com.djc.rentbook.tenant;

import jakarta.validation.constraints.NotBlank;

public final class TenantDtos {
    private TenantDtos() {}

    public record TenantRequest(
            @NotBlank String name,
            @NotBlank String phone,
            String idCard,
            String emergencyContact,
            String emergencyPhone,
            String source,
            String notes
    ) {}
}
