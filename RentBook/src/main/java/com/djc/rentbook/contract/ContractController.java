package com.djc.rentbook.contract;

import com.djc.rentbook.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(status));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ContractDtos.ContractRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        service.updateStatus(id, request.status());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable Long id, @Valid @RequestBody ContractDtos.TerminateRequest request) {
        service.terminate(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Void> transfer(@PathVariable Long id, @Valid @RequestBody ContractDtos.TransferRequest request) {
        service.transfer(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/change-room")
    public ApiResponse<Void> changeRoom(@PathVariable Long id, @Valid @RequestBody ContractDtos.ChangeRoomRequest request) {
        service.changeRoom(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/renew")
    public ApiResponse<Void> renew(@PathVariable Long id, @Valid @RequestBody ContractDtos.RenewRequest request) {
        service.renew(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    public record StatusRequest(@NotBlank String status) {}
}
