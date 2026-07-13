package com.djc.rentbook.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class HealthController {

    private final Optional<BuildProperties> buildProperties;
    private final String applicationName;

    public HealthController(Optional<BuildProperties> buildProperties,
                            @Value("${spring.application.name:RentBook}") String applicationName) {
        this.buildProperties = buildProperties;
        this.applicationName = applicationName;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(status("running"));
    }

    @RequestMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(status("ok")));
    }

    private Map<String, Object> status(String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", applicationName);
        body.put("status", status);
        body.put("version", buildProperties.map(BuildProperties::getVersion).orElse("dev"));
        body.put("time", OffsetDateTime.now());
        return body;
    }
}
