package com.djc.rentbook.property;

import com.djc.rentbook.common.ApiResponse;
import com.djc.rentbook.roomimage.RoomImageService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody PropertyDtos.PropertyCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PropertyDtos.PropertyCreateRequest request) {
        service.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms")
    public ApiResponse<Map<String, Long>> createRoom(@Valid @RequestBody PropertyDtos.RoomCreateRequest request) {
        return ApiResponse.ok(Map.of("id", service.createRoom(request)));
    }

    @PutMapping("/rooms/{roomId}")
    public ApiResponse<Void> updateRoom(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomCreateRequest request) {
        service.updateRoom(roomId, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomId) {
        service.deleteRoom(roomId);
        return ApiResponse.ok(null);
    }

    @PatchMapping("/rooms/{roomId}/status")
    public ApiResponse<Void> updateRoomStatus(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomStatusRequest request) {
        service.updateRoomStatus(roomId, request.status());
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms/{roomId}/rent")
    public ApiResponse<Void> startRoomRent(@PathVariable Long roomId, @Valid @RequestBody PropertyDtos.RoomRentRequest request) {
        service.startRoomRent(roomId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rooms/{roomId}/collect")
    public ApiResponse<Map<String, Long>> collectRoomRent(@PathVariable Long roomId, @RequestBody PropertyDtos.RoomCollectRentRequest request) {
        return ApiResponse.ok(Map.of("id", service.collectRoomRent(roomId, request)));
    }

    @GetMapping("/rooms/{roomId}/images")
    public ApiResponse<List<Map<String, Object>>> listRoomImages(@PathVariable Long roomId) {
        return ApiResponse.ok(roomImageService.list(roomId));
    }

    @PostMapping("/rooms/{roomId}/images")
    public ApiResponse<Map<String, Object>> replaceRoomImage(@PathVariable Long roomId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(roomImageService.replace(roomId, file));
    }

    @DeleteMapping("/rooms/{roomId}/images/{imageId}")
    public ApiResponse<Void> deleteRoomImage(@PathVariable Long roomId, @PathVariable Long imageId) {
        roomImageService.delete(roomId, imageId);
        return ApiResponse.ok(null);
    }
}
