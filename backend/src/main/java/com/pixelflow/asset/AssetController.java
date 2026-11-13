package com.pixelflow.asset;

import com.pixelflow.common.ErrorCodes;
import com.pixelflow.common.ApiException;
import com.pixelflow.identity.CurrentUser;
import com.pixelflow.storage.StorageService;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final StorageService storageService;
    private final CurrentUser currentUser;

    public AssetController(AssetService assetService, StorageService storageService, CurrentUser currentUser) {
        this.assetService = assetService;
        this.storageService = storageService;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetService.AssetView> upload(@RequestParam("templateId") String templateId,
                                                         @RequestParam("file") MultipartFile file) {
        AssetService.AssetView view = assetService.upload(currentUser.requireUserId(), templateId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    /** Caller-owned asset bytes. Never cached: the URL is private. */
    @GetMapping("/{assetId}/content")
    public ResponseEntity<byte[]> content(@PathVariable("assetId") String assetId) {
        Asset asset = assetService.requireOwned(assetId, currentUser.requireUserId());
        if (!storageService.existsReadable(asset.getStorageKey(), asset.getSizeBytes())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.ASSET_NOT_FOUND, "素材文件不可用");
        }
        byte[] bytes = storageService.read(asset.getStorageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .contentLength(bytes.length)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }
}
