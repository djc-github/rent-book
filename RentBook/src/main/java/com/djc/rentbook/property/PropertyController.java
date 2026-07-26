package com.djc.rentbook.property;

import com.djc.rentbook.common.ApiResponse;
import com.djc.rentbook.mutation.MutationOperation;
import com.djc.rentbook.roomimage.RoomImageService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {
    private final PropertyService service;
    private final RoomImageService roomImageService;

    public PropertyController(PropertyService service, RoomImageService roomImageService) {
        this.service = service;
        this.roomImageService = roomImageService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<PropertyDtos.PropertyDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/rooms")
    public ApiResponse<List<Map<String, Object>>> listRooms(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listRooms(status));
    }

    @PostMapping
    @MutationOperation(module = "房源", action = "新增房源")
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody PropertyDtos.PropertyCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    @MutationOperation(module = "房源", action = "修改房源")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PropertyDtos.PropertyCreateRequest request) {
        service.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @MutationOperation(module = "房源", action = "删除房源")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms")
    @MutationOperation(module = "房间", action = "新增房间")
    public ApiResponse<Map<String, Long>> createRoom(@Valid @RequestBody PropertyDtos.RoomCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.createRoom(request)));
    }

    @PutMapping("/rooms/{roomId}")
    @MutationOperation(module = "房间", action = "修改房间")
    public ApiResponse<Void> updateRoom(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomCreateRequest request) {
        service.updateRoom(roomId, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/rooms/{roomId}")
    @MutationOperation(module = "房间", action = "删除房间")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomId) {
        service.deleteRoom(roomId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/rooms/{roomId}/status")
    @MutationOperation(module = "房间", action = "修改房态")
    public ApiResponse<Void> updateRoomStatus(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomStatusRequest request) {
        service.updateRoomStatus(roomId, request.status());
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms/{roomId}/rent")
    @MutationOperation(module = "房间", action = "设置出租与收租规则")
    public ApiResponse<Void> startRoomRent(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomRentRequest request) {
        service.startRoomRent(roomId, request);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/rooms/{roomId}/next-due-date")
    @MutationOperation(module = "收租", action = "调整下次应收日")
    public ApiResponse<Void> adjustRoomNextDueDate(
            @PathVariable Long roomId,
            @Valid @RequestBody PropertyDtos.RoomNextDueDateRequest request) {
        service.adjustRoomNextDueDate(roomId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms/{roomId}/collect")
    @MutationOperation(module = "收租", action = "登记收租")
    public ApiResponse<Map<String, Long>> collectRoomRent(@PathVariable Long roomId,
                                                          @Valid @RequestBody PropertyDtos.RoomCollectRentRequest request) {
        return ApiResponse.ok(Map.of("id", service.collectRoomRent(roomId, request)));
    }

    @GetMapping("/rooms/{roomId}/settlement-preview")
    public ApiResponse<Map<String, Object>> settlementPreview(
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate moveOutDate) {
        return ApiResponse.ok(service.settlementPreview(roomId, moveOutDate));
    }

    @PostMapping("/rooms/{roomId}/settle")
    @MutationOperation(module = "房间", action = "退租结算")
    public ApiResponse<Map<String, Long>> settleRoomRent(
            @PathVariable Long roomId,
            @Valid @RequestBody PropertyDtos.RoomSettlementRequest request) {
        return ApiResponse.ok(Map.of("id", service.settleRoomRent(roomId, request)));
    }

    @GetMapping("/rooms/{roomId}/images")
    public ApiResponse<List<Map<String, Object>>> listRoomImages(@PathVariable Long roomId) {
        return ApiResponse.ok(roomImageService.list(roomId));
    }

    @PostMapping("/rooms/{roomId}/images")
    @MutationOperation(module = "房间图片", action = "上传或替换图片")
    public ApiResponse<Map<String, Object>> replaceRoomImage(@PathVariable Long roomId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(roomImageService.replace(roomId, file));
    }

    @DeleteMapping("/rooms/{roomId}/images/{imageId}")
    @MutationOperation(module = "房间图片", action = "删除图片")
    public ApiResponse<Void> deleteRoomImage(@PathVariable Long roomId, @PathVariable Long imageId) {
        roomImageService.delete(roomId, imageId);
        return ApiResponse.ok(null);
    }
}
