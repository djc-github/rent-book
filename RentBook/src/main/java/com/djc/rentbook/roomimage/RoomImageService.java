package com.djc.rentbook.roomimage;

import com.djc.rentbook.property.PropertyMapper;
import com.djc.rentbook.storage.StorageService;
import com.djc.rentbook.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class RoomImageService {
    private static final Logger log = LoggerFactory.getLogger(RoomImageService.class);

    private final RoomImageMapper mapper;
    private final PropertyMapper propertyMapper;
    private final StorageService storageService;

    public RoomImageService(RoomImageMapper mapper, PropertyMapper propertyMapper, StorageService storageService) {
        this.mapper = mapper;
        this.propertyMapper = propertyMapper;
        this.storageService = storageService;
    }

    public List<Map<String, Object>> list(Long roomId) {
        assertRoomExists(roomId);
        return mapper.listByRoom(roomId);
    }

    @Transactional
    public Map<String, Object> replace(Long roomId, MultipartFile file) {
        assertRoomExists(roomId);
        RoomImageRecord old = mapper.findActiveByRoom(roomId);
        StoredFile stored = storageService.storeRoomImage(roomId, file);
        if (old != null) {
            mapper.softDeleteByRoom(roomId);
            storageService.delete(old.getStorageKey());
            if (old.getThumbnailStorageKey() != null && !old.getThumbnailStorageKey().equals(old.getStorageKey())) {
                storageService.delete(old.getThumbnailStorageKey());
            }
        }
        RoomImageRecord image = new RoomImageRecord();
        image.setRoomId(roomId);
        image.setStorageKey(stored.storageKey());
        image.setUrl(stored.url());
        image.setThumbnailStorageKey(stored.thumbnailStorageKey());
        image.setThumbnailUrl(stored.thumbnailUrl());
        image.setOriginalName(file.getOriginalFilename());
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.sizeBytes());
        image.setSortOrder(1);
        mapper.create(image);
        log.info("Replaced room image roomId={}, imageId={}, url={}, thumbnailUrl={}",
                roomId, image.getId(), image.getUrl(), image.getThumbnailUrl());
        return Map.of("id", image.getId(), "url", image.getUrl(), "thumbnailUrl", image.getThumbnailUrl());
    }

    @Transactional
    public void delete(Long roomId, Long imageId) {
        RoomImageRecord image = mapper.findActive(roomId, imageId);
        if (image == null) {
            throw new IllegalArgumentException("房间图片不存在");
        }
        mapper.softDelete(roomId, imageId);
        storageService.delete(image.getStorageKey());
        if (image.getThumbnailStorageKey() != null && !image.getThumbnailStorageKey().equals(image.getStorageKey())) {
            storageService.delete(image.getThumbnailStorageKey());
        }
        log.info("Deleted room image roomId={}, imageId={}", roomId, imageId);
    }

    @Transactional
    public void deleteAllForRoom(Long roomId) {
        RoomImageRecord image = mapper.findActiveByRoom(roomId);
        if (image == null) {
            return;
        }
        mapper.softDeleteByRoom(roomId);
        storageService.delete(image.getStorageKey());
        if (image.getThumbnailStorageKey() != null && !image.getThumbnailStorageKey().equals(image.getStorageKey())) {
            storageService.delete(image.getThumbnailStorageKey());
        }
        log.info("Deleted all room images roomId={}", roomId);
    }

    @Transactional
    public void deleteAllForRooms(List<Long> roomIds) {
        for (Long roomId : roomIds) {
            deleteAllForRoom(roomId);
        }
    }

    private void assertRoomExists(Long roomId) {
        if (propertyMapper.findRoomRecord(roomId) == null) {
            throw new IllegalArgumentException("房间不存在");
        }
    }
}
