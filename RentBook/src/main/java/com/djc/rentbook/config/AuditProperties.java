package com.djc.rentbook.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@Component
@ConfigurationProperties(prefix = "rentbook.audit")
public class AuditProperties {
    @Min(1)
    private int retentionYears = 3;
    @NotBlank
    private String zone = "Asia/Shanghai";

    public int getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(int retentionYears) {
        this.retentionYears = retentionYears;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
