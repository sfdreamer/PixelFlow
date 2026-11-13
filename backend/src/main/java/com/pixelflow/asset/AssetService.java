package com.pixelflow.asset;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.pixelflow.asset.mapper.AssetMapper;
import com.pixelflow.catalog.CatalogService;
import com.pixelflow.catalog.Template;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.Uuids;
import com.pixelflow.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);
    private static final int MAX_ORIGINAL_NAME = 255;

    private final AssetMapper assetMapper;
    private final CatalogService catalogService;
    private final StorageService storageService;

    public AssetService(AssetMapper assetMapper, CatalogService catalogService, StorageService storageService) {
        this.assetMapper = assetMapper;
        this.catalogService = catalogService;
        this.storageService = storageService;
    }

    public record AssetView(String id, String templateId, String originalName, String mimeType, long sizeBytes,
                            int width, int height, String previewUrl, Instant createdAt) {
    }

    public AssetView upload(String userId, String templateIdRaw, MultipartFile file) {
        String templateId = Uuids.require(templateIdRaw, "templateId");
        Template template = catalogService.requireTemplate(templateId);
        if (file == null) {
            throw ApiException.badRequest(ErrorCodes.INVALID_REQUEST, "缺少上传文件");
        }
        if (file.isEmpty() || file.getSize() <= 0) {
            throw ApiException.unprocessable(ErrorCodes.INVALID_IMAGE, "文件内容为空");
        }
        long maxBytes = template.getMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE,
                    "文件超过大小限制：" + maxBytes + " 字节");
        }
        List<String> accepted = catalogService.parseMimeTypes(template.getAcceptedMimeTypes());
        String declared = normalizeContentType(file.getContentType());
        if (declared == null || accepted.stream().noneMatch(a -> a.equalsIgnoreCase(declared))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                    "仅支持 JPEG 或 PNG 图片");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCodes.INVALID_REQUEST, "读取上传文件失败");
        }
        if (bytes.length > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE,
                    "文件超过大小限制：" + maxBytes + " 字节");
        }

        ImageInspector.ImageInfo info;
        try {
            info = ImageInspector.inspect(bytes);
        } catch (ImageInspector.DimensionsExceededException e) {
            throw ApiException.unprocessable(ErrorCodes.IMAGE_DIMENSIONS_EXCEEDED, e.getMessage());
        } catch (ImageInspector.InvalidImageException e) {
            throw ApiException.unprocessable(ErrorCodes.INVALID_IMAGE, e.getMessage());
        }
        if (!info.mimeType().equalsIgnoreCase(declared)) {
            throw ApiException.unprocessable(ErrorCodes.INVALID_IMAGE, "文件内容与声明的图片类型不一致");
        }
        try {
            ImageInspector.requireDecodable(bytes);
        } catch (ImageInspector.InvalidImageException e) {
            throw ApiException.unprocessable(ErrorCodes.INVALID_IMAGE, e.getMessage());
        }

        StorageService.StoredFile stored = storageService.writeAsset(bytes, info.mimeType());

        Asset asset = new Asset();
        asset.setId(UUID.randomUUID().toString());
        asset.setUserId(userId);
        asset.setTemplateId(templateId);
        asset.setStorageKey(stored.storageKey());
        asset.setOriginalName(sanitizeName(file.getOriginalFilename()));
        asset.setMimeType(info.mimeType());
        asset.setSizeBytes(bytes.length);
        asset.setWidth(info.width());
        asset.setHeight(info.height());
        asset.setSha256(sha256(bytes));
        asset.setCreatedAt(Instant.now());

        try {
            assetMapper.insert(asset);
        } catch (RuntimeException e) {
            // Only the single file created above may be removed; no directory-level cleanup.
            storageService.deleteSingleFile(stored.storageKey());
            log.error("asset persist failed, removed the file created by this request: {}", stored.storageKey(), e);
            throw e;
        }
        return toView(asset);
    }

    public Asset requireOwned(String assetIdRaw, String userId) {
        String assetId = Uuids.require(assetIdRaw, "assetId");
        Asset asset = assetMapper.findByIdAndUser(assetId, userId);
        if (asset == null) {
            throw ApiException.notFound(ErrorCodes.ASSET_NOT_FOUND, "素材不存在或不可访问");
        }
        return asset;
    }

    public AssetView toView(Asset asset) {
        return new AssetView(asset.getId(), asset.getTemplateId(), asset.getOriginalName(), asset.getMimeType(),
                asset.getSizeBytes(), asset.getWidth(), asset.getHeight(),
                "/api/v1/assets/" + asset.getId() + "/content", asset.getCreatedAt());
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String value = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /** The client filename is display metadata only: strip path segments and control characters. */
    static String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "image";
        }
        return name.length() > MAX_ORIGINAL_NAME ? name.substring(0, MAX_ORIGINAL_NAME) : name;
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
