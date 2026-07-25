package com.djc.rentbook.tenant;

import com.djc.rentbook.common.ApiResponse;
import com.djc.rentbook.mutation.MutationOperation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {
    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @PostMapping
    @MutationOperation(module = "租客", action = "新增租客")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody TenantDtos.TenantRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    @MutationOperation(module = "租客", action = "修改租客")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody TenantDtos.TenantRequest request) {
        service.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @MutationOperation(module = "租客", action = "删除租客")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
