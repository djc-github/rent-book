package com.djc.rentbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rentbook.upload")
public class UploadProperties {
    private String baseDir = "./uploads";
    private String publicPath = "/uploads";
    private int maxRoomImages = 1;
    private long maxFileSizeMb = 5;
    private int thumbnailMaxWidth = 480;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public int getMaxRoomImages() {
        return maxRoomImages;
    }

    public void setMaxRoomImages(int maxRoomImages) {
        this.maxRoomImages = maxRoomImages;
    }

    public long getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(long maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public int getThumbnailMaxWidth() {
        return thumbnailMaxWidth;
    }

    public void setThumbnailMaxWidth(int thumbnailMaxWidth) {
        this.thumbnailMaxWidth = thumbnailMaxWidth;
    }

    public int normalizedThumbnailMaxWidth() {
        return Math.max(160, thumbnailMaxWidth);
    }

    public long maxFileSizeBytes() {
        return Math.max(1, maxFileSizeMb) * 1024 * 1024;
    }

    public String normalizedPublicPath() {
        String path = publicPath == null || publicPath.isBlank() ? "/uploads" : publicPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
