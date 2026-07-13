package com.djc.rentbook.storage;

public record StoredFile(
        String storageKey,
        String url,
        String thumbnailStorageKey,
        String thumbnailUrl,
        String contentType,
        long sizeBytes
) {
}
