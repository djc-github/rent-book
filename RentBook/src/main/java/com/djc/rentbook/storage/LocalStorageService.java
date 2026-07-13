package com.djc.rentbook.storage;

import com.djc.rentbook.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final UploadProperties properties;
    private final Path root;

    public LocalStorageService(UploadProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile storeRoomImage(Long roomId, MultipartFile file) {
        validate(file);
        LocalDate now = LocalDate.now();
        String contentType = normalizeContentType(file.getContentType());
        String storageKey = "rooms/%d/%02d/room-%d-%s%s".formatted(
                now.getYear(),
                now.getMonthValue(),
                roomId,
                UUID.randomUUID(),
                EXTENSIONS.get(contentType)
        );
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件存储路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String url = properties.normalizedPublicPath() + "/" + storageKey.replace('\\', '/');
            Thumbnail thumbnail = createThumbnail(roomId, target, storageKey, contentType);
            log.info("Stored room image roomId={}, storageKey={}, thumbnailKey={}, sizeBytes={}",
                    roomId, storageKey, thumbnail.storageKey(), file.getSize());
            return new StoredFile(storageKey, url, thumbnail.storageKey(), thumbnail.url(), contentType, file.getSize());
        } catch (IOException ex) {
            throw new IllegalStateException("保存图片失败，请稍后重试", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            log.warn("Skip deleting image outside upload root, storageKey={}", storageKey);
            return;
        }
        try {
            Files.deleteIfExists(target);
            log.info("Deleted stored file storageKey={}", storageKey);
        } catch (IOException ex) {
            log.warn("Failed to delete stored file storageKey={}", storageKey, ex);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择房间图片");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new IllegalArgumentException("图片不能超过" + properties.getMaxFileSizeMb() + "MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("只支持 JPG、PNG、WEBP 图片");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
    }

    private Thumbnail createThumbnail(Long roomId, Path source, String storageKey, String contentType) {
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            return originalAsThumbnail(storageKey);
        }
        try {
            BufferedImage sourceImage = ImageIO.read(source.toFile());
            if (sourceImage == null) {
                log.warn("Skip thumbnail because image cannot be decoded roomId={}, storageKey={}", roomId, storageKey);
                return originalAsThumbnail(storageKey);
            }
            int maxWidth = properties.normalizedThumbnailMaxWidth();
            double scale = Math.min(1D, (double) maxWidth / sourceImage.getWidth());
            int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
            BufferedImage thumbnailImage = new BufferedImage(width, height, "image/png".equals(contentType)
                    ? BufferedImage.TYPE_INT_ARGB
                    : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = thumbnailImage.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(sourceImage, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            String thumbnailKey = thumbnailKey(storageKey, contentType);
            Path thumbnailTarget = root.resolve(thumbnailKey).normalize();
            if (!thumbnailTarget.startsWith(root)) {
                log.warn("Skip thumbnail outside upload root roomId={}, thumbnailKey={}", roomId, thumbnailKey);
                return originalAsThumbnail(storageKey);
            }
            Files.createDirectories(thumbnailTarget.getParent());
            boolean written = ImageIO.write(thumbnailImage, "image/png".equals(contentType) ? "png" : "jpg", thumbnailTarget.toFile());
            if (!written) {
                log.warn("Skip thumbnail because no writer is available roomId={}, storageKey={}", roomId, storageKey);
                return originalAsThumbnail(storageKey);
            }
            String thumbnailUrl = properties.normalizedPublicPath() + "/" + thumbnailKey.replace('\\', '/');
            log.debug("Created thumbnail roomId={}, thumbnailKey={}, width={}, height={}", roomId, thumbnailKey, width, height);
            return new Thumbnail(thumbnailKey, thumbnailUrl);
        } catch (IOException ex) {
            log.warn("Failed to create thumbnail roomId={}, storageKey={}", roomId, storageKey, ex);
            return originalAsThumbnail(storageKey);
        }
    }

    private Thumbnail originalAsThumbnail(String storageKey) {
        String url = properties.normalizedPublicPath() + "/" + storageKey.replace('\\', '/');
        return new Thumbnail(storageKey, url);
    }

    private String thumbnailKey(String storageKey, String contentType) {
        int slash = storageKey.lastIndexOf('/');
        String dir = slash >= 0 ? storageKey.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
        String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        int dot = fileName.lastIndexOf('.');
        String baseName = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return dir + "thumb-" + baseName + extension;
    }

    private record Thumbnail(String storageKey, String url) {
    }
}
