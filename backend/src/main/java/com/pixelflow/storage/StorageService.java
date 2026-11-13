package com.pixelflow.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.pixelflow.asset.ImageInspector;
import com.pixelflow.common.ApiException;
import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.PixelFlowProperties;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Owns the storage root. Every key is resolved and normalized inside the root; user-supplied server
 * paths are never accepted. Demo covers/results are packaged read-only classpath resources.
 */
@Service
public class StorageService {

    private final Path root;
    private final Path tempDir;

    public StorageService(PixelFlowProperties properties) {
        this.root = properties.storageRootPath();
        this.tempDir = root.resolve("tmp");
        try {
            Files.createDirectories(root);
            Files.createDirectories(tempDir);
            Files.createDirectories(root.resolve("assets"));
            Files.createDirectories(root.resolve("results"));
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化存储目录: " + root, e);
        }
    }

    public record StoredFile(String storageKey, String mimeType, long sizeBytes, int width, int height) {
    }

    public Path root() {
        return root;
    }

    /** Resolves a key inside the root and rejects traversal attempts. */
    public Path resolve(String storageKey) {
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "非法的存储路径");
        }
        return candidate;
    }

    public StoredFile writeAsset(byte[] bytes, String mimeType) {
        String key = "assets/" + UUID.randomUUID() + extensionFor(mimeType);
        Path target = resolve(key);
        writeAtomically(target, bytes);
        return new StoredFile(key, mimeType, bytes.length, 0, 0);
    }

    public StoredFile writeResultFromDemo(String taskId, String demoKey) {
        byte[] bytes = readDemoResource(demoKey);
        ImageInspector.ImageInfo info;
        try {
            info = ImageInspector.inspect(bytes);
        } catch (ImageInspector.InvalidImageException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.RESULT_WRITE_FAILED,
                    "预置结果素材不可用");
        }
        String key = "results/" + taskId + extensionFor(info.mimeType());
        writeAtomically(resolve(key), bytes);
        return new StoredFile(key, info.mimeType(), bytes.length, info.width(), info.height());
    }

    public byte[] readDemoResource(String demoKey) {
        ClassPathResource resource = new ClassPathResource(demoKey);
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.RESULT_FILE_MISSING, "文件不存在");
        }
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "读取资源失败");
        }
    }

    public byte[] read(String storageKey) {
        Path path = resolve(storageKey);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.RESULT_FILE_MISSING, "文件不存在");
        }
    }

    public boolean existsReadable(String storageKey, long expectedSize) {
        Path path = resolve(storageKey);
        try {
            return Files.isRegularFile(path) && Files.isReadable(path) && Files.size(path) > 0
                    && (expectedSize <= 0 || Files.size(path) == expectedSize);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Removes exactly one file created by this request. Directory-level cleanup is intentionally
     * absent: it could delete other users' assets.
     */
    public void deleteSingleFile(String storageKey) {
        try {
            Path path = resolve(storageKey);
            if (Files.isRegularFile(path)) {
                Files.delete(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // best effort; an orphan file is preferable to deleting the wrong path
        }
    }

    private void writeAtomically(Path target, byte[] bytes) {
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(tempDir, "pf-", ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Only this request's own temp file is removed; nothing else is ever cleaned up.
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // an orphan temp file is harmless
                }
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.RESULT_WRITE_FAILED,
                    "写入文件失败");
        }
    }

    private static String extensionFor(String mimeType) {
        return ImageInspector.PNG.equals(mimeType) ? ".png" : ".jpg";
    }
}
