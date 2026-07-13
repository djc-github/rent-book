package com.djc.rentbook.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StoredFile storeRoomImage(Long roomId, MultipartFile file);

    void delete(String storageKey);
}
