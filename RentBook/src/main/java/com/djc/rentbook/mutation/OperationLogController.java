package com.djc.rentbook.mutation;

import com.djc.rentbook.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {
    private final OperationLogService service;

    public OperationLogController(OperationLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.list(module, status, cursorId, limit));
    }

    @GetMapping("/{traceId}/changes")
    public ApiResponse<List<Map<String, Object>>> changes(@PathVariable String traceId) {
        return ApiResponse.ok(service.listChanges(traceId));
    }
}
