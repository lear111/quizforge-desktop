package io.quizforge.core.asset;

import java.nio.file.Path;
import java.util.Objects;

/** An asset's identity and location within one workspace. The path is never absolute. */
public record Asset(String assetId, AssetType assetType, String currentPath, String title,
        String contentId, String schemaVersion) {
    public Asset(String assetId, AssetType assetType, String currentPath, String title) {
        this(assetId, assetType, currentPath, title, null, "1.0");
    }

    public Asset {
        if (assetId == null || assetId.isBlank() || assetId.length() > 255
                || assetId.indexOf('\n') >= 0 || assetId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Asset ID is invalid");
        }
        Objects.requireNonNull(assetType);
        if (currentPath == null || currentPath.isBlank() || currentPath.indexOf('\\') >= 0
                || Path.of(currentPath).isAbsolute() || currentPath.startsWith("/")
                || currentPath.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Asset path must be workspace-relative");
        }
        for (String segment : currentPath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Asset path is invalid");
            }
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Asset title is required");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("Asset schema version is required");
        }
        if (contentId != null && !(assetType == AssetType.STANDARD_DOCUMENT
                && contentId.matches("qfd:v[12]:[0-9a-f]{64}")
                || assetType == AssetType.QUESTION_BANK
                && contentId.matches("qfb:v1:[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Asset content ID is invalid");
        }
        assetId = assetId.trim();
        title = title.trim();
        schemaVersion = schemaVersion.trim();
    }
}
